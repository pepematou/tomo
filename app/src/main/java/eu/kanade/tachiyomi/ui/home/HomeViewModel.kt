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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
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
) : ViewModel() {

    val state: StateFlow<State> = combine(
        getHistory.subscribe("").map { it.take(RECENT_COUNT) },
        getUpdates.subscribe(read = false, after = Clock.System.now() - 7.days).map(::groupNewChapters),
        libraryUpdateIssues.issues,
    ) { history, newChapters, issues ->
        State(isLoading = false, history = history, newChapters = newChapters, issues = issues)
    }
        .catch { logcat(LogPriority.ERROR, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** The chapter "Continue" opens: the same one if unfinished, otherwise the next. */
    suspend fun chapterToContinue(history: HistoryWithRelations): Chapter? =
        getNextChapters.await(history.mangaId, history.chapterId, onlyUnread = false).firstOrNull()

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val history: List<HistoryWithRelations> = emptyList(),
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
    )

    companion object {
        private const val RECENT_COUNT = 10
    }
}

/** One row per series instead of one per chapter, newest first. */
internal fun groupNewChapters(updates: List<UpdatesWithRelations>): List<HomeViewModel.NewChapters> =
    updates
        .groupBy { it.mangaId }
        .map { (mangaId, chapters) ->
            val first = chapters.first()
            HomeViewModel.NewChapters(
                mangaId = mangaId,
                title = first.mangaTitle,
                coverData = first.coverData,
                count = chapters.size,
                latest = chapters.maxOf { it.dateFetch },
            )
        }
        .sortedByDescending { it.latest }
