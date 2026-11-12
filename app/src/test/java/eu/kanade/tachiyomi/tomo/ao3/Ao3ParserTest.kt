package eu.kanade.tachiyomi.tomo.ao3

import eu.kanade.tachiyomi.tomo.text.TextBlock
import eu.kanade.tachiyomi.tomo.text.parseBlocks
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class Ao3ParserTest {

    private val searchHtml = """
        <ol class="work index group">
        <li id="work_123" class="work blurb group" role="article">
          <div class="header module">
            <h4 class="heading">
              <a href="/works/123">Lo que el invierno no dijo</a> by
              <a rel="author" href="/users/luna/pseuds/luna">luna_de_tinta</a>
            </h4>
            <h5 class="fandoms heading"><span class="landmark">Fandoms:</span>
              <a class="tag" href="/tags/x/works">Academia de Magos</a></h5>
            <ul class="required-tags">
              <li><span class="rating-teen rating" title="Teen And Up Audiences"><span class="text">T</span></span></li>
              <li><span class="complete-no iswip" title="Work in Progress"></span></li>
            </ul>
            <p class="datetime">09 Nov 2026</p>
          </div>
          <ul class="tags commas">
            <li class="warnings"><strong><a class="tag">No Archive Warnings Apply</a></strong></li>
            <li class="relationships"><a class="tag">Elena/Marcos</a></li>
            <li class="freeforms"><a class="tag">Enemigos a amantes</a></li>
            <li class="freeforms"><a class="tag">Final feliz</a></li>
          </ul>
          <blockquote class="userstuff summary"><p>Dos rivales.</p><p>Un invierno.</p></blockquote>
          <dl class="stats">
            <dt class="words">Words:</dt><dd class="words">48,210</dd>
            <dt class="chapters">Chapters:</dt><dd class="chapters"><a href="/works/123/chapters/9">14</a>/20</dd>
          </dl>
        </li>
        </ol>
        <ol class="pagination actions"><li class="next"><a rel="next" href="/works/search?page=2">Next</a></li></ol>
    """.trimIndent()

    @Test
    fun `search results carry the work, its tags and stats`() {
        val page = parseSearch(searchHtml)
        page.hasNextPage shouldBe true
        val work = page.works.single()
        work.url shouldBe "/works/123"
        work.title shouldBe "Lo que el invierno no dijo"
        work.author shouldBe "luna_de_tinta"
        work.summary shouldBe "Dos rivales.\n\nUn invierno."
        work.tags shouldBe listOf("Enemigos a amantes", "Final feliz")
        with(work.meta) {
            words shouldBe 48210
            chaptersPosted shouldBe 14
            chaptersTotal shouldBe 20
            complete shouldBe false
            fandoms shouldBe listOf("Academia de Magos")
            relationships shouldBe listOf("Elena/Marcos")
            warnings shouldBe listOf("No Archive Warnings Apply")
            rating shouldBe "Teen And Up Audiences"
            (updatedAt > 0) shouldBe true
        }
    }

    @Test
    fun `work page gives the metadata of the ficha`() {
        val html = """
            <dl class="work meta group">
              <dd class="rating tags"><ul><li><a class="tag">General Audiences</a></li></ul></dd>
              <dd class="warning tags"><ul><li><a class="tag">No Archive Warnings Apply</a></li></ul></dd>
              <dd class="fandom tags"><ul><li><a class="tag">Academia de Magos</a></li></ul></dd>
              <dd class="relationship tags"><ul><li><a class="tag">A/B</a></li></ul></dd>
              <dd class="freeform tags"><ul><li><a class="tag">Invierno</a></li></ul></dd>
              <dd class="stats"><dl class="stats">
                <dd class="published">2026-01-02</dd><dd class="status">2026-11-09</dd>
                <dd class="words">3,900</dd><dd class="chapters">3/3</dd>
              </dl></dd>
            </dl>
            <div id="workskin"><div class="preface group">
              <h2 class="title heading">El deshielo</h2>
              <h3 class="byline heading"><a rel="author" href="/users/a">autora</a></h3>
              <div class="summary module"><blockquote class="userstuff"><p>Resumen.</p></blockquote></div>
            </div></div>
        """.trimIndent()
        val work = parseWork(html, "/works/5")
        work.title shouldBe "El deshielo"
        work.author shouldBe "autora"
        work.summary shouldBe "Resumen."
        work.tags shouldBe listOf("Invierno")
        work.meta.complete shouldBe true
        work.meta.rating shouldBe "General Audiences"
        work.meta.relationships shouldBe listOf("A/B")
        Ao3Meta.fromMemo(work.meta.toMemo()) shouldBe work.meta
    }

    @Test
    fun `chapter index keeps order and dates, one-shots fall back to the work`() {
        val html = """
            <ol class="chapter index group">
              <li><a href="/works/5/chapters/11">1. La primera nevada</a> <span class="datetime">(2026-06-01)</span></li>
              <li><a href="/works/5/chapters/12">2. Ventanas cerradas</a> <span class="datetime">(2026-06-08)</span></li>
            </ol>
        """.trimIndent()
        val chapters = parseNavigate(html, "/works/5")
        chapters.map { it.url } shouldBe listOf("/works/5/chapters/11", "/works/5/chapters/12")
        chapters.map { it.number } shouldBe listOf(1, 2)
        (chapters[1].date > chapters[0].date) shouldBe true
        parseNavigate("<html></html>", "/works/7").single().url shouldBe "/works/7"
    }

    @Test
    fun `chapter text folds the author notes and drops the landmark`() {
        val html = """
            <div id="chapters"><div class="chapter">
              <div class="chapter preface group">
                <h3 class="title"><a href="#">Chapter 14</a>: El deshielo</h3>
                <div class="notes module"><blockquote class="userstuff"><p>Gracias por leer.</p></blockquote></div>
              </div>
              <div class="userstuff module" role="article">
                <h3 class="landmark heading">Chapter Text</h3>
                <p>La nieve llegó.</p><p>—No va a parar.</p>
              </div>
            </div></div>
        """.trimIndent()
        val text = parseChapter(html)
        text shouldNotContain "Chapter Text"
        val blocks = parseBlocks(text)
        blocks.map { it.kind } shouldBe listOf(
            TextBlock.Kind.HEADING,
            TextBlock.Kind.NOTES,
            TextBlock.Kind.PARAGRAPH,
            TextBlock.Kind.PARAGRAPH,
        )
        blocks[1].text shouldBe "Gracias por leer."
    }

    @Test
    fun `every search is limited to Spanish`() {
        val url = searchUrl(Ao3Search(query = "Elena", complete = true, maxWords = 10000, noWarnings = true), page = 2)
        url.queryParameter("work_search[language_id]") shouldBe "es"
        url.queryParameter("work_search[complete]") shouldBe "T"
        url.queryParameter("work_search[words_to]") shouldBe "10000"
        url.queryParameter("page") shouldBe "2"
        url.queryParameterValues("work_search[archive_warning_ids][]") shouldContainAll listOf("16")
        searchUrl(Ao3Search(), 1).toString() shouldContain "language_id%5D=es"
    }

    @Test
    fun `login page means a restricted work`() {
        isLoginPage("""<form id="new_user" action="/users/login"></form>""") shouldBe true
        isLoginPage("""<div id="workskin"></div>""") shouldBe false
    }
}
