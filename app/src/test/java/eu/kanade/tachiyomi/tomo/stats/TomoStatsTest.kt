package eu.kanade.tachiyomi.tomo.stats

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TomoStatsTest {

    private val now = Instant.parse("2026-11-21T12:00:00Z")
    private val works = listOf(
        StatsWork(1, "One Piece", "MangaDex", listOf("Aventura", "Acción"), isText = false, chaptersRead = 10),
        StatsWork(2, "Bajo la lluvia", "FanFiction.net", listOf("Romance"), isText = true, 4, wordsPerChapter = 2_000),
        StatsWork(3, "Sin leer", "MangaDex", listOf("Acción"), isText = false, chaptersRead = 0),
    )

    private fun read(manga: Long, daysAgo: Int, minutes: Int) =
        StatsRead(manga, now - daysAgo.days, minutes.minutes.inWholeMilliseconds)

    @Test
    fun `weeks, shares and rankings`() {
        val reads = listOf(read(1, 0, 30), read(1, 1, 20), read(2, 2, 15), read(1, 9, 10), read(99, 0, 500))
        val stats = tomoStats(works, reads, now, TimeZone.UTC)

        stats.weeks.size shouldBe WEEKS
        stats.weeks.last() shouldBe 65.minutes.inWholeMilliseconds
        stats.weeks[WEEKS - 2] shouldBe 10.minutes.inWholeMilliseconds
        stats.streakDays shouldBe 3
        stats.topSeries.map { it.name } shouldBe listOf("One Piece", "Bajo la lluvia")
        stats.manga shouldBe Share(60.minutes.inWholeMilliseconds, chapters = 10, words = 0)
        stats.fanfics shouldBe Share(15.minutes.inWholeMilliseconds, chapters = 4, words = 8_000)
        stats.genres.map { it.name } shouldBe listOf("Aventura", "Acción", "Romance")
        stats.sources.first().name shouldBe "MangaDex"
    }

    @Test
    fun `a streak lasts until the end of the next day`() {
        val today = LocalDate(2026, 11, 21)
        val days = setOf(LocalDate(2026, 11, 20), LocalDate(2026, 11, 19), LocalDate(2026, 11, 17))
        streak(days, today) shouldBe 2
        streak(days + today, today) shouldBe 3
        streak(emptySet(), today) shouldBe 0
    }
}
