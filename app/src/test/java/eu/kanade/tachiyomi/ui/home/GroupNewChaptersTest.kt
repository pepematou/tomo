package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.model.UpdatesWithRelations
import kotlin.time.Instant

class GroupNewChaptersTest {

    private fun chapter(mangaId: Long, chapterId: Long, fetchedAt: Long) = UpdatesWithRelations(
        mangaId = mangaId,
        mangaTitle = "Manga $mangaId",
        chapterId = chapterId,
        chapterName = "Chapter $chapterId",
        scanlator = null,
        chapterUrl = "",
        read = false,
        bookmark = false,
        lastPageRead = 0,
        sourceId = 0,
        dateFetch = Instant.fromEpochSeconds(fetchedAt),
        coverData = MangaCover(mangaId, 0, true, null, null),
    )

    @Test
    fun `groups chapters per series, newest series first`() {
        val groups = groupNewChapters(
            listOf(
                chapter(mangaId = 1, chapterId = 1, fetchedAt = 100),
                chapter(mangaId = 2, chapterId = 2, fetchedAt = 200),
                chapter(mangaId = 1, chapterId = 3, fetchedAt = 300),
                chapter(mangaId = 1, chapterId = 4, fetchedAt = 50),
            ),
        )

        groups.map { it.mangaId to it.count } shouldBe listOf(1L to 3, 2L to 1)
        groups.first().latest shouldBe Instant.fromEpochSeconds(300)
        groups.first().latestChapterName shouldBe "Chapter 3"
    }
}
