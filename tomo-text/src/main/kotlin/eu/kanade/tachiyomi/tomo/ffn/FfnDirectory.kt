package eu.kanade.tachiyomi.tomo.ffn

import eu.kanade.tachiyomi.tomo.text.Fandom
import org.jsoup.Jsoup
import java.text.Normalizer

/** FanFiction.net categories and how Tomo names their kind of work. */
val CATEGORIES = mapOf(
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
fun storyCount(text: String?): Long {
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
