package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.tomo.GlassIconButton
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.tomo.text.WorkType
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Search
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/** Library header of the dark glass design: "42 obras / Biblioteca", search and filter, and the last update. */
@Composable
fun LibraryGlassHeader(
    count: Int,
    lastUpdated: Long,
    hasActiveFilters: Boolean,
    onClickSearch: () -> Unit,
    onClickFilter: () -> Unit,
    shelfView: Boolean,
    onShelfViewChange: (Boolean) -> Unit,
    type: String,
    onTypeChange: (String) -> Unit,
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        TomoHeader(
            title = stringResource(MR.strings.label_library),
            subtitle = pluralStringResource(MR.plurals.tomo_library_count, count, count),
        ) {
            GlassIconButton(
                icon = MaterialSymbols.Rounded.Search,
                contentDescription = stringResource(MR.strings.action_search),
                onClick = onClickSearch,
            )
            GlassIconButton(
                icon = MaterialSymbols.Rounded.FilterList,
                contentDescription = stringResource(MR.strings.action_filter),
                onClick = onClickFilter,
                tint = if (hasActiveFilters) Tomo.gold else Tomo.text,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = Tomo.screenPadding, vertical = 4.dp),
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Refresh,
                contentDescription = null,
                tint = Tomo.textFaint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(
                    MR.strings.tomo_library_updated,
                    relativeTimeSpanString(lastUpdated).replaceFirstChar { it.lowercase() },
                ),
                color = Tomo.textFaint,
                fontSize = 12.sp,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Tomo.screenPadding, end = Tomo.screenPadding, top = 8.dp),
        ) {
            Text(
                text = if (shelfView) stringResource(MR.strings.tomo_shelf_hint) else "",
                color = Tomo.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .glass(CircleShape, border = Color.White.copy(alpha = 0.12f))
                    .padding(3.dp),
            ) {
                listOf(true to MR.strings.tomo_shelf, false to MR.strings.tomo_covers).forEach { (value, label) ->
                    val selected = shelfView == value
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(34.dp)
                            .background(if (selected) Tomo.text else Color.Transparent, CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton) { onShelfViewChange(value) }
                            .padding(horizontal = 14.dp),
                    ) {
                        Text(
                            text = stringResource(label),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Tomo.night else Tomo.textSecondary,
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = Tomo.screenPadding, end = Tomo.screenPadding, top = 10.dp),
        ) {
            listOf(
                LibraryPreferences.TYPE_ALL to MR.strings.tomo_type_all,
                WorkType.MANGA.name to MR.strings.tomo_type_manga,
                WorkType.MANHWA.name to MR.strings.tomo_type_manhwa,
                WorkType.TEXT.name to MR.strings.tomo_type_text,
            ).forEach { (value, label) ->
                val selected = type == value
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(36.dp)
                        .then(
                            if (selected) Modifier.background(Tomo.text, CircleShape) else Modifier.glass(CircleShape),
                        )
                        .selectable(selected = selected, role = Role.RadioButton) { onTypeChange(value) }
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = stringResource(label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Tomo.night else Tomo.text,
                    )
                }
            }
        }
    }
}
