package eu.kanade.presentation.library.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.tomo.LocalGlassTheme
import eu.kanade.presentation.tomo.Tomo
import tachiyomi.domain.category.model.Category
import tachiyomi.presentation.core.components.material.TabText

@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getItemCountForCategory: (Category) -> Int?,
    onTabItemClick: (Int) -> Unit,
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    val glass = LocalGlassTheme.current
    PrimaryScrollableTabRow(
        selectedTabIndex = currentPageIndex,
        edgePadding = 0.dp,
        // Tomo: tabs float over the background glows with a short gold underline
        containerColor = if (glass) Color.Transparent else TabRowDefaults.primaryContainerColor,
        divider = { if (!glass) HorizontalDivider() },
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(currentPageIndex, matchContentSize = true),
                width = if (glass) 24.dp else Dp.Unspecified,
                color = if (glass) Tomo.gold else MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        categories.forEachIndexed { index, category ->
            Tab(
                selected = currentPageIndex == index,
                onClick = { onTabItemClick(index) },
                text = {
                    TabText(
                        text = category.visualName,
                        badgeCount = getItemCountForCategory(category),
                    )
                },
                unselectedContentColor = if (glass) Tomo.textFaint else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
