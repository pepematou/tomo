package eu.kanade.presentation.reader.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.TomoBottomSheet
import eu.kanade.presentation.theme.Onest
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.TomoSwitch
import eu.kanade.tachiyomi.tomo.text.TextBlock
import eu.kanade.tachiyomi.tomo.text.TextReaderPreferences
import eu.kanade.tachiyomi.tomo.text.TextReaderPreferences.Font
import eu.kanade.tachiyomi.tomo.text.TextReaderPreferences.Theme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBack
import mihon.icons.materialsymbols.automirroredrounded.FormatListBulleted
import mihon.icons.materialsymbols.rounded.Tune
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

private const val WORDS_PER_MINUTE = 230

private data class Palette(val background: Color, val text: Color, val muted: Color, val bar: Color)

private fun Theme.palette() = when (this) {
    Theme.LIGHT -> Palette(Color(0xFFFAF8F3), Color(0xFF1C1B1A), Color(0xFF6B675F), Color(0xF2FFFFFF))
    Theme.SEPIA -> Palette(Color(0xFFF4ECD8), Color(0xFF4A3B2C), Color(0xFF7A6650), Color(0xF2EFE4CB))
    Theme.DARK -> Palette(Tomo.night, Color(0xFFE6E8EE), Tomo.textSecondary, Color(0xF21E2230))
    Theme.BLACK -> Palette(Color.Black, Color(0xFFD6D6D6), Color(0xFF9A9A9A), Color(0xF2141414))
}

private fun Font.family() = when (this) {
    Font.SERIF -> FontFamily.Serif
    Font.SANS -> Onest
    Font.MONO -> FontFamily.Monospace
}

