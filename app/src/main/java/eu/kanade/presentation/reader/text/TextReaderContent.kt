package eu.kanade.presentation.reader.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.TomoBottomSheet
import eu.kanade.presentation.theme.Literata
import eu.kanade.presentation.theme.Onest
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoSwitch
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.tomo.text.TextBlock
import eu.kanade.tachiyomi.tomo.text.TextReaderPreferences
import eu.kanade.tachiyomi.tomo.text.TextReaderPreferences.Font
import eu.kanade.tachiyomi.tomo.text.TextReaderPreferences.Spacing
import eu.kanade.tachiyomi.tomo.text.TextReaderPreferences.Theme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBack
import mihon.icons.materialsymbols.rounded.Bookmark
import mihon.icons.materialsymbols.rounded.BookmarkAdd
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

private const val WORDS_PER_MINUTE = 230

private data class Palette(
    val background: Color,
    val text: Color,
    val muted: Color,
    val panel: Color,
    val border: Color,
    val chip: Color,
    val note: Color,
    val accent: Color,
)

private fun Theme.palette() = when (this) {
    Theme.LIGHT -> Palette(
        background = Color(0xFFF7F7F4),
        text = Color(0xFF1E1F24),
        muted = Color(0xFF5E6270),
        panel = Color(0xD1FFFFFF),
        border = Color(0x14000000),
        chip = Color(0x0F000000),
        note = Color(0x0D000000),
        accent = Color(0xFF1E1F24),
    )
    Theme.SEPIA -> Palette(
        background = Color(0xFFF1E6D2),
        text = Color(0xFF43341F),
        muted = Color(0xFF6E5C44),
        panel = Color(0xDBFAF2E4),
        border = Color(0x1F43341F),
        chip = Color(0x1443341F),
        note = Color(0x1243341F),
        accent = Color(0xFF8A5A1E),
    )
    Theme.DARK -> Palette(
        background = Color(0xFF141A26),
        text = Color(0xFFDCE0E8),
        muted = Color(0xFFA3ABBD),
        panel = Color(0xD1242A3A),
        border = Color(0x24FFFFFF),
        chip = Color(0x1AFFFFFF),
        note = Color(0x0FFFFFFF),
        accent = Tomo.gold,
    )
    Theme.BLACK -> Palette(
        background = Color.Black,
        text = Color(0xFFC9CDD6),
        muted = Color(0xFF8A93A6),
        panel = Color(0xDB1C1E24),
        border = Color(0x1FFFFFFF),
        chip = Color(0x1AFFFFFF),
        note = Color(0x0FFFFFFF),
        accent = Tomo.gold,
    )
}

private fun Font.family(): FontFamily = when (this) {
    Font.SERIF -> Literata
    Font.SANS -> Onest
    Font.MONO -> FontFamily.Monospace
}

private val chapterTitlePrefix = Regex("""^(?:Chapter|Capítulo|Cap\.)\s*\d+\s*[:.\-–—]?\s*""", RegexOption.IGNORE_CASE)

