package eu.kanade.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.updates.UpdateIssuesBanner
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.ui.home.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.History
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.time.Duration.Companion.seconds

@Composable
fun HomeContent(
    state: HomeViewModel.State,
    snackbarHostState: SnackbarHostState,
    onContinue: (HistoryWithRelations) -> Unit,
    onClickManga: (mangaId: Long) -> Unit,
    onSeeAllNewChapters: () -> Unit,
    onClickHistory: () -> Unit,
    onClickIssues: () -> Unit,
    onUpdateLibrary: suspend () -> Boolean,
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.tomo_tab_home),
                actions = {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.label_recent_manga),
                                icon = MaterialSymbols.Rounded.History,
                                onClick = onClickHistory,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.action_update_library),
                                icon = MaterialSymbols.Rounded.Refresh,
                                onClick = { scope.launch { onUpdateLibrary() } },
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        if (state.isLoading) {
            LoadingScreen(Modifier.padding(contentPadding))
            return@Scaffold
        }

        var isRefreshing by remember { mutableStateOf(false) }
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
            indicatorPadding = contentPadding,
        ) {
            val isEmpty = state.history.isEmpty() && state.newChapters.isEmpty() && state.issues.isEmpty()
            if (isEmpty) {
                EmptyScreen(
                    stringRes = MR.strings.tomo_home_empty,
                    modifier = Modifier.padding(contentPadding).fillMaxSize(),
                )
                return@PullRefresh
            }

            LazyColumn(
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                state.history.firstOrNull()?.let { last ->
                    item(key = "continue") {
                        SectionTitle(stringResource(MR.strings.tomo_continue_reading))
                        ContinueCard(history = last, onContinue = { onContinue(last) }, onClickManga = onClickManga)
                    }
                }

                val recent = state.history.drop(1)
                if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        SectionTitle(stringResource(MR.strings.tomo_recent))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            items(recent, key = { it.id }) { history ->
                                MangaCover.Book(
                                    data = history.coverData,
                                    contentDescription = history.title,
                                    modifier = Modifier.width(96.dp),
                                    onClick = { onClickManga(history.mangaId) },
                                )
                            }
                        }
                    }
                }

                if (state.newChapters.isNotEmpty()) {
                    item(key = "new-header") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(stringResource(MR.strings.tomo_new_chapters), Modifier.weight(1f))
                            TextButton(onClick = onSeeAllNewChapters) {
                                Text(stringResource(MR.strings.tomo_see_all))
                            }
                        }
                    }
                    item(key = "new-chapters") {
                        GlassPanel {
                            state.newChapters.take(NEW_CHAPTERS_SHOWN).forEach { group ->
                                NewChaptersRow(group = group, onClick = { onClickManga(group.mangaId) })
                            }
                        }
                    }
                }

                if (state.issues.isNotEmpty()) {
                    item(key = "issues") {
                        UpdateIssuesBanner(count = state.issues.size, onClick = onClickIssues)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(
            horizontal = MaterialTheme.padding.medium,
            vertical = MaterialTheme.padding.small,
        ),
    )
}

@Composable
private fun ContinueCard(
    history: HistoryWithRelations,
    onContinue: () -> Unit,
    onClickManga: (Long) -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        // The card takes the color of the series: its own cover, blurred, behind a glass veil
        AsyncImage(
            model = history.coverData,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(40.dp),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)),
        )
        Row(
            modifier = Modifier.padding(MaterialTheme.padding.medium).height(140.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCover.Book(
                data = history.coverData,
                contentDescription = history.title,
                modifier = Modifier.width(92.dp),
                onClick = { onClickManga(history.mangaId) },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (history.chapterNumber > -1) {
                    Text(
                        text = stringResource(
                            MR.strings.display_mode_chapter,
                            formatChapterNumber(history.chapterNumber),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = onContinue) {
                    Text(stringResource(MR.strings.tomo_action_continue))
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(vertical = MaterialTheme.padding.small),
    ) {
        content()
    }
}

@Composable
private fun NewChaptersRow(group: HomeViewModel.NewChapters, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(72.dp)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(data = group.coverData, modifier = Modifier.width(44.dp))
        Text(
            text = group.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MaterialTheme.padding.medium),
        )
        Text(
            text = "+${group.count}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private const val NEW_CHAPTERS_SHOWN = 5
