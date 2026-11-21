package eu.kanade.tachiyomi.ui.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.tomo.ffn.FfnFandoms
import eu.kanade.tachiyomi.tomo.ffn.FfnSource
import eu.kanade.tachiyomi.tomo.ffn.matchFandoms
import eu.kanade.tachiyomi.tomo.text.Fandom
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
 * Searching and browsing Spanish fanfics on FanFiction.net from Discover (RF-30d), and following them.
 * When the site does not answer it says why and offers the other site and the downloaded fanfics (RF-30i).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class FanficsViewModel(
    ffnSource: FfnSource,
    private val followFanfic: FollowFanfic,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val getFavorites: GetFavorites,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val textCache: TextCache,
    private val ffnFandoms: FfnFandoms,
) : ViewModel() {

    // AO3 left Discover at the user's request; works already followed still open and read from the phone
    val sources: List<TextSource> = listOf(ffnSource)

    private val _state = MutableStateFlow(
        State(source = ffnSource),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var fandomsJob: Job? = null

    init {
        load(reset = true)
        // Directory categories arrive one by one; suggestions follow
        viewModelScope.launch {
            ffnFandoms.fandoms.collect { _state.update { it.copy(series = seriesFor(it)) } }
        }
    }

    /** Fandoms of FanFiction.net matching the typed text, so any series can be opened, not only the tiles. */
    private fun seriesFor(state: State): List<Fandom> =
        if (state.source.id == FfnSource.ID && state.search.query.trim().length >= 2) {
            matchFandoms(ffnFandoms.fandoms.value, state.search.query)
        } else {
            emptyList()
        }

    fun setSource(source: TextSource) {
        _state.update { State(source = source, downloaded = it.downloaded) }
        load(reset = true)
    }

    fun setQuery(query: String) {
        _state.update {
            it.copy(search = it.search.copy(query = query)).let { new -> new.copy(series = seriesFor(new)) }
        }
        if (_state.value.source.id == FfnSource.ID && fandomsJob == null) {
            fandomsJob = viewModelScope.launch {
                try {
                    ffnFandoms.ensureLoaded()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Suggestions are a help; the search itself still works, and the next typing tries again
                }
                fandomsJob = null
            }
        }
        load(reset = true, debounce = true)
    }

    /** Shows the stories of [fandom] (a tile or a suggestion), or the general list again when null. */
    fun selectFandom(fandom: Fandom?) {
        _state.update {
            it.copy(
                search = it.search.copy(fandom = fandom?.filter.orEmpty(), query = ""),
                fandom = fandom,
                series = emptyList(),
            )
        }
        load(reset = true)
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
        /** The fandom being shown, for its chip; null for the general list. */
        val fandom: Fandom? = null,
        /** Fandoms matching the typed text. */
        val series: List<Fandom> = emptyList(),
    )
}
