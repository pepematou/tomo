package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReadingProgressTest {

    @Test
    fun `caught up only when every chapter is read`() {
        HomeViewModel.ReadingProgress(page = 0, readChapters = 40, totalChapters = 40).caughtUp shouldBe true
        HomeViewModel.ReadingProgress(page = 3, readChapters = 12, totalChapters = 40).caughtUp shouldBe false
        HomeViewModel.ReadingProgress(page = 0, readChapters = 0, totalChapters = 0).caughtUp shouldBe false
    }
}
