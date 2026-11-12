package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.tomo.ao3.Ao3Source
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.manga.model.applyFilter

class SourceDownIdsTest {

    private fun issue(id: Long, action: LibraryUpdateIssues.Action) = LibraryUpdateIssues.Issue(
        mangaId = id,
        title = "Manga $id",
        kind = LibraryUpdateIssues.Kind.ERROR,
        reason = "",
        action = action,
    )

    private val issues = listOf(
        issue(1, LibraryUpdateIssues.Action.MIGRATE),
        issue(2, LibraryUpdateIssues.Action.INSTALL_EXTENSION),
        issue(3, LibraryUpdateIssues.Action.WEBVIEW),
        issue(4, LibraryUpdateIssues.Action.NONE),
    )

    @Test
    fun `only missing extensions and dead sites count as source down`() {
        issues.sourceDownIds() shouldBe setOf(1L, 2L)
    }

    @Test
    fun `fanfics whose site is down are not offered for migration`() {
        val fanfic = issue(5, LibraryUpdateIssues.Action.MIGRATE).copy(sourceId = Ao3Source.ID)
        (issues + fanfic).sourceDownIds() shouldBe setOf(1L, 2L)
    }

    @Test
    fun `filter includes, excludes or ignores source down series`() {
        val ids = issues.sourceDownIds()
        val library = listOf(1L, 3L, 5L)
        library.filter { applyFilter(TriState.ENABLED_IS) { it in ids } } shouldBe listOf(1L)
        library.filter { applyFilter(TriState.ENABLED_NOT) { it in ids } } shouldBe listOf(3L, 5L)
        library.filter { applyFilter(TriState.DISABLED) { it in ids } } shouldBe library
    }
}
