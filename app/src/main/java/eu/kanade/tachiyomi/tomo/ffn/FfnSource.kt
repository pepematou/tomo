package eu.kanade.tachiyomi.tomo.ffn

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.tomo.text.Fandom
import eu.kanade.tachiyomi.tomo.text.FanficSearch
import eu.kanade.tachiyomi.tomo.text.FanficSearchPage
import eu.kanade.tachiyomi.tomo.text.TextSource
import eu.kanade.tachiyomi.tomo.text.fetchText
import eu.kanade.tachiyomi.tomo.text.toSManga
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

/**
 * FanFiction.net as the second built-in text source (RF-30d), Spanish stories only, so fanfics keep working when AO3
 * is down. Its Cloudflare check is solved by Mihon's client in a hidden WebView, or by the user in the browser.
 */
@Inject
@SingleIn(AppScope::class)
class FfnSource(networkHelper: NetworkHelper) : TextSource {

    override val id: Long = ID
    override val name: String = "FanFiction.net"
    override val lang: String = "es"
    override val supportsLatest: Boolean = true

    // Also sends the WebView's user agent, which the Cloudflare cookie is tied to
    private val client: OkHttpClient = networkHelper.client.newBuilder()
        .rateLimit(permits = 1, period = 3.seconds)
        .build()

    override val sorts = listOf(FanficSearch.Sort.UPDATED, FanficSearch.Sort.FAVORITES, FanficSearch.Sort.REVIEWS)

    // Fandoms with many stories in Spanish; the filter is the fandom's page
    override val fandoms = listOf(
        Fandom("Harry Potter", "Libro", "/book/Harry-Potter/"),
        Fandom("Naruto", "Anime", "/anime/Naruto/"),
        Fandom("Inuyasha", "Anime", "/anime/Inuyasha/"),
        Fandom("Twilight", "Libro", "/book/Twilight/"),
        Fandom("Saint Seiya", "Anime", "/anime/Saint-Seiya/"),
        Fandom("Dragon Ball Z", "Anime", "/anime/Dragon-Ball-Z/"),
        Fandom("Pokémon", "Videojuego", "/game/Pokémon/"),
        Fandom("Percy Jackson", "Libro", "/book/Percy-Jackson-and-the-Olympians/"),
    )

    override suspend fun searchWorks(search: FanficSearch, page: Int): FanficSearchPage =
        parseFfnSearch(client.fetchText(ffnSearchUrl(search, page, fandoms.first().filter).toString()), search)

    override suspend fun getPopularManga(page: Int): MangasPage =
        searchWorks(FanficSearch(sort = FanficSearch.Sort.FAVORITES), page).toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchWorks(FanficSearch(), page).toMangasPage()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        searchWorks(FanficSearch(query = query), page).toMangasPage()

    private fun FanficSearchPage.toMangasPage() = MangasPage(works.map { it.toSManga() }, hasNextPage)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // The story page has both the details and the chapter selector
        val html = client.fetchText("$FFN_BASE${manga.url}")
        val details = if (fetchDetails) parseFfnStory(html, manga.url).toSManga() else manga
        val chapterList = if (fetchChapters) {
            parseFfnChapters(html, manga.url)
                .map { chapter ->
                    SChapter.create().apply {
                        url = chapter.url
                        name = chapter.name
                        chapter_number = chapter.number.toFloat()
                        date_upload = chapter.date
                    }
                }
                .reversed()
        } else {
            chapters
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getChapterHtml(chapterUrl: String): String =
        parseFfnChapter(client.fetchText("$FFN_BASE$chapterUrl"))

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    override fun getFilterList(): FilterList = FilterList()

    override fun webUrl(url: String) = "$FFN_BASE$url"

    override fun toString() = name

    companion object {
        /** Fixed id, next to AO3's and far from the hashes extensions get. */
        const val ID = 7_316_030_304L
    }
}
