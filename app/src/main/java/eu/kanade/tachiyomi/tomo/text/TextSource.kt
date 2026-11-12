package eu.kanade.tachiyomi.tomo.text

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.tomo.ao3.Ao3Source
import eu.kanade.tachiyomi.tomo.ffn.FfnSource
import eu.kanade.tachiyomi.tomo.ffn.ffnStoryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import tachiyomi.i18n.MR
import java.net.ConnectException
import java.net.UnknownHostException

/** A built-in fanfic site (AO3, FanFiction.net): Spanish search, work details, chapters and the text of each one. */
interface TextSource : CatalogueSource {

    /** The sort orders this site offers, in menu order. */
    val sorts: List<FanficSearch.Sort>

    /** The fandom tiles of the explore grid. */
    val fandoms: List<Fandom>

    suspend fun searchWorks(search: FanficSearch, page: Int): FanficSearchPage

    /** The text of a chapter as HTML, author's notes in `<aside class="notes">`. */
    suspend fun getChapterHtml(chapterUrl: String): String

    fun webUrl(url: String): String
}

/** Why a fanfic site did not answer, so Tomo can say it (RF-30i). */
class SourceUnavailableException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { OFFLINE, DOWN, VERIFICATION, RESTRICTED, UNKNOWN }
}

/** What to tell the user, with the site's name as argument. */
fun SourceUnavailableException.Reason.message(): StringResource = when (this) {
    SourceUnavailableException.Reason.OFFLINE -> MR.strings.tomo_source_offline
    SourceUnavailableException.Reason.DOWN -> MR.strings.tomo_source_down
    SourceUnavailableException.Reason.VERIFICATION -> MR.strings.tomo_source_verification
    SourceUnavailableException.Reason.RESTRICTED -> MR.strings.tomo_source_restricted
    SourceUnavailableException.Reason.UNKNOWN -> MR.strings.tomo_source_unknown
}

fun classifyFailure(e: Throwable): SourceUnavailableException.Reason {
    val causes = generateSequence(e) { it.cause }.toList()
    causes.firstNotNullOfOrNull { it as? SourceUnavailableException }?.let { return it.reason }
    return when {
        // Mihon's CloudflareInterceptor wraps its private CloudflareBypassException when the hidden WebView fails
        causes.any { it::class.simpleName == "CloudflareBypassException" } ->
            SourceUnavailableException.Reason.VERIFICATION
        causes.any { it is UnknownHostException || it is ConnectException } -> SourceUnavailableException.Reason.OFFLINE
        else -> SourceUnavailableException.Reason.UNKNOWN
    }
}

/** True for the "we're down for maintenance" pages fanfic sites show instead of the content. */
internal fun isMaintenancePage(code: Int, body: String): Boolean =
    code in 502..504 || (code >= 500 && body.contains("maintenance", ignoreCase = true))

/**
 * The last failure of each fanfic site, by host, so the ficha and the reader can say why a work did not update
 * (RF-30i). Cleared by the next page that loads.
 */
object TextSourceHealth {
    private val _problems = MutableStateFlow<Map<String, SourceUnavailableException.Reason>>(emptyMap())
    val problems: StateFlow<Map<String, SourceUnavailableException.Reason>> = _problems.asStateFlow()

    fun problemOf(source: TextSource): SourceUnavailableException.Reason? =
        _problems.value[source.webUrl("/").toHttpUrl().host]

    internal fun record(host: String, reason: SourceUnavailableException.Reason?) {
        _problems.update { if (reason == null) it - host else it + (host to reason) }
    }
}

/** GET a page as text, turning outages and Cloudflare walls into [SourceUnavailableException]. */
internal suspend fun OkHttpClient.fetchText(url: String): String = withContext(Dispatchers.IO) {
    val host = url.toHttpUrl().host
    try {
        newCall(GET(url)).await().use { response ->
            val body = response.body.string()
            when {
                isMaintenancePage(response.code, body) ->
                    throw SourceUnavailableException(SourceUnavailableException.Reason.DOWN)
                response.code == 403 && response.header("cf-mitigated") != null ->
                    throw SourceUnavailableException(SourceUnavailableException.Reason.VERIFICATION)
                !response.isSuccessful -> error("HTTP error ${response.code}")
            }
            if (response.request.url.encodedPath.startsWith("/users/login")) {
                throw SourceUnavailableException(SourceUnavailableException.Reason.RESTRICTED)
            }
            TextSourceHealth.record(host, null)
            body
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A restricted work is about that work, not the site
        classifyFailure(e).takeIf { it != SourceUnavailableException.Reason.RESTRICTED }
            ?.let { TextSourceHealth.record(host, it) }
        throw e
    }
}

/** The fanfic site and path of the work in a shared link (AO3 or FanFiction.net), or null if it is not one. */
fun fanficLink(text: String): Pair<Long, String>? =
    ao3WorkId(text)?.let { Ao3Source.ID to Ao3Source.workUrl(it) } ?: ffnStoryId(text)?.let { FfnSource.ID to "/s/$it" }

/** The built-in fanfic sources; their series are text works. */
val TEXT_SOURCE_IDS = setOf(Ao3Source.ID, FfnSource.ID)
