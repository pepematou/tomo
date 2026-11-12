package eu.kanade.tachiyomi.tomo.text

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * What Tomo shows of a fanfic besides title, author and summary; stored in the series' `memo`. Field names are the
 * ones v0.15 wrote for AO3, so works followed then still read.
 */
@Serializable
data class FanficMeta(
    val words: Int = 0,
    val chaptersPosted: Int = 0,
    /** Null when the author has not said how many chapters there will be ("14/?"). */
    val chaptersTotal: Int? = null,
    val complete: Boolean = false,
    val fandoms: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val rating: String? = null,
    val updatedAt: Long = 0,
) {
    fun toMemo(): JsonObject = memoJson.encodeToJsonElement(this).jsonObject

    companion object {
        private val memoJson = Json { ignoreUnknownKeys = true }

        fun fromMemo(memo: JsonObject): FanficMeta? =
            if (memo.isEmpty()) null else runCatching { memoJson.decodeFromJsonElement<FanficMeta>(memo) }.getOrNull()
    }
}

data class FanficWork(
    /** Path on its site, e.g. "/works/123" or "/s/123". */
    val url: String,
    val title: String,
    val author: String,
    val summary: String,
    val tags: List<String>,
    val meta: FanficMeta,
)

data class FanficSearchPage(val works: List<FanficWork>, val hasNextPage: Boolean)

data class FanficChapter(val url: String, val name: String, val number: Int, val date: Long)

/** Options of the fanfic search; the language is always Spanish (RF-30a). */
data class FanficSearch(
    val query: String = "",
    val fandom: String = "",
    /** true for complete works, false for works in progress, null for both. */
    val complete: Boolean? = null,
    val maxWords: Int? = null,
    /** AO3: "No Archive Warnings Apply". FanFiction.net has no warnings, so there it means rated K to T. */
    val noWarnings: Boolean = false,
    val sort: Sort = Sort.UPDATED,
) {
    enum class Sort { UPDATED, KUDOS, WORDS, HITS, FAVORITES, REVIEWS }
}

/** A fandom tile of the explore grid: what it is called, what kind of work, and how the source filters by it. */
data class Fandom(val name: String, val kind: String, val filter: String)

fun FanficWork.toSManga(): SManga = SManga.create().also {
    it.url = url
    it.title = title
    it.author = author
    it.description = summary
    it.genre = tags.joinToString()
    it.status = if (meta.complete) SManga.COMPLETED else SManga.ONGOING
    it.memo = meta.toMemo()
    it.initialized = true
}
