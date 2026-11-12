package eu.kanade.tachiyomi.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.manga.components.AvailableList
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.MatchLabel
import eu.kanade.presentation.manga.components.SheetPrimaryButton
import eu.kanade.presentation.manga.components.TomoBottomSheet
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.TomoProgressBar
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowForward
import mihon.icons.materialsymbols.rounded.Check
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/** "Fuente caída": review the suggested sources for every series that can't update, then migrate the checked ones. */
class TomoSourceDownScreen(private val mangaIds: List<Long>) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<SourceDownViewModel, SourceDownViewModel.Factory> { create(mangaIds) }
        val state by viewModel.state.collectAsState()
        var showWithout by rememberSaveable { mutableStateOf(false) }
        var changing by rememberSaveable { mutableStateOf<Long?>(null) }

        TomoBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                TomoHeader(title = stringResource(MR.strings.tomo_filter_source_down), navigateUp = navigator::pop)
                Summary(state)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = Tomo.screenPadding, vertical = 12.dp),
                ) {
                    Chip(
                        stringResource(MR.strings.tomo_with_alternative, state.withAlternative.size),
                        selected = !showWithout,
                    ) { showWithout = false }
                    Chip(
                        stringResource(MR.strings.tomo_without_alternative, state.withoutAlternative.size),
                        selected = showWithout,
                    ) { showWithout = true }
                }
                val rows = if (showWithout) state.withoutAlternative else state.withAlternative
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Tomo.screenPadding, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(rows, key = { it.manga.id }) { item ->
                        SeriesRow(
                            item = item,
                            onToggle = { viewModel.toggle(item.manga.id) },
                            onChange = { changing = item.manga.id },
                            onSearchMore = { navigator.push(MigrateSearchScreen(item.manga.id)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                Box(modifier = Modifier.padding(Tomo.screenPadding)) {
                    when {
                        state.finished -> SheetPrimaryButton(
                            text = pluralStringResource(MR.plurals.tomo_migrated_count, state.migrated, state.migrated),
                            onClick = navigator::pop,
                        )
                        state.migrating -> SheetPrimaryButton(
                            text = stringResource(MR.strings.tomo_migrating_progress, state.migrated, state.toMigrate),
                            loading = true,
                            onClick = {},
                        )
                        else -> SheetPrimaryButton(
                            text = if (state.selectedCount == 0) {
                                stringResource(MR.strings.tomo_pick_one_series)
                            } else {
                                pluralStringResource(
                                    MR.plurals.tomo_migrate_selected,
                                    state.selectedCount,
                                    state.selectedCount,
                                )
                            },
                            enabled = state.selectedCount > 0,
                            onClick = viewModel::migrateSelected,
                        )
                    }
                }
            }
        }

        val item = state.items.find { it.manga.id == changing }
        if (item != null) {
            TomoBottomSheet(onDismissRequest = { changing = null }) {
                AvailableList(
                    sourceName = "",
                    sourceDown = false,
                    lastChapter = item.lastChapter,
                    search = item.search,
                    actionLabel = stringResource(MR.strings.tomo_action_choose),
                    onDismissRequest = { changing = null },
                    onRetry = { viewModel.retry(item.manga.id) },
                    onChooseSources = null,
                    onPick = {
                        viewModel.choose(item.manga.id, it)
                        changing = null
                    },
                )
            }
        }
    }

    @Composable
    private fun Summary(state: SourceDownViewModel.State) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(horizontal = Tomo.screenPadding)
                .fillMaxWidth()
                .glass(RoundedCornerShape(22.dp))
                .padding(16.dp),
        ) {
            Text(
                text = pluralStringResource(MR.plurals.tomo_source_down_summary, state.items.size, state.items.size) +
                    " " + stringResource(MR.strings.tomo_found_alternative_for, state.withAlternative.size),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            val total = state.items.size.coerceAtLeast(1)
            TomoProgressBar(fraction = state.searchedCount / total.toFloat())
            Text(
                text = stringResource(MR.strings.tomo_review_before_migrating),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Tomo.textSecondary,
            )
        }
    }

    @Composable
    private fun SeriesRow(
        item: SourceDownViewModel.Item,
        onToggle: () -> Unit,
        onChange: () -> Unit,
        onSearchMore: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val choice = item.choice
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxWidth()
                .glass(
                    RoundedCornerShape(18.dp),
                    fill = if (item.selected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f),
                )
                .padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .toggleable(
                        value = item.selected,
                        enabled = choice != null,
                        role = Role.Checkbox,
                        onValueChange = { onToggle() },
                    ),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .then(
                            if (item.selected) {
                                Modifier.background(Tomo.mint, RoundedCornerShape(7.dp))
                            } else {
                                Modifier.border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                            },
                        ),
                ) {
                    if (item.selected) {
                        Icon(
                            MaterialSymbols.Rounded.Check,
                            contentDescription = null,
                            tint = Tomo.night,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            MangaCover.Book(
                data = item.manga.asMangaCover(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.width(44.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = item.manga.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.reason.isNotEmpty()) {
                    Text(
                        text = item.reason,
                        fontSize = 12.sp,
                        color = Tomo.amber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (choice != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            MaterialSymbols.AutoMirroredRounded.ArrowForward,
                            contentDescription = null,
                            tint = Tomo.mint,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = listOfNotNull(
                                choice.sourceName,
                                choice.lang.uppercase(),
                                choice.lastChapter.takeIf { it >= 0 }?.let {
                                    stringResource(MR.strings.tomo_chapter_short, formatChapterNumber(it))
                                },
                            ).joinToString(" · "),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MatchLabel(choice.match)
                } else if (item.searched) {
                    Text(
                        text = stringResource(MR.strings.tomo_no_alternative),
                        fontSize = 12.sp,
                        color = Tomo.textFaint,
                    )
                }
            }
            if (item.searched) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .glass(CircleShape, fill = Tomo.glassStrong)
                        .clickable(role = Role.Button, onClick = if (choice != null) onChange else onSearchMore)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (choice !=
                                null
                            ) {
                                MR.strings.tomo_action_change
                            } else {
                                MR.strings.tomo_search_more
                            },
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    @Composable
    private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(40.dp)
                .then(if (selected) Modifier.background(Tomo.text, CircleShape) else Modifier.glass(CircleShape))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Tomo.night else Tomo.text,
            )
        }
    }
}
