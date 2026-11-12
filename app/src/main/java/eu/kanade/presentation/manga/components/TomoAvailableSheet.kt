package eu.kanade.presentation.manga.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoSwitch
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.tomo.alternatives.AlternativeFinder
import eu.kanade.tachiyomi.tomo.alternatives.AlternativeFinder.Alternative
import eu.kanade.tachiyomi.tomo.alternatives.chapterDelta
import eu.kanade.tachiyomi.ui.manga.AvailableViewModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBack
import mihon.icons.materialsymbols.automirroredrounded.ArrowForward
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Close
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

private val SheetFill = Color(0xF21E2230)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Bottom sheet of the dark glass design. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TomoBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
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
        modifier = Modifier.border(BorderStroke(1.dp, Tomo.glassBorder), SheetShape),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            content = content,
        )
    }
}

/** "Disponible en" for one series: the list of sources that have it, the migration options and the result. */
@Composable
fun TomoAvailableSheet(
    state: AvailableViewModel.State,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onChooseSources: () -> Unit,
    onPick: (Alternative) -> Unit,
    onBack: () -> Unit,
    onOptionsChange: (AlternativeFinder.MigrateOptions) -> Unit,
    onMigrate: () -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    TomoBottomSheet(onDismissRequest = onDismissRequest) {
        when (val step = state.step) {
            AvailableViewModel.Step.List -> AvailableList(
                sourceName = state.sourceName,
                sourceDown = state.sourceDown,
                lastChapter = state.lastChapter,
                search = state.search,
                actionLabel = stringResource(MR.strings.tomo_action_migrate),
                onDismissRequest = onDismissRequest,
                onRetry = onRetry,
                onChooseSources = onChooseSources,
                onPick = onPick,
            )
            is AvailableViewModel.Step.Confirm -> ConfirmMigration(
                alternative = step.alternative,
                state = state,
                onBack = onBack,
                onOptionsChange = onOptionsChange,
                onMigrate = onMigrate,
            )
            is AvailableViewModel.Step.Done -> MigrationDone(
                alternative = step.alternative,
                lastReadChapter = state.lastReadChapter,
                keptProgress = state.options.keepProgress,
                onOpenSeries = { onOpenSeries(step.newMangaId) },
                onSeeOthers = onBack,
            )
        }
    }
}

