package eu.kanade.presentation.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.Literata
import eu.kanade.presentation.tomo.GlassPanel
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.TomoHeader
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.tomo.tomoContentWidth
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.tomo.ao3.Ao3Search
import eu.kanade.tachiyomi.tomo.ao3.Ao3Work
import eu.kanade.tachiyomi.tomo.text.coverHue
import eu.kanade.tachiyomi.ui.browse.FanficsViewModel
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.MenuBook
import mihon.icons.materialsymbols.rounded.Public
import mihon.icons.materialsymbols.rounded.Search
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.LocalFloatingBarInset
import tachiyomi.presentation.core.i18n.stringResource
import java.text.NumberFormat

/** A popular fandom among works in Spanish: its AO3 tag and the kind of work it comes from. */
private data class Fandom(val name: String, val kind: String, val tag: String)

private val popularFandoms = listOf(
    Fandom("Harry Potter", "Libro", "Harry Potter - J. K. Rowling"),
    Fandom("My Hero Academia", "Anime", "僕のヒーローアカデミア | Boku no Hero Academia | My Hero Academia"),
    Fandom("Genshin Impact", "Videojuego", "原神 | Genshin Impact (Video Game)"),
    Fandom("BTS", "Música", "방탄소년단 | Bangtan Boys | BTS"),
    Fandom("Naruto", "Anime", "Naruto"),
    Fandom("Marvel", "Película", "Marvel Cinematic Universe"),
    Fandom("Haikyuu!!", "Anime", "Haikyuu!!"),
    Fandom("Jujutsu Kaisen", "Anime", "呪術廻戦 | Jujutsu Kaisen (Anime & Manga)"),
)

/** Discover → Fanfics: Spanish works of Archive of Our Own, searched, filtered by fandom and followed. */
@Composable
fun TomoFanficsExplore(
    state: FanficsViewModel.State,
    modeSwitch: @Composable () -> Unit,
    onQuery: (String) -> Unit,
    onSearch: (Ao3Search) -> Unit,
    onLoadMore: () -> Unit,
    onToggleFollow: (Ao3Work) -> Unit,
    onOpen: (Ao3Work) -> Unit,
    onImportFile: () -> Unit,
    onImportLink: () -> Unit,
) {
    val search = state.search
    val listState = rememberLazyListState()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        LocalFloatingBarInset.current + 16.dp

    LaunchedEffect(listState, state.works.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= listState.layoutInfo.totalItemsCount - 3) onLoadMore() }
    }

    TomoBackground(topGlow = Color(0xFF8B3A62)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .tomoContentWidth()
                .statusBarsPadding(),
        ) {
            item(key = "header") {
                Column {
                    TomoHeader(
                        title = stringResource(MR.strings.tomo_fanfics),
                        subtitle = "Archive of Our Own",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                    ) {
                        Text(
                            text = stringResource(MR.strings.tomo_spanish_only),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tomo.mint,
                            modifier = Modifier
                                .background(Tomo.mint.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            item(key = "mode") { modeSwitch() }
            item(key = "search") { SearchField(search.query, onQuery) }
            item(key = "chips") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Tomo.screenPadding),
                ) {
                    FilterChip(stringResource(MR.strings.tomo_fanfic_complete), search.complete == true) {
                        onSearch(search.copy(complete = if (search.complete == true) null else true))
                    }
                    FilterChip(stringResource(MR.strings.tomo_fanfic_in_progress), search.complete == false) {
                        onSearch(search.copy(complete = if (search.complete == false) null else false))
                    }
                    FilterChip(stringResource(MR.strings.tomo_fanfic_short), search.maxWords != null) {
                        onSearch(search.copy(maxWords = if (search.maxWords == null) 10_000 else null))
                    }
                    FilterChip(stringResource(MR.strings.tomo_fanfic_no_warnings), search.noWarnings) {
                        onSearch(search.copy(noWarnings = !search.noWarnings))
                    }
                }
            }
            item(key = "fandoms") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                ) {
                    Text(
                        stringResource(MR.strings.tomo_fanfic_by_fandom),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    popularFandoms.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { fandom ->
                                FandomTile(
                                    fandom = fandom,
                                    selected = search.fandom == fandom.tag,
                                    onClick = {
                                        onSearch(
                                            search.copy(
                                                fandom = if (search.fandom ==
                                                    fandom.tag
                                                ) {
                                                    ""
                                                } else {
                                                    fandom.tag
                                                },
                                            ),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "results-title") {
                var sortOpen by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = Tomo.screenPadding, end = 4.dp),
                ) {
                    Text(
                        text = stringResource(search.sort.label()),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { sortOpen = true }) {
                            Text(stringResource(MR.strings.action_sort), color = Tomo.textSecondary)
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            Ao3Search.Sort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(sort.label())) },
                                    onClick = {
                                        sortOpen = false
                                        onSearch(search.copy(sort = sort))
                                    },
                                )
                            }
                        }
                    }
                }
            }
            items(state.works, key = { it.url }) { work ->
                WorkCard(
                    work = work,
                    followed = work.url in state.followed,
                    onToggleFollow = { onToggleFollow(work) },
                    onClick = { onOpen(work) },
                )
            }
            item(key = "status") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    when {
                        state.loading -> CircularProgressIndicator(
                            color = Tomo.gold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                        state.failed -> Text(
                            stringResource(MR.strings.tomo_import_error_network),
                            color = Tomo.amber,
                            fontSize = 13.sp,
                        )
                        state.works.isEmpty() -> Text(
                            stringResource(MR.strings.no_results_found),
                            color = Tomo.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            item(key = "import") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(MR.strings.tomo_import),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                    )
                    GlassPanel(modifier = Modifier.padding(horizontal = Tomo.screenPadding)) {
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.MenuBook,
                            title = stringResource(MR.strings.tomo_import_file),
                            subtitle = stringResource(MR.strings.tomo_import_file_summary),
                            onClick = onImportFile,
                        )
                        DiscoverRow(
                            icon = MaterialSymbols.Rounded.Public,
                            title = stringResource(MR.strings.tomo_import_ao3),
                            subtitle = stringResource(MR.strings.tomo_import_ao3_summary),
                            onClick = onImportLink,
                        )
                    }
                }
            }
        }
    }
}

