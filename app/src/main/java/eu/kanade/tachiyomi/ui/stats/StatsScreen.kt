package eu.kanade.tachiyomi.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.stats.StatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.tomo.LocalGlassTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.tomo.stats.TomoStatsContent
import eu.kanade.tachiyomi.tomo.stats.TomoStatsViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class StatsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = metroViewModel<StatsViewModel>()
        val state by viewModel.state.collectAsState()

        // Tomo: the glass statistics, with the new sections over Mihon's totals (phase 5)
        if (LocalGlassTheme.current) {
            val tomoStats by metroViewModel<TomoStatsViewModel>().stats.collectAsState()
            TomoStatsContent(tomoStats, state as? StatsScreenState.Success, navigator::pop)
            return
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_stats),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            if (state is StatsScreenState.Loading) {
                LoadingScreen()
                return@Scaffold
            }

            StatsScreenContent(
                state = state as StatsScreenState.Success,
                paddingValues = paddingValues,
            )
        }
    }
}
