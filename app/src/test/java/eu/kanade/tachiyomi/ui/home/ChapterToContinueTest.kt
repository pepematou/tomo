package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover

class ChapterToContinueTest {

    private val history = HistoryWithRelations(
        id = 1,
        chapterId = 284,
        mangaId = 7,
        title = "Sin fin skills",
        chapterNumber = 284.0,
        readAt = null,
        readDuration = 0,
        coverData = MangaCover(7, 0, true, null, null),
    )

    private fun viewModel(next: List<Chapter>): HomeViewModel {
        val getNextChapters = mockk<GetNextChapters> {
            coEvery { await(7, 284, onlyUnread = false) } returns next
        }
        return HomeViewModel(
            getHistory = mockk { every { subscribe(any()) } returns emptyFlow() },
            getUpdates = mockk { every { subscribe(any<Boolean>(), any()) } returns emptyFlow() },
            libraryUpdateIssues = mockk { every { issues } returns MutableStateFlow(emptyList()) },
            getNextChapters = getNextChapters,
            getChaptersByMangaId = mockk(),
            chapterLengths = mockk(relaxed = true),
            textWorks = mockk(relaxed = true),
        )
    }

    @Test
    fun `opens the next chapter when there is one`() = runTest {
        val next = Chapter.create().copy(id = 285, mangaId = 7)
        viewModel(listOf(next)).chapterToContinue(history) shouldBe (7L to 285L)
    }

    @Test
    fun `reopens the last chapter read when the series is finished`() = runTest {
        viewModel(emptyList()).chapterToContinue(history) shouldBe (7L to 284L)
    }
}
