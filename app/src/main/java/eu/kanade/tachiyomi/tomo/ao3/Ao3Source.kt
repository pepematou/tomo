package eu.kanade.tachiyomi.tomo.ao3

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.tomo.text.Fandom
import eu.kanade.tachiyomi.tomo.text.FanficSearch
import eu.kanade.tachiyomi.tomo.text.FanficSearchPage
import eu.kanade.tachiyomi.tomo.text.SourceUnavailableException
import eu.kanade.tachiyomi.tomo.text.TextSource
import eu.kanade.tachiyomi.tomo.text.fetchText
import eu.kanade.tachiyomi.tomo.text.toSManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

/**
 * Archive of Our Own as a built-in text source: Spanish works only (RF-30a). Following a work puts it in the library,
 * so the library update finds its new chapters like any other source.
 *
 * Not an HttpSource on purpose: manga global search and migration only use HttpSources, so fanfics stay out of them.
 */
@Inject
@SingleIn(AppScope::class)
class Ao3Source(networkHelper: NetworkHelper) : TextSource {

    override val id: Long = ID
    override val name: String = "Archive of Our Own"
    override val lang: String = "es"
    override val supportsLatest: Boolean = true

    // AO3 has no API; one request every few seconds keeps it polite and avoids being blocked
    private val client: OkHttpClient = networkHelper.client.newBuilder()
        .rateLimit(permits = 1, period = 3.seconds)
        .build()

    override val sorts = listOf(
        FanficSearch.Sort.UPDATED,
        FanficSearch.Sort.KUDOS,
        FanficSearch.Sort.WORDS,
        FanficSearch.Sort.HITS,
    )

    // Canonical AO3 tag names, matched exactly by the fandom filter
    override val fandoms = listOf(
        Fandom("Harry Potter", "Libro", "Harry Potter - J. K. Rowling"),
        Fandom("My Hero Academia", "Anime", "僕のヒーローアカデミア | Boku no Hero Academia | My Hero Academia"),
        Fandom("Genshin Impact", "Videojuego", "原神 | Genshin Impact (Video Game)"),
        Fandom("BTS", "Música", "방탄소년단 | Bangtan Boys | BTS"),
        Fandom("Naruto", "Anime", "Naruto"),
        Fandom("Marvel", "Película", "Marvel Cinematic Universe"),
        Fandom("Haikyuu!!", "Anime", "Haikyuu!!"),
        Fandom("Jujutsu Kaisen", "Anime", "呪術廻戦 | Jujutsu Kaisen (Anime & Manga)"),
    )

    override suspend fun getPopularManga(page: Int): MangasPage = search(
        FanficSearch(sort = FanficSearch.Sort.KUDOS),
        page,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = search(FanficSearch(), page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        search(filters.toSearch(query), page)

    private suspend fun search(search: FanficSearch, page: Int): MangasPage {
        val result = searchWorks(search, page)
        return MangasPage(result.works.map { it.toSManga() }, result.hasNextPage)
    }

    override suspend fun searchWorks(search: FanficSearch, page: Int): FanficSearchPage =
        parseSearch(get(searchUrl(search, page).toString()))

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) {
            parseWork(get("$AO3_BASE${manga.url}?view_adult=true"), manga.url).toSManga()
        } else {
            manga
        }
        val chapterList = if (fetchChapters) {
            parseNavigate(get("$AO3_BASE${manga.url}/navigate"), manga.url)
                .map { chapter ->
                    SChapter.create().apply {
                        url = chapter.url
                        name = chapter.name
                        chapter_number = chapter.number.toFloat()
                        date_upload = chapter.date
                    }
                }
                // Mihon lists the newest chapter first
                .reversed()
        } else {
            chapters
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getChapterHtml(chapterUrl: String): String = parseChapter(
        get("$AO3_BASE$chapterUrl?view_adult=true"),
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Solo obras en español"),
        FandomFilter(),
        StatusFilter(),
        ShortFilter(),
        NoWarningsFilter(),
        SortFilter(),
    )

    private suspend fun get(url: String): String = client.fetchText(url).also { body ->
        if (isLoginPage(body)) throw SourceUnavailableException(SourceUnavailableException.Reason.RESTRICTED)
    }

    override fun webUrl(url: String) = "$AO3_BASE$url".toHttpUrl().toString()

    override fun toString() = name

    private class FandomFilter : Filter.Text("Fandom")
    private class StatusFilter : Filter.Select<String>("Estado", arrayOf("Todos", "Completados", "En curso"))
    private class ShortFilter : Filter.CheckBox("Menos de 10 mil palabras")
    private class NoWarningsFilter : Filter.CheckBox("Sin advertencias")
    private class SortFilter : Filter.Select<String>(
        "Ordenar",
        arrayOf("Actualizados recientemente", "Más kudos", "Más palabras", "Más visitas"),
    )

    private fun FilterList.toSearch(query: String): FanficSearch {
        var search = FanficSearch(query = query)
        forEach { filter ->
            search = when (filter) {
                is FandomFilter -> search.copy(fandom = filter.state)
                is StatusFilter -> search.copy(complete = listOf(null, true, false)[filter.state])
                is ShortFilter -> search.copy(maxWords = if (filter.state) 10_000 else null)
                is NoWarningsFilter -> search.copy(noWarnings = filter.state)
                is SortFilter -> search.copy(sort = sorts[filter.state])
                else -> search
            }
        }
        return search
    }

    companion object {
        /** Fixed id, far from the hashes extensions get. */
        const val ID = 7_316_030_303L

        fun workUrl(workId: Long) = "/works/$workId"
    }
}
