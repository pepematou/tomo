package eu.kanade.tachiyomi.tomo.text

import eu.kanade.tachiyomi.tomo.text.TextBlock.Kind
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class TextBlocksTest {

    @Test
    fun `splits headings, paragraphs, quotes and breaks`() {
        val blocks = parseBlocks(
            """
            <html><head><style>p{}</style><script>x()</script></head><body>
            <h2>Chapter 4: The Return</h2>
            <p>Harry had not slept   in three days.</p>
            <hr/>
            <blockquote>A letter.</blockquote>
            <div><p>Inside a div.</p>Loose text</div>
            <p>   </p>
            </body></html>
            """.trimIndent(),
        )
        blocks.map { it.kind to it.text } shouldBe listOf(
            Kind.HEADING to "Chapter 4: The Return",
            Kind.PARAGRAPH to "Harry had not slept in three days.",
            Kind.BREAK to "",
            Kind.QUOTE to "A letter.",
            Kind.PARAGRAPH to "Inside a div.",
            Kind.PARAGRAPH to "Loose text",
        )
    }

    @Test
    fun `keeps italic and bold runs`() {
        val block = parseBlocks("<p>The <em>letter</em> was <strong>still</strong> there.</p>").single()
        block.text shouldBe "The letter was still there."
        block.spans shouldBe listOf(
            TextBlock.Span(4, 10, bold = false, italic = true),
            TextBlock.Span(15, 20, bold = true, italic = false),
        )
    }

    @Test
    fun `line breaks stay inside the paragraph`() {
        parseBlocks("<p>Line one<br/>Line two</p>").single().text shouldBe "Line one\nLine two"
    }

    @Test
    fun `plain text becomes an epub the reader can parse back`() {
        val body = textToXhtmlBody("First line\nsame paragraph\n\nSecond <paragraph> & more\n")
        val out = ByteArrayOutputStream()
        writeEpub("My Fic", "Someone", body, out)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes().decodeToString() }
        }
        entries.keys.first() shouldBe "mimetype"
        val opf = Jsoup.parse(entries.getValue("OEBPS/content.opf"), "", Parser.xmlParser())
        epubMetadata(opf).title shouldBe "My Fic"
        epubMetadata(opf).author shouldBe "Someone"
        parseBlocks(entries.getValue("OEBPS/text.xhtml")).map { it.text } shouldBe
            listOf("First line\nsame paragraph", "Second <paragraph> & more")
    }

    @Test
    fun `reads AO3 tags and summary from the package document`() {
        val opf = Jsoup.parse(
            """
            <package><metadata>
            <dc:title>Fic</dc:title><dc:creator>A</dc:creator><dc:creator>B</dc:creator>
            <dc:subject>Fanfiction</dc:subject><dc:subject>Harry Potter, Angst</dc:subject>
            <dc:description>&lt;p&gt;A summary.&lt;/p&gt;</dc:description>
            </metadata></package>
            """.trimIndent(),
            "",
            Parser.xmlParser(),
        )
        epubMetadata(opf) shouldBe EpubMetadata(
            title = "Fic",
            author = "A, B",
            tags = listOf("Fanfiction", "Harry Potter", "Angst"),
            description = "A summary.",
        )
    }

    @Test
    fun `finds the AO3 work id in every link form`() {
        ao3WorkId("https://archiveofourown.org/works/123456") shouldBe 123456
        ao3WorkId("Look: https://archiveofourown.org/works/42/chapters/99#workskin") shouldBe 42
        ao3WorkId("archiveofourown.org/works/7?view_full_work=true") shouldBe 7
        ao3WorkId("https://archiveofourown.org/collections/fest/works/8") shouldBe 8
        ao3WorkId("https://example.com/works/1") shouldBe null
    }

    @Test
    fun `work type comes from the import, the source language or the tags`() {
        workType(listOf("Action"), "es", isText = true) shouldBe WorkType.TEXT
        workType(listOf("Action"), "ko", isText = false) shouldBe WorkType.MANHWA
        workType(listOf("Action", "Webtoon"), "es", isText = false) shouldBe WorkType.MANHWA
        workType(listOf("Action"), "ja", isText = false) shouldBe WorkType.MANGA
        workType(null, "en", isText = false) shouldBe WorkType.MANGA
    }

    @Test
    fun `spine pages resolve against the package folder`() {
        val opf = Jsoup.parse(
            """
            <package><manifest>
            <item id="c2" href="Text/ch2.xhtml" media-type="application/xhtml+xml"/>
            <item id="c1" href="Text/ch%201.xhtml#top" media-type="application/xhtml+xml"/>
            <item id="css" href="style.css" media-type="text/css"/>
            </manifest><spine><itemref idref="c1"/><itemref idref="c2"/><itemref idref="css"/></spine></package>
            """.trimIndent(),
            "",
            Parser.xmlParser(),
        )
        spineHrefs(opf) shouldBe listOf("Text/ch%201.xhtml", "Text/ch2.xhtml")
        resolveEntry("OEBPS", "Text/ch%201.xhtml") shouldBe "OEBPS/Text/ch 1.xhtml"
        resolveEntry("OEBPS/Text", "../Images/a.xhtml") shouldBe "OEBPS/Images/a.xhtml"
        resolveEntry("", "text.xhtml") shouldBe "text.xhtml"
    }

    @Test
    fun `folder names drop characters storage rejects`() {
        folderName("Harry: Year 1 / Part \"2\"?") shouldBe "Harry Year 1 Part 2"
        folderName("  ...  ") shouldBe "Texto"
    }
}
