package eu.kanade.tachiyomi.tomo.ffn

import eu.kanade.tachiyomi.tomo.text.FanficMeta
import eu.kanade.tachiyomi.tomo.text.FanficSearch
import eu.kanade.tachiyomi.tomo.text.SourceUnavailableException
import eu.kanade.tachiyomi.tomo.text.classifyFailure
import eu.kanade.tachiyomi.tomo.text.fanficLink
import eu.kanade.tachiyomi.tomo.text.isMaintenancePage
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.UnknownHostException

class FfnParserTest {

    private fun item(id: Int, title: String, info: String) = """
        <div class="z-list zhover zpointer">
          <a class="stitle" href="/s/$id/1/Slug"><img class="cimage">$title</a>
          by <a href="/u/9/Autora">Autora</a>
          <div class="z-indent z-padtop">Un resumen.<div class="z-padtop2 xgray">$info</div></div>
        </div>
    """

    private val listHtml = "<div id=\"content_wrapper_inner\">" +
        item(
            11,
            "Bajo la lluvia",
            "Rated: T - Spanish - Romance/Hurt/Comfort - Chapters: 20 - Words: 48,210 - Reviews: 3 - " +
                "Updated: <span data-xutime='1700000000'>Nov 3</span> - " +
                "Published: <span data-xutime='1600000000'>Sep 13</span> - [Naruto U., Hinata H.] Sasuke U.",
        ) +
        item(
            12,
            "Terminado",
            "Rated: M - Spanish - Drama - Chapters: 3 - Words: 9,000 - " +
                "Published: <span data-xutime='1600000000'>Sep 13</span> - Complete",
        ) +
        item(13, "In English", "Rated: K - English - General - Chapters: 1 - Words: 500 - Complete") +
        "<center><a href=\"?p=2\">2</a> <a href=\"?p=2\">Next &#187;</a></center></div>"

    @Test
    fun `list keeps Spanish stories with their stats`() {
        val page = parseFfnSearch(listHtml, FanficSearch())
        page.works.map { it.url } shouldBe listOf("/s/11", "/s/12")
        page.hasNextPage shouldBe true
        val first = page.works.first()
        first.title shouldBe "Bajo la lluvia"
        first.author shouldBe "Autora"
        first.summary shouldBe "Un resumen."
        first.tags shouldBe listOf("Romance", "Dolor", "Consuelo")
        first.meta.words shouldBe 48_210
        first.meta.chaptersPosted shouldBe 20
        first.meta.chaptersTotal shouldBe null
        first.meta.complete shouldBe false
        first.meta.rating shouldBe "Teen And Up Audiences"
        first.meta.relationships shouldBe listOf("Naruto U./Hinata H.")
        first.meta.updatedAt shouldBe 1_700_000_000_000
        page.works[1].meta.complete shouldBe true
        page.works[1].meta.updatedAt shouldBe 1_600_000_000_000
    }

    @Test
    fun `filters the URL can't express are applied to the list`() {
        parseFfnSearch(listHtml, FanficSearch(complete = true)).works.map { it.url } shouldBe listOf("/s/12")
        parseFfnSearch(listHtml, FanficSearch(maxWords = 10_000)).works.map { it.url } shouldBe listOf("/s/12")
        parseFfnSearch(listHtml, FanficSearch(noWarnings = true)).works.map { it.url } shouldBe listOf("/s/11")
    }

    @Test
    fun `search is always in Spanish`() {
        val search = ffnSearchUrl(FanficSearch(query = "lluvia"), 2, "/book/Harry-Potter/")
        search.encodedPath shouldBe "/search/"
        search.queryParameter("languageid") shouldBe "2"
        search.queryParameter("ppage") shouldBe "2"
        val fandom = ffnSearchUrl(FanficSearch(fandom = "/anime/Naruto/"), 1, "/book/Harry-Potter/")
        fandom.encodedPath shouldBe "/anime/Naruto/"
        fandom.queryParameter("lan") shouldBe "2"
        ffnSearchUrl(FanficSearch(), 1, "/book/Harry-Potter/").encodedPath shouldBe "/book/Harry-Potter/"
    }

