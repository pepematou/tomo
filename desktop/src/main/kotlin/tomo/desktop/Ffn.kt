package tomo.desktop

import eu.kanade.tachiyomi.tomo.ffn.CATEGORIES
import eu.kanade.tachiyomi.tomo.ffn.FFN_BASE
import eu.kanade.tachiyomi.tomo.ffn.ffnSearchUrl
import eu.kanade.tachiyomi.tomo.ffn.parseFfnChapter
import eu.kanade.tachiyomi.tomo.ffn.parseFfnChapters
import eu.kanade.tachiyomi.tomo.ffn.parseFfnDirectory
import eu.kanade.tachiyomi.tomo.ffn.parseFfnSearch
import eu.kanade.tachiyomi.tomo.ffn.parseFfnStory
import eu.kanade.tachiyomi.tomo.text.Fandom
import eu.kanade.tachiyomi.tomo.text.FanficChapter
import eu.kanade.tachiyomi.tomo.text.FanficSearch
import eu.kanade.tachiyomi.tomo.text.FanficSearchPage
import eu.kanade.tachiyomi.tomo.text.FanficWork
import eu.kanade.tachiyomi.tomo.text.TextBlock
import eu.kanade.tachiyomi.tomo.text.parseBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** FanFiction.net answered with its Cloudflare check, which the computer can't pass without a browser. */
class VerificationNeeded : IOException("FanFiction.net pide verificación")

class FfnStory(val work: FanficWork, val chapters: List<FanficChapter>)

/** FanFiction.net from the computer, with the same pace and browser identity as the Android source. */
object Ffn {
    private val client = OkHttpClient()
    private val pace = Mutex()
    private var lastCall = 0L

    private suspend fun get(url: String): String = pace.withLock {
        // One request every 3 seconds, as on Android, so the site does not block us
        val wait = lastCall + 3.seconds.inWholeMilliseconds - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastCall = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (response.code == 403 && response.header("cf-mitigated") != null) throw VerificationNeeded()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
        }
    }

    suspend fun search(search: FanficSearch, page: Int): FanficSearchPage =
        parseFfnSearch(get(ffnSearchUrl(search, page).toString()), search)

    suspend fun story(url: String): FfnStory {
        val html = get(FFN_BASE + url)
        return FfnStory(parseFfnStory(html, url), parseFfnChapters(html, url))
    }

    suspend fun chapter(storyUrl: String, number: Int): List<TextBlock> =
        parseBlocks(parseFfnChapter(get("$FFN_BASE$storyUrl/$number")))

    /** Every fandom, read once from the category directories and kept a month in the cache folder. */
    suspend fun fandoms(): List<Fandom> {
        val file = File(Paths.cache, "ffn_fandoms.json")
        val serializer = ListSerializer(Fandom.serializer())
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 30.days.inWholeMilliseconds
        if (fresh) runCatching { return Json.decodeFromString(serializer, file.readText()) }
        val all = CATEGORIES.keys.flatMap { category -> parseFfnDirectory(get("$FFN_BASE/$category/"), category) }
        runCatching { file.writeText(Json.encodeToString(serializer, all)) }
        return all
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
}
