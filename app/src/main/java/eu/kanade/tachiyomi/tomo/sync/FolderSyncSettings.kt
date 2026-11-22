package eu.kanade.tachiyomi.tomo.sync

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/** "Sincronizar entre aparatos" in Data and storage, next to backups. */
@Composable
fun tomoSyncGroup(): Preference.PreferenceGroup {
    val context = LocalContext.current
    val folder = remember { context.appGraph.folderSync.folder }
    val current by folder.collectAsState()
    val pick = SettingsDataScreen.storageLocationPicker(folder)
    return Preference.PreferenceGroup(
        title = stringResource(MR.strings.tomo_sync_title),
        preferenceItems = listOfNotNull(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.tomo_sync_folder),
                subtitle = SettingsDataScreen.storageLocationText(folder),
                onClick = {
                    try {
                        pick.launch(null)
                    } catch (_: ActivityNotFoundException) {
                        context.toast(MR.strings.file_picker_error)
                    }
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.tomo_sync_stop),
                onClick = { folder.delete() },
            ).takeIf { current.isNotEmpty() },
            Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.tomo_sync_info)),
        ),
    )
}
