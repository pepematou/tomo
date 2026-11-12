package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.MR

/** Tomo groups settings by task: library updates and downloads are one place. */
object SettingsUpdatesScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.tomo_settings_updates

    @Composable
    override fun getPreferences(): List<Preference> =
        SettingsLibraryScreen.getPreferences() + SettingsDownloadScreen.getPreferences()
}
