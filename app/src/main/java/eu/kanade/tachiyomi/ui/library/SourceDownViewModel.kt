package eu.kanade.tachiyomi.ui.library

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
import eu.kanade.tachiyomi.data.library.LibraryUpdateIssues
import eu.kanade.tachiyomi.tomo.alternatives.AlternativeFinder
import eu.kanade.tachiyomi.tomo.alternatives.bestAlternative
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga

/**
 * Batch review of series whose source is gone: finds an alternative for each one, lets the user check the
 * suggestions and migrates only the selected ones.
 */
@AssistedInject
class SourceDownViewModel(
    @Assisted private val mangaIds: List<Long>,
    private val finder: AlternativeFinder,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val libraryUpdateIssues: LibraryUpdateIssues,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaIds: List<Long>): SourceDownViewModel
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // shortcut: searches only while the screen is open (results are cached for a day); move to a worker if large
    // libraries take too long
    init {
        viewModelScope.launch {
            val reasons = libraryUpdateIssues.issues.value.associate { it.mangaId to it.reason }
            val items = mangaIds.mapNotNull { id ->
                val manga = getManga.await(id) ?: return@mapNotNull null
                Item(
                    manga = manga,
                    reason = reasons[id].orEmpty(),
                    lastChapter = getChaptersByMangaId.await(id).maxOfOrNull { it.chapterNumber } ?: -1.0,
                )
            }
            _state.update { it.copy(items = items) }
            items.forEach { search(it.manga, refresh = false) }
        }
    }

    private suspend fun search(manga: Manga, refresh: Boolean) {
        finder.find(manga, refresh).collect { search ->
            if (!search.done) return@collect
            val best = bestAlternative(search.results)
            updateItem(manga.id) { it.copy(search = search, choice = best, selected = best != null) }
        }
    }

    fun retry(mangaId: Long) {
        val manga = _state.value.items.find { it.manga.id == mangaId }?.manga ?: return
        viewModelScope.launch { search(manga, refresh = true) }
    }

    fun toggle(mangaId: Long) = updateItem(mangaId) { it.copy(selected = it.choice != null && !it.selected) }

    fun choose(mangaId: Long, alternative: AlternativeFinder.Alternative) =
        updateItem(mangaId) { it.copy(choice = alternative, selected = true) }

    fun migrateSelected() {
        if (_state.value.migrating) return
        val selected = _state.value.items.filter { it.selected && it.choice != null }
        _state.update { it.copy(migrating = true, migrated = 0, toMigrate = selected.size) }
        viewModelScope.launch {
            val options = finder.defaultOptions().copy(move = true)
            selected.forEach { item ->
                finder.migrate(item.manga, item.choice!!, options)
                _state.update { state ->
                    state.copy(
                        migrated = state.migrated + 1,
                        items = state.items.filterNot { it.manga.id == item.manga.id },
                    )
                }
            }
            _state.update { it.copy(migrating = false, finished = true) }
        }
    }

    private fun updateItem(mangaId: Long, transform: (Item) -> Item) = _state.update { state ->
        state.copy(items = state.items.map { if (it.manga.id == mangaId) transform(it) else it })
    }

    @Immutable
    data class Item(
        val manga: Manga,
        val reason: String,
        val lastChapter: Double,
        val search: AlternativeFinder.Search? = null,
        val choice: AlternativeFinder.Alternative? = null,
        val selected: Boolean = false,
    ) {
        val searched: Boolean get() = search != null
    }

    @Immutable
    data class State(
        val items: List<Item> = emptyList(),
        val migrating: Boolean = false,
        val migrated: Int = 0,
        val toMigrate: Int = 0,
        val finished: Boolean = false,
    ) {
        val searchedCount: Int get() = items.count { it.searched }
        val withAlternative: List<Item> get() = items.filter { it.choice != null }
        val withoutAlternative: List<Item> get() = items.filter { it.searched && it.choice == null }
        val selectedCount: Int get() = items.count { it.selected }
    }
}
