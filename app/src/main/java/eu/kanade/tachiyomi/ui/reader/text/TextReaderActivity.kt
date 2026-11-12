package eu.kanade.tachiyomi.ui.reader.text

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.reader.text.TextReaderContent
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import mihon.app.di.appGraph
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/** Reader for text works (fanfics, novels) imported as EPUB, TXT or HTML. */
class TextReaderActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val mangaId = intent.getLongExtra(MANGA_KEY, -1)
        val chapterId = intent.getLongExtra(CHAPTER_KEY, -1)
        val preferences = appGraph.textReaderPreferences

        setComposeContent {
            val viewModel = assistedMetroViewModel<TextReaderViewModel, TextReaderViewModel.Factory> {
                create(mangaId, chapterId)
            }
            val state by viewModel.state.collectAsState()
            when (val current = state) {
                TextReaderViewModel.State.Loading -> LoadingScreen()
                is TextReaderViewModel.State.Error -> TomoBackground {
                    EmptyScreen(message = current.message.orEmpty(), modifier = Modifier.fillMaxSize())
                }
                is TextReaderViewModel.State.Ready -> key(current.chapter.id) {
                    TextReaderContent(
                        title = current.title,
                        chapter = current.chapter,
                        chapterIndex = current.chapterIndex,
                        chapters = current.chapters,
                        blocks = current.blocks,
                        startIndex = current.startIndex,
                        bookmarked = current.bookmarked,
                        preferences = preferences,
                        onPosition = viewModel::onPosition,
                        onLeave = viewModel::flush,
                        onMove = viewModel::move,
                        onOpenChapter = viewModel::open,
                        onToggleBookmark = viewModel::toggleBookmark,
                        onBack = ::finish,
                    )
                }
            }
        }
    }

    companion object {
        private const val MANGA_KEY = "manga"
        private const val CHAPTER_KEY = "chapter"

        fun newIntent(context: Context, mangaId: Long, chapterId: Long): Intent =
            Intent(context, TextReaderActivity::class.java).apply {
                putExtra(MANGA_KEY, mangaId)
                putExtra(CHAPTER_KEY, chapterId)
            }
    }
}
