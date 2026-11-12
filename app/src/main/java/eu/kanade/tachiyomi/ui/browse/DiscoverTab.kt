package eu.kanade.tachiyomi.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.DiscoverModeSwitch
import eu.kanade.presentation.browse.DiscoverOverview
import eu.kanade.presentation.browse.TomoFanficsExplore
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.LocalSource

/** Discover tab with the glass theme: an overview that leads into Mihon's Browse tabs. */
@Composable
internal fun DiscoverTab() {
    val navigator = LocalNavigator.currentOrThrow
    val extensionsViewModel = metroViewModel<ExtensionsViewModel>()
    val state by extensionsViewModel.state.collectAsStateWithLifecycle()

    val items = state.items.values.flatten()
    val updates = items.mapNotNull { it.extension as? Extension.Installed }.filter { it.hasUpdate }
    val updating = items.filter { it.installStep != InstallStep.Idle }.mapTo(HashSet()) { it.extension.pkgName }
    val context = LocalContext.current
    val uiPreferences = remember { context.appGraph.uiPreferences }
    val fanfics by uiPreferences.discoverFanfics.collectAsState()
    val modeSwitch: @Composable () -> Unit = { DiscoverModeSwitch(fanfics, uiPreferences.discoverFanfics::set) }

    if (fanfics) {
        val fanficsViewModel = metroViewModel<FanficsViewModel>()
        val fanficsState by fanficsViewModel.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        TomoFanficsExplore(
            state = fanficsState,
            modeSwitch = modeSwitch,
            onQuery = fanficsViewModel::setQuery,
            onSearch = fanficsViewModel::setSearch,
            onLoadMore = fanficsViewModel::loadMore,
            onToggleFollow = fanficsViewModel::toggleFollow,
            onOpen = { work ->
                scope.launch { navigator.push(MangaScreen(fanficsViewModel.open(work), fromSource = true)) }
            },
            onImportFile = { navigator.push(TomoImportScreen(pickFile = true)) },
            onImportLink = { navigator.push(TomoImportScreen()) },
        )
        return
    }

    DiscoverOverview(
        updates = updates,
        updatingPackages = updating,
        onClickSearch = { navigator.push(GlobalSearchScreen()) },
        onUpdateAll = extensionsViewModel::updateAllExtensions,
        onUpdate = extensionsViewModel::updateExtension,
        onOpenSources = { navigator.push(BrowseTabsScreen(initialPage = 0)) },
        onOpenExtensions = { navigator.push(BrowseTabsScreen(initialPage = 1)) },
        onOpenLocal = { navigator.push(BrowseSourceScreen(LocalSource.ID, null)) },
        onOpenMigrate = { navigator.push(BrowseTabsScreen(initialPage = 2)) },
        onImportFile = { navigator.push(TomoImportScreen(pickFile = true)) },
        onImportAo3 = { navigator.push(TomoImportScreen()) },
        modeSwitch = modeSwitch,
    )
}
