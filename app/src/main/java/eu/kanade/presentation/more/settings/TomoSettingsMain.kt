package eu.kanade.presentation.more.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.browse.DiscoverRow
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsReaderScreen
import eu.kanade.presentation.more.settings.screen.SettingsSecurityScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.SettingsUpdatesScreen
import eu.kanade.presentation.tomo.GlassPanel
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.TomoSectionTitle
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.util.relativeTimeSpanString
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.BarChart
import mihon.icons.materialsymbols.rounded.Cloud
import mihon.icons.materialsymbols.rounded.Extension
import mihon.icons.materialsymbols.rounded.Lock
import mihon.icons.materialsymbols.rounded.MenuBook
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.Tune
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.LocalFloatingBarInset
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/** Settings home of the dark glass design: search, the two most used actions and settings grouped by task. */
@Composable
fun TomoSettingsMain(
    updateIntervalHours: Int,
    lastLibraryUpdate: Long,
    extensionUpdates: Int,
    navigateUp: () -> Unit,
    onClickSearch: () -> Unit,
    onUpdateLibrary: () -> Unit,
    onUpdateExtensions: () -> Unit,
    onOpen: (Screen) -> Unit,
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        LocalFloatingBarInset.current + 16.dp

    TomoBackground {
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            item(key = "header") {
                TomoHeader(
                    title = stringResource(MR.strings.label_settings),
                    navigateUp = navigateUp,
                    navigateUpDescription = stringResource(MR.strings.action_bar_up_description),
                )
            }

            item(key = "search") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = Tomo.screenPadding)
                        .fillMaxWidth()
                        .glass(CircleShape)
                        .clickable(onClick = onClickSearch)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Icon(imageVector = MaterialSymbols.Rounded.Search, contentDescription = null, tint = Tomo.textFaint)
                    Text(
                        text = stringResource(MR.strings.tomo_settings_search_hint),
                        color = Tomo.textFaint,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            item(key = "most-used") {
                Column {
                    TomoSectionTitle(stringResource(MR.strings.tomo_settings_most_used))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                    ) {
                        QuickTile(
                            icon = MaterialSymbols.Rounded.Refresh,
                            title = stringResource(MR.strings.action_update_library),
                            subtitle = stringResource(
                                MR.strings.tomo_last_time,
                                relativeTimeSpanString(lastLibraryUpdate).replaceFirstChar { it.lowercase() },
                            ),
                            onClick = onUpdateLibrary,
                            modifier = Modifier.weight(1f),
                        )
                        QuickTile(
                            icon = MaterialSymbols.Rounded.Extension,
                            title = stringResource(MR.strings.tomo_update_extensions),
                            subtitle = pluralStringResource(
                                MR.plurals.tomo_extension_updates,
                                extensionUpdates,
                                extensionUpdates,
                            ),
                            onClick = onUpdateExtensions,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item(key = "all") {
                Column {
                    TomoSectionTitle(stringResource(MR.strings.tomo_settings_all))
                    GlassPanel(modifier = Modifier.padding(horizontal = Tomo.screenPadding)) {
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.Refresh,
                            title = stringResource(MR.strings.tomo_settings_updates),
                            subtitle = updatesSummary(updateIntervalHours),
                            onClick = { onOpen(SettingsUpdatesScreen) },
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.MenuBook,
                            title = stringResource(MR.strings.pref_category_reader),
                            subtitle = stringResource(MR.strings.tomo_settings_reader_summary),
                            onClick = { onOpen(SettingsReaderScreen) },
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.Palette,
                            title = stringResource(MR.strings.pref_category_appearance),
                            subtitle = stringResource(MR.strings.tomo_settings_appearance_summary),
                            onClick = { onOpen(SettingsAppearanceScreen) },
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.Extension,
                            title = stringResource(MR.strings.tomo_discover_subtitle),
                            subtitle = stringResource(MR.strings.tomo_settings_browse_summary),
                            onClick = { onOpen(SettingsBrowseScreen) },
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.BarChart,
                            title = stringResource(MR.strings.pref_category_tracking),
                            subtitle = stringResource(MR.strings.tomo_settings_tracking_summary),
                            onClick = { onOpen(SettingsTrackingScreen) },
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.Cloud,
                            title = stringResource(MR.strings.tomo_settings_backups),
                            subtitle = stringResource(MR.strings.tomo_settings_backups_summary),
                            onClick = { onOpen(SettingsDataScreen) },
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.Lock,
                            title = stringResource(MR.strings.tomo_settings_privacy),
                            subtitle = stringResource(MR.strings.tomo_settings_privacy_summary),
                            onClick = { onOpen(SettingsSecurityScreen) },
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.Tune,
                            title = stringResource(MR.strings.pref_category_advanced),
                            subtitle = stringResource(MR.strings.tomo_settings_advanced_summary),
                            onClick = { onOpen(SettingsAdvancedScreen) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .glass(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .glass(RoundedCornerShape(12.dp), fill = Tomo.text),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Tomo.night, modifier = Modifier.size(22.dp))
        }
        Text(
            text = title,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )
        Text(text = subtitle, fontSize = 12.sp, color = Tomo.textSecondary)
    }
}

/** "Every 12 h, any network, extensions": the summary line of the updates group. */
@Composable
private fun updatesSummary(intervalHours: Int): String = if (intervalHours > 0) {
    stringResource(MR.strings.tomo_settings_updates_summary, intervalHours)
} else {
    stringResource(MR.strings.tomo_settings_updates_summary_manual)
}
