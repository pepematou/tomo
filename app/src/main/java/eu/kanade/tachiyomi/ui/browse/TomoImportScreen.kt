package eu.kanade.tachiyomi.ui.browse

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.components.SheetPrimaryButton
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.tomo.tomoContentWidth
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.tomo.text.TextImporter
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Imports a text work from a file or an Archive of Our Own link; [link] starts right away (shared from AO3). */
class TomoImportScreen(
    private val link: String? = null,
    private val pickFile: Boolean = false,
) : Screen() {

    private sealed interface Status {
        data object Idle : Status
        data object Working : Status
        data class Done(val mangaId: Long, val title: String) : Status
        data class Failed(val reason: TextImporter.Reason) : Status
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val importer = remember { context.appGraph.textImporter }
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf<Status>(Status.Idle) }
        var linkText by rememberSaveable { mutableStateOf(link.orEmpty()) }

        fun run(block: suspend () -> TextImporter.Imported) {
            status = Status.Working
            scope.launch {
                status = try {
                    val imported = block()
                    Status.Done(imported.mangaId, imported.title)
                } catch (e: TextImporter.ImportException) {
                    Status.Failed(e.reason)
                } catch (_: Exception) {
                    Status.Failed(TextImporter.Reason.UNSUPPORTED)
                }
            }
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) run { importer.importFile(uri) }
        }
        LaunchedEffect(Unit) {
            when {
                link != null -> run { importer.importAo3(link) }
                pickFile -> picker.launch(
                    arrayOf("application/epub+zip", "text/plain", "text/html", "application/xhtml+xml"),
                )
            }
        }

        TomoBackground {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .tomoContentWidth()
                    .statusBarsPadding(),
            ) {
                TomoHeader(title = stringResource(MR.strings.tomo_import_title), navigateUp = navigator::pop)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(horizontal = Tomo.screenPadding)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    Text(stringResource(MR.strings.tomo_import_ao3), fontSize = 16.sp)
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        placeholder = { Text(stringResource(MR.strings.tomo_import_link_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { run { importer.importAo3(linkText) } }),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Tomo.gold,
                            unfocusedBorderColor = Tomo.glassBorder,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SheetPrimaryButton(
                        text = stringResource(MR.strings.tomo_import_action),
                        enabled = linkText.isNotBlank() && status != Status.Working,
                        onClick = { run { importer.importAo3(linkText) } },
                    )
                }
                Box(modifier = Modifier.padding(horizontal = Tomo.screenPadding)) {
                    SheetPrimaryButton(
                        text = stringResource(MR.strings.tomo_import_pick),
                        enabled = status != Status.Working,
                        onClick = {
                            picker.launch(
                                arrayOf("application/epub+zip", "text/plain", "text/html", "application/xhtml+xml"),
                            )
                        },
                    )
                }
                when (val current = status) {
                    Status.Idle -> Unit
                    Status.Working -> Row(Modifier.padding(horizontal = Tomo.screenPadding)) {
                        CircularProgressIndicator(
                            color = Tomo.gold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(MR.strings.tomo_import_working),
                            color = Tomo.textSecondary,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    is Status.Done -> Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                    ) {
                        Text(
                            stringResource(MR.strings.tomo_import_done, current.title),
                            color = Tomo.mint,
                            fontSize = 15.sp,
                        )
                        SheetPrimaryButton(
                            text = stringResource(MR.strings.tomo_import_open),
                            onClick = { navigator.replace(MangaScreen(current.mangaId)) },
                        )
                    }
                    is Status.Failed -> Text(
                        text = stringResource(current.reason.message()),
                        color = Tomo.amber,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                    )
                }
            }
        }
    }
}

private fun TextImporter.Reason.message() = when (this) {
    TextImporter.Reason.NO_STORAGE -> MR.strings.tomo_import_error_storage
    TextImporter.Reason.UNSUPPORTED -> MR.strings.tomo_import_error_unsupported
    TextImporter.Reason.AO3_RESTRICTED -> MR.strings.tomo_import_error_restricted
    TextImporter.Reason.AO3_NOT_FOUND -> MR.strings.tomo_import_error_not_found
    TextImporter.Reason.INVALID_LINK -> MR.strings.tomo_import_error_link
    TextImporter.Reason.NETWORK -> MR.strings.tomo_import_error_network
}

/** Opens the ficha of the fanfic in a shared link (RF-30c): from there it can be read or followed. */
class FanficWorkScreen(private val sourceId: Long, private val url: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        LaunchedEffect(url) {
            val mangaId = context.appGraph.followFanfic.openWork(sourceId, url)
            navigator.replace(MangaScreen(mangaId, fromSource = true))
        }
        tachiyomi.presentation.core.screens.LoadingScreen()
    }
}
