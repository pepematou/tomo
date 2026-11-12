package eu.kanade.tachiyomi.tomo.alternatives

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.library.LibraryUpdateIssues
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import java.io.File
import java.text.Normalizer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Looks for a series in the other installed sources, under every title it is known by ("Disponible en"), and
 * migrates it to the one the user picks.
 */
@Inject
@SingleIn(AppScope::class)
class AlternativeFinder(
    context: Context,
    private val json: Json,
    private val sourceManager: SourceManager,
    private val sourcePreferences: SourcePreferences,
    private val getTracks: GetTracks,
    private val networkToLocalManga: NetworkToLocalManga,
    private val migrateManga: MigrateMangaUseCase,
    private val libraryUpdateIssues: LibraryUpdateIssues,
) {
    private val cacheFile = File(context.filesDir, "alternatives_cache.json")
    private val cacheLock = Mutex()
    private var cache: MutableMap<Long, Search>? = null

    // Few requests at a time across every search, so sources don't block the user (RF-51)
    private val requests = Semaphore(MAX_PARALLEL_SOURCES)

    /** The series title plus the titles its trackers know it by. */
    suspend fun titlesFor(manga: Manga): List<String> =
        (listOf(manga.title) + getTracks.await(manga.id).map { it.title })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    /**
     * Sources to search, in the order chosen in "¿Dónde buscar?": Mihon's migration sources, else the pinned ones,
     * else every enabled online source.
     */
    suspend fun searchSources(excludeSourceId: Long): List<CatalogueSource> {
        val online = sourceManager.getOnlineSources()
        val byId = online.associateBy { it.id }
        val chosen = sourcePreferences.migrationSources.get().mapNotNull { byId[it] }
        val pinned = sourcePreferences.pinnedSources.get().mapNotNull { it.toLongOrNull()?.let(byId::get) }
        val enabled = run {
            val languages = sourcePreferences.enabledLanguages.get()
            val disabled = sourcePreferences.disabledSources.get()
            online.filter { it.lang in languages && "${it.id}" !in disabled }
        }
        return (chosen.ifEmpty { pinned }.ifEmpty { enabled }).filter { it.id != excludeSourceId }
    }

    /** Emits the search as it advances; a finished search less than a day old is reused unless [refresh]. */
    fun find(manga: Manga, refresh: Boolean = false): Flow<Search> = channelFlow {
        if (!refresh) {
            cached(manga.id)?.let {
                send(it)
                return@channelFlow
            }
        }
        val titles = titlesFor(manga)
        val sources = searchSources(manga.source)
        val order = sources.withIndex().associate { (index, source) -> source.id to index }
        val lock = Mutex()
        var search = Search(titles = titles, total = sources.size)
        send(search)

        sources.map { source ->
            launch {
                val found = requests.withPermit {
                    try {
                        searchSource(source, titles)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        null
                    }
                }
                val alternative = found?.getOrNull()?.let { it.copy(match = matchOf(manga.author, it.author)) }
                lock.withLock {
                    search = search.copy(
                        searched = search.searched + 1,
                        failed = search.failed + if (found == null) 1 else 0,
                        results = (search.results + listOfNotNull(alternative))
                            .sortedWith(compareBy({ it.match }, { order[it.sourceId] ?: Int.MAX_VALUE })),
                    )
                    send(search)
                }
            }
        }.forEach { it.join() }

        val finished = search.copy(done = true, at = Clock.System.now().toEpochMilliseconds())
        send(finished)
        store(manga.id, finished)
    }

    /** null when the source failed; a successful empty result when it answered without the series. */
    private suspend fun searchSource(source: CatalogueSource, titles: List<String>): Result<Alternative?>? {
        val engine = SmartSourceSearchEngine(null)
        val hit = titles.firstNotNullOfOrNull { engine.regularSearch(source, it) }
            ?: engine.deepSearch(source, titles.first())
            ?: return Result.success(null)
        val update = withContext(Dispatchers.IO) {
            source.getMangaUpdate(hit.toSManga(), emptyList(), fetchDetails = true, fetchChapters = true)
        }
        val details = update.manga
        val lastChapter = update.chapters
            .maxOfOrNull {
                ChapterRecognition.parseChapterNumber(details.title, it.name, it.chapter_number.toDouble())
            }
            ?: -1.0
        return Result.success(
            Alternative(
                sourceId = source.id,
                sourceName = source.name,
                lang = source.lang,
                url = hit.url,
                title = details.title.ifBlank { hit.title },
                thumbnailUrl = details.thumbnail_url ?: hit.thumbnailUrl,
                author = details.author ?: hit.author,
                lastChapter = lastChapter,
                lastUpload = update.chapters.maxOfOrNull { it.date_upload } ?: 0L,
                chapterCount = update.chapters.size,
                memo = hit.memo,
            ),
        )
    }

    /**
     * Moves [current] to [alternative]: progress, categories and tracking travel with it according to [options], and
     * the "source down" notice for it is cleared.
     */
    suspend fun migrate(current: Manga, alternative: Alternative, options: MigrateOptions): Manga {
        val target = networkToLocalManga(alternative.toManga())
        val userFlags = sourcePreferences.migrationFlags.get()
        // MigrateMangaUseCase reads its options from the user's migration flags, so they are set for this run only
        sourcePreferences.migrationFlags.set(options.applyTo(userFlags))
        try {
            migrateManga(current, target, replace = options.move)
        } finally {
            sourcePreferences.migrationFlags.set(userFlags)
        }
        libraryUpdateIssues.remove(current.id)
        cacheLock.withLock { loadCache().remove(current.id) }
        return target
    }

    fun defaultOptions(): MigrateOptions {
        val flags = sourcePreferences.migrationFlags.get()
        return MigrateOptions(
            keepProgress = MigrationFlag.CHAPTER in flags,
            keepCategories = MigrationFlag.CATEGORY in flags,
            deleteDownloads = MigrationFlag.REMOVE_DOWNLOAD in flags,
            move = true,
        )
    }

    private suspend fun cached(mangaId: Long): Search? = cacheLock.withLock {
        loadCache()[mangaId]?.takeIf { isFresh(it.at, Clock.System.now().toEpochMilliseconds()) }
    }

    private suspend fun store(mangaId: Long, search: Search) = cacheLock.withLock {
        val entries = loadCache()
        val now = Clock.System.now().toEpochMilliseconds()
        entries.entries.removeAll { !isFresh(it.value.at, now) }
        entries[mangaId] = search
        try {
            cacheFile.writeText(json.encodeToString(entries.toMap()))
        } catch (_: Exception) {}
    }

    private fun loadCache(): MutableMap<Long, Search> = cache ?: try {
        json.decodeFromString<Map<Long, Search>>(cacheFile.readText()).toMutableMap()
    } catch (_: Exception) {
        mutableMapOf()
    }.also { cache = it }

    @Serializable
    data class Search(
        val titles: List<String> = emptyList(),
        val total: Int = 0,
        val searched: Int = 0,
        val failed: Int = 0,
        val results: List<Alternative> = emptyList(),
        val done: Boolean = false,
        val at: Long = 0,
    )

    @Serializable
    data class Alternative(
        val sourceId: Long,
        val sourceName: String,
        val lang: String,
        val url: String,
        val title: String,
        val thumbnailUrl: String?,
        val author: String?,
        val lastChapter: Double,
        val lastUpload: Long,
        val chapterCount: Int,
        val memo: JsonObject,
        val match: Match = Match.POSSIBLE,
    ) {
        fun toManga(): Manga = Manga.create().copy(
            url = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            author = author,
            source = sourceId,
            memo = memo,
        )
    }

    /** Declared best first, so sorting by it puts same-author results on top. */
    enum class Match { SAME_AUTHOR, POSSIBLE }

    data class MigrateOptions(
        val keepProgress: Boolean,
        val keepCategories: Boolean,
        val deleteDownloads: Boolean,
        /** Moves the series (the old one leaves the library) instead of keeping both. */
        val move: Boolean,
    ) {
        fun applyTo(flags: Set<MigrationFlag>): Set<MigrationFlag> = flags
            .toggle(MigrationFlag.CHAPTER, keepProgress)
            .toggle(MigrationFlag.CATEGORY, keepCategories)
            .toggle(MigrationFlag.REMOVE_DOWNLOAD, deleteDownloads)

        private fun Set<MigrationFlag>.toggle(flag: MigrationFlag, on: Boolean) = if (on) this + flag else this - flag
    }

    companion object {
        const val MAX_PARALLEL_SOURCES = 3
        private val CACHE_TTL = 24.hours
        internal fun isFresh(at: Long, now: Long): Boolean = at > 0 && now - at < CACHE_TTL.inWholeMilliseconds
    }
}

