package eu.kanade.tachiyomi.tomo.sync

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FolderSyncTest {

    @Test
    fun `only other devices that changed are merged`() {
        val files = listOf(
            "tomo-sync-phone.tachibk" to 30L,
            "tomo-sync-tablet.tachibk" to 50L,
            "tomo-sync-old.tachibk" to 10L,
            "app.tomo_2026-11-21_10-00.tachibk" to 99L,
            "notes.txt" to 99L,
        )
        val seen = mapOf("tomo-sync-old.tachibk" to 10L)
        filesToImport(files, "tablet", seen).map { it.first } shouldBe listOf("tomo-sync-phone.tachibk")
        filesToImport(files, "pc", emptyMap()).map { it.first } shouldBe
            listOf("tomo-sync-old.tachibk", "tomo-sync-phone.tachibk", "tomo-sync-tablet.tachibk")
    }

    @Test
    fun `seen files survive being saved`() {
        val seen = mapOf("tomo-sync-a.tachibk" to 1L, "tomo-sync-b.tachibk" to 2_000L)
        parseSeen(formatSeen(seen)) shouldBe seen
        parseSeen("") shouldBe emptyMap()
    }
}
