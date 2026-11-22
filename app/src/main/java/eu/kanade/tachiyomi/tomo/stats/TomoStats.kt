package eu.kanade.tachiyomi.tomo.stats

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** A series of the library as the statistics see it. */
data class StatsWork(
    val id: Long,
    val title: String,
    val sourceName: String,
    val genres: List<String>,
    val isText: Boolean,
    val chaptersRead: Long,
    /** Words of a fanfic divided by its chapters, null for manga or when unknown. */
    val wordsPerChapter: Int? = null,
)

/** One chapter's history: when it was last read and the time spent on it, in milliseconds. */
data class StatsRead(val mangaId: Long, val readAt: Instant, val durationMs: Long)

@Immutable
data class Ranked(val name: String, val durationMs: Long, val chapters: Long = 0)

@Immutable
data class Share(val durationMs: Long = 0, val chapters: Long = 0, val words: Long = 0)

@Immutable
data class TomoStats(
    /** Time read in each of the last [WEEKS] weeks, the oldest first and this week last. */
    val weeks: List<Long>,
    val streakDays: Int,
    val topSeries: List<Ranked>,
    val manga: Share,
    val fanfics: Share,
    val genres: List<Ranked>,
    val sources: List<Ranked>,
)

const val WEEKS = 8
private const val TOP = 5

/**
 * The new statistics (phase 5). History keeps only the last time each chapter was read, so the weeks and the streak
 * count a chapter on that day; good enough to see a habit, not a log.
 */
fun tomoStats(works: List<StatsWork>, reads: List<StatsRead>, now: Instant, zone: TimeZone): TomoStats {
    val today = now.toLocalDateTime(zone).date
    val byId = works.associateBy { it.id }
    val known = reads.filter { it.mangaId in byId }
    val days = known.map { it.readAt.toLocalDateTime(zone).date }

    val weeks = LongArray(WEEKS)
    known.forEach { read ->
        val ago = read.readAt.toLocalDateTime(zone).date.daysUntil(today) / 7
        if (ago in 0 until WEEKS) weeks[WEEKS - 1 - ago] += read.durationMs
    }

    val timeByWork = known.groupBy { it.mangaId }.mapValues { (_, list) -> list.sumOf { it.durationMs } }
    fun timeOf(work: StatsWork) = timeByWork[work.id] ?: 0
    fun rank(keys: (StatsWork) -> List<String>): List<Ranked> = works
        .flatMap { work -> keys(work).map { it to work } }
        .groupBy({ it.first }, { it.second })
        .map { (name, list) -> Ranked(name, list.sumOf(::timeOf), list.sumOf { it.chaptersRead }) }
        .filter { it.durationMs > 0 }
        .sortedByDescending { it.durationMs }
        .take(TOP)
    fun share(list: List<StatsWork>) = Share(
        durationMs = list.sumOf(::timeOf),
        chapters = list.sumOf { it.chaptersRead },
        words = list.sumOf { (it.wordsPerChapter ?: 0).toLong() * it.chaptersRead },
    )

    return TomoStats(
        weeks = weeks.toList(),
        streakDays = streak(days.toSet(), today),
        topSeries = works.map { Ranked(it.title, timeOf(it), it.chaptersRead) }
            .filter { it.durationMs > 0 }
            .sortedByDescending { it.durationMs }
            .take(TOP),
        manga = share(works.filterNot { it.isText }),
        fanfics = share(works.filter { it.isText }),
        genres = rank { work -> work.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct() },
        sources = rank { listOf(it.sourceName) },
    )
}

/** Days in a row with something read, up to today; a streak still counts until the end of the day after. */
internal fun streak(days: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in days) today else today.minus(1, DateTimeUnit.DAY)
    var count = 0
    while (day in days) {
        count++
        day = day.minus(1, DateTimeUnit.DAY)
    }
    return count
}
