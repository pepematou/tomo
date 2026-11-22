package eu.kanade.tachiyomi.tomo.text

import mihon.core.archive.EpubReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Every page of an EPUB, in reading order (its spine), as one list of blocks. */
fun readEpubBlocks(epub: EpubReader): List<TextBlock> {
    val packageHref = epub.getPackageHref()
    val opf = epub.getPackageDocument(packageHref)
    val base = packageHref.substringBeforeLast('/', "")
    return spineHrefs(opf).flatMap { href ->
        val path = resolveEntry(base, href)
        epub.getInputStream(path)?.use { parseBlocks(it.readBytes().decodeToString()) }.orEmpty()
    }
}

/** The XHTML pages listed in the spine of a package document, in reading order. */
fun spineHrefs(opf: Document): List<String> {
    val items = opf.select("manifest > item").associateBy { it.attr("id") }
    return opf.select("spine > itemref")
        .mapNotNull { items[it.attr("idref")] }
        .filter { it.attr("media-type").let { type -> type == "application/xhtml+xml" || type == "text/html" } }
        .map { it.attr("href").substringBefore('#') }
}

/** Zip entry of [href] relative to the package folder [base], resolving "../" and percent-escapes. */
fun resolveEntry(base: String, href: String): String {
    val parts = ArrayDeque<String>()
    (base.split('/') + java.net.URLDecoder.decode(href, "UTF-8").split('/')).forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> parts.removeLastOrNull()
            else -> parts.addLast(part)
        }
    }
    return parts.joinToString("/")
}

/** Title, authors, tags and summary from an EPUB package document (`content.opf`). */
data class EpubMetadata(
    val title: String?,
    val author: String?,
    val tags: List<String>,
    val description: String?,
)

fun epubMetadata(opf: Document): EpubMetadata {
    fun all(tag: String) = opf.getElementsByTag(tag).map { it.text().trim() }.filter { it.isNotEmpty() }
    return EpubMetadata(
        title = all("dc:title").firstOrNull(),
        author = all("dc:creator").takeIf { it.isNotEmpty() }?.joinToString(", "),
        tags = all("dc:subject").flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        description = all("dc:description").firstOrNull()?.let { Jsoup.parse(it).text() },
    )
}

/** Plain text as XHTML paragraphs: blank lines split paragraphs, single line breaks are kept. */
fun textToXhtmlBody(text: String): String = text
    .replace("\r\n", "\n")
    .split(Regex("\n\\s*\n"))
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n") { paragraph ->
        "<p>" + paragraph.lines().joinToString("<br/>") { Parser.unescapeEntities(it, false).escapeXml() } + "</p>"
    }

private fun String.escapeXml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** Writes a minimal valid EPUB with one XHTML page holding [bodyXhtml]. */
fun writeEpub(title: String, author: String?, bodyXhtml: String, out: OutputStream) {
    ZipOutputStream(out).use { zip ->
        // The mimetype entry must come first and be stored uncompressed
        val mimetype = "application/epub+zip".toByteArray()
        zip.putNextEntry(
            ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                crc = CRC32().apply { update(mimetype) }.value
            },
        )
        zip.write(mimetype)
        zip.closeEntry()
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        entry(
            "META-INF/container.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""",
        )
        entry(
            "OEBPS/content.opf",
            """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="id">tomo-${title.hashCode()}</dc:identifier>
<dc:title>${title.escapeXml()}</dc:title>
${author?.let { "<dc:creator>${it.escapeXml()}</dc:creator>" }.orEmpty()}
<dc:language>und</dc:language>
</metadata>
<manifest><item id="text" href="text.xhtml" media-type="application/xhtml+xml"/></manifest>
<spine><itemref idref="text"/></spine>
</package>""",
        )
        entry(
            "OEBPS/text.xhtml",
            """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${title.escapeXml()}</title></head>
<body>
$bodyXhtml
</body></html>""",
        )
    }
}

private val ao3WorkRegex = Regex("""archiveofourown\.org/(?:collections/[^/]+/)?works/(\d+)""")

/** The work id of an AO3 link, from any of its forms (chapter, full work, collection); null for other links. */
fun ao3WorkId(text: String): Long? = ao3WorkRegex.find(text)?.groupValues?.get(1)?.toLongOrNull()

enum class WorkType { MANGA, MANHWA, TEXT }

private val manhwaTags = listOf("manhwa", "webtoon", "long strip", "manhua")

/** Text when imported as such; manhwa when tagged as a vertical comic or from a Korean source; manga otherwise. */
fun workType(genres: List<String>?, sourceLang: String, isText: Boolean): WorkType = when {
    isText -> WorkType.TEXT
    sourceLang.equals("ko", ignoreCase = true) -> WorkType.MANHWA
    genres.orEmpty().any { genre -> manhwaTags.any { genre.contains(it, ignoreCase = true) } } -> WorkType.MANHWA
    else -> WorkType.MANGA
}