/** Text reader of the design (mockup "Lector de texto"): one chapter at a time, with floating controls. */
@Composable
fun TextReaderContent(
    title: String,
    chapter: Chapter,
    chapterIndex: Int,
    chapters: List<Chapter>,
    blocks: List<TextBlock>,
    startIndex: Int,
    bookmarked: Boolean,
    preferences: TextReaderPreferences,
    onPosition: (index: Int, atEnd: Boolean) -> Unit,
    onLeave: (index: Int, atEnd: Boolean) -> Unit,
    onMove: (offset: Int) -> Unit,
    onOpenChapter: (Long) -> Unit,
    onToggleBookmark: () -> Unit,
    onBack: () -> Unit,
) {
    val font by preferences.font.collectAsState()
    val fontSize by preferences.fontSize.collectAsState()
    val spacing by preferences.spacing.collectAsState()
    val margin by preferences.margin.collectAsState()
    val justify by preferences.justify.collectAsState()
    val theme by preferences.theme.collectAsState()
    val paged by preferences.paged.collectAsState()
    val palette = theme.palette()

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val scope = rememberCoroutineScope()
    var showControls by rememberSaveable { mutableStateOf(false) }
    var showStyle by rememberSaveable { mutableStateOf(false) }
    var showIndex by rememberSaveable { mutableStateOf(false) }
    var openNotes by remember { mutableStateOf(emptySet<Int>()) }

    // The chapter's own heading becomes the "Capítulo 14 / El deshielo" header instead of a plain block
    val firstHeading = blocks.firstOrNull()?.takeIf { it.kind == TextBlock.Kind.HEADING }
    val chapterTitle = firstHeading?.text?.replace(chapterTitlePrefix, "")?.ifBlank { null } ?: chapter.name
    val wordsBefore = remember(blocks) {
        blocks.runningFold(0) { total, block -> total + block.text.split(' ').count { it.isNotBlank() } }
    }
    val totalWords = wordsBefore.last().coerceAtLeast(1)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to !listState.canScrollForward }
            .distinctUntilChanged()
            .collect { (index, atEnd) -> onPosition(index, atEnd) }
    }
    DisposableEffect(Unit) {
        onDispose { onLeave(listState.firstVisibleItemIndex, !listState.canScrollForward) }
    }

    val textStyle = TextStyle(
        fontFamily = font.family(),
        fontSize = fontSize.sp,
        lineHeight = (fontSize * spacing.lineHeight).sp,
        color = palette.text,
        textAlign = if (justify) TextAlign.Justify else TextAlign.Start,
    )
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        val pageHeight = with(density) { (maxHeight * 0.88f).toPx() }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = margin.dp, end = margin.dp, top = 104.dp, bottom = 220.dp),
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                // About 65 characters a line, so lines stay comfortable on tablets
                .widthIn(max = (fontSize * 36).dp)
                .pointerInput(paged) {
                    detectTapGestures { offset ->
                        val third = size.width / 3
                        when {
                            paged && offset.x < third -> scope.launch { listState.animateScrollBy(-pageHeight) }
                            paged && offset.x > third * 2 -> scope.launch { listState.animateScrollBy(pageHeight) }
                            else -> showControls = !showControls
                        }
                    }
                },
        ) {
            if (firstHeading == null) {
                item { ChapterHeader(chapter, chapter.name, palette) }
            }
            itemsIndexed(blocks) { index, block ->
                when {
                    index == 0 && firstHeading != null -> ChapterHeader(chapter, chapterTitle, palette)
                    block.kind == TextBlock.Kind.NOTES -> Notes(
                        text = block.text,
                        open = index in openNotes,
                        palette = palette,
                        onToggle = { openNotes = if (index in openNotes) openNotes - index else openNotes + index },
                    )
                    else -> {
                        val previous = blocks.getOrNull(index - 1)?.kind
                        val indent = block.kind == TextBlock.Kind.PARAGRAPH && previous == TextBlock.Kind.PARAGRAPH
                        Block(block, textStyle, palette, indent)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopPill(
                title = title,
                position = stringResource(MR.strings.tomo_text_chapter_of, chapterIndex + 1, chapters.size),
                bookmarked = bookmarked,
                palette = palette,
                onBack = onBack,
                onToggleBookmark = onToggleBookmark,
            )
        }

        val index = listState.firstVisibleItemIndex.coerceIn(0, wordsBefore.lastIndex)
        val percent = if (!listState.canScrollForward) 100 else wordsBefore[index] * 100 / totalWords
        val minutesLeft = (totalWords - wordsBefore[index]) / WORDS_PER_MINUTE
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .fillMaxWidth()
                    .background(palette.panel, RoundedCornerShape(28.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(28.dp))
                    .padding(16.dp),
            ) {
                if (showStyle) StylePanel(preferences, palette)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(palette.chip, CircleShape),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(percent / 100f)
                                .height(4.dp)
                                .background(palette.accent, CircleShape),
                        )
                    }
                    Text(
                        text = if (minutesLeft > 0) {
                            stringResource(MR.strings.tomo_text_progress, percent, minutesLeft)
                        } else {
                            stringResource(MR.strings.tomo_text_progress_done, percent)
                        },
                        color = palette.muted,
                        fontSize = 12.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PanelButton(stringResource(MR.strings.tomo_text_previous), palette, enabled = chapterIndex > 0) {
                        onMove(-1)
                    }
                    PanelButton(stringResource(MR.strings.tomo_text_contents), palette) { showIndex = true }
                    PanelButton("Aa", palette, selected = showStyle, serif = true) { showStyle = !showStyle }
                    PanelButton(
                        stringResource(MR.strings.tomo_text_next),
                        palette,
                        enabled = chapterIndex < chapters.lastIndex,
                    ) { onMove(1) }
                }
            }
        }
    }

    if (showIndex) {
        TomoBottomSheet(onDismissRequest = { showIndex = false }) {
            Text(stringResource(MR.strings.tomo_text_contents), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val headings = blocks.withIndex()
                .filter { it.value.kind == TextBlock.Kind.HEADING }
                .drop(if (firstHeading != null) 1 else 0)
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                if (chapters.size > 1) {
                    itemsIndexed(chapters) { position, item ->
                        IndexRow(item.name, current = position == chapterIndex) {
                            showIndex = false
                            if (position != chapterIndex) onOpenChapter(item.id)
                        }
                    }
                }
                itemsIndexed(headings) { _, (blockIndex, block) ->
                    IndexRow(block.text, current = false) {
                        showIndex = false
                        showControls = false
                        scope.launch { listState.scrollToItem(blockIndex) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPill(
    title: String,
    position: String,
    bookmarked: Boolean,
    palette: Palette,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .background(palette.panel, CircleShape)
            .border(1.dp, palette.border, CircleShape)
            .padding(horizontal = 6.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = MaterialSymbols.AutoMirroredRounded.ArrowBack,
                contentDescription = stringResource(MR.strings.action_bar_up_description),
                tint = palette.text,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = palette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(position, color = palette.muted, fontSize = 11.sp)
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = if (bookmarked) MaterialSymbols.Rounded.Bookmark else MaterialSymbols.Rounded.BookmarkAdd,
                contentDescription = stringResource(MR.strings.action_bookmark),
                tint = if (bookmarked) palette.accent else palette.text,
            )
        }
    }
}

@Composable
private fun ChapterHeader(chapter: Chapter, title: String, palette: Palette) {
    Column(modifier = Modifier.padding(bottom = 18.dp)) {
        if (chapter.chapterNumber >= 0) {
            Text(
                text = stringResource(MR.strings.tomo_text_chapter_label, formatChapterNumber(chapter.chapterNumber)),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                color = palette.muted,
            )
        }
        Text(
            text = title,
            fontFamily = Literata,
            fontSize = 27.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Author's notes, folded so they don't interrupt the story (RF-31). */
@Composable
private fun Notes(text: String, open: Boolean, palette: Palette, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(bottom = 22.dp)
            .fillMaxWidth()
            .background(palette.note, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(MR.strings.tomo_text_notes),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            if (!open) {
                Text(" · " + stringResource(MR.strings.tomo_text_notes_folded), fontSize = 13.sp, color = palette.muted)
            }
        }
        Text(
            text = if (open) text else stringResource(MR.strings.tomo_text_notes_hint),
            fontSize = if (open) 14.sp else 12.sp,
            lineHeight = if (open) 21.sp else 16.sp,
            color = if (open) palette.text else palette.muted,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun Block(block: TextBlock, style: TextStyle, palette: Palette, indent: Boolean) {
    val spacing = Modifier.padding(bottom = (style.fontSize.value * 0.9f).dp)
    when (block.kind) {
        TextBlock.Kind.BREAK -> Text(
            text = "⁂",
            style = style.copy(textAlign = TextAlign.Center, color = palette.muted),
            modifier = spacing.fillMaxWidth(),
        )
        TextBlock.Kind.HEADING -> Text(
            text = block.annotated(),
            style = style.copy(
                fontSize = style.fontSize * 1.25f,
                lineHeight = style.lineHeight * 1.15f,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
            ),
            modifier = spacing.padding(top = 12.dp),
        )
        TextBlock.Kind.QUOTE, TextBlock.Kind.NOTES -> Text(
            text = block.annotated(),
            style = style.copy(fontStyle = FontStyle.Italic, color = palette.muted),
            modifier = spacing.padding(start = 20.dp),
        )
        TextBlock.Kind.PARAGRAPH -> Text(
            text = block.annotated(),
            style = if (indent) style.copy(textIndent = TextIndent(firstLine = 1.2.em)) else style,
            modifier = spacing,
        )
    }
}

private fun TextBlock.annotated(): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { span ->
        addStyle(
            SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
            ),
            span.start,
            span.end,
        )
    }
}

@Composable
private fun RowScope.PanelButton(
    label: String,
    palette: Palette,
    enabled: Boolean = true,
    selected: Boolean = false,
    serif: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .background(if (selected) palette.text else palette.chip, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = if (serif) Literata else null,
            fontSize = if (serif) 16.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                selected -> palette.background
                enabled -> palette.text
                else -> palette.muted
            },
        )
    }
}

@Composable
private fun StylePanel(preferences: TextReaderPreferences, palette: Palette) {
    val fontSize by preferences.fontSize.collectAsState()
    val spacing by preferences.spacing.collectAsState()
    val paged by preferences.paged.collectAsState()
    var more by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ThemePicker(preferences, palette)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Label(stringResource(MR.strings.tomo_text_size), palette, Modifier.weight(1f))
            SizeButton(14, palette, fontSize > 14) { preferences.fontSize.set((fontSize - 1).coerceAtLeast(14)) }
            Value("$fontSize", palette)
            SizeButton(20, palette, fontSize < 26) { preferences.fontSize.set((fontSize + 1).coerceAtMost(26)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(MR.strings.tomo_text_line_height), palette, Modifier.weight(1f))
            Segmented(
                options = listOf(
                    Spacing.COMPACT to stringResource(MR.strings.tomo_text_spacing_compact),
                    Spacing.NORMAL to stringResource(MR.strings.tomo_text_spacing_normal),
                    Spacing.WIDE to stringResource(MR.strings.tomo_text_spacing_wide),
                ),
                selected = spacing,
                palette = palette,
                onSelect = preferences.spacing::set,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(MR.strings.tomo_text_mode), palette, Modifier.weight(1f))
            Segmented(
                options = listOf(
                    true to stringResource(MR.strings.tomo_text_mode_paged),
                    false to stringResource(MR.strings.tomo_text_mode_scroll),
                ),
                selected = paged,
                palette = palette,
                onSelect = preferences.paged::set,
            )
        }
        Text(
            text = stringResource(if (more) MR.strings.tomo_less else MR.strings.tomo_more),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.accent,
            modifier = Modifier.clickable(role = Role.Button) { more = !more },
        )
        if (more) MoreStyle(preferences, palette)
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.border),
        )
    }
}

@Composable
private fun ThemePicker(preferences: TextReaderPreferences, palette: Palette) {
    val theme by preferences.theme.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Theme.LIGHT to MR.strings.tomo_text_theme_light,
            Theme.SEPIA to MR.strings.tomo_text_theme_sepia,
            Theme.DARK to MR.strings.tomo_text_theme_dark,
            Theme.BLACK to MR.strings.tomo_text_theme_black,
        ).forEach { (option, label) ->
            val colors = option.palette()
            val selected = option == theme
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton) { preferences.theme.set(option) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.background, CircleShape)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) palette.accent else palette.border,
                            CircleShape,
                        ),
                ) {
                    Text(
                        "Aa",
                        fontFamily = Literata,
                        color = colors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(stringResource(label), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            }
        }
    }
}

