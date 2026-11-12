package eu.kanade.presentation.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

class GreetingTest {

    @Test
    fun `greeting follows the time of day`() {
        greetingFor(4) shouldBe MR.strings.tomo_greeting_evening
        greetingFor(5) shouldBe MR.strings.tomo_greeting_morning
        greetingFor(11) shouldBe MR.strings.tomo_greeting_morning
        greetingFor(12) shouldBe MR.strings.tomo_greeting_afternoon
        greetingFor(19) shouldBe MR.strings.tomo_greeting_afternoon
        greetingFor(20) shouldBe MR.strings.tomo_greeting_evening
    }
}
