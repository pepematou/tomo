package eu.kanade.presentation.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.glass
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ChevronRight
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.History
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

private const val MAX_RECENT_SEARCHES = 8

/** [query] first, without duplicates, keeping at most [MAX_RECENT_SEARCHES]. */
internal fun addRecentSearch(recents: List<String>, query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return recents
    return (listOf(trimmed) + recents.filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
}

/** Global search of the dark glass design (mockup "Búsqueda"); same view model and actions as Mihon's. */
@Composable
fun TomoGlobalSearchScreen(
    state: SearchViewModel.State,
    navigateUp: () -> Unit,
    recentSearches: List<String>,
    pinnedSources: List<Source>,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onRetry: () -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            Column {
                Box {
                    SearchToolbar(
                        searchQuery = state.searchQuery,
                        onChangeSearchQuery = onChangeSearchQuery,
                        onSearch = onSearch,
                        navigateUp = navigateUp,
                        scrollBehavior = scrollBehavior,
                    )
                    if (state.progress in 1..<state.total) {
                        LinearProgressIndicator(
                            progress = { state.progress / state.total.toFloat() },
                            color = Tomo.gold,
                            trackColor = Tomo.glass,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Tomo.screenPadding, vertical = 8.dp),
                ) {
                    ScopeChip(
                        text = stringResource(MR.strings.pinned_sources) + " · " + pinnedSources.size,
                        selected = state.sourceFilter == SourceFilter.PinnedOnly,
                        onClick = { onChangeSearchFilter(SourceFilter.PinnedOnly) },
                    )
                    ScopeChip(
                        text = stringResource(MR.strings.all),
                        selected = state.sourceFilter == SourceFilter.All,
                        onClick = { onChangeSearchFilter(SourceFilter.All) },
                    )
                    ScopeChip(
                        text = stringResource(MR.strings.has_results),
                        selected = state.onlyShowHasResults,
                        onClick = onToggleResults,
                    )
                }
            }
        },
    ) { paddingValues ->
        if (state.searchQuery.isNullOrBlank() || state.items.isEmpty()) {
            EmptySearch(
                contentPadding = paddingValues,
                recentSearches = recentSearches,
                pinnedSources = pinnedSources,
                onPickRecent = {
                    onChangeSearchQuery(it)
                    onSearch(it)
                },
                onRemoveRecent = onRemoveRecent,
                onClickSource = onClickSource,
            )
        } else {
            SearchResults(
                state = state,
                contentPadding = paddingValues,
                onRetry = onRetry,
                getManga = getManga,
                onClickSource = onClickSource,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
            )
        }
    }
}

@Composable
private fun ScopeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(40.dp)
            .then(
                if (selected) {
                    Modifier.background(Tomo.text, CircleShape)
                } else {
                    Modifier.glass(CircleShape)
                },
            )
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Tomo.night else Tomo.text,
        )
    }
}

@Composable
private fun EmptySearch(
    contentPadding: PaddingValues,
    recentSearches: List<String>,
    pinnedSources: List<Source>,
    onPickRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClickSource: (Source) -> Unit,
) {
    LazyColumn(contentPadding = contentPadding) {
        if (recentSearches.isNotEmpty()) {
            item(key = "recent-title") { SectionTitle(stringResource(MR.strings.tomo_recent_searches)) }
            items(recentSearches, key = { "recent-$it" }) { query ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickRecent(query) }
                        .padding(start = Tomo.screenPadding + 4.dp, end = 8.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.History,
                        contentDescription = null,
                        tint = Tomo.textFaint,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = query,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = { onRemoveRecent(query) }) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = stringResource(MR.strings.tomo_remove_recent, query),
                            tint = Tomo.textFaint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        if (pinnedSources.isNotEmpty()) {
            item(key = "pinned-title") { SectionTitle(stringResource(MR.strings.tomo_pinned_sources)) }
            item(key = "pinned") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = Tomo.screenPadding),
                ) {
                    items(pinnedSources, key = { it.id }) { source ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .width(64.dp)
                                .clickable { onClickSource(source) },
                        ) {
                            SourceInitial(source, size = 56)
                            Text(
                                text = source.name,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = Tomo.textSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = Tomo.screenPadding + 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SourceInitial(source: Source, size: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .glass(RoundedCornerShape((size / 3).dp), fill = Tomo.glassStrong),
    ) {
        Text(
            text = source.name.take(1).uppercase(),
            fontSize = (size / 3).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SearchResults(
    state: SearchViewModel.State,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    // Sources that answered with nothing are folded into one line at the end instead of an empty panel each
    val noResults = state.items.count { (_, result) -> result is SearchItemResult.Success && result.isEmpty }
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.filteredItems.forEach { (source, result) ->
            if (result is SearchItemResult.Success && result.isEmpty) return@forEach
            item(key = source.id) {
                when (result) {
                    is SearchItemResult.Error -> SourceError(source, onRetry, Modifier.animateItem())
                    else -> SourcePanel(
                        source = source,
                        result = result,
                        getManga = getManga,
                        onClickSource = onClickSource,
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        if (noResults > 0) {
            item(key = "no-results") {
                Text(
                    text = pluralStringResource(MR.plurals.tomo_no_results_in, noResults, noResults),
                    fontSize = 12.sp,
                    color = Tomo.textFaint,
                    modifier = Modifier.padding(horizontal = Tomo.screenPadding + 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SourcePanel(
    source: Source,
    result: SearchItemResult,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = Tomo.screenPadding)
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .padding(vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClickSource(source) }
                .padding(horizontal = 14.dp)
                .heightIn(min = 40.dp),
        ) {
            Text(
                text = source.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = source.lang.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Tomo.glassStrong, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Icon(
                imageVector = MaterialSymbols.Rounded.ChevronRight,
                contentDescription = null,
                tint = Tomo.textFaint,
            )
        }
        when (result) {
            is SearchItemResult.Success -> GlobalSearchCardRow(
                titles = result.result,
                getManga = getManga,
                onClick = onClickItem,
                onLongClick = onLongClickItem,
            )
            else -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                CircularProgressIndicator(color = Tomo.gold, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SourceError(source: Source, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = Tomo.screenPadding)
            .fillMaxWidth()
            .glass(
                RoundedCornerShape(18.dp),
                fill = Tomo.amber.copy(alpha = 0.12f),
                border = Tomo.amber.copy(alpha = 0.28f),
            )
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(MR.strings.tomo_source_no_response, source.name),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = stringResource(MR.strings.action_retry),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tomo.amber,
            )
        }
    }
}
