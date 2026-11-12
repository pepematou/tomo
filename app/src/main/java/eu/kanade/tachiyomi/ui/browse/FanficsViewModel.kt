package eu.kanade.tachiyomi.ui.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.tomo.ao3.Ao3Search
import eu.kanade.tachiyomi.tomo.ao3.Ao3Source
import eu.kanade.tachiyomi.tomo.ao3.Ao3Work
import eu.kanade.tachiyomi.tomo.ao3.toSManga
import eu.kanade.tachiyomi.tomo.text.FollowFanfic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import kotlin.time.Duration.Companion.milliseconds

/** Searching and browsing Spanish fanfics on AO3 from Discover (RF-30b), and following them. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class FanficsViewModel(
    private val source: Ao3Source,
    private val followFanfic: FollowFanfic,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun setQuery(query: String) {
        _state.update { it.copy(search = it.search.copy(query = query)) }
        load(reset = true, debounce = true)
    }

    fun setSearch(search: Ao3Search) {
        _state.update { it.copy(search = search) }
        load(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasNextPage) return
        load(reset = false)
    }

    private fun load(reset: Boolean, debounce: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounce) delay(500.milliseconds)
            val page = if (reset) 1 else _state.value.page + 1
            _state.update { it.copy(loading = true, failed = false, works = if (reset) emptyList() else it.works) }
            try {
                val result = source.searchWorks(_state.value.search, page)
                val followed = result.works.mapNotNull { work ->
                    getMangaByUrlAndSourceId.await(work.url, Ao3Source.ID)?.takeIf { it.favorite }?.let { work.url }
                }
                _state.update {
                    it.copy(
                        works = it.works + result.works,
                        page = page,
                        hasNextPage = result.hasNextPage,
                        followed = it.followed + followed,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun toggleFollow(work: Ao3Work) {
        viewModelScope.launch {
            if (work.url in _state.value.followed) {
                getMangaByUrlAndSourceId.await(work.url, Ao3Source.ID)?.let { followFanfic.unfollow(it.id) }
                _state.update { it.copy(followed = it.followed - work.url) }
            } else {
                _state.update { it.copy(followed = it.followed + work.url) }
                followFanfic.follow(work.toSManga(), Ao3Source.ID)
            }
        }
    }

    /** The series id of a work, adding it to the database (not the library) so its ficha can open. */
    suspend fun open(work: Ao3Work): Long = followFanfic.openAo3Work(work.url.substringAfterLast('/').toLong())

    @Immutable
    data class State(
        val search: Ao3Search = Ao3Search(),
        val works: List<Ao3Work> = emptyList(),
        val page: Int = 0,
        val hasNextPage: Boolean = false,
        val loading: Boolean = false,
        val failed: Boolean = false,
        val followed: Set<String> = emptySet(),
    )
}
