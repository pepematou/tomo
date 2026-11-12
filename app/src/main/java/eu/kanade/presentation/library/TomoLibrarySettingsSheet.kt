package eu.kanade.presentation.library

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoSwitch
import eu.kanade.presentation.tomo.glass
import eu.kanade.tachiyomi.ui.library.LibrarySettingsViewModel
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Close
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

private val SheetFill = Color(0xF21E2230)

/**
 * Library filters, sort and display of the dark glass design: a bottom sheet with the same preferences and actions
 * as Mihon's [LibrarySettingsDialog], plus the "Fuente caída" filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TomoLibrarySettingsSheet(
    onDismissRequest: () -> Unit,
    viewModel: LibrarySettingsViewModel,
    category: Category?,
    seriesCount: Int,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = SheetFill,
        contentColor = Tomo.text,
        scrimColor = Tomo.night.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape),
            )
        },
        modifier = Modifier.border(
            BorderStroke(1.dp, Tomo.glassBorder),
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            SegmentedTabs(
                titles = listOf(
                    stringResource(MR.strings.action_filter),
                    stringResource(MR.strings.action_sort),
                    stringResource(MR.strings.tomo_appearance),
                ),
                selected = page,
                onSelect = { page = it },
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (page) {
                    0 -> FilterPage(viewModel)
                    1 -> SortPage(viewModel, category)
                    else -> DisplayPage(viewModel)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(
                    text = stringResource(MR.strings.action_reset),
                    primary = false,
                    onClick = { resetFilters(viewModel) },
                )
                SheetButton(
                    text = pluralStringResource(MR.plurals.tomo_show_series, seriesCount, seriesCount),
                    primary = true,
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun resetFilters(viewModel: LibrarySettingsViewModel) {
    val prefs = viewModel.libraryPreferences
    listOf(
        prefs.filterDownloaded,
        prefs.filterUnread,
        prefs.filterStarted,
        prefs.filterBookmarked,
        prefs.filterCompleted,
        prefs.filterIntervalCustom,
        prefs.filterSourceDown,
    ).forEach { it.set(TriState.DISABLED) }
    viewModel.trackersFlow.value.forEach { prefs.filterTracking(it.id.toInt()).set(TriState.DISABLED) }
}

@Composable
private fun SegmentedTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(CircleShape, border = Color.White.copy(alpha = 0.12f))
            .padding(4.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val isSelected = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(if (isSelected) Tomo.text else Color.Transparent, CircleShape)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Tomo.night else Tomo.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(viewModel: LibrarySettingsViewModel) {
    Text(
        text = stringResource(MR.strings.tomo_filter_hint),
        fontSize = 12.sp,
        color = Tomo.textSecondary,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
    )
    val downloadedOnly by viewModel.preferences.downloadedOnly.collectAsState()
    val filterDownloaded by viewModel.libraryPreferences.filterDownloaded.collectAsState()
    TriStateRow(
        label = stringResource(MR.strings.label_downloaded),
        state = if (downloadedOnly) TriState.ENABLED_IS else filterDownloaded,
        enabled = !downloadedOnly,
        onClick = { viewModel.toggleFilter(LibraryPreferences::filterDownloaded) },
    )
    TriStateFilter(viewModel, MR.strings.action_filter_unread.let { stringResource(it) }) { it.filterUnread }
    TriStateFilter(viewModel, stringResource(MR.strings.label_started)) { it.filterStarted }
    TriStateFilter(viewModel, stringResource(MR.strings.action_filter_bookmarked)) { it.filterBookmarked }
    TriStateFilter(viewModel, stringResource(MR.strings.completed)) { it.filterCompleted }
    TriStateFilter(viewModel, stringResource(MR.strings.tomo_filter_source_down)) { it.filterSourceDown }

    val trackers by viewModel.trackersFlow.collectAsState()
    trackers.forEach { service ->
        val state by viewModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
        TriStateRow(
            label = if (trackers.size == 1) stringResource(MR.strings.action_filter_tracked) else service.name,
            state = state,
            onClick = { viewModel.toggleTracker(service.id.toInt()) },
        )
    }
}

@Composable
private fun TriStateFilter(
    viewModel: LibrarySettingsViewModel,
    label: String,
    preference: (LibraryPreferences) -> Preference<TriState>,
) {
    val state by remember { preference(viewModel.libraryPreferences) }.collectAsState()
    TriStateRow(label = label, state = state, onClick = { viewModel.toggleFilter(preference) })
}

/** Empty circle (ignored), mint check (include) or amber cross (exclude), with the state spelled out. */
@Composable
private fun TriStateRow(label: String, state: TriState, onClick: () -> Unit, enabled: Boolean = true) {
    val stateLabel = when (state) {
        TriState.DISABLED -> null
        TriState.ENABLED_IS -> stringResource(MR.strings.tomo_filter_include)
        TriState.ENABLED_NOT -> stringResource(MR.strings.tomo_filter_exclude)
    }
    val accent = if (state == TriState.ENABLED_IS) Tomo.mint else Tomo.amber
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(
                if (state == TriState.DISABLED) Color.Transparent else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (state == TriState.DISABLED) {
                        Modifier.border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    } else {
                        Modifier.background(accent, CircleShape)
                    },
                ),
        ) {
            if (state != TriState.DISABLED) {
                Icon(
                    imageVector = if (state ==
                        TriState.ENABLED_IS
                    ) {
                        MaterialSymbols.Rounded.Check
                    } else {
                        MaterialSymbols.Rounded.Close
                    },
                    contentDescription = null,
                    tint = Tomo.night,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (enabled) Tomo.text else Tomo.textFaint,
            modifier = Modifier.weight(1f),
        )
        if (stateLabel != null) {
            Text(text = stateLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
        }
    }
}

@Composable
private fun ColumnScope.SortPage(viewModel: LibrarySettingsViewModel, category: Category?) {
    val trackers by viewModel.trackersFlow.collectAsState()
    val sortingMode = category.sort.type
    val sortDescending = !category.sort.isAscending
    val options = listOfNotNull(
        MR.strings.action_sort_alpha to LibrarySort.Type.Alphabetical,
        MR.strings.action_sort_last_read to LibrarySort.Type.LastRead,
        MR.strings.action_sort_last_manga_update to LibrarySort.Type.LastUpdate,
        MR.strings.action_sort_unread_count to LibrarySort.Type.UnreadCount,
        MR.strings.action_sort_total to LibrarySort.Type.TotalChapters,
        MR.strings.action_sort_latest_chapter to LibrarySort.Type.LatestChapter,
        MR.strings.action_sort_chapter_fetch_date to LibrarySort.Type.ChapterFetchDate,
        MR.strings.action_sort_date_added to LibrarySort.Type.DateAdded,
        (MR.strings.action_sort_tracker_score to LibrarySort.Type.TrackerMean).takeIf { trackers.isNotEmpty() },
        MR.strings.action_sort_random to LibrarySort.Type.Random,
    )
    options.forEach { (titleRes, mode) ->
        val isSelected = sortingMode == mode
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .background(
                    if (isSelected) Color.White.copy(alpha = 0.05f) else Color.Transparent,
                    RoundedCornerShape(14.dp),
                )
                .selectable(selected = isSelected, role = Role.RadioButton) {
                    // Picking the current order again flips its direction; Random just reshuffles
                    val direction = when {
                        mode == LibrarySort.Type.Random -> LibrarySort.Direction.Ascending
                        isSelected && sortDescending -> LibrarySort.Direction.Ascending
                        isSelected -> LibrarySort.Direction.Descending
                        sortDescending -> LibrarySort.Direction.Descending
                        else -> LibrarySort.Direction.Ascending
                    }
                    viewModel.setSort(category, mode, direction)
                }
                .padding(horizontal = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        if (isSelected) 2.dp else 1.5.dp,
                        if (isSelected) Tomo.gold else Color.White.copy(alpha = 0.35f),
                        CircleShape,
                    ),
            ) {
                if (isSelected) Box(Modifier.size(10.dp).background(Tomo.gold, CircleShape))
            }
            Text(text = stringResource(titleRes), fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (isSelected && mode != LibrarySort.Type.Random) {
                Text(
                    text = stringResource(if (sortDescending) MR.strings.action_desc else MR.strings.action_asc),
                    fontSize = 12.sp,
                    color = Tomo.gold,
                )
            }
        }
    }
    Text(
        text = stringResource(MR.strings.tomo_sort_hint),
        fontSize = 12.sp,
        color = Tomo.textSecondary,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp),
    )
}

