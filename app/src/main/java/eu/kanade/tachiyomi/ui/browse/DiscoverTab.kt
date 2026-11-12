package eu.kanade.tachiyomi.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.DiscoverOverview
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
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
    )
}
