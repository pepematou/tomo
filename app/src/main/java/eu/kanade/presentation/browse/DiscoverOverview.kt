package eu.kanade.presentation.browse

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.tomo.GlassIconButton
import eu.kanade.presentation.tomo.GlassPanel
import eu.kanade.presentation.tomo.GoldBadge
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.TomoPrimaryButton
import eu.kanade.presentation.tomo.TomoSectionTitle
import eu.kanade.presentation.tomo.glass
import eu.kanade.tachiyomi.extension.model.Extension
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ChevronRight
import mihon.icons.materialsymbols.rounded.Extension
import mihon.icons.materialsymbols.rounded.Folder
import mihon.icons.materialsymbols.rounded.GridView
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.SwapHoriz
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.LocalFloatingBarInset
import tachiyomi.presentation.core.i18n.stringResource

/** Discover of the dark glass design: extensions to update first, then the ways into sources. */
@Composable
fun DiscoverOverview(
    updates: List<Extension.Installed>,
    updatingPackages: Set<String>,
    onClickSearch: () -> Unit,
    onUpdateAll: () -> Unit,
    onUpdate: (Extension.Installed) -> Unit,
    onOpenSources: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenMigrate: () -> Unit,
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        LocalFloatingBarInset.current + 16.dp

    TomoBackground(topGlow = Tomo.glowTeal) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            item(key = "header") {
                TomoHeader(
                    title = stringResource(MR.strings.tomo_tab_discover),
                    subtitle = stringResource(MR.strings.tomo_discover_subtitle),
                ) {
                    GlassIconButton(
                        icon = MaterialSymbols.Rounded.Search,
                        contentDescription = stringResource(MR.strings.action_global_search),
                        onClick = onClickSearch,
                    )
                }
            }

            item(key = "extensions") {
                GlassPanel(
                    radius = 28.dp,
                    contentPadding = PaddingValues(20.dp),
                    modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(MR.strings.label_extensions),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (updates.isNotEmpty()) {
                            GoldBadge(stringResource(MR.strings.tomo_extensions_to_update, updates.size))
                        }
                    }
                    if (updates.isEmpty()) {
                        Text(
                            text = stringResource(MR.strings.tomo_extensions_up_to_date),
                            color = Tomo.textSecondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        TextButton(onClick = onOpenExtensions, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(MR.strings.tomo_see_extensions), color = Tomo.gold)
                        }
                    } else {
                        TomoPrimaryButton(
                            text = stringResource(MR.strings.ext_update_all),
                            onClick = onUpdateAll,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                        updates.forEach { extension ->
                            ExtensionUpdateRow(
                                extension = extension,
                                isUpdating = extension.pkgName in updatingPackages,
                                onUpdate = { onUpdate(extension) },
                            )
                        }
                    }
                }
            }

            item(key = "sources") {
                Column {
                    TomoSectionTitle(stringResource(MR.strings.tomo_sources)) {
                        TextButton(onClick = onOpenSources) {
                            Text(stringResource(MR.strings.tomo_see_all), color = Tomo.textSecondary)
                        }
                    }
                    GlassPanel(modifier = Modifier.padding(horizontal = Tomo.screenPadding)) {
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.GridView,
                            title = stringResource(MR.strings.tomo_all_sources),
                            subtitle = stringResource(MR.strings.tomo_all_sources_summary),
                            onClick = onOpenSources,
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.Extension,
                            title = stringResource(MR.strings.label_extensions),
                            subtitle = stringResource(MR.strings.tomo_extensions_summary),
                            onClick = onOpenExtensions,
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.Folder,
                            title = stringResource(MR.strings.local_source),
                            subtitle = stringResource(MR.strings.tomo_local_summary),
                            onClick = onOpenLocal,
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.SwapHoriz,
                            title = stringResource(MR.strings.label_migration),
                            subtitle = stringResource(MR.strings.tomo_migrate_summary),
                            onClick = onOpenMigrate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionUpdateRow(extension: Extension.Installed, isUpdating: Boolean, onUpdate: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .glass(CircleShape, fill = Tomo.glassStrong),
        ) {
            Text(text = extension.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(text = extension.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "v${extension.versionName}", fontSize = 12.sp, color = Tomo.textFaint)
        }
        TextButton(onClick = onUpdate, enabled = !isUpdating) {
            Text(
                text = stringResource(if (isUpdating) MR.strings.ext_installing else MR.strings.ext_update),
                color = if (isUpdating) Tomo.textFaint else Tomo.gold,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Row of a glass list: square glass icon, title and summary, and a chevron. */
@Composable
internal fun DiscoverRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 13.sp, color = Tomo.textSecondary, maxLines = 1)
            }
        }
        Icon(imageVector = MaterialSymbols.Rounded.ChevronRight, contentDescription = null, tint = Tomo.textFaint)
    }
}
