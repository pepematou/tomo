package eu.kanade.presentation.library

import eu.kanade.tachiyomi.ui.library.LibraryItem
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

class ShelfTest {

    private fun item(total: Long, read: Long) = LibraryManga(
        manga = Manga.create().copy(id = 7, title = "Berserk"),
        categories = emptyList(),
        totalChapters = total,
        readCount = read,
        bookmarkCount = 0,
        latestUpload = null,
        chapterFetchedAt = null,
        lastRead = null,
    ).let {
        LibraryItem(
            libraryManga = it,
            downloadCount = 0,
            unreadCount = total - read,
            isLocal = false,
            sourceName = "",
            sourceLanguage = "es",
            badges = LibraryItem.Badges(
                downloadCount = 0,
                unreadCount = total - read,
                isLocal = false,
                sourceLanguage = "es",
            ),
        )
    }

    @Test
    fun `spines grow with the chapter count between 26 and 44 dp`() {
        spineWidth(0) shouldBe 26
        spineWidth(160) shouldBe 36
        spineWidth(5000) shouldBe 44
    }

    @Test
    fun `spine height is stable per series and within range`() {
        spineHeight(42) shouldBe spineHeight(42)
        (1L..500L).forEach { spineHeight(it) shouldBeInRange 180..223 }
        spineHeight(-3) shouldBeInRange 180..223
    }

    @Test
    fun `card offers migrating, reading on or opening the series`() {
        shelfAction(item(total = 10, read = 4), sourceDown = true) shouldBe ShelfAction.SOURCE_DOWN
        shelfAction(item(total = 10, read = 4), sourceDown = false) shouldBe ShelfAction.CONTINUE
        shelfAction(item(total = 10, read = 10), sourceDown = false) shouldBe ShelfAction.CAUGHT_UP
    }
}
