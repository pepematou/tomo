package eu.kanade.tachiyomi.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.library.LibraryUpdateIssues
import eu.kanade.tachiyomi.tomo.ChapterLengths
import eu.kanade.tachiyomi.tomo.text.TEXT_SOURCE_IDS
import eu.kanade.tachiyomi.tomo.text.TextWorks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class HomeViewModel(
    getHistory: GetHistory,
    getUpdates: GetUpdates,
    libraryUpdateIssues: LibraryUpdateIssues,
    private val getNextChapters: GetNextChapters,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val chapterLengths: ChapterLengths,
    private val textWorks: TextWorks,
) : ViewModel() {

    private fun isText(mangaId: Long, sourceId: Long?) = sourceId in TEXT_SOURCE_IDS || textWorks.isText(mangaId)

    val state: StateFlow<State> = combine(
        getHistory.subscribe("").map { it.take(RECENT_COUNT) },
        getUpdates.subscribe(read = false, after = Clock.System.now() - 7.days).map { updates ->
            groupNewChapters(updates).map { it.copy(isText = isText(it.mangaId, it.sourceId)) }
        },
        libraryUpdateIssues.issues,
    ) { history, newChapters, issues ->
        State(
            isLoading = false,
            history = history,
            continueProgress = history.firstOrNull()?.let { readingProgress(it) },
            newChapters = newChapters,
            issues = issues,
        )
    }
        .catch { logcat(LogPriority.ERROR, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /**
     * The (manga id, chapter id) "Continue" opens: the same chapter if unfinished, otherwise the next one, and the
     * last one read when the series has no next chapter, rather than a "not found" message.
     */
    suspend fun chapterToContinue(history: HistoryWithRelations): Pair<Long, Long> {
        val next = getNextChapters.await(history.mangaId, history.chapterId, onlyUnread = false).firstOrNull()
        return next?.let { it.mangaId to it.id } ?: (history.mangaId to history.chapterId)
    }

    private suspend fun readingProgress(history: HistoryWithRelations): ReadingProgress {
        val chapters = getChaptersByMangaId.await(history.mangaId)
        return ReadingProgress(
            page = chapters.find { it.id == history.chapterId }?.lastPageRead?.plus(1) ?: 0,
            readChapters = chapters.count { it.read },
            totalChapters = chapters.size,
            chapterLength = chapterLengths[history.chapterId],
            isText = isText(history.mangaId, null),
        )
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val history: List<HistoryWithRelations> = emptyList(),
        val continueProgress: ReadingProgress? = null,
        val newChapters: List<NewChapters> = emptyList(),
        val issues: List<LibraryUpdateIssues.Issue> = emptyList(),
    )

    @Immutable
    data class NewChapters(
        val mangaId: Long,
        val title: String,
        val coverData: MangaCover,
        val count: Int,
        val latest: Instant,
        val latestChapterName: String,
        val latestChapterNumber: Double,
        val sourceId: Long = 0,
        val isText: Boolean = false,
    )

    /** Mihon stores the page read but not a chapter's page count, so progress is measured over the series. */
    @Immutable
    data class ReadingProgress(
        val page: Int,
        val readChapters: Int,
        val totalChapters: Int,
        /** Pages (or text blocks) of the chapter being read, when it was opened before; null if unknown. */
        val chapterLength: Int? = null,
        val isText: Boolean = false,
    ) {
        val fraction: Float get() = if (totalChapters == 0) 0f else readChapters.toFloat() / totalChapters
        val caughtUp: Boolean get() = totalChapters > 0 && readChapters >= totalChapters
    }

    companion object {
        private const val RECENT_COUNT = 10
    }
}

/** One row per series instead of one per chapter, newest first. */
internal fun groupNewChapters(updates: List<UpdatesWithRelations>): List<HomeViewModel.NewChapters> =
    updates
        .groupBy { it.mangaId }
        .map { (mangaId, chapters) ->
            val newest = chapters.maxBy { it.dateFetch }
            HomeViewModel.NewChapters(
                mangaId = mangaId,
                title = newest.mangaTitle,
                coverData = newest.coverData,
                count = chapters.size,
                latest = newest.dateFetch,
                latestChapterName = newest.chapterName,
                latestChapterNumber = ChapterRecognition.parseChapterNumber(newest.mangaTitle, newest.chapterName),
                sourceId = newest.sourceId,
            )
        }
        .sortedByDescending { it.latest }
