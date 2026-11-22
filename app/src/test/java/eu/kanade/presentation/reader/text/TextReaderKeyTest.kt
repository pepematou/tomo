package eu.kanade.presentation.reader.text

import androidx.compose.ui.input.key.Key
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TextReaderKeyTest {

    @Test
    fun `keyboard moves the text and leaves`() {
        textReaderKey(Key.Spacebar, shift = false) shouldBe TextReaderKey.PAGE_DOWN
        textReaderKey(Key.Spacebar, shift = true) shouldBe TextReaderKey.PAGE_UP
        textReaderKey(Key.PageDown, shift = false) shouldBe TextReaderKey.PAGE_DOWN
        textReaderKey(Key.DirectionLeft, shift = false) shouldBe TextReaderKey.PREVIOUS_CHAPTER
        textReaderKey(Key.Escape, shift = false) shouldBe TextReaderKey.EXIT
        textReaderKey(Key.A, shift = false) shouldBe null
    }
}