/** The sources a series was found in, grouped by language; also used to change a batch suggestion. */
@Composable
fun ColumnScope.AvailableList(
    sourceName: String,
    sourceDown: Boolean,
    lastChapter: Double,
    search: AlternativeFinder.Search?,
    actionLabel: String,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onChooseSources: (() -> Unit)?,
    onPick: (Alternative) -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(MR.strings.tomo_available_in), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = availableStatus(sourceName, sourceDown, search),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = if (sourceDown) Tomo.amber else Tomo.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        IconButton(onClick = onDismissRequest, modifier = Modifier.background(Tomo.glassStrong, CircleShape)) {
            Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(MR.strings.action_close))
        }
    }
    if (search != null && search.titles.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(MR.strings.tomo_searched_as), fontSize = 12.sp, color = Tomo.textFaint)
            search.titles.forEach { title ->
                Text(
                    text = title,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
    ) {
        if (search == null || (!search.done && search.results.isEmpty())) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                CircularProgressIndicator(color = Tomo.gold, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
        search?.results.orEmpty().groupBy { it.lang }.forEach { (lang, results) ->
            Text(
                text = LocaleHelper.getLocalizedDisplayName(lang),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tomo.textSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
            results.forEach { alternative ->
                AlternativeRow(alternative, lastChapter, actionLabel) { onPick(alternative) }
            }
        }
    }
    if (search != null && search.done) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(MR.strings.tomo_searched_summary, search.total, search.failed),
                fontSize = 12.sp,
                color = Tomo.textFaint,
            )
            TextLink(stringResource(MR.strings.action_retry), onRetry)
        }
        if (onChooseSources != null) {
            TextLink(
                stringResource(MR.strings.tomo_choose_sources),
                onChooseSources,
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun availableStatus(sourceName: String, sourceDown: Boolean, search: AlternativeFinder.Search?): String {
    val down = if (sourceDown) stringResource(MR.strings.tomo_source_not_responding, sourceName) + " " else ""
    return when {
        search == null -> down
        !search.done -> down + stringResource(MR.strings.tomo_searching_sources, search.searched, search.total)
        search.results.isEmpty() -> down + stringResource(MR.strings.tomo_available_none)
        else -> down + pluralStringResource(MR.plurals.tomo_found_in_sources, search.results.size, search.results.size)
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tomo.gold)
    }
}

@Composable
private fun AlternativeRow(alternative: Alternative, lastChapter: Double, actionLabel: String, onClick: () -> Unit) {
    val delta = chapterDelta(lastChapter, alternative)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), border = Color.White.copy(alpha = 0.14f))
            .padding(12.dp),
    ) {
        SourceTile(alternative.sourceName)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(text = alternative.sourceName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = alternativeMeta(alternative),
                fontSize = 12.sp,
                color = Tomo.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (delta > 0) {
                        stringResource(MR.strings.tomo_chapters_ahead, delta)
                    } else {
                        stringResource(MR.strings.tomo_same_as_yours)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (delta > 0) Tomo.mint else Tomo.textSecondary,
                    modifier = Modifier
                        .background(
                            if (delta > 0) Tomo.mint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(9.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
                MatchLabel(alternative.match)
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(44.dp)
                .glass(CircleShape, fill = Tomo.glassStrong)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
        ) {
            Text(text = actionLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun MatchLabel(match: AlternativeFinder.Match) {
    Text(
        text = stringResource(
            if (match ==
                AlternativeFinder.Match.SAME_AUTHOR
            ) {
                MR.strings.tomo_same_author
            } else {
                MR.strings.tomo_possible_match
            },
        ),
        fontSize = 11.sp,
        color = if (match == AlternativeFinder.Match.SAME_AUTHOR) Tomo.textSecondary else Tomo.amber,
    )
}

/** "Cap. 290 · hace 2 días", with the title first when the source calls the series something else. */
@Composable
private fun alternativeMeta(alternative: Alternative): String = listOfNotNull(
    alternative.title,
    alternative.lastChapter.takeIf { it >= 0 }?.let {
        stringResource(MR.strings.tomo_chapter_short, formatChapterNumber(it))
    },
    alternative.lastUpload.takeIf { it > 0 }?.let { relativeTimeSpanString(it).replaceFirstChar(Char::lowercase) },
).joinToString(" · ")

@Composable
fun SourceTile(name: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .glass(RoundedCornerShape(12.dp), fill = Tomo.glassStrong),
    ) {
        Text(text = name.take(1).uppercase(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColumnScope.ConfirmMigration(
    alternative: Alternative,
    state: AvailableViewModel.State,
    onBack: () -> Unit,
    onOptionsChange: (AlternativeFinder.MigrateOptions) -> Unit,
    onMigrate: () -> Unit,
) {
    val options = state.options
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.background(Tomo.glassStrong, CircleShape)) {
            Icon(
                MaterialSymbols.AutoMirroredRounded.ArrowBack,
                contentDescription = stringResource(MR.strings.action_bar_up_description),
            )
        }
        Text(
            text = stringResource(MR.strings.tomo_migrate_to, alternative.sourceName),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EndpointCard(
            label = stringResource(MR.strings.tomo_from),
            name = state.sourceName,
            detail = if (state.sourceDown) {
                stringResource(MR.strings.tomo_not_responding)
            } else {
                stringResource(MR.strings.tomo_chapter_short, formatChapterNumber(state.lastChapter))
            },
            accent = Tomo.amber,
            modifier = Modifier.weight(1f),
        )
        Icon(MaterialSymbols.AutoMirroredRounded.ArrowForward, contentDescription = null, tint = Tomo.gold)
        EndpointCard(
            label = stringResource(MR.strings.tomo_to),
            name = alternative.sourceName,
            detail = listOfNotNull(
                alternative.lastChapter.takeIf { it >= 0 }?.let {
                    stringResource(MR.strings.tomo_chapter_short, formatChapterNumber(it))
                },
                LocaleHelper.getLocalizedDisplayName(alternative.lang),
            ).joinToString(" · "),
            accent = Tomo.mint,
            modifier = Modifier.weight(1f),
        )
    }
    Column(modifier = Modifier.glass(RoundedCornerShape(20.dp), border = Color.White.copy(alpha = 0.14f))) {
        OptionRow(
            label = stringResource(MR.strings.tomo_keep_progress),
            description = if (state.lastReadChapter >= 0) {
                stringResource(MR.strings.tomo_keep_progress_summary, formatChapterNumber(state.lastReadChapter))
            } else {
                null
            },
            checked = options.keepProgress,
        ) { onOptionsChange(options.copy(keepProgress = it)) }
        OptionRow(stringResource(MR.strings.tomo_keep_categories), null, options.keepCategories) {
            onOptionsChange(options.copy(keepCategories = it))
        }
        OptionRow(stringResource(MR.strings.tomo_delete_downloads), null, options.deleteDownloads) {
            onOptionsChange(options.copy(deleteDownloads = it))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(CircleShape, border = Color.White.copy(alpha = 0.12f))
            .padding(4.dp),
    ) {
        listOf(true to MR.strings.tomo_move, false to MR.strings.tomo_keep_both).forEach { (move, label) ->
            val selected = options.move == move
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (selected) Tomo.text else Color.Transparent, CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        onOptionsChange(options.copy(move = move))
                    },
            ) {
                Text(
                    text = stringResource(label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Tomo.night else Tomo.textSecondary,
                )
            }
        }
    }
    Text(
        text = stringResource(
            if (options.move) MR.strings.tomo_move_help else MR.strings.tomo_keep_both_help,
            state.sourceName,
        ),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = Tomo.textSecondary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    SheetPrimaryButton(
        text = stringResource(MR.strings.tomo_migrate_to, alternative.sourceName),
        loading = state.migrating,
        onClick = onMigrate,
    )
}

@Composable
private fun EndpointCard(label: String, name: String, detail: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .glass(RoundedCornerShape(18.dp), fill = accent.copy(alpha = 0.1f), border = accent.copy(alpha = 0.3f))
            .padding(14.dp),
    ) {
        Text(text = label, fontSize = 11.sp, color = Tomo.textSecondary)
        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = detail, fontSize = 12.sp, color = accent, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun OptionRow(label: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 15.sp)
            if (description != null) {
                Text(text = description, fontSize = 12.sp, color = Tomo.textSecondary)
            }
        }
        TomoSwitch(checked = checked)
    }
}

@Composable
fun SheetPrimaryButton(text: String, onClick: () -> Unit, loading: Boolean = false, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(if (enabled) Tomo.text else Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
    ) {
        if (loading) {
            CircularProgressIndicator(color = Tomo.night, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Tomo.night else Tomo.textSecondary,
            )
        }
    }
}

@Composable
private fun ColumnScope.MigrationDone(
    alternative: Alternative,
    lastReadChapter: Double,
    keptProgress: Boolean,
    onOpenSeries: () -> Unit,
    onSeeOthers: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = 16.dp)
            .size(88.dp)
            .background(Tomo.mint, CircleShape)
            .align(Alignment.CenterHorizontally),
    ) {
        Icon(
            MaterialSymbols.Rounded.Check,
            contentDescription = null,
            tint = Tomo.night,
            modifier = Modifier.size(40.dp),
        )
    }
    Text(
        text = stringResource(MR.strings.tomo_migrated_to, alternative.sourceName),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (keptProgress && lastReadChapter >= 0) {
        Text(
            text = stringResource(MR.strings.tomo_keep_progress_summary, formatChapterNumber(lastReadChapter)),
            fontSize = 14.sp,
            color = Tomo.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SheetPrimaryButton(text = stringResource(MR.strings.tomo_open_series), onClick = onOpenSeries)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White.copy(alpha = 0.1f), CircleShape)
            .clickable(role = Role.Button, onClick = onSeeOthers),
    ) {
        Text(
            text = stringResource(MR.strings.tomo_see_other_sources),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
