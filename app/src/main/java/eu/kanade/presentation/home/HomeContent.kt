package eu.kanade.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.tomo.CoverCard
import eu.kanade.presentation.tomo.CoverCardSpacing
import eu.kanade.presentation.tomo.CoverCardWidth
import eu.kanade.presentation.tomo.GlassIconButton
import eu.kanade.presentation.tomo.GoldBadge
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.TomoPrimaryButton
import eu.kanade.presentation.tomo.TomoProgressBar
import eu.kanade.presentation.tomo.TomoSectionTitle
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.tomo.tomoContentWidth
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.library.LibraryUpdateIssues
import eu.kanade.tachiyomi.ui.home.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.History
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.Warning
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.LocalFloatingBarInset
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds

@Composable
fun HomeContent(
    state: HomeViewModel.State,
    snackbarHostState: SnackbarHostState,
    onContinue: (HistoryWithRelations) -> Unit,
    onClickManga: (mangaId: Long) -> Unit,
    onSeeAllNewChapters: () -> Unit,
    onClickHistory: () -> Unit,
    onClickSearch: () -> Unit,
    onClickIssues: () -> Unit,
    onUpdateLibrary: suspend () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        LocalFloatingBarInset.current + 16.dp

    TomoBackground {
        if (state.isLoading) {
            LoadingScreen()
            return@TomoBackground
        }
        PullRefresh(
            refreshing = isRefreshing,
            enabled = true,
            onRefresh = {
                scope.launch {
                    if (!onUpdateLibrary()) return@launch
                    // The update runs in the background; only show the indicator briefly
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .tomoContentWidth()
                    .statusBarsPadding(),
            ) {
                item(key = "header") {
                    TomoHeader(
                        title = stringResource(MR.strings.tomo_tab_home),
                        subtitle = stringResource(greetingFor(LocalTime.now().hour)),
                    ) {
                        GlassIconButton(
                            icon = MaterialSymbols.Rounded.Search,
                            contentDescription = stringResource(MR.strings.action_search),
                            onClick = onClickSearch,
                        )
                        GlassIconButton(
                            icon = MaterialSymbols.Rounded.History,
                            contentDescription = stringResource(MR.strings.label_recent_manga),
                            onClick = onClickHistory,
                        )
                        GlassIconButton(
                            icon = MaterialSymbols.Rounded.Refresh,
                            contentDescription = stringResource(MR.strings.action_update_library),
                            onClick = { scope.launch { onUpdateLibrary() } },
                        )
                    }
                }

                val last = state.history.firstOrNull()
                if (last == null && state.newChapters.isEmpty() && state.issues.isEmpty()) {
                    item(key = "empty") {
                        EmptyScreen(stringRes = MR.strings.tomo_home_empty, modifier = Modifier.height(400.dp))
                    }
                }

                if (last != null) {
                    item(key = "continue") {
                        ContinueCard(
                            history = last,
                            progress = state.continueProgress,
                            onContinue = { onContinue(last) },
                            onClickManga = onClickManga,
                        )
                    }
                }

                val alsoReading = state.history.drop(1).take(ALSO_READING_COUNT)
                if (alsoReading.isNotEmpty()) {
                    item(key = "also-reading") {
                        Column {
                            TomoSectionTitle(stringResource(MR.strings.tomo_also_reading))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Tomo.screenPadding),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(alsoReading, key = { it.id }) { history ->
                                    RecentCard(history = history, onClick = { onContinue(history) })
                                }
                            }
                        }
                    }
                }

                if (state.issues.isNotEmpty()) {
                    item(key = "issues") {
                        IssuesNotice(issues = state.issues, onClick = onClickIssues)
                    }
                }

                if (state.newChapters.isNotEmpty()) {
                    item(key = "new-chapters-title") {
                        TomoSectionTitle(stringResource(MR.strings.tomo_new_chapters)) {
                            TextButton(onClick = onSeeAllNewChapters) {
                                Text(
                                    text = stringResource(MR.strings.tomo_see_all_count, state.newChapters.size),
                                    color = Tomo.textSecondary,
                                )
                            }
                        }
                    }
                    items(
                        state.newChapters.take(NEW_CHAPTERS_SHOWN).chunked(NEW_CHAPTERS_COLUMNS),
                        key = { row -> "new-${row.first().mangaId}" },
                    ) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CoverCardSpacing),
                            modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                        ) {
                            row.forEach { group ->
                                CoverCard(
                                    cover = group.coverData,
                                    title = group.title,
                                    subtitle = if (group.latestChapterNumber > -1) {
                                        stringResource(
                                            MR.strings.tomo_chapter_short,
                                            formatChapterNumber(group.latestChapterNumber),
                                        )
                                    } else {
                                        group.latestChapterName
                                    },
                                    topStart = { GoldBadge("+${group.count}") },
                                    topEnd = if (group.isText) {
                                        { FanficBadge() }
                                    } else {
                                        null
                                    },
                                    onClick = { onClickManga(group.mangaId) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(NEW_CHAPTERS_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        // Above the floating navigation bar, which this screen draws its content under
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding),
        )
    }
}

@Composable
private fun ContinueCard(
    history: HistoryWithRelations,
    progress: HomeViewModel.ReadingProgress?,
    onContinue: () -> Unit,
    onClickManga: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = Tomo.screenPadding)
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(14.dp),
    ) {
        MangaCover.Book(
            data = history.coverData,
            contentDescription = history.title,
            shape = RoundedCornerShape(10.dp),
            onClick = { onClickManga(history.mangaId) },
            modifier = Modifier.width(88.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = stringResource(MR.strings.tomo_continue_reading),
                color = Tomo.gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = history.title,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (history.chapterNumber > -1) {
                val chapter = formatChapterNumber(history.chapterNumber)
                Text(
                    text = when {
                        progress == null || progress.page <= 0 -> stringResource(MR.strings.tomo_chapter_short, chapter)
                        progress.chapterLength != null -> stringResource(
                            if (progress.isText) {
                                MR.strings.tomo_chapter_paragraph_of
                            } else {
                                MR.strings.tomo_chapter_page_of
                            },
                            chapter,
                            progress.page,
                            progress.chapterLength,
                        )
                        else -> stringResource(
                            if (progress.isText) MR.strings.tomo_chapter_paragraph else MR.strings.tomo_chapter_page,
                            chapter,
                            progress.page,
                        )
                    },
                    color = Tomo.textSecondary,
                    fontSize = 13.sp,
                )
            }
            if (progress != null && progress.totalChapters > 0) {
                // The chapter being read when its length is known (RF-34), else the series
                val fraction = progress.chapterLength
                    ?.let { length -> (progress.page.toFloat() / length).coerceIn(0f, 1f) }
                    ?: progress.fraction
                TomoProgressBar(fraction = fraction, modifier = Modifier.padding(vertical = 4.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(40.dp)
                        .background(Tomo.text, CircleShape)
                        .clickable(role = Role.Button, onClick = onContinue)
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = stringResource(MR.strings.tomo_action_keep_reading_short),
                        color = Tomo.night,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (progress != null && progress.totalChapters > 0) {
                    val unread = progress.totalChapters - progress.readChapters
                    Text(
                        text = if (progress.caughtUp) {
                            stringResource(MR.strings.tomo_caught_up)
                        } else {
                            pluralStringResource(MR.plurals.tomo_unread_left, unread, unread)
                        },
                        color = if (progress.caughtUp) Tomo.mint else Tomo.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A series read lately, as a small glass card that resumes it. */
@Composable
private fun RecentCard(history: HistoryWithRelations, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(200.dp)
            .glass(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        MangaCover.Book(
            data = history.coverData,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.width(38.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = history.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    history.chapterNumber.takeIf { it > -1 }?.let {
                        stringResource(MR.strings.tomo_chapter_short, formatChapterNumber(it))
                    },
                    history.readAt?.let {
                        relativeTimeSpanString(it.toEpochMilliseconds()).replaceFirstChar(Char::lowercase)
                    },
                ).joinToString(" · "),
                color = Tomo.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val ALSO_READING_COUNT = 3
private const val NEW_CHAPTERS_COLUMNS = 3
private const val NEW_CHAPTERS_SHOWN = 9

@Composable
private fun IssuesNotice(issues: List<LibraryUpdateIssues.Issue>, onClick: () -> Unit) {
    val skipped = issues.count { it.kind == LibraryUpdateIssues.Kind.SKIPPED }
    val failed = issues.size - skipped
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = Tomo.screenPadding)
            .fillMaxWidth()
            .glass(
                RoundedCornerShape(20.dp),
                fill = Tomo.amber.copy(alpha = 0.08f),
                border = Tomo.amber.copy(alpha = 0.30f),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(imageVector = MaterialSymbols.Rounded.Warning, contentDescription = null, tint = Tomo.amber)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = pluralStringResource(MR.plurals.update_issues_count, issues.size, issues.size),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(MR.strings.tomo_issues_detail, skipped, failed),
                color = Tomo.textSecondary,
                fontSize = 13.sp,
            )
        }
        Text(text = stringResource(MR.strings.tomo_action_see), color = Tomo.amber, fontWeight = FontWeight.SemiBold)
    }
}

/** The line above the title changes with the time of day, as in the mockup ("Buenas tardes"). */
internal fun greetingFor(hour: Int): StringResource = when (hour) {
    in 5..11 -> MR.strings.tomo_greeting_morning
    in 12..19 -> MR.strings.tomo_greeting_afternoon
    else -> MR.strings.tomo_greeting_evening
}

/** "Fanfic" label of new chapters of text works (RF-30g). */
@Composable
internal fun FanficBadge() {
    Text(
        text = stringResource(MR.strings.tomo_fanfics),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Tomo.night,
        modifier = Modifier
            .background(Tomo.mint, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
