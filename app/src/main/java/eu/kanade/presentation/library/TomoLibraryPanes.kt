package eu.kanade.presentation.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.i18n.stringResource

/**
 * On a tablet in landscape the library stays on the left and the chosen series opens on the right, so moving between
 * series needs no back and forth.
 */
@Composable
fun TomoLibraryPanes(
    twoPane: Boolean,
    mangaId: Long?,
    onClose: () -> Unit,
    library: @Composable () -> Unit,
) {
    if (!twoPane) {
        library()
        return
    }
    TwoPanelBox(
        startContent = { library() },
        endContent = {
            if (mangaId == null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text(stringResource(MR.strings.tomo_pick_series), color = Tomo.textSecondary)
                }
                return@TwoPanelBox
            }
            BoxWithConstraints {
                // The ficha's wide layout splits in two again, too cramped for the pane on smaller tablets
                val configuration = Configuration(LocalConfiguration.current).apply {
                    if (maxWidth < 720.dp) smallestScreenWidthDp = maxWidth.value.toInt()
                }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    key(mangaId) {
                        Navigator(MangaScreen(mangaId), onBackPressed = null) { navigator ->
                            val back: () -> Unit = { if (navigator.canPop) navigator.pop() else onClose() }
                            CompositionLocalProvider(LocalBackPress provides back) {
                                DefaultNavigatorScreenTransition(navigator = navigator)
                            }
                        }
                    }
                }
            }
        },
    )
}
