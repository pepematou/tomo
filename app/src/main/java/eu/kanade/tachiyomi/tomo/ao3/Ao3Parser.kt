package eu.kanade.tachiyomi.tomo.ao3

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

const val AO3_BASE = "https://archiveofourown.org"

/** What Tomo shows of an AO3 work besides title, author and summary; stored in the series' `memo`. */
@Serializable
data class Ao3Meta(
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

        fun fromMemo(memo: JsonObject): Ao3Meta? =
            if (memo.isEmpty()) null else runCatching { memoJson.decodeFromJsonElement<Ao3Meta>(memo) }.getOrNull()
    }
}

data class Ao3Work(
    /** "/works/123" */
    val url: String,
    val title: String,
    val author: String,
    val summary: String,
    val tags: List<String>,
    val meta: Ao3Meta,
)

data class Ao3SearchPage(val works: List<Ao3Work>, val hasNextPage: Boolean)

data class Ao3Chapter(val url: String, val name: String, val number: Int, val date: Long)

/** Options of the fanfic search; the language is always Spanish (RF-30a). */
data class Ao3Search(
    val query: String = "",
    val fandom: String = "",
    /** true for complete works, false for works in progress, null for both. */
    val complete: Boolean? = null,
    val maxWords: Int? = null,
    val noWarnings: Boolean = false,
    val sort: Sort = Sort.UPDATED,
) {
    enum class Sort(
        val column: String,
    ) {
        UPDATED("revised_at"),
        KUDOS("kudos_count"),
        WORDS("word_count"),
        HITS("hits"),
    }
}

fun searchUrl(search: Ao3Search, page: Int): HttpUrl = "$AO3_BASE/works/search".toHttpUrl().newBuilder()
    .addQueryParameter("page", page.toString())
    .addQueryParameter("work_search[query]", search.query)
    .addQueryParameter("work_search[language_id]", "es")
    .addQueryParameter("work_search[fandom_names]", search.fandom)
    .addQueryParameter(
        "work_search[complete]",
        when (search.complete) {
            true -> "T"
            false -> "F"
            null -> ""
        },
    )
    .addQueryParameter("work_search[words_to]", search.maxWords?.toString().orEmpty())
    .apply { if (search.noWarnings) addQueryParameter("work_search[archive_warning_ids][]", NO_WARNINGS_ID) }
    .addQueryParameter("work_search[sort_column]", search.sort.column)
    .addQueryParameter("work_search[sort_direction]", "desc")
    .build()

/** Tag id of "No Archive Warnings Apply". */
private const val NO_WARNINGS_ID = "16"

fun parseSearch(html: String): Ao3SearchPage {
    val document = Jsoup.parse(html, AO3_BASE)
    val works = document.select("li.work.blurb, li.blurb.work").mapNotNull(::parseBlurb)
    return Ao3SearchPage(works, hasNextPage = document.selectFirst("ol.pagination li.next a") != null)
}

private fun parseBlurb(blurb: Element): Ao3Work? {
    val titleLink = blurb.selectFirst("h4.heading a[href^=/works/]") ?: return null
    val stats = blurb.selectFirst("dl.stats")
    val (posted, total) = parseChapterCount(stats?.selectFirst("dd.chapters")?.text())
    val tags = blurb.select("ul.tags")
    return Ao3Work(
        url = titleLink.attr("href").substringBefore('?'),
        title = titleLink.text(),
        author = blurb.select("h4.heading a[rel=author]").joinToString(", ") { it.text() }.ifEmpty { "Anonymous" },
        summary = blurb.selectFirst("blockquote.summary")?.wholeTextParagraphs().orEmpty(),
        tags = tags.select("li.freeforms a.tag").map { it.text() },
        meta = Ao3Meta(
            words = stats?.selectFirst("dd.words")?.text().toCount(),
            chaptersPosted = posted,
            chaptersTotal = total,
            complete = blurb.selectFirst(
                "ul.required-tags .complete-yes",
            ) != null || (total != null && posted >= total),
            fandoms = blurb.select("h5.fandoms a.tag").map { it.text() },
            relationships = tags.select("li.relationships a.tag").map { it.text() },
            warnings = tags.select("li.warnings a.tag").map { it.text() },
            rating = blurb.selectFirst("ul.required-tags span.rating")?.attr("title"),
            updatedAt = parseBlurbDate(blurb.selectFirst("p.datetime")?.text()),
        ),
    )
}