private val displayModes = listOf(
    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
    MR.strings.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    MR.strings.action_display_list to LibraryDisplayMode.List,
)

@Composable
private fun ColumnScope.DisplayPage(viewModel: LibrarySettingsViewModel) {
    val displayMode by viewModel.libraryPreferences.displayMode.collectAsState()
    displayModes.chunked(2).forEach { rowModes ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            rowModes.forEach { (titleRes, mode) ->
                val isSelected = displayMode == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .glass(
                            RoundedCornerShape(16.dp),
                            fill = if (isSelected) Tomo.gold.copy(alpha = 0.14f) else Tomo.glass,
                            border = if (isSelected) Tomo.gold else Tomo.glassBorder,
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton) {
                            viewModel.setDisplayMode(mode)
                        },
                ) {
                    Text(text = stringResource(titleRes), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (displayMode != LibraryDisplayMode.List) {
        val configuration = LocalConfiguration.current
        val columnPreference = remember {
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                viewModel.libraryPreferences.landscapeColumns
            } else {
                viewModel.libraryPreferences.portraitColumns
            }
        }
        val columns by columnPreference.collectAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = 4.dp),
        ) {
            Text(
                text = stringResource(MR.strings.pref_library_columns),
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            StepButton("−", stringResource(MR.strings.tomo_fewer_columns), enabled = columns > 0) {
                columnPreference.set((columns - 1).coerceAtLeast(0))
            }
            Text(
                text = if (columns > 0) columns.toString() else stringResource(MR.strings.label_auto),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(44.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepButton("+", stringResource(MR.strings.tomo_more_columns), enabled = columns < 10) {
                columnPreference.set((columns + 1).coerceAtMost(10))
            }
        }
    }

    val prefs = viewModel.libraryPreferences
    SwitchRow(stringResource(MR.strings.action_display_unread_badge), prefs.unreadBadge)
    SwitchRow(stringResource(MR.strings.action_display_download_badge), prefs.downloadBadge)
    SwitchRow(stringResource(MR.strings.action_display_language_badge), prefs.languageBadge)
    SwitchRow(stringResource(MR.strings.action_display_local_badge), prefs.localBadge)
    SwitchRow(stringResource(MR.strings.action_display_show_continue_reading_button), prefs.showContinueReadingButton)
    SwitchRow(stringResource(MR.strings.action_display_show_tabs), prefs.categoryTabs)
    SwitchRow(stringResource(MR.strings.action_display_show_number_of_items), prefs.categoryNumberOfItems)
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .background(Tomo.glassStrong, CircleShape)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick),
    ) {
        Text(text = symbol, fontSize = 20.sp, color = if (enabled) Tomo.text else Tomo.textFaint)
    }
}

@Composable
private fun SwitchRow(label: String, preference: Preference<Boolean>) {
    val checked by preference.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = preference::set)
            .padding(horizontal = 4.dp),
    ) {
        Text(text = label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TomoSwitch(checked = checked)
    }
}

@Composable
private fun SheetButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .then(
                if (primary) {
                    Modifier.background(Tomo.text, CircleShape)
                } else {
                    Modifier.glass(CircleShape, fill = Tomo.glassStrong)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) Tomo.night else Tomo.text,
        )
    }
}
