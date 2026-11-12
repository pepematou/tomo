package eu.kanade.tachiyomi.tomo.text

import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class TextCoverTest {

    @Test
    fun `each title keeps the same colour, in range`() {
        coverHue("Lo que el invierno no dijo") shouldBe coverHue("LO QUE EL INVIERNO NO DIJO")
        coverHue("Lo que el invierno no dijo") shouldNotBe coverHue("La cartógrafa de estrellas")
        listOf("", "a", "Doce cartas para el alba").forEach { coverHue(it).shouldBeBetween(0f, 359f, 0f) }
    }
}
