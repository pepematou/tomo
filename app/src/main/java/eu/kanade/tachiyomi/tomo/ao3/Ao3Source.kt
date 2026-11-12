package eu.kanade.tachiyomi.tomo.ao3

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
class Ao3Source(networkHelper: NetworkHelper) : CatalogueSource {

    override val id: Long = ID
    override val name: String = "Archive of Our Own"
    override val lang: String = "es"
    override val supportsLatest: Boolean = true

    // AO3 has no API; one request every few seconds keeps it polite and avoids being blocked
    private val client: OkHttpClient = networkHelper.client.newBuilder()
        .rateLimit(permits = 1, period = 3.seconds)
        .build()

    class RestrictedException : Exception("This work is only available to registered AO3 users")

    override suspend fun getPopularManga(page: Int): MangasPage = search(Ao3Search(sort = Ao3Search.Sort.KUDOS), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = search(Ao3Search(), page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        search(filters.toSearch(query), page)

    suspend fun search(search: Ao3Search, page: Int): MangasPage {
        val result = parseSearch(get(searchUrl(search, page).toString()))
        return MangasPage(result.works.map { it.toSManga() }, result.hasNextPage)
    }

    /** Same search, keeping AO3's details (summary, tags, stats) that MangasPage would drop. */
    suspend fun searchWorks(search: Ao3Search, page: Int): Ao3SearchPage =
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

    /** The text of a chapter as HTML, author's notes included (see [parseChapter]). */
    suspend fun getChapterHtml(chapterUrl: String): String = parseChapter(get("$AO3_BASE$chapterUrl?view_adult=true"))

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Solo obras en español"),
        FandomFilter(),
        StatusFilter(),
        ShortFilter(),
        NoWarningsFilter(),
        SortFilter(),
    )

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(GET(url)).await().use { response ->
            if (response.request.url.encodedPath.startsWith("/users/login")) throw RestrictedException()
            val body = response.body.string()
            if (!response.isSuccessful) error("HTTP error ${response.code}")
            if (isLoginPage(body)) throw RestrictedException()
            body
        }
    }

    override fun toString() = name

    private class FandomFilter : Filter.Text("Fandom")
    private class StatusFilter : Filter.Select<String>("Estado", arrayOf("Todos", "Completados", "En curso"))
    private class ShortFilter : Filter.CheckBox("Menos de 10 mil palabras")
    private class NoWarningsFilter : Filter.CheckBox("Sin advertencias")
    private class SortFilter : Filter.Select<String>(
        "Ordenar",
        arrayOf("Actualizados recientemente", "Más kudos", "Más palabras", "Más visitas"),
    )

    private fun FilterList.toSearch(query: String): Ao3Search {
        var search = Ao3Search(query = query)
        forEach { filter ->
            search = when (filter) {
                is FandomFilter -> search.copy(fandom = filter.state)
                is StatusFilter -> search.copy(complete = listOf(null, true, false)[filter.state])
                is ShortFilter -> search.copy(maxWords = if (filter.state) 10_000 else null)
                is NoWarningsFilter -> search.copy(noWarnings = filter.state)
                is SortFilter -> search.copy(sort = Ao3Search.Sort.entries[filter.state])
                else -> search
            }
        }
        return search
    }

    companion object {
        /** Fixed id, far from the hashes extensions get. */
        const val ID = 7_316_030_303L

        fun workUrl(workId: Long) = "/works/$workId"

        fun webUrl(url: String) = "$AO3_BASE$url".toHttpUrl().toString()
    }
}

fun Ao3Work.toSManga(): SManga = SManga.create().also {
    it.url = url
    it.title = title
    it.author = author
    it.description = summary
    it.genre = tags.joinToString()
    it.status = if (meta.complete) SManga.COMPLETED else SManga.ONGOING
    it.memo = meta.toMemo()
    it.initialized = true
}
