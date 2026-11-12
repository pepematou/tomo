package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.browse.TomoGlobalSearchScreen
import eu.kanade.presentation.browse.addRecentSearch
import eu.kanade.presentation.tomo.LocalGlassTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.app.di.appGraph
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState

class GlobalSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel =
            assistedMetroViewModel<GlobalSearchViewModel, GlobalSearchViewModel.Factory> {
                create(initialQuery = searchQuery, initialExtensionFilter = extensionFilter)
            }
        val state by viewModel.state.collectAsState()
        var showSingleLoadingScreen by remember {
            mutableStateOf(searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1)
        }

        if (showSingleLoadingScreen) {
            LoadingScreen()

            LaunchedEffect(state.items) {
                when (val result = state.items.values.singleOrNull()) {
                    SearchItemResult.Loading -> return@LaunchedEffect
                    is SearchItemResult.Success -> {
                        val manga = result.result.singleOrNull()
                        if (manga != null) {
                            navigator.replace(MangaScreen(manga.id, true))
                        } else {
                            // Backoff to result screen
                            showSingleLoadingScreen = false
                        }
                    }
                    else -> showSingleLoadingScreen = false
                }
            }
        } else if (LocalGlassTheme.current) {
            val context = LocalContext.current
            val sourcePreferences = remember { context.appGraph.sourcePreferences }
            val recentText by sourcePreferences.recentSearches.collectAsState()
            val recentSearches = remember(recentText) { recentText.lines().filter { it.isNotBlank() } }
            val pinnedSources by produceState(emptyList<Source>()) {
                val sourceManager = context.appGraph.sourceManager
                value = sourcePreferences.pinnedSources.get()
                    .mapNotNull { id -> id.toLongOrNull()?.let { sourceManager.get(it) } }
                    .sortedBy { it.name.lowercase() }
            }
            TomoGlobalSearchScreen(
                state = state,
                navigateUp = navigator::pop,
                recentSearches = recentSearches,
                pinnedSources = pinnedSources,
                onChangeSearchQuery = viewModel::updateSearchQuery,
                onSearch = { query ->
                    sourcePreferences.recentSearches.set(addRecentSearch(recentSearches, query).joinToString("\n"))
                    viewModel.search()
                },
                onRemoveRecent = { query ->
                    sourcePreferences.recentSearches.set((recentSearches - query).joinToString("\n"))
                },
                onRetry = viewModel::retry,
                getManga = { viewModel.getManga(it) },
                onChangeSearchFilter = viewModel::setSourceFilter,
                onToggleResults = viewModel::toggleFilterResults,
                onClickSource = { navigator.push(BrowseSourceScreen(it.id, state.searchQuery)) },
                onClickItem = { navigator.push(MangaScreen(it.id, true)) },
                onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
            )
        } else {
            GlobalSearchScreen(
                state = state,
                navigateUp = navigator::pop,
                onChangeSearchQuery = viewModel::updateSearchQuery,
                onSearch = { viewModel.search() },
                getManga = { viewModel.getManga(it) },
                onChangeSearchFilter = viewModel::setSourceFilter,
                onToggleResults = viewModel::toggleFilterResults,
                onClickSource = {
                    navigator.push(BrowseSourceScreen(it.id, state.searchQuery))
                },
                onClickItem = { navigator.push(MangaScreen(it.id, true)) },
                onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
            )
        }
    }
}
