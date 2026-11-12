package eu.kanade.tachiyomi.ui.reader.text

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.tomo.text.TextBlock
import eu.kanade.tachiyomi.tomo.text.readEpubBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.archive.epubReader
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.Format
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Loads a text chapter as blocks and saves the paragraph the user is on. */
@AssistedInject
class TextReaderViewModel(
    @Assisted private val mangaId: Long,
    @Assisted private val chapterId: Long,
    private val context: Context,
    private val getManga: GetManga,
    private val getChapter: GetChapter,
    private val sourceManager: SourceManager,
    private val updateChapter: UpdateChapter,
    private val upsertHistory: UpsertHistory,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long, chapterId: Long): TextReaderViewModel
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private var saveJob: Job? = null
    private var lastSaved = -1
    private var finished = false

    init {
        viewModelScope.launch {
            _state.value = try {
                load()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to open text chapter $chapterId" }
                State.Error(e.message)
            }
        }
    }

    private suspend fun load(): State = withContext(Dispatchers.IO) {
        val manga = getManga.await(mangaId) ?: error("Series not found")
        val chapter = getChapter.await(chapterId) ?: error("Chapter not found")
        val source = sourceManager.get(manga.source) as? LocalSource ?: error("Not a local series")
        val format = source.getFormat(chapter.toSChapter()) as? Format.Epub ?: error("Not an EPUB")
        val blocks = format.file.epubReader(context).use(::readEpubBlocks)
        finished = chapter.read
        lastSaved = chapter.lastPageRead.toInt()
        State.Ready(
            title = manga.title,
            chapterName = chapter.name,
            blocks = blocks,
            startIndex = chapter.lastPageRead.toInt().coerceIn(0, (blocks.size - 1).coerceAtLeast(0)),
        )
    }

    /** Saves the first visible block a moment after the user stops scrolling; the end marks the chapter read. */
    fun onPosition(index: Int, atEnd: Boolean) {
        if (index == lastSaved && (!atEnd || finished)) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1.seconds)
            save(index, atEnd)
        }
    }

    fun flush(index: Int, atEnd: Boolean) {
        saveJob?.cancel()
        viewModelScope.launch(NonCancellable) { save(index, atEnd) }
    }

    private suspend fun save(index: Int, atEnd: Boolean) {
        if (atEnd) finished = true
        lastSaved = index
        updateChapter.await(
            ChapterUpdate(chapterId) {
                lastPageRead = index
                if (atEnd) read = true
            },
        )
        upsertHistory.await(HistoryUpdate(chapterId, Clock.System.now(), sessionReadDuration = 0))
    }

    sealed interface State {
        data object Loading : State

        data class Error(val message: String?) : State

        @Immutable
        data class Ready(
            val title: String,
            val chapterName: String,
            val blocks: List<TextBlock>,
            val startIndex: Int,
        ) : State
    }
}
