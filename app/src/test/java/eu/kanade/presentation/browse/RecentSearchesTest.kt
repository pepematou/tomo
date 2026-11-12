package eu.kanade.presentation.browse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RecentSearchesTest {

    @Test
    fun `newest first without duplicates`() {
        addRecentSearch(listOf("Berserk", "Solo Leveling"), "solo leveling") shouldBe
            listOf("solo leveling", "Berserk")
    }

    @Test
    fun `keeps at most eight and ignores blank queries`() {
        val recents = (1..8).map { "q$it" }
        addRecentSearch(recents, "new") shouldBe listOf("new") + (1..7).map { "q$it" }
        addRecentSearch(recents, "  ") shouldBe recents
    }
}
