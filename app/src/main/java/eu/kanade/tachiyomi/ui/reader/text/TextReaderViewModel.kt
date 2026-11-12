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
import eu.kanade.tachiyomi.tomo.ChapterLengths
import eu.kanade.tachiyomi.tomo.text.TextBlock
import eu.kanade.tachiyomi.tomo.text.TextCache
import eu.kanade.tachiyomi.tomo.text.TextSource
import eu.kanade.tachiyomi.tomo.text.classifyFailure
import eu.kanade.tachiyomi.tomo.text.message
import eu.kanade.tachiyomi.tomo.text.parseBlocks
import eu.kanade.tachiyomi.tomo.text.readEpubBlocks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.archive.epubReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.Format
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Loads the chapters of a text work (EPUB of the Local source, or AO3 pages cached for offline reading), moves between
 * them and saves the paragraph the user is on.
 */
@AssistedInject
class TextReaderViewModel(
    @Assisted private val mangaId: Long,
    @Assisted chapterId: Long,
    private val context: Context,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val sourceManager: SourceManager,
    private val updateChapter: UpdateChapter,
    private val upsertHistory: UpsertHistory,
    private val chapterLengths: ChapterLengths,
    private val textCache: TextCache,
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
    private var currentChapterId = chapterId
    private var lastSaved = -1

    init {
        open(chapterId)
    }

    fun open(chapterId: Long) {
        currentChapterId = chapterId
        _state.value = State.Loading
        viewModelScope.launch {
            _state.value = try {
                load(chapterId)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to open text chapter $chapterId" }
                State.Error(e.message)
            }
        }
    }

    private suspend fun load(chapterId: Long): State = withContext(Dispatchers.IO) {
        val manga = getManga.await(mangaId) ?: error("Series not found")
        val chapters = getChaptersByMangaId.await(mangaId).sortedWith(
            compareBy({
                it.chapterNumber
            }, { it.sourceOrder }),
        )
        val index = chapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 } ?: error("Chapter not found")
        val chapter = chapters[index]
        val blocks = when (val source = sourceManager.get(manga.source)) {
            is LocalSource -> {
                val format = source.getFormat(chapter.toSChapter()) as? Format.Epub ?: error("Not an EPUB")
                format.file.epubReader(context).use(::readEpubBlocks)
            }
            is TextSource -> parseBlocks(onlineHtml(source, chapter))
            else -> error("Not a text source")
        }
        chapterLengths.put(chapter.id, blocks.size)
        lastSaved = chapter.lastPageRead.toInt()
        State.Ready(
            title = manga.title,
            chapter = chapter,
            chapterIndex = index,
            chapters = chapters,
            blocks = blocks,
            startIndex = if (chapter.read) {
                0
            } else {
                chapter.lastPageRead.toInt().coerceIn(
                    0,
                    (blocks.size - 1).coerceAtLeast(0),
                )
            },
            bookmarked = chapter.bookmark,
        )
    }

    /** Fanfic chapters are fetched once and kept, so they read again without connection (RF-30h). */
    private suspend fun onlineHtml(source: TextSource, chapter: Chapter): String {
        textCache.read(chapter.id)?.let { return it }
        return try {
            source.getChapterHtml(chapter.url).also { textCache.write(chapter.id, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Exception(context.stringResource(classifyFailure(e).message(), source.name), e)
        }
    }

    /** Saves the first visible block a moment after the user stops scrolling; the end marks the chapter read. */
    fun onPosition(index: Int, atEnd: Boolean) {
        if (index == lastSaved && !atEnd) return
        saveJob?.cancel()
        val chapterId = currentChapterId
        saveJob = viewModelScope.launch {
            delay(1.seconds)
            save(chapterId, index, atEnd)
        }
    }

    fun flush(index: Int, atEnd: Boolean) {
        saveJob?.cancel()
        val chapterId = currentChapterId
        viewModelScope.launch(NonCancellable) { save(chapterId, index, atEnd) }
    }

    /** Opens the chapter [offset] away (-1 previous, +1 next); moving on marks the current one read. */
    fun move(offset: Int) {
        val ready = _state.value as? State.Ready ?: return
        val target = ready.chapters.getOrNull(ready.chapterIndex + offset) ?: return
        viewModelScope.launch(NonCancellable) {
            if (offset > 0) save(ready.chapter.id, ready.blocks.lastIndex.coerceAtLeast(0), atEnd = true)
            open(target.id)
        }
    }

    fun toggleBookmark() {
        val ready = _state.value as? State.Ready ?: return
        val bookmarked = !ready.bookmarked
        _state.update { ready.copy(bookmarked = bookmarked) }
        viewModelScope.launch { updateChapter.await(ChapterUpdate(ready.chapter.id) { bookmark = bookmarked }) }
    }

    private suspend fun save(chapterId: Long, index: Int, atEnd: Boolean) {
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
            val chapter: Chapter,
            val chapterIndex: Int,
            val chapters: List<Chapter>,
            val blocks: List<TextBlock>,
            val startIndex: Int,
            val bookmarked: Boolean,
        ) : State
    }
}
