package eu.kanade.tachiyomi.ui.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.tomo.ao3.Ao3Source
import eu.kanade.tachiyomi.tomo.ffn.FfnSource
import eu.kanade.tachiyomi.tomo.text.FanficSearch
import eu.kanade.tachiyomi.tomo.text.FanficWork
import eu.kanade.tachiyomi.tomo.text.FollowFanfic
import eu.kanade.tachiyomi.tomo.text.SourceUnavailableException
import eu.kanade.tachiyomi.tomo.text.TEXT_SOURCE_IDS
import eu.kanade.tachiyomi.tomo.text.TextCache
import eu.kanade.tachiyomi.tomo.text.TextSource
import eu.kanade.tachiyomi.tomo.text.classifyFailure
import eu.kanade.tachiyomi.tomo.text.toSManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import kotlin.time.Duration.Companion.milliseconds

/**
 * Searching and browsing Spanish fanfics on AO3 or FanFiction.net from Discover (RF-30b, RF-30d), and following them.
 * When the site does not answer it says why and offers the other site and the downloaded fanfics (RF-30i).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class FanficsViewModel(
    ao3Source: Ao3Source,
    ffnSource: FfnSource,
    private val uiPreferences: UiPreferences,
    private val followFanfic: FollowFanfic,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val getFavorites: GetFavorites,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val textCache: TextCache,
) : ViewModel() {

    val sources: List<TextSource> = listOf(ao3Source, ffnSource)

    private val _state = MutableStateFlow(
        State(source = sources.find { it.id == uiPreferences.fanficSource.get() } ?: ao3Source),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(reset = true)
    }

    fun setSource(source: TextSource) {
        uiPreferences.fanficSource.set(source.id)
        _state.update { State(source = source, downloaded = it.downloaded) }
        load(reset = true)
    }

    fun setQuery(query: String) {
        _state.update { it.copy(search = it.search.copy(query = query)) }
        load(reset = true, debounce = true)
    }

    fun setSearch(search: FanficSearch) {
        _state.update { it.copy(search = search) }
        load(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasNextPage) return
        load(reset = false)
    }

    fun retry() = load(reset = true)

    private fun load(reset: Boolean, debounce: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounce) delay(500.milliseconds)
            val source = _state.value.source
            val page = if (reset) 1 else _state.value.page + 1
            _state.update { it.copy(loading = true, problem = null, works = if (reset) emptyList() else it.works) }
            try {
                val result = source.searchWorks(_state.value.search, page)
                val followed = result.works.mapNotNull { work ->
                    getMangaByUrlAndSourceId.await(work.url, source.id)?.takeIf { it.favorite }?.let { work.url }
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
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, problem = classifyFailure(e), downloaded = downloaded()) }
            }
        }
    }

    /** Followed fanfics with chapters on the phone, to read while the site is down. */
    private suspend fun downloaded(): List<Downloaded> =
        getFavorites.await().filter { it.source in TEXT_SOURCE_IDS }.mapNotNull { manga ->
            val saved = getChaptersByMangaId.await(manga.id).count { textCache.has(it.id) }
            if (saved == 0) null else Downloaded(manga.id, manga.title, saved)
        }

    fun toggleFollow(work: FanficWork) {
        val sourceId = _state.value.source.id
        viewModelScope.launch {
            if (work.url in _state.value.followed) {
                getMangaByUrlAndSourceId.await(work.url, sourceId)?.let { followFanfic.unfollow(it.id) }
                _state.update { it.copy(followed = it.followed - work.url) }
            } else {
                _state.update { it.copy(followed = it.followed + work.url) }
                followFanfic.follow(work.toSManga(), sourceId)
            }
        }
    }

    /** The series id of a work, adding it to the database (not the library) so its ficha can open. */
    suspend fun open(work: FanficWork): Long = followFanfic.openWork(_state.value.source.id, work.url)

    @Immutable
    data class Downloaded(val mangaId: Long, val title: String, val chapters: Int)

    @Immutable
    data class State(
        val source: TextSource,
        val search: FanficSearch = FanficSearch(),
        val works: List<FanficWork> = emptyList(),
        val page: Int = 0,
        val hasNextPage: Boolean = false,
        val loading: Boolean = false,
        /** Why the site did not answer, null while it works. */
        val problem: SourceUnavailableException.Reason? = null,
        val downloaded: List<Downloaded> = emptyList(),
        val followed: Set<String> = emptySet(),
    )
}
