package eu.kanade.tachiyomi.tomo.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.tomo.GlassPanel
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.TomoProgressBar
import eu.kanade.presentation.tomo.TomoSectionTitle
import eu.kanade.presentation.tomo.tomoBackground
import eu.kanade.presentation.tomo.tomoContentWidth
import eu.kanade.presentation.util.toDurationString
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.text.NumberFormat
import kotlin.time.Duration.Companion.milliseconds

/** Statistics of the glass design: the week, what is read most, manga against fanfics, and Mihon's totals below. */
@Composable
fun TomoStatsContent(stats: TomoStats?, totals: StatsScreenState.Success?, navigateUp: () -> Unit) {
    val context = LocalContext.current
    val none = stringResource(MR.strings.none)
    fun duration(ms: Long) = ms.milliseconds.toDurationString(context, fallback = none)

    LazyColumn(
        contentPadding = WindowInsets.systemBars.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().tomoBackground(),
    ) {
        item { TomoHeader(title = stringResource(MR.strings.label_stats), navigateUp = navigateUp) }
        if (stats == null || totals == null) {
            item {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(48.dp)) {
                    CircularProgressIndicator(color = Tomo.gold)
                }
            }
            return@LazyColumn
        }

        item {
            Panel {
                Text(stringResource(MR.strings.tomo_stats_this_week), color = Tomo.textSecondary, fontSize = 14.sp)
                Text(duration(stats.weeks.last()), style = MaterialTheme.typography.headlineMedium)
                if (stats.streakDays > 0) {
                    Text(
                        pluralStringResource(MR.plurals.tomo_stats_streak, stats.streakDays, stats.streakDays),
                        color = Tomo.gold,
                        fontSize = 14.sp,
                    )
                }
                WeekBars(stats.weeks, ::duration)
                Text(stringResource(MR.strings.tomo_stats_weeks), color = Tomo.textFaint, fontSize = 12.sp)
            }
        }

        if (stats.topSeries.isEmpty()) {
            item {
                Text(
                    stringResource(MR.strings.tomo_stats_empty),
                    color = Tomo.textSecondary,
                    modifier = Modifier.tomoContentWidth().padding(horizontal = Tomo.screenPadding, vertical = 8.dp),
                )
            }
        }

        ranking(MR.strings.tomo_stats_top_series, stats.topSeries, ::duration, showChapters = true)

        item { TomoSectionTitle(stringResource(MR.strings.tomo_stats_types), Modifier.tomoContentWidth()) }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.tomoContentWidth().padding(horizontal = Tomo.screenPadding),
            ) {
                ShareCard(stringResource(MR.strings.manga), stats.manga, ::duration, Modifier.weight(1f))
                ShareCard(stringResource(MR.strings.tomo_stats_fanfics), stats.fanfics, ::duration, Modifier.weight(1f))
            }
        }

        ranking(MR.strings.tomo_stats_genres, stats.genres, ::duration)
        ranking(MR.strings.tomo_stats_sources, stats.sources, ::duration)

        item { TomoSectionTitle(stringResource(MR.strings.tomo_stats_totals), Modifier.tomoContentWidth()) }
        item {
            Panel {
                Total(stringResource(MR.strings.in_library), totals.overview.libraryMangaCount.toString())
                Total(stringResource(MR.strings.label_completed_titles), totals.overview.completedMangaCount.toString())
                Total(stringResource(MR.strings.label_read_duration), duration(totals.overview.totalReadDuration))
                Total(stringResource(MR.strings.label_read_chapters), totals.chapters.readChapterCount.toString())
                Total(stringResource(MR.strings.label_total_chapters), totals.chapters.totalChapterCount.toString())
                Total(stringResource(MR.strings.label_downloaded), totals.chapters.downloadCount.toString())
            }
        }
    }
}

private fun LazyListScope.ranking(
    title: StringResource,
    items: List<Ranked>,
    duration: (Long) -> String,
    showChapters: Boolean = false,
) {
    if (items.isEmpty()) return
    item { TomoSectionTitle(stringResource(title), Modifier.tomoContentWidth()) }
    item {
        val top = items.first().durationMs.coerceAtLeast(1)
        Panel {
            items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Row {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            listOfNotNull(
                                duration(item.durationMs),
                                pluralStringResource(
                                    MR.plurals.tomo_stats_chapters,
                                    item.chapters.toInt(),
                                    item.chapters,
                                )
                                    .takeIf { showChapters },
                            ).joinToString(" · "),
                            color = Tomo.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    TomoProgressBar(item.durationMs.toFloat() / top)
                }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    GlassPanel(
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.tomoContentWidth().padding(horizontal = Tomo.screenPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** Eight weeks as bars, this week in gold; each bar names its time for screen readers. */
@Composable
private fun WeekBars(weeks: List<Long>, duration: (Long) -> String) {
    val top = weeks.max().coerceAtLeast(1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth().height(96.dp).padding(top = 12.dp),
    ) {
        weeks.forEachIndexed { index, ms ->
            val description = duration(ms)
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = description },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((ms.toFloat() / top).coerceAtLeast(0.03f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index == weeks.lastIndex) Tomo.gold else Tomo.glassStrong),
                )
            }
        }
    }
}

@Composable
private fun ShareCard(title: String, share: Share, duration: (Long) -> String, modifier: Modifier) {
    GlassPanel(contentPadding = PaddingValues(16.dp), modifier = modifier) {
        Text(title, color = Tomo.textSecondary, fontSize = 14.sp)
        Text(duration(share.durationMs), style = MaterialTheme.typography.titleLarge)
        Text(
            pluralStringResource(MR.plurals.tomo_stats_chapters, share.chapters.toInt(), share.chapters),
            fontSize = 13.sp,
        )
        if (share.words > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(MR.strings.tomo_stats_words, NumberFormat.getIntegerInstance().format(share.words)),
                fontSize = 13.sp,
                color = Tomo.textSecondary,
            )
        }
    }
}

@Composable
private fun Total(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = Tomo.textSecondary, modifier = Modifier.weight(1f))
        Text(value)
    }
}
