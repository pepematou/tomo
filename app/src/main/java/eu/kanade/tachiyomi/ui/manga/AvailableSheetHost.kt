package eu.kanade.tachiyomi.ui.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.manga.components.TomoAvailableSheet

/** "Disponible en" sheet of a series; [refresh] searches again instead of using the last day's results. */
@Composable
fun AvailableSheetHost(
    mangaId: Long,
    refresh: Boolean,
    onDismissRequest: () -> Unit,
    onChooseSources: () -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val viewModel = assistedMetroViewModel<AvailableViewModel, AvailableViewModel.Factory> { create(mangaId) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(refresh) {
        if (refresh) viewModel.search(refresh = true)
    }
    TomoAvailableSheet(
        state = state,
        onDismissRequest = {
            viewModel.backToList()
            onDismissRequest()
        },
        onRetry = { viewModel.search(refresh = true) },
        onChooseSources = onChooseSources,
        onPick = viewModel::pick,
        onBack = viewModel::backToList,
        onOptionsChange = viewModel::setOptions,
        onMigrate = viewModel::migrate,
        onOpenSeries = onOpenSeries,
    )
}
