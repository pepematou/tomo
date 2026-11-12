package eu.kanade.tachiyomi.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.home.HomeContent
import eu.kanade.presentation.updates.UpdateIssuesDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.launch
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Home
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object HomeTab : Tab {

    private val snackbarHostState = SnackbarHostState()

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = stringResource(MR.strings.tomo_tab_home),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Home),
        )

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val viewModel = metroViewModel<HomeViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        var showIssues by remember { mutableStateOf(false) }

        HomeContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onContinue = { history ->
                scope.launch {
                    val chapter = viewModel.chapterToContinue(history)
                    if (chapter != null) {
                        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
                    } else {
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
                    }
                }
            },
            onClickManga = { navigator.push(MangaScreen(it)) },
            onSeeAllNewChapters = { navigator.push(UpdatesTab) },
            onClickHistory = { navigator.push(HistoryTab) },
            onClickIssues = { showIssues = true },
            onUpdateLibrary = {
                val started = LibraryUpdateWorker.startNow(context.workManager)
                scope.launch {
                    val msg = if (started) MR.strings.updating_library else MR.strings.update_already_running
                    snackbarHostState.showSnackbar(context.stringResource(msg))
                }
                started
            },
        )

        if (showIssues) {
            UpdateIssuesDialog(
                issues = state.issues,
                onDismissRequest = { showIssues = false },
                onOpenManga = {
                    showIssues = false
                    navigator.push(MangaScreen(it.mangaId))
                },
                onOpenWebView = { issue ->
                    issue.webViewUrl?.let { url ->
                        context.startActivity(WebViewActivity.newIntent(context, url, issue.sourceId, issue.title))
                    }
                },
                onMigrate = {
                    showIssues = false
                    navigator.push(MigrationConfigScreen(it))
                },
                onGoToExtensions = {
                    showIssues = false
                    scope.launch { HomeScreen.openTab(HomeScreen.Tab.Browse(toExtensions = true)) }
                },
            )
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }
    }
}
