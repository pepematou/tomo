package eu.kanade.tachiyomi.tomo.text

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReadAloudTest {

    @Test
    fun `reads from the given block, skipping breaks, notes and long paragraphs in parts`() {
        val blocks = listOf(
            TextBlock(TextBlock.Kind.HEADING, "Capítulo 1"),
            TextBlock(TextBlock.Kind.NOTES, "Gracias por leer"),
            TextBlock(TextBlock.Kind.PARAGRAPH, "Llovía."),
            TextBlock(TextBlock.Kind.BREAK),
            TextBlock(TextBlock.Kind.PARAGRAPH, "a".repeat(5_000)),
        )
        speakable(blocks, 0).map { it.first } shouldBe listOf("0:0", "2:0", "4:0", "4:1")
        speakable(blocks, 3).map { it.first } shouldBe listOf("4:0", "4:1")
        blockOf("4:1") shouldBe 4
    }
}
