package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.tomo.LocalGlassTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import eu.kanade.presentation.util.rememberAnimatedVectorPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val enter = AnimatedImageVector.animatedVectorResource(R.drawable.animated_browse_enter)
            val exit = AnimatedImageVector.animatedVectorResource(R.drawable.animated_browse_exit)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.tomo_tab_discover),
                icon = rememberAnimatedVectorPainter(enter, exit, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalSearchScreen())
    }

    private val switchToExtensionTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToExtensionTabChannel.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        if (LocalGlassTheme.current) {
            DiscoverTab()
            LaunchedEffect(Unit) {
                switchToExtensionTabChannel.receiveAsFlow()
                    .collectLatest { navigator.push(BrowseTabsScreen(initialPage = 1)) }
            }
        } else {
            BrowseTabs(initialPage = 0, switchToExtensions = switchToExtensionTabChannel)
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

/** Mihon's Browse: sources, extensions and migration tabs. With the glass theme it opens from Discover. */
data class BrowseTabsScreen(private val initialPage: Int) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        BrowseTabs(initialPage = initialPage, navigateUp = navigator::pop)
    }
}

@Composable
private fun VoyagerScreen.BrowseTabs(
    initialPage: Int,
    switchToExtensions: Channel<Unit>? = null,
    navigateUp: (() -> Unit)? = null,
) {
    // Hoisted for extensions tab's search bar
    val extensionsViewModel = metroViewModel<ExtensionsViewModel>()
    val extensionsSearchQuery by extensionsViewModel.searchQuery.collectAsStateWithLifecycle()

    val tabs = listOf(
        sourcesTab(),
        extensionsTab(extensionsViewModel),
        migrateSourceTab(),
    )

    val state = rememberPagerState(initialPage = initialPage) { tabs.size }

    TabbedScreen(
        titleRes = MR.strings.tomo_tab_discover,
        tabs = tabs,
        state = state,
        searchQuery = extensionsSearchQuery,
        onChangeSearchQuery = extensionsViewModel::search,
        navigateUp = navigateUp,
    )
    if (switchToExtensions != null) {
        LaunchedEffect(Unit) {
            switchToExtensions.receiveAsFlow()
                .collectLatest { state.scrollToPage(1) }
        }
    }
}
