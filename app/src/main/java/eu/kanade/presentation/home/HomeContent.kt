package eu.kanade.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.library.LibraryUpdateIssues
import eu.kanade.tachiyomi.ui.home.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                    .fillMaxSize()
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

                if (state.issues.isNotEmpty()) {
                    item(key = "issues") {
                        IssuesNotice(issues = state.issues, onClick = onClickIssues)
                    }
                }

                if (state.newChapters.isNotEmpty()) {
                    item(key = "new-chapters") {
                        Column {
                            TomoSectionTitle(stringResource(MR.strings.tomo_new_chapters)) {
                                TextButton(onClick = onSeeAllNewChapters) {
                                    Text(stringResource(MR.strings.tomo_see_all), color = Tomo.textSecondary)
                                }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Tomo.screenPadding),
                                horizontalArrangement = Arrangement.spacedBy(CoverCardSpacing),
                            ) {
                                items(state.newChapters, key = { it.mangaId }) { group ->
                                    CoverCard(
                                        cover = group.coverData,
                                        title = group.title,
                                        subtitle = group.latestChapterName,
                                        topStart = { GoldBadge("+${group.count}") },
                                        onClick = { onClickManga(group.mangaId) },
                                        modifier = Modifier.width(CoverCardWidth),
                                    )
                                }
                            }
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
    Column(
        modifier = Modifier
            .padding(horizontal = Tomo.screenPadding)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Row {
            MangaCover.Book(
                data = history.coverData,
                contentDescription = history.title,
                shape = RoundedCornerShape(10.dp),
                onClick = { onClickManga(history.mangaId) },
                modifier = Modifier.width(104.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.tomo_continue_reading),
                    color = Tomo.gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = history.title,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (history.chapterNumber > -1) {
                    val chapter = formatChapterNumber(history.chapterNumber)
                    Text(
                        text = if (progress != null && progress.page > 0) {
                            stringResource(MR.strings.tomo_chapter_page, chapter, progress.page)
                        } else {
                            stringResource(MR.strings.tomo_chapter_short, chapter)
                        },
                        color = Tomo.textSecondary,
                        fontSize = 14.sp,
                    )
                }
                if (progress != null && progress.totalChapters > 0) {
                    TomoProgressBar(fraction = progress.fraction, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        text = stringResource(MR.strings.tomo_series_progress, (progress.fraction * 100).toInt()),
                        color = Tomo.textFaint,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        TomoPrimaryButton(text = stringResource(MR.strings.tomo_action_keep_reading), onClick = onContinue)
    }
}

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
