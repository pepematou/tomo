package tomo.desktop

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SettingsTest {

    @Test
    fun `only fanfics already followed and grown are news`() {
        val current = mapOf(
            "/s/1" to ("Bajo la lluvia" to 5),
            "/s/2" to ("Igual" to 3),
            "/s/3" to ("Recién seguido" to 9),
        )
        val known = mapOf("/s/1" to 3, "/s/2" to 3)
        newChapters(current, known) shouldBe listOf("Bajo la lluvia" to 2)
    }
}
