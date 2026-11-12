package eu.kanade.tachiyomi.tomo.ffn

import eu.kanade.tachiyomi.tomo.text.FanficChapter
import eu.kanade.tachiyomi.tomo.text.FanficMeta
import eu.kanade.tachiyomi.tomo.text.FanficSearch
import eu.kanade.tachiyomi.tomo.text.FanficSearchPage
import eu.kanade.tachiyomi.tomo.text.FanficWork
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

const val FFN_BASE = "https://www.fanfiction.net"

/** FanFiction.net's id for Spanish, in both search (`languageid`) and fandom pages (`lan`). */
private const val SPANISH = "2"

/**
 * A fandom page when a fandom is chosen (FanFiction.net search can't filter by fandom name), otherwise the keyword
 * search. Both are always in Spanish (RF-30a).
 */
fun ffnSearchUrl(search: FanficSearch, page: Int, defaultFandom: String): HttpUrl =
    if (search.query.isNotBlank() && search.fandom.isBlank()) {
        "$FFN_BASE/search/".toHttpUrl().newBuilder()
            .addQueryParameter("keywords", search.query.trim())
            .addQueryParameter("type", "story")
            .addQueryParameter("ready", "1")
            .addQueryParameter("languageid", SPANISH)
            .addQueryParameter("ppage", page.toString())
            .build()
    } else {
        // shortcut: with no query and no fandom it shows the default fandom; a Spanish "just in" list would be better
        "$FFN_BASE${search.fandom.ifBlank { defaultFandom }}".toHttpUrl().newBuilder()
            .addQueryParameter("lan", SPANISH)
            .addQueryParameter("srt", search.sort.ffnSort())
            .addQueryParameter("p", page.toString())
            .build()
    }

private fun FanficSearch.Sort.ffnSort() = when (this) {
    FanficSearch.Sort.FAVORITES, FanficSearch.Sort.KUDOS -> "4"
    FanficSearch.Sort.REVIEWS -> "3"
    else -> "1"
}

/**
 * The stories of a search or fandom page. Filters FanFiction.net's URL can't express (status, length, rating) are
 * applied here, and anything not in Spanish is dropped in case the language parameter is ignored.
 */
fun parseFfnSearch(html: String, search: FanficSearch): FanficSearchPage {
    val document = Jsoup.parse(html, FFN_BASE)
    val works = document.select("div.z-list").mapNotNull(::parseListItem).filter { (work, language) ->
        val query = search.query.trim()
        language == "Spanish" &&
            (search.complete == null || work.meta.complete == search.complete) &&
            (search.maxWords == null || work.meta.words < search.maxWords) &&
            (!search.noWarnings || work.meta.rating != MATURE) &&
            (search.fandom.isBlank() || query.isEmpty() || work.title.contains(query, ignoreCase = true))
    }.map { it.first }
    val hasNext = document.select("center a, #content_wrapper_inner a").any { it.text().startsWith("Next") }
    return FanficSearchPage(works, hasNext)
}

private fun parseListItem(item: Element): Pair<FanficWork, String>? {
    val link = item.selectFirst("a.stitle") ?: return null
    val id = storyId(link.attr("href")) ?: return null
    val info = item.selectFirst("div.xgray") ?: return null
    val stats = parseStats(info)
    val summary = item.selectFirst("div.z-indent")?.ownText().orEmpty()
    return FanficWork(
        url = "/s/$id",
        title = link.text(),
        author = item.selectFirst("a[href^=/u/]")?.text().orEmpty(),
        summary = summary,
        tags = stats.genres,
        meta = stats.meta,
    ) to stats.language
}

/** The story page `/s/<id>`: title, author, summary and the gray line of stats. */
fun parseFfnStory(html: String, url: String): FanficWork {
    val document = Jsoup.parse(html, FFN_BASE)
    val top = document.selectFirst("#profile_top")
    val stats = top?.selectFirst("span.xgray")?.let(::parseStats)
    val fandom = document.select("#pre_story_links a").lastOrNull()?.text()
    return FanficWork(
        url = url,
        title = top?.selectFirst("b.xcontrast_txt")?.text().orEmpty(),
        author = top?.selectFirst("a[href^=/u/]")?.text().orEmpty(),
        summary = top?.selectFirst("div.xcontrast_txt")?.text().orEmpty(),
        tags = stats?.genres.orEmpty(),
        meta = (stats?.meta ?: FanficMeta()).copy(fandoms = listOfNotNull(fandom)),
    )
}

