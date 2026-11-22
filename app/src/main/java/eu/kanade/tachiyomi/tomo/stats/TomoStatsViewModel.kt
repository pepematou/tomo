package eu.kanade.tachiyomi.tomo.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.tomo.text.FanficMeta
import eu.kanade.tachiyomi.tomo.text.TEXT_SOURCE_IDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class TomoStatsViewModel(
    getLibraryManga: GetLibraryManga,
    historyRepository: HistoryRepository,
    sourceManager: SourceManager,
) : ViewModel() {

    private val _stats = MutableStateFlow<TomoStats?>(null)
    val stats: StateFlow<TomoStats?> = _stats.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val library = getLibraryManga.await().distinctBy { it.id }
            val works = library.map { item ->
                val manga = item.manga
                val isText = manga.source in TEXT_SOURCE_IDS
                StatsWork(
                    id = manga.id,
                    title = manga.title,
                    sourceName = sourceManager.getOrStub(manga.source).name,
                    genres = manga.genre.orEmpty(),
                    isText = isText,
                    chaptersRead = item.readCount,
                    wordsPerChapter = FanficMeta.fromMemo(manga.memo)?.takeIf { isText }
                        ?.let { it.words / it.chaptersPosted.coerceAtLeast(1) },
                )
            }
            val reads = historyRepository.getHistoryByMangaIds(library.map { it.id }).flatMap { (mangaId, list) ->
                list.mapNotNull { history -> history.readAt?.let { StatsRead(mangaId, it, history.readDuration) } }
            }
            _stats.value = tomoStats(works, reads, Clock.System.now(), TimeZone.currentSystemDefault())
        }
    }
}
