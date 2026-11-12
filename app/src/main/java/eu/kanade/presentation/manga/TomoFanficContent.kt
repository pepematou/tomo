package eu.kanade.presentation.manga

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.FollowButton
import eu.kanade.presentation.theme.Literata
import eu.kanade.presentation.tomo.GlassIconButton
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoBackground
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.tomo.tomoContentWidth
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.tomo.ao3.Ao3Meta
import eu.kanade.tachiyomi.tomo.text.coverHue
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBack
import mihon.icons.materialsymbols.rounded.Public
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Ficha of a fanfic (RF-30): the work as a book, its AO3 stats and tags, the summary and the chapters. */
@Composable
fun TomoFanficContent(
    manga: Manga,
    chapters: List<Chapter>,
    nextChapter: Chapter?,
    isAo3: Boolean,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    onRefresh: () -> Unit,
    onRead: () -> Unit,
    onToggleFollow: () -> Unit,
    onChapterClick: (Chapter) -> Unit,
) {
    val meta = Ao3Meta.fromMemo(manga.memo)
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val hue = coverHue(manga.title)

    TomoBackground(topGlow = Color.hsv(hue, 0.5f, 0.55f)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .tomoContentWidth()
                .statusBarsPadding(),
        ) {
            item(key = "bar") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = Tomo.screenPadding, end = Tomo.screenPadding, top = 8.dp),
                ) {
                    GlassIconButton(
                        MaterialSymbols.AutoMirroredRounded.ArrowBack,
                        stringResource(MR.strings.action_bar_up_description),
                        onBack,
                    )
                    Spacer(Modifier.weight(1f))
                    if (isAo3) {
                        GlassIconButton(
                            MaterialSymbols.Rounded.Public,
                            stringResource(MR.strings.tomo_open_in_ao3),
                            onOpenWeb,
                        )
                    }
                    GlassIconButton(
                        MaterialSymbols.Rounded.Refresh,
                        stringResource(MR.strings.action_webview_refresh),
                        onRefresh,
                    )
                }
            }
            item(key = "head") {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                ) {
                    FanficBook(manga.title, manga.author, hue)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (isAo3) MR.strings.tomo_fanfic_badge_ao3 else MR.strings.tomo_type_text,
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tomo.mint,
                            modifier = Modifier
                                .background(Tomo.mint.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Text(
                            manga.title,
                            fontFamily = Literata,
                            fontSize = 24.sp,
                            lineHeight = 29.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        manga.author?.let {
                            Text(
                                stringResource(MR.strings.tomo_fanfic_by, it),
                                fontSize = 13.sp,
                                color = Color(0xFFC9CFDB),
                            )
                        }
                        meta?.fandoms?.firstOrNull()?.let { Text(it, fontSize = 12.sp, color = Tomo.textSecondary) }
                    }
                }
            }
            item(key = "stats") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                ) {
                    StatBox(
                        meta?.words?.takeIf {
                            it > 0
                        }?.let { wordsShort(it) } ?: "—",
                        stringResource(MR.strings.tomo_fanfic_stat_words),
                    )
                    // Local text works have no AO3 stats: all their chapters are out
                    val posted = meta?.chaptersPosted ?: chapters.size
                    val total = if (meta == null) chapters.size.toString() else meta.chaptersTotal?.toString() ?: "?"
                    StatBox("$posted/$total", stringResource(MR.strings.tomo_fanfic_stat_chapters))
                    StatBox(
                        stringResource(
                            if (meta?.complete != false) {
                                MR.strings.tomo_fanfic_status_complete
                            } else {
                                MR.strings.tomo_fanfic_status_wip
                            },
                        ),
                        stringResource(MR.strings.tomo_fanfic_stat_status),
                    )
                    StatBox(
                        meta?.updatedAt?.takeIf { it > 0 }?.let { relativeTimeSpanString(it) } ?: "—",
                        stringResource(MR.strings.tomo_fanfic_stat_updated),
                    )
                }
            }
            item(key = "actions") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .background(Tomo.text, CircleShape)
                                .clickable(enabled = chapters.isNotEmpty(), role = Role.Button, onClick = onRead),
                        ) {
                            Text(
                                text = nextChapter?.let {
                                    stringResource(MR.strings.tomo_read_chapter, formatChapterNumber(it.chapterNumber))
                                } ?: stringResource(MR.strings.tomo_action_keep_reading_short),
                                color = Tomo.night,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        FollowButton(followed = manga.favorite, onClick = onToggleFollow, height = 50)
                    }
                    Text(
                        text = stringResource(
                            if (manga.favorite) {
                                MR.strings.tomo_fanfic_following_help
                            } else {
                                MR.strings.tomo_fanfic_follow_help
                            },
                        ),
                        fontSize = 12.sp,
                        color = Tomo.textSecondary,
                    )
                }
            }
            if (meta != null || manga.genre.orEmpty().isNotEmpty()) {
                item(key = "tags") { TagsPanel(meta, manga.genre.orEmpty()) }
            }
            if (!manga.description.isNullOrBlank()) {
                item(key = "summary") {
                    var open by rememberSaveable { mutableStateOf(false) }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                    ) {
                        Text(
                            stringResource(MR.strings.tomo_fanfic_summary),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = manga.description.orEmpty(),
                            fontFamily = Literata,
                            fontStyle = FontStyle.Italic,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            color = Color(0xFFC9CFDB),
                            maxLines = if (open) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(if (open) MR.strings.tomo_less else MR.strings.tomo_more),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Tomo.gold,
                            modifier = Modifier
                                .clickable(role = Role.Button) { open = !open }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
            item(key = "chapters-title") {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                ) {
                    Text(
                        stringResource(MR.strings.chapters),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            MR.strings.tomo_fanfic_published,
                            chapters.size,
                            meta?.chaptersTotal?.toString() ?: "?",
                        ),
                        fontSize = 12.sp,
                        color = Tomo.textSecondary,
                    )
                }
            }
            itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) 20.dp else 0.dp,
                    topEnd = if (index == 0) 20.dp else 0.dp,
                    bottomStart = if (index == chapters.lastIndex) 20.dp else 0.dp,
                    bottomEnd = if (index == chapters.lastIndex) 20.dp else 0.dp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(horizontal = Tomo.screenPadding)
                        .fillMaxWidth()
                        .background(Tomo.glass, shape)
                        .clickable { onChapterClick(chapter) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    val dot = if (!chapter.read) Tomo.gold else Color.Transparent
                    Box(Modifier.size(8.dp).background(dot, CircleShape))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            chapter.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (chapter.read) Color(0xFFC9CFDB) else Tomo.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        chapter.dateUpload?.let { uploaded ->
                            Text(
                                text = relativeTimeSpanString(uploaded.toEpochMilliseconds()),
                                fontSize = 11.sp,
                                color = Tomo.textFaint,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The cover of a text work: a book in the work's colour with its title and author. */
@Composable
fun FanficBook(title: String, author: String?, hue: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    val glow = Color.hsv(hue, 0.5f, 0.7f)
    val paper = Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.70f), Color.hsv(hue, 0.65f, 0.25f)))
    Box(
        modifier = modifier
            .width(112.dp)
            .height(168.dp)
            .shadow(24.dp, shape, ambientColor = glow, spotColor = glow)
            .background(paper, shape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.25f)))
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
        ) {
            Text(
                text = title,
                fontFamily = Literata,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            if (author != null) {
                Text(
                    author,
                    fontSize = 10.sp,
                    color = Color(0xFFD9CFF0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatBox(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .glass(RoundedCornerShape(16.dp), border = Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 10.sp, color = Tomo.textSecondary, maxLines = 1)
    }
}

@Composable
private fun TagsPanel(meta: Ao3Meta?, tags: List<String>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(horizontal = Tomo.screenPadding)
            .fillMaxWidth()
            .glass(
                RoundedCornerShape(20.dp),
                fill = Color.White.copy(alpha = 0.06f),
                border = Color.White.copy(alpha = 0.12f),
            )
            .padding(14.dp),
    ) {
        if (meta != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                meta.rating?.let { Chip(ratingLabel(it), Tomo.mint, Tomo.mint.copy(alpha = 0.14f)) }
                meta.warnings.forEach { Chip(warningLabel(it), Tomo.text, Color.White.copy(alpha = 0.1f)) }
                meta.relationships.forEach { Chip(it, Tomo.gold, Tomo.gold.copy(alpha = 0.16f)) }
            }
        }
        if (tags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                tags.forEach { tag ->
                    Text(
                        tag,
                        fontSize = 11.sp,
                        color = Color(0xFFC9CFDB),
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, background: Color) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** AO3's English rating names in the app's language. */
@Composable
private fun ratingLabel(rating: String): String = when (rating) {
    "General Audiences" -> stringResource(MR.strings.tomo_rating_general)
    "Teen And Up Audiences" -> stringResource(MR.strings.tomo_rating_teen)
    "Mature" -> stringResource(MR.strings.tomo_rating_mature)
    "Explicit" -> stringResource(MR.strings.tomo_rating_explicit)
    "Not Rated" -> stringResource(MR.strings.tomo_rating_none)
    else -> rating
}

@Composable
private fun warningLabel(warning: String): String = when (warning) {
    "No Archive Warnings Apply" -> stringResource(MR.strings.tomo_fanfic_no_warnings)
    "Creator Chose Not To Use Archive Warnings" -> stringResource(MR.strings.tomo_warning_not_given)
    else -> warning
}

/** "48 mil" words, as the ficha shows them at a glance. */
@Composable
private fun wordsShort(words: Int): String =
    if (words < 1000) words.toString() else stringResource(MR.strings.tomo_thousands, words / 1000)
