package eu.kanade.tachiyomi.tomo.ffn

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.tomo.text.Fandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.io.File
import java.text.Normalizer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Every fandom of FanFiction.net, so any series can be found by name, not just the tiles. Read from the nine category
 * directories once and kept for a month.
 */
@Inject
@SingleIn(AppScope::class)
class FfnFandoms(
    context: Context,
    private val json: Json,
    private val source: FfnSource,
) {
    private val file = File(context.filesDir, "ffn_fandoms.json")
    private val mutex = Mutex()

    private var cache: Cache = load()
    private val _fandoms = MutableStateFlow(cache.all())
    val fandoms: StateFlow<List<Fandom>> = _fandoms.asStateFlow()

    /** Downloads the categories still missing or older than a month; each one shows up as soon as it arrives. */
    suspend fun ensureLoaded() = mutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - cache.savedAt > 30.days.inWholeMilliseconds) cache = Cache(savedAt = now)
        CATEGORIES.keys.filterNot { it in cache.byCategory }.forEach { category ->
            val fandoms = parseFfnDirectory(source.directoryHtml(category), category)
            cache = cache.copy(byCategory = cache.byCategory + (category to fandoms))
            _fandoms.value = cache.all()
            try {
                file.writeText(json.encodeToString(cache))
            } catch (_: Exception) {}
        }
    }

    private fun load(): Cache = try {
        json.decodeFromString<Cache>(file.readText())
    } catch (_: Exception) {
        Cache()
    }

    @Serializable
    private data class Cache(val savedAt: Long = 0, val byCategory: Map<String, List<Fandom>> = emptyMap()) {
        fun all() = byCategory.values.flatten()
    }
}

/** FanFiction.net categories and how Tomo names their kind of work. */
internal val CATEGORIES = mapOf(
    "anime" to "Anime",
    "book" to "Libro",
    "cartoon" to "Caricatura",
    "comic" to "Cómic",
    "game" to "Videojuego",
    "misc" to "Varios",
    "movie" to "Película",
    "play" to "Teatro",
    "tv" to "TV",
)

/** The fandoms of a category directory page: each link to `/<category>/<Name>/` followed by its count, "(31.2K)". */
fun parseFfnDirectory(html: String, category: String): List<Fandom> {
    val path = Regex("""^/$category/[^/]+/$""")
    return Jsoup.parse(html, FFN_BASE).select("a[href^=/$category/]")
        .filter { path.matches(it.attr("href")) }
        .map { link ->
            Fandom(
                name = link.text(),
                kind = CATEGORIES[category].orEmpty(),
                filter = link.attr("href"),
                stories = link.nextElementSibling()?.takeIf { it.hasClass("gray") }?.text()?.trim('(', ')', ' '),
            )
        }
        .distinctBy { it.filter }
}

/** Up to [limit] fandoms whose name contains every word of [query], ignoring accents and case; most stories first. */
fun matchFandoms(fandoms: List<Fandom>, query: String, limit: Int = 6): List<Fandom> {
    val words = query.fold().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return fandoms.filter { fandom -> fandom.name.fold().let { name -> words.all { it in name } } }
        .sortedByDescending { storyCount(it.stories) }
        .take(limit)
}

/** "31.2K" → 31 200, "1.1M" → 1 100 000, "850" → 850. */
internal fun storyCount(text: String?): Long {
    val value = text.orEmpty().trim()
    val number = value.trimEnd('K', 'M', 'k', 'm').replace(",", "").toDoubleOrNull() ?: return 0
    val factor = when (value.lastOrNull()?.uppercaseChar()) {
        'K' -> 1_000
        'M' -> 1_000_000
        else -> 1
    }
    return (number * factor).toLong()
}

private fun String.fold(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
