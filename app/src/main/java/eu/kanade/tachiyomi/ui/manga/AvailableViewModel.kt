package eu.kanade.tachiyomi.ui.manga

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
import eu.kanade.tachiyomi.data.library.sourceDownIds
import eu.kanade.tachiyomi.tomo.alternatives.AlternativeFinder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/** State of the "Disponible en" sheet of a series: the search, the chosen source and the migration. */
@AssistedInject
class AvailableViewModel(
    @Assisted private val mangaId: Long,
    private val finder: AlternativeFinder,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val sourceManager: SourceManager,
    private val libraryUpdateIssues: LibraryUpdateIssues,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): AvailableViewModel
    }

    private val _state = MutableStateFlow(State(options = finder.defaultOptions()))
    val state: StateFlow<State> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val manga = getManga.await(mangaId) ?: return@launch
            val chapters = getChaptersByMangaId.await(mangaId)
            _state.update {
                it.copy(
                    manga = manga,
                    sourceName = sourceManager.getOrStub(manga.source).name,
                    sourceDown = mangaId in libraryUpdateIssues.issues.value.sourceDownIds(),
                    lastChapter = chapters.maxOfOrNull { c -> c.chapterNumber } ?: -1.0,
                    lastReadChapter = chapters.filter { c -> c.read }.maxOfOrNull { c -> c.chapterNumber } ?: -1.0,
                )
            }
            search(refresh = false)
        }
    }

    fun search(refresh: Boolean) {
        val manga = _state.value.manga ?: return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            finder.find(manga, refresh).collect { search -> _state.update { it.copy(search = search) } }
        }
    }

    fun pick(alternative: AlternativeFinder.Alternative) = _state.update { it.copy(step = Step.Confirm(alternative)) }

    fun backToList() = _state.update { it.copy(step = Step.List) }

    fun setOptions(options: AlternativeFinder.MigrateOptions) = _state.update { it.copy(options = options) }

    fun migrate() {
        val current = _state.value
        val manga = current.manga ?: return
        val alternative = (current.step as? Step.Confirm)?.alternative ?: return
        if (current.migrating) return
        _state.update { it.copy(migrating = true) }
        viewModelScope.launch {
            val target = finder.migrate(manga, alternative, current.options)
            _state.update { it.copy(migrating = false, step = Step.Done(alternative, target.id)) }
        }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val sourceName: String = "",
        val sourceDown: Boolean = false,
        val lastChapter: Double = -1.0,
        val lastReadChapter: Double = -1.0,
        val search: AlternativeFinder.Search? = null,
        val step: Step = Step.List,
        val options: AlternativeFinder.MigrateOptions,
        val migrating: Boolean = false,
    )

    sealed interface Step {
        data object List : Step
        data class Confirm(val alternative: AlternativeFinder.Alternative) : Step
        data class Done(val alternative: AlternativeFinder.Alternative, val newMangaId: Long) : Step
    }
}
