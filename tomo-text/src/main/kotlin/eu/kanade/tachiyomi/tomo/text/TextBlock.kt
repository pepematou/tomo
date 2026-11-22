package eu.kanade.tachiyomi.tomo.text

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** One block of a text chapter: a heading, a paragraph, a quote or a scene break. */
data class TextBlock(val kind: Kind, val text: String = "", val spans: List<Span> = emptyList()) {
    /** NOTES are the author's notes, shown folded so they don't interrupt the story. */
    enum class Kind { HEADING, PARAGRAPH, QUOTE, BREAK, NOTES }

    /** Bold or italic run inside [text]. */
    data class Span(val start: Int, val end: Int, val bold: Boolean, val italic: Boolean)
}

private val headingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val paragraphTags = setOf("p", "li", "dd", "dt", "pre")
private val skippedTags = setOf("script", "style", "head", "title", "nav", "svg", "img", "image")
private val boldTags = setOf("b", "strong")
private val italicTags = setOf("i", "em", "cite")

/** Splits an (X)HTML page into readable blocks, keeping bold and italic and dropping scripts, styles and images. */
fun parseBlocks(html: String): List<TextBlock> {
    val body = Jsoup.parse(html).body()
    return buildList { collectBlocks(body, this) }
}

private fun collectBlocks(element: Element, out: MutableList<TextBlock>) {
    val tag = element.tagName().lowercase()
    when {
        tag in skippedTags -> return
        tag == "hr" -> out += TextBlock(TextBlock.Kind.BREAK)
        tag == "aside" && element.hasClass("notes") -> {
            val paragraphs = element.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }
            val text = paragraphs.ifEmpty { listOf(element.text().trim()) }.joinToString("\n\n")
            if (text.isNotBlank()) out += TextBlock(TextBlock.Kind.NOTES, text)
        }
        tag in headingTags -> inlineBlock(element, TextBlock.Kind.HEADING)?.let(out::add)
        tag in paragraphTags -> inlineBlock(element, TextBlock.Kind.PARAGRAPH)?.let(out::add)
        tag == "blockquote" && element.children().none { it.isBlock } ->
            inlineBlock(element, TextBlock.Kind.QUOTE)?.let(out::add)
        // A container: its loose text becomes a paragraph, its block children are read one by one
        else -> {
            val loose = mutableListOf<Node>()
            fun flushLoose() {
                if (loose.isEmpty()) return
                val holder = Element("p")
                loose.forEach { holder.appendChild(it.clone()) }
                inlineBlock(holder, TextBlock.Kind.PARAGRAPH)?.let(out::add)
                loose.clear()
            }
            element.childNodes().forEach { child ->
                if (child is Element && (child.isBlock || child.tagName().lowercase() in skippedTags)) {
                    flushLoose()
                    collectBlocks(child, out)
                } else {
                    loose += child
                }
            }
            flushLoose()
        }
    }
}

private fun inlineBlock(element: Element, kind: TextBlock.Kind): TextBlock? {
    val text = StringBuilder()
    val spans = mutableListOf<TextBlock.Span>()
    fun walk(node: Node, bold: Boolean, italic: Boolean) {
        when (node) {
            is TextNode -> {
                val value = node.wholeText.replace(Regex("\\s+"), " ")
                if (value.isEmpty()) return
                // Collapse spaces across nodes the way a browser would
                val piece = if (text.endsWith(" ") || text.isEmpty() ||
                    text.endsWith("\n")
                ) {
                    value.trimStart()
                } else {
                    value
                }
                if (piece.isEmpty()) return
                val start = text.length
                text.append(piece)
                if (bold || italic) spans += TextBlock.Span(start, text.length, bold, italic)
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                if (tag in skippedTags) return
                if (tag == "br") {
                    if (text.isNotEmpty()) text.append('\n')
                    return
                }
                node.childNodes().forEach { walk(it, bold || tag in boldTags, italic || tag in italicTags) }
            }
        }
    }
    walk(element, bold = false, italic = false)
    val trimmedEnd = text.trimEnd().length
    if (trimmedEnd == 0) return null
    return TextBlock(
        kind = kind,
        text = text.substring(0, trimmedEnd),
        spans = spans.mapNotNull { span ->
            span.copy(end = minOf(span.end, trimmedEnd)).takeIf { it.start < it.end }
        },
    )
}
