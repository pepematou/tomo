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
import java.io.File
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