/** Text chapter reader: continuous or paged, with typeface, size, spacing, margins and theme of the user's choice. */
@Composable
fun TextReaderContent(
    title: String,
    chapterName: String,
    blocks: List<TextBlock>,
    startIndex: Int,
    preferences: TextReaderPreferences,
    onPosition: (index: Int, atEnd: Boolean) -> Unit,
    onLeave: (index: Int, atEnd: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val font by preferences.font.collectAsState()
    val fontSize by preferences.fontSize.collectAsState()
    val lineHeight by preferences.lineHeight.collectAsState()
    val margin by preferences.margin.collectAsState()
    val justify by preferences.justify.collectAsState()
    val theme by preferences.theme.collectAsState()
    val paged by preferences.paged.collectAsState()
    val palette = theme.palette()

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val scope = rememberCoroutineScope()
    var showBar by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }

    // Words before each block, to tell the progress and the reading time left
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

    val baseStyle = TextStyle(
        fontFamily = font.family(),
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineHeight / 10f).sp,
        color = palette.text,
        textAlign = if (justify) TextAlign.Justify else TextAlign.Start,
    )
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        val pageHeight = with(density) { (maxHeight * 0.9f).toPx() }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = margin.dp, vertical = 72.dp),
            verticalArrangement = Arrangement.spacedBy((fontSize * 0.8f).dp),
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                // About 65 characters a line, so long lines stay comfortable on tablets
                .widthIn(max = (fontSize * 36).dp)
                .pointerInput(paged) {
                    detectTapGestures { offset ->
                        val third = size.width / 3
                        when {
                            paged && offset.x < third -> scope.launch { listState.animateScrollBy(-pageHeight) }
                            paged && offset.x > third * 2 -> scope.launch { listState.animateScrollBy(pageHeight) }
                            else -> showBar = !showBar
                        }
                    }
                },
        ) {
            itemsIndexed(blocks) { _, block -> Block(block, baseStyle, palette) }
        }

        AnimatedVisibility(
            visible = showBar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bar)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        MaterialSymbols.AutoMirroredRounded.ArrowBack,
                        stringResource(MR.strings.action_bar_up_description),
                        tint = palette.text,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = palette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        chapterName,
                        color = palette.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { sheet = "toc" }) {
                    Icon(
                        MaterialSymbols.AutoMirroredRounded.FormatListBulleted,
                        stringResource(MR.strings.tomo_text_contents),
                        tint = palette.text,
                    )
                }
                IconButton(onClick = { sheet = "style" }) {
                    Icon(MaterialSymbols.Rounded.Tune, stringResource(MR.strings.tomo_text_style), tint = palette.text)
                }
            }
        }

        val index = listState.firstVisibleItemIndex.coerceIn(0, wordsBefore.lastIndex)
        val percent = if (!listState.canScrollForward) 100 else wordsBefore[index] * 100 / totalWords
        val minutesLeft = (totalWords - wordsBefore[index]) / WORDS_PER_MINUTE
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (showBar) Modifier.background(palette.bar) else Modifier)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("$percent %", color = palette.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (minutesLeft > 0) {
                Text(
                    stringResource(MR.strings.tomo_text_minutes_left, minutesLeft),
                    color = palette.muted,
                    fontSize = 12.sp,
                )
            }
        }
    }

    when (sheet) {
        "style" -> TomoBottomSheet(onDismissRequest = { sheet = null }) { StyleSheet(preferences) }
        "toc" -> TomoBottomSheet(onDismissRequest = { sheet = null }) {
            Text(stringResource(MR.strings.tomo_text_contents), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val headings = blocks.withIndex().filter { it.value.kind == TextBlock.Kind.HEADING }
            if (headings.isEmpty()) {
                Text(stringResource(MR.strings.tomo_text_no_contents), color = Tomo.textSecondary, fontSize = 14.sp)
            }
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                itemsIndexed(headings) { _, (blockIndex, block) ->
                    Text(
                        text = block.text,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sheet = null
                                showBar = false
                                scope.launch { listState.scrollToItem(blockIndex) }
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Block(block: TextBlock, style: TextStyle, palette: Palette) {
    when (block.kind) {
        TextBlock.Kind.BREAK -> Text(
            text = "⁂",
            style = style.copy(textAlign = TextAlign.Center, color = palette.muted),
            modifier = Modifier.fillMaxWidth(),
        )
        TextBlock.Kind.HEADING -> Text(
            text = block.annotated(),
            style = style.copy(
                fontSize = style.fontSize * 1.3f,
                lineHeight = style.lineHeight * 1.2f,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
            ),
            modifier = Modifier.padding(top = 16.dp),
        )
        TextBlock.Kind.QUOTE -> Text(
            text = block.annotated(),
            style = style.copy(fontStyle = FontStyle.Italic, color = palette.muted),
            modifier = Modifier.padding(start = 20.dp),
        )
        TextBlock.Kind.PARAGRAPH -> Text(text = block.annotated(), style = style)
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
private fun StyleSheet(preferences: TextReaderPreferences) {
    val font by preferences.font.collectAsState()
    val fontSize by preferences.fontSize.collectAsState()
    val lineHeight by preferences.lineHeight.collectAsState()
    val margin by preferences.margin.collectAsState()
    val justify by preferences.justify.collectAsState()
    val theme by preferences.theme.collectAsState()
    val paged by preferences.paged.collectAsState()

    Text(stringResource(MR.strings.tomo_text_style), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Segmented(
        options = listOf(Font.SERIF to "Serif", Font.SANS to "Sans", Font.MONO to "Mono"),
        selected = font,
        onSelect = preferences.font::set,
    )
    Stepper(stringResource(MR.strings.tomo_text_size), "$fontSize", fontSize > 14, fontSize < 28) {
        preferences.fontSize.set((fontSize + it).coerceIn(14, 28))
    }
    Stepper(
        stringResource(MR.strings.tomo_text_line_height),
        "%.1f".format(lineHeight / 10f),
        lineHeight > 13,
        lineHeight < 20,
    ) {
        preferences.lineHeight.set((lineHeight + it).coerceIn(13, 20))
    }
    Stepper(stringResource(MR.strings.tomo_text_margins), "$margin", margin > 8, margin < 48) {
        preferences.margin.set((margin + it * 4).coerceIn(8, 48))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Theme.entries.forEach { option ->
            val colors = option.palette()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.background, CircleShape)
                    .border(
                        if (option ==
                            theme
                        ) {
                            2.dp
                        } else {
                            1.dp
                        },
                        if (option == theme) Tomo.gold else Tomo.glassBorder,
                        CircleShape,
                    )
                    .selectable(selected = option == theme, role = Role.RadioButton) { preferences.theme.set(option) },
            ) {
                Text("Aa", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    SwitchRow(stringResource(MR.strings.tomo_text_justify), justify, preferences.justify::set)
    SwitchRow(stringResource(MR.strings.tomo_text_paged), paged, preferences.paged::set)
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tomo.glass, CircleShape)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(if (isSelected) Tomo.text else Color.Transparent, CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(value) },
            ) {
                Text(
                    label,
                    color = if (isSelected) Tomo.night else Tomo.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, canDecrease: Boolean, canIncrease: Boolean, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        StepButton("−", canDecrease) { onStep(-1) }
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        StepButton("+", canIncrease) { onStep(1) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .background(Tomo.glassStrong, RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(symbol, fontSize = 20.sp, color = if (enabled) Tomo.text else Tomo.textFaint)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TomoSwitch(checked = checked)
    }
}