@Composable
private fun MoreStyle(preferences: TextReaderPreferences, palette: Palette) {
    val font by preferences.font.collectAsState()
    val justify by preferences.justify.collectAsState()
    val margin by preferences.margin.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label(stringResource(MR.strings.tomo_text_font), palette, Modifier.weight(1f))
        Segmented(
            options = listOf(Font.SERIF to "Literata", Font.SANS to "Onest", Font.MONO to "Mono"),
            selected = font,
            palette = palette,
            onSelect = preferences.font::set,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(stringResource(MR.strings.tomo_text_margins), palette, Modifier.weight(1f))
        SizeButton(14, palette, margin > 8, symbol = "−") { preferences.margin.set((margin - 4).coerceAtLeast(8)) }
        Value("$margin", palette)
        SizeButton(14, palette, margin < 48, symbol = "+") { preferences.margin.set((margin + 4).coerceAtMost(48)) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.toggleable(value = justify, role = Role.Switch, onValueChange = preferences.justify::set),
    ) {
        Label(stringResource(MR.strings.tomo_text_justify), palette, Modifier.weight(1f))
        TomoSwitch(checked = justify)
    }
}

@Composable
private fun Label(text: String, palette: Palette, modifier: Modifier = Modifier) {
    Text(text, fontSize = 14.sp, color = palette.text, modifier = modifier)
}

@Composable
private fun Value(text: String, palette: Palette) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = palette.text,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = 34.dp),
    )
}

@Composable
private fun SizeButton(size: Int, palette: Palette, enabled: Boolean, symbol: String = "A", onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 44.dp, height = 40.dp)
            .background(palette.chip, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = symbol,
            fontFamily = Literata,
            fontSize = size.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) palette.text else palette.muted,
        )
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, palette: Palette, onSelect: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(palette.chip, CircleShape)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(34.dp)
                    .background(if (isSelected) palette.text else Color.Transparent, CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) palette.background else palette.text,
                )
            }
        }
    }
}

@Composable
private fun IndexRow(text: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        color = if (current) Tomo.gold else Tomo.text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