/** Chapters from the chapter selector, oldest first; a one-shot has no selector. */
fun parseFfnChapters(html: String, url: String): List<FanficChapter> {
    val document = Jsoup.parse(html, FFN_BASE)
    val stats = document.selectFirst("#profile_top span.xgray")?.let(::parseStats)
    val options = document.selectFirst("select#chap_select")?.select("option").orEmpty()
    val chapters = options.mapIndexed { index, option ->
        val number = option.attr("value").toIntOrNull() ?: (index + 1)
        FanficChapter("$url/$number", option.text(), number, date = 0)
    }.ifEmpty { listOf(FanficChapter("$url/1", "1", 1, 0)) }
    // FanFiction.net only dates the story, so the last chapter gets the update date and the first the publication
    return chapters.mapIndexed { index, chapter ->
        when (index) {
            chapters.lastIndex -> chapter.copy(date = stats?.meta?.updatedAt ?: 0)
            0 -> chapter.copy(date = stats?.published ?: 0)
            else -> chapter
        }
    }
}

/** The chapter text; FanFiction.net mixes the author's notes into it, so they stay as they are. */
fun parseFfnChapter(html: String): String = Jsoup.parse(html, FFN_BASE).selectFirst("#storytext")?.html().orEmpty()

fun ffnStoryId(text: String): Long? = Regex("""fanfiction\.net/s/(\d+)""").find(text)?.groupValues?.get(1)
    ?.toLongOrNull()

private fun storyId(href: String): Long? = Regex("""^/s/(\d+)""").find(href)?.groupValues?.get(1)?.toLongOrNull()

private class Stats(val meta: FanficMeta, val genres: List<String>, val language: String, val published: Long)

/**
 * The gray line, e.g. "Naruto - Rated: T - Spanish - Romance/Drama - Chapters: 20 - Words: 48,210 - Updated: … -
 * Published: … - [Naruto U., Hinata H.] - Complete". Dates are `data-xutime` spans in seconds.
 */
private fun parseStats(info: Element): Stats {
    val times = info.select("span[data-xutime]").map { (it.attr("data-xutime").toLongOrNull() ?: 0) * 1000 }
    val hasUpdated = info.text().contains("Updated:")
    val published = times.getOrNull(if (hasUpdated) 1 else 0) ?: 0
    val updated = if (hasUpdated) times.getOrNull(0) ?: 0 else published
    val parts = info.text().split(" - ").map { it.trim() }
    fun value(key: String) = parts.firstOrNull { it.startsWith(key) }?.removePrefix(key)?.trim()
    val language = parts.firstOrNull { it in LANGUAGES }.orEmpty()
    val genres = parts.firstOrNull { part -> part.split('/').all { it in GENRES.keys } }
        ?.split('/')?.map { GENRES.getValue(it) }.orEmpty()
    val chapters = value("Chapters:")?.filter(Char::isDigit)?.toIntOrNull() ?: 1
    val complete = parts.any { it == "Complete" || it == "Status: Complete" }
    val characters = parts.firstOrNull { '[' in it && ':' !in it }
    return Stats(
        meta = FanficMeta(
            words = value("Words:")?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
            chaptersPosted = chapters,
            chaptersTotal = if (complete) chapters else null,
            complete = complete,
            relationships = Regex("""\[([^\]]+)]""").findAll(characters.orEmpty())
                .map { it.groupValues[1].replace(", ", "/") }.toList(),
            rating = value("Rated:")?.removePrefix("Fiction")?.trim()?.let(::ratingName),
            updatedAt = updated,
        ),
        genres = genres,
        language = language,
        published = published,
    )
}

private const val MATURE = "Mature"

/** FanFiction.net ratings with AO3's names, which the ficha already shows in the app's language. */
private fun ratingName(rating: String) = when (rating) {
    "K", "K+" -> "General Audiences"
    "T" -> "Teen And Up Audiences"
    "M" -> MATURE
    else -> rating
}

private val LANGUAGES = setOf("Spanish", "English", "Portuguese", "French", "German", "Italian", "Catalan")

private val GENRES = mapOf(
    "Adventure" to "Aventura",
    "Angst" to "Angustia",
    "Crime" to "Crimen",
    "Drama" to "Drama",
    "Family" to "Familia",
    "Fantasy" to "Fantasía",
    "Friendship" to "Amistad",
    "General" to "General",
    "Horror" to "Terror",
    "Humor" to "Humor",
    "Hurt" to "Dolor",
    "Comfort" to "Consuelo",
    "Mystery" to "Misterio",
    "Parody" to "Parodia",
    "Poetry" to "Poesía",
    "Romance" to "Romance",
    "Sci-Fi" to "Ciencia ficción",
    "Spiritual" to "Espiritual",
    "Supernatural" to "Sobrenatural",
    "Suspense" to "Suspenso",
    "Tragedy" to "Tragedia",
    "Western" to "Western",
)
