package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.i18n.MR
import java.io.File
import kotlin.time.Duration
import kotlin.time.Instant

class LibraryUpdateWorkerTest {

    private val windowEnd = Instant.fromEpochSeconds(1_000_000)

    private fun libraryManga(status: Long = SManga.ONGOING.toLong(), total: Long = 10, read: Long = 0) = LibraryManga(
        manga = Manga.create().copy(id = 1, title = "Berserk", status = status),
        categories = emptyList(),
        totalChapters = total,
        readCount = read,
        bookmarkCount = 0,
        latestUpload = null,
        chapterFetchedAt = null,
        lastRead = null,
    )

    private val allRestrictions = setOf(
        LibraryPreferences.MANGA_HAS_UNREAD,
        LibraryPreferences.MANGA_NON_COMPLETED,
        LibraryPreferences.MANGA_NON_READ,
        LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD,
    )

    @Test
    fun `manual updates, which pass no restrictions, skip nothing`() {
        skipReason(libraryManga(status = SManga.COMPLETED.toLong()), emptySet(), windowEnd) shouldBe null
        skipReason(libraryManga(read = 3), emptySet(), windowEnd) shouldBe null
    }

    @Test
    fun `every skipped series gets a reason`() {
        skipReason(libraryManga(status = SManga.COMPLETED.toLong()), allRestrictions, windowEnd) shouldBe
            MR.strings.skipped_reason_completed
        skipReason(libraryManga(read = 3), allRestrictions, windowEnd) shouldBe MR.strings.skipped_reason_not_caught_up
        skipReason(libraryManga(read = 0), allRestrictions, windowEnd) shouldBe MR.strings.skipped_reason_not_caught_up
        skipReason(libraryManga(read = 10), allRestrictions, windowEnd) shouldBe null
    }

    @Test
    fun `retries a busy source then gives up with the last error`() = runTest {
        var calls = 0
        val error = shouldThrow<HttpException> {
            retryWhenSourceBusy(List(3) { Duration.ZERO }) {
                calls++
                throw HttpException(429)
            }
        }
        error.code shouldBe 429
        calls shouldBe 4
    }

    @Test
    fun `does not retry client errors`() = runTest {
        var calls = 0
        shouldThrow<HttpException> {
            retryWhenSourceBusy(List(3) { Duration.ZERO }) {
                calls++
                throw HttpException(404)
            }
        }
        calls shouldBe 1
    }

    @Test
    fun `each failure points to the action that fixes it`() {
        issueAction(SourceNotInstalledException(), isCloudflare = false) shouldBe
            LibraryUpdateIssues.Action.INSTALL_EXTENSION
        listOf(301, 404, 410).forEach {
            issueAction(HttpException(it), isCloudflare = false) shouldBe LibraryUpdateIssues.Action.MIGRATE
        }
        issueAction(Exception("HTTP error 404"), isCloudflare = false) shouldBe LibraryUpdateIssues.Action.MIGRATE
        issueAction(HttpException(500), isCloudflare = false) shouldBe LibraryUpdateIssues.Action.NONE
        issueAction(Exception("blocked"), isCloudflare = true) shouldBe LibraryUpdateIssues.Action.WEBVIEW
    }

    @Test
    fun `issues survive a restart and only the checked series are replaced`(@TempDir dir: File) {
        val context = mockk<Context> { every { filesDir } returns dir }
        val skipped = LibraryUpdateIssues.Issue(1, "Berserk", LibraryUpdateIssues.Kind.SKIPPED, "complete")
        val failed = LibraryUpdateIssues.Issue(2, "Tower of God", LibraryUpdateIssues.Kind.ERROR, "Cloudflare")

        LibraryUpdateIssues(context, Json).replace(setOf(1, 2), listOf(skipped, failed))
        val reloaded = LibraryUpdateIssues(context, Json)
        reloaded.issues.value shouldBe listOf(skipped, failed)

        reloaded.replace(setOf(1), emptyList())
        LibraryUpdateIssues(context, Json).issues.value shouldBe listOf(failed)
    }
}