private fun Ao3Search.Sort.label() = when (this) {
    Ao3Search.Sort.UPDATED -> MR.strings.tomo_fanfic_sort_updated
    Ao3Search.Sort.KUDOS -> MR.strings.tomo_fanfic_sort_kudos
    Ao3Search.Sort.WORDS -> MR.strings.tomo_fanfic_sort_words
    Ao3Search.Sort.HITS -> MR.strings.tomo_fanfic_sort_hits
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(horizontal = Tomo.screenPadding)
            .fillMaxWidth()
            .height(50.dp)
            .glass(CircleShape, fill = Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            MaterialSymbols.Rounded.Search,
            contentDescription = null,
            tint = Tomo.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(stringResource(MR.strings.tomo_fanfic_search_hint), color = Tomo.textFaint, fontSize = 15.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Tomo.text, fontSize = 15.sp),
                cursorBrush = SolidColor(Tomo.gold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(38.dp)
            .then(if (selected) Modifier.background(Tomo.text, CircleShape) else Modifier.glass(CircleShape))
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Tomo.night else Tomo.text)
    }
}

@Composable
private fun FandomTile(fandom: Fandom, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hue = coverHue(fandom.name)
    val shape = RoundedCornerShape(18.dp)
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(64.dp)
            .background(
                Brush.linearGradient(
                    listOf(Color.hsv(hue, 0.55f, 0.62f), Color.hsv(hue, 0.65f, 0.22f)),
                ),
                shape,
            )
            .border(if (selected) 2.dp else 1.dp, if (selected) Tomo.gold else Color.White.copy(alpha = 0.14f), shape)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(
            fandom.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(fandom.kind, fontSize = 11.sp, color = Color(0xFFC9CFDB))
    }
}

@Composable
private fun WorkCard(work: Ao3Work, followed: Boolean, onToggleFollow: () -> Unit, onClick: () -> Unit) {
    val meta = work.meta
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(horizontal = Tomo.screenPadding)
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    work.title,
                    fontFamily = Literata,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        stringResource(MR.strings.tomo_fanfic_by, work.author),
                        meta.fandoms.firstOrNull(),
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = Tomo.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            FollowButton(followed, onToggleFollow)
        }
        if (work.summary.isNotBlank()) {
            Text(
                work.summary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFFC9CFDB),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val tags = (meta.relationships.take(1) + work.tags).take(3)
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Text(
                        tag,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Text(fanficStats(work), fontSize = 12.sp, color = Tomo.textFaint)
    }
}

@Composable
fun FollowButton(followed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, height: Int = 36) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .heightIn(min = height.dp)
            .then(
                if (followed) {
                    Modifier.background(Tomo.mint, CircleShape)
                } else {
                    Modifier.glass(CircleShape, fill = Tomo.glassStrong)
                },
            )
            .selectable(selected = followed, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        if (followed) {
            Icon(
                MaterialSymbols.Rounded.Check,
                contentDescription = null,
                tint = Tomo.night,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = stringResource(if (followed) MR.strings.tomo_fanfic_following else MR.strings.tomo_fanfic_follow),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (followed) Tomo.night else Tomo.text,
        )
    }
}

/** "48 210 palabras · 14 de 20 cap. · En curso · hace 3 días" */
@Composable
private fun fanficStats(work: Ao3Work): String {
    val meta = work.meta
    return listOfNotNull(
        stringResource(MR.strings.tomo_fanfic_words, NumberFormat.getIntegerInstance().format(meta.words)),
        stringResource(MR.strings.tomo_fanfic_chapters, meta.chaptersPosted, meta.chaptersTotal?.toString() ?: "?"),
        stringResource(
            if (meta.complete) MR.strings.tomo_fanfic_status_complete else MR.strings.tomo_fanfic_status_wip,
        ),
        meta.updatedAt.takeIf { it > 0 }?.let { relativeTimeSpanString(it).replaceFirstChar(Char::lowercase) },
    ).joinToString(" · ")
}