/** The work page, read with `view_adult=true`: title, author, summary, tags and stats. */
fun parseWork(html: String, url: String): Ao3Work {
    val document = Jsoup.parse(html, AO3_BASE)
    val meta = document.selectFirst("dl.work.meta")
    fun tagsOf(kind: String) = meta?.select("dd.$kind.tags a.tag")?.map { it.text() }.orEmpty()
    val stats = meta?.selectFirst("dl.stats")
    val (posted, total) = parseChapterCount(stats?.selectFirst("dd.chapters")?.text())
    val updated = stats?.selectFirst("dd.status")?.text() ?: stats?.selectFirst("dd.published")?.text()
    return Ao3Work(
        url = url,
        title = document.selectFirst("#workskin .preface h2.title")?.text().orEmpty(),
        author = document.select("#workskin .preface h3.byline a[rel=author]").joinToString(", ") { it.text() }
            .ifEmpty { "Anonymous" },
        summary = document.selectFirst(
            "#workskin .preface .summary blockquote.userstuff",
        )?.wholeTextParagraphs().orEmpty(),
        tags = tagsOf("freeform"),
        meta = Ao3Meta(
            words = stats?.selectFirst("dd.words")?.text().toCount(),
            chaptersPosted = posted,
            chaptersTotal = total,
            complete = total != null && posted >= total,
            fandoms = tagsOf("fandom"),
            relationships = tagsOf("relationship"),
            warnings = tagsOf("warning"),
            rating = tagsOf("rating").firstOrNull(),
            updatedAt = parseIsoDate(updated),
        ),
    )
}

/** True when AO3 answered with its login page: the work is only for registered users. */
fun isLoginPage(html: String): Boolean {
    val document = Jsoup.parse(html)
    return document.selectFirst("#workskin") == null &&
        document.selectFirst("form#new_user, form[action*=login]") != null
}

/** Chapter index from `/works/<id>/navigate`, oldest first. A one-shot without index gets the work itself. */
fun parseNavigate(html: String, workUrl: String): List<Ao3Chapter> {
    val chapters = Jsoup.parse(html, AO3_BASE).select("ol.chapter.index li").mapIndexedNotNull { index, item ->
        val link = item.selectFirst("a[href*=/chapters/]") ?: return@mapIndexedNotNull null
        Ao3Chapter(
            url = link.attr("href").substringBefore('?').substringBefore('#'),
            name = link.text(),
            number = index + 1,
            date = parseIsoDate(item.selectFirst("span.datetime")?.text()?.trim('(', ')', ' ')),
        )
    }
    return chapters.ifEmpty { listOf(Ao3Chapter(workUrl, "1", 1, 0)) }
}

/** Chapter text as HTML: the title, the author's notes folded into a NOTES block, and the body. */
fun parseChapter(html: String): String {
    val document = Jsoup.parse(html, AO3_BASE)
    val chapter = document.selectFirst("#chapters .chapter") ?: document.selectFirst("#chapters") ?: return ""
    val title = chapter.selectFirst(".chapter.preface h3.title, h3.title")?.text()
    val notes = chapter.select(".notes blockquote.userstuff, .end.notes blockquote.userstuff")
        .filterNot { it.closest(".summary") != null }
    val body = chapter.selectFirst("div.userstuff[role=article], div.userstuff.module, div.userstuff")?.clone()
    body?.select("h3.landmark")?.remove()
    return buildString {
        title?.let { append("<h2>").append(org.jsoup.nodes.Entities.escape(it)).append("</h2>") }
        notes.firstOrNull()?.let { append("<aside class=\"notes\">").append(it.html()).append("</aside>") }
        body?.let { append(it.html()) }
        notes.drop(1).forEach { append("<aside class=\"notes\">").append(it.html()).append("</aside>") }
    }
}

/** "14/20" → (14, 20); "9/?" → (9, null). */
internal fun parseChapterCount(text: String?): Pair<Int, Int?> {
    val parts = text.orEmpty().split('/')
    val posted = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
    return posted to parts.getOrNull(1)?.trim()?.toIntOrNull()
}

private fun String?.toCount(): Int = this.orEmpty().filter(Char::isDigit).toIntOrNull() ?: 0

private val blurbDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun parseBlurbDate(text: String?): Long = runCatching {
    LocalDate.parse(text!!.trim(), blurbDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}.getOrDefault(0)

private fun parseIsoDate(text: String?): Long = runCatching {
    LocalDate.parse(text!!.trim()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}.getOrDefault(0)

/** Paragraphs of a summary as plain text separated by blank lines. */
private fun Element.wholeTextParagraphs(): String {
    val paragraphs = select("p")
    return if (paragraphs.isEmpty()) text() else paragraphs.joinToString("\n\n") { it.text() }
}
