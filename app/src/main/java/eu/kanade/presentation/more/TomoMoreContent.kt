package eu.kanade.presentation.more

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.DiscoverRow
import eu.kanade.presentation.tomo.GlassPanel
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.TomoSwitch
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.tomo.tomoContentWidth
import eu.kanade.tachiyomi.BuildConfig
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Label
import mihon.icons.materialsymbols.rounded.CloudOff
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Eyeglasses2
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.QueryStats
import mihon.icons.materialsymbols.rounded.Settings
import mihon.icons.materialsymbols.rounded.Storage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.LocalFloatingBarInset
import tachiyomi.presentation.core.i18n.stringResource

/** "Tú" with the glass theme: the same entries as Mihon's More, as glass panels and without Mihon's own links. */
@Composable
fun TomoMoreContent(
    downloadQueueSummary: String?,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickDataAndStorage: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        LocalFloatingBarInset.current + 16.dp

    TomoBackground {
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .tomoContentWidth()
                .statusBarsPadding(),
        ) {
            item(key = "header") {
                TomoHeader(
                    title = stringResource(MR.strings.tomo_tab_you),
                    subtitle = "Tomo · v${BuildConfig.VERSION_NAME}",
                )
            }
            item(key = "modes") {
                GlassPanel(modifier = Modifier.padding(horizontal = Tomo.screenPadding)) {
                    GlassSwitchRow(
                        icon = MaterialSymbols.Rounded.CloudOff,
                        title = stringResource(MR.strings.label_downloaded_only),
                        subtitle = stringResource(MR.strings.downloaded_only_summary),
                        checked = downloadedOnly,
                        onCheckedChange = onDownloadedOnlyChange,
                    )
                    GlassSwitchRow(
                        icon = MaterialSymbols.Rounded.Eyeglasses2,
                        title = stringResource(MR.strings.pref_incognito_mode),
                        subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                        checked = incognitoMode,
                        onCheckedChange = onIncognitoModeChange,
                    )
                }
            }
            item(key = "library") {
                GlassPanel(modifier = Modifier.padding(horizontal = Tomo.screenPadding)) {
                    DiscoverRow(
                        icon = MaterialSymbols.Rounded.Download,
                        title = stringResource(MR.strings.label_download_queue),
                        subtitle = downloadQueueSummary,
                        onClick = onClickDownloadQueue,
                    )
                    DiscoverRow(
                        icon = MaterialSymbols.AutoMirroredRounded.Label,
                        title = stringResource(MR.strings.categories),
                        subtitle = null,
                        onClick = onClickCategories,
                    )
                    DiscoverRow(
                        icon = MaterialSymbols.Rounded.QueryStats,
                        title = stringResource(MR.strings.label_stats),
                        subtitle = null,
                        onClick = onClickStats,
                    )
                    DiscoverRow(
                        icon = MaterialSymbols.Rounded.Storage,
                        title = stringResource(MR.strings.label_data_storage),
                        subtitle = null,
                        onClick = onClickDataAndStorage,
                    )
                }
            }
            item(key = "app") {
                GlassPanel(modifier = Modifier.padding(horizontal = Tomo.screenPadding)) {
                    DiscoverRow(
                        icon = MaterialSymbols.Rounded.Settings,
                        title = stringResource(MR.strings.label_settings),
                        subtitle = null,
                        onClick = onClickSettings,
                    )
                    DiscoverRow(
                        icon = MaterialSymbols.Rounded.Info,
                        title = stringResource(MR.strings.pref_category_about),
                        subtitle = null,
                        onClick = onClickAbout,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .glass(RoundedCornerShape(12.dp), fill = Tomo.glassStrong),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Tomo.text, modifier = Modifier.size(22.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(text = title, fontSize = 16.sp)
            Text(text = subtitle, fontSize = 13.sp, color = Tomo.textSecondary)
        }
        TomoSwitch(checked = checked)
    }
}