    private val storyHtml = """
        <div id="pre_story_links"><span class="lc-left"><a href="/anime/">Anime/Manga</a> »
          <a href="/anime/Naruto/">Naruto</a></span></div>
        <div id="profile_top">
          <b class="xcontrast_txt">Bajo la lluvia</b>
          <span class="xcontrast_txt">By:</span> <a class="xcontrast_txt" href="/u/9/Autora">Autora</a>
          <div class="xcontrast_txt" style="margin-top:2px">Dos ninjas y una tormenta.</div>
          <span class="xgray xcontrast_txt">Rated: <a>Fiction  T</a> - Spanish - Romance - Chapters: 3 -
            Words: 9,000 - Updated: <span data-xutime='1700000000'>Nov 3</span> -
            Published: <span data-xutime='1600000000'>Sep 13</span> - Status: Complete - id: 11</span>
        </div>
        <select id="chap_select"><option value="1" selected>1. Inicio<option value="2">2. Medio
          <option value="3">3. Final</select>
        <div class="storytext xcontrast_txt nocopy" id="storytext"><p>Llovía.</p><p>Mucho.</p></div>
    """

    @Test
    fun `story page gives details and chapters`() {
        val work = parseFfnStory(storyHtml, "/s/11")
        work.title shouldBe "Bajo la lluvia"
        work.author shouldBe "Autora"
        work.summary shouldBe "Dos ninjas y una tormenta."
        work.meta.complete shouldBe true
        work.meta.chaptersTotal shouldBe 3
        work.meta.fandoms shouldBe listOf("Naruto")
        val chapters = parseFfnChapters(storyHtml, "/s/11")
        chapters.map { it.url } shouldBe listOf("/s/11/1", "/s/11/2", "/s/11/3")
        chapters.first().name shouldBe "1. Inicio"
        chapters.first().date shouldBe 1_600_000_000_000
        chapters.last().date shouldBe 1_700_000_000_000
        parseFfnChapter(storyHtml) shouldBe "<p>Llovía.</p>\n<p>Mucho.</p>"
    }

    @Test
    fun `one-shot without selector has one chapter`() {
        parseFfnChapters("<div id='profile_top'></div>", "/s/5").map { it.url } shouldBe listOf("/s/5/1")
    }

    @Test
    fun `shared links of both sites open the work`() {
        fanficLink("mira https://www.fanfiction.net/s/123/4/Titulo")?.second shouldBe "/s/123"
        fanficLink("https://archiveofourown.org/works/77/chapters/9")?.second shouldBe "/works/77"
        fanficLink("https://example.com") shouldBe null
    }

    @Test
    fun `failures are told apart`() {
        classifyFailure(IOException(UnknownHostException())) shouldBe SourceUnavailableException.Reason.OFFLINE
        classifyFailure(IOException("x", SourceUnavailableException(SourceUnavailableException.Reason.DOWN))) shouldBe
            SourceUnavailableException.Reason.DOWN
        classifyFailure(IllegalStateException("HTTP error 500")) shouldBe SourceUnavailableException.Reason.UNKNOWN
        isMaintenancePage(503, "") shouldBe true
        isMaintenancePage(500, "Down for maintenance") shouldBe true
        isMaintenancePage(404, "maintenance") shouldBe false
    }

    @Test
    fun `memo written by v0_15 still reads`() {
        val memo = Json.parseToJsonElement(
            """{"words":48210,"chaptersPosted":14,"chaptersTotal":20,"complete":false,"fandoms":["X"],""" +
                """"relationships":["A/B"],"warnings":[],"rating":"Mature","updatedAt":5}""",
        ).jsonObject
        FanficMeta.fromMemo(memo)?.chaptersPosted shouldBe 14
        FanficMeta.fromMemo(memo)?.rating shouldBe "Mature"
    }
}
