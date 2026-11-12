package eu.kanade.tachiyomi.ui.browse.migration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.manga.components.SheetPrimaryButton
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.TomoSwitch
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.util.Screen
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DragHandle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * "¿Dónde buscar?": which sources "Disponible en" searches and in what order, saved in the same preference as Mihon's
 * migration sources.
 */
class TomoWhereToSearchScreen(private val title: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<MigrationConfigScreen.Model>()
        val state by viewModel.state.collectAsState()
        // null shows every language
        var language by rememberSaveable { mutableStateOf<String?>(null) }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val languages = state.sources.map { it.shortLanguage }.distinct()
        val shown = state.sources.filter { language == null || it.shortLanguage == language }
        val selectedCount = state.sources.count { it.isSelected }
        val listState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            val selected = state.sources.filter { it.isSelected }
            val fromIndex = selected.indexOfFirst { it.id == from.key }
            val toIndex = selected.indexOfFirst { it.id == to.key }
            if (fromIndex != -1 && toIndex != -1) viewModel.orderSource(fromIndex, toIndex)
        }

        TomoBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                TomoHeader(
                    title = stringResource(MR.strings.tomo_where_to_search),
                    subtitle = stringResource(MR.strings.tomo_for_title, title),
                    navigateUp = navigator::pop,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Tomo.screenPadding),
                ) {
                    Chip(stringResource(MR.strings.all), selected = language == null) { language = null }
                    languages.forEach { lang ->
                        Chip(lang.uppercase(), selected = language == lang) { language = lang }
                    }
                    Chip(stringResource(MR.strings.tomo_select_all), selected = false) {
                        viewModel.toggleSelection(MigrationConfigScreen.Model.SelectionConfig.All)
                    }
                    Chip(stringResource(MR.strings.tomo_select_none), selected = false) {
                        viewModel.toggleSelection(MigrationConfigScreen.Model.SelectionConfig.None)
                    }
                }
                Text(
                    text = stringResource(MR.strings.tomo_where_to_search_hint),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Tomo.textSecondary,
                    modifier = Modifier.padding(horizontal = Tomo.screenPadding + 4.dp, vertical = 12.dp),
                )
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = Tomo.screenPadding, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(shown, key = { it.id }) { item ->
                        ReorderableItem(reorderState, key = item.id, enabled = item.isSelected && language == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glass(RoundedCornerShape(16.dp))
                                    .toggleable(
                                        value = item.isSelected,
                                        role = Role.Switch,
                                        onValueChange = { viewModel.toggleSelection(item.id) },
                                    )
                                    .heightIn(min = 60.dp)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.DragHandle,
                                    contentDescription = stringResource(MR.strings.tomo_drag_to_reorder),
                                    tint = if (item.isSelected) Tomo.textFaint else Tomo.textFaint.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .then(if (item.isSelected) Modifier.draggableHandle() else Modifier),
                                )
                                SourceIcon(source = item.source, modifier = Modifier.size(32.dp))
                                Text(
                                    text = item.name,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (item.isSelected) Tomo.text else Tomo.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = item.shortLanguage.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Tomo.glassStrong, RoundedCornerShape(9.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                                TomoSwitch(checked = item.isSelected)
                            }
                        }
                    }
                }
                Box(modifier = Modifier.padding(Tomo.screenPadding)) {
                    SheetPrimaryButton(
                        text = if (selectedCount == 0) {
                            stringResource(MR.strings.tomo_pick_one_source)
                        } else {
                            stringResource(MR.strings.tomo_search_in_sources, selectedCount)
                        },
                        enabled = selectedCount > 0,
                        onClick = {
                            viewModel.saveSources()
                            navigator.pop()
                        },
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