/** Lowercase, without accents, punctuation or the order of names, so "Hokazono, Takeru" equals "Takeru Hokazono". */
internal fun normalizeAuthor(author: String?): Set<String> {
    if (author.isNullOrBlank()) return emptySet()
    return Normalizer.normalize(author, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }
        .toSet()
}

internal fun matchOf(currentAuthor: String?, candidateAuthor: String?): AlternativeFinder.Match {
    val current = normalizeAuthor(currentAuthor)
    val candidate = normalizeAuthor(candidateAuthor)
    return if (current.isNotEmpty() && candidate.isNotEmpty() &&
        (current.containsAll(candidate) || candidate.containsAll(current))
    ) {
        AlternativeFinder.Match.SAME_AUTHOR
    } else {
        AlternativeFinder.Match.POSSIBLE
    }
}

/** How many chapters ahead [alternative] is of the series' own last chapter; 0 when it is not ahead or unknown. */
internal fun chapterDelta(currentLastChapter: Double, alternative: AlternativeFinder.Alternative): Int {
    if (currentLastChapter < 0 || alternative.lastChapter < 0) return 0
    return (alternative.lastChapter - currentLastChapter).toInt().coerceAtLeast(0)
}

/** The suggestion for a batch: the first same-author result, else the first result, in the user's source order. */
internal fun bestAlternative(results: List<AlternativeFinder.Alternative>): AlternativeFinder.Alternative? =
    results.firstOrNull { it.match == AlternativeFinder.Match.SAME_AUTHOR } ?: results.firstOrNull()
