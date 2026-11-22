package tomo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.tomo.ffn.matchFandoms
import eu.kanade.tachiyomi.tomo.text.Fandom
import eu.kanade.tachiyomi.tomo.text.FanficSearch
import eu.kanade.tachiyomi.tomo.text.FanficWork
import eu.kanade.tachiyomi.tomo.text.TextBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser

private val Night = Color(0xFF080B14)
private val Glass = Color.White.copy(alpha = 0.07f)
private val Gold = Color(0xFFF2C46D)
private val Muted = Color(0xFFA3ABBD)
private val Amber = Color(0xFFFFB45C)
private val ReadingText = Color(0xFFE8E3D6)

@Composable
fun TomoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Night,
            surface = Night,
            primary = Gold,
            onPrimary = Night,
            onBackground = Color(0xFFF4F6FB),
            onSurface = Color(0xFFF4F6FB),
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Night)) { content() }
    }
}

class SettingsHolder(initial: Settings) {
    var settings by mutableStateOf(initial)
        private set

    fun update(change: (Settings) -> Settings) {
        settings = change(settings)
        SettingsStore.save(settings)
    }
}

private sealed interface Screen {
    data object Library : Screen
    data object Search : Screen
    data class Story(val url: String) : Screen
    data class Reader(val storyUrl: String, val title: String, val chapter: Int) : Screen
}

@Composable
fun App(store: SettingsHolder) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Library) }
    val screen = stack.last()
    fun open(next: Screen) = stack.add(next)
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                event.isCtrlPressed && event.key == Key.F -> {
                    if (screen != Screen.Search) open(Screen.Search)
                    true
                }
                event.key == Key.Escape && stack.size > 1 -> {
                    back()
                    true
                }
                else -> false
            }
        },
    ) {
        if (screen !is Screen.Reader) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                if (stack.size > 1) TextButton(onClick = ::back) { Text("← Atrás") }
                Text("Tomo", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = screen == Screen.Library,
                    onClick = { stack.retainAll { it == Screen.Library } },
                    label = { Text("Biblioteca") },
                )
                FilterChip(
                    selected = screen == Screen.Search,
                    onClick = { if (screen != Screen.Search) open(Screen.Search) },
                    label = { Text("Fanfics (Ctrl+F)") },
                )
            }
        }
        when (screen) {
            Screen.Library -> LibraryScreen(store) { entry ->
                val chapter = store.settings.progress[entry.url] ?: entry.nextChapter ?: 1
                open(Screen.Reader(entry.url, entry.title, chapter))
            }
            Screen.Search -> SearchScreen { open(Screen.Story(it.url)) }
            is Screen.Story -> StoryScreen(screen.url) { title, chapter ->
                open(Screen.Reader(screen.url, title, chapter))
            }
            is Screen.Reader -> ReaderScreen(
                screen = screen,
                onChapter = { number ->
                    store.update { it.copy(progress = it.progress + (screen.storyUrl to number)) }
                    stack[stack.lastIndex] = screen.copy(chapter = number)
                },
                onBack = ::back,
                onOpened = { store.update { it.copy(progress = it.progress + (screen.storyUrl to screen.chapter)) } },
            )
        }
    }
}

/** Loads something from the network once per key, with the error said in words and a retry. */
@Composable
private fun <T> Loaded(key: Any, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var result by remember(key) { mutableStateOf<Result<T>?>(null) }
    var attempt by remember(key) { mutableStateOf(0) }
    LaunchedEffect(key, attempt) {
        result = null
        result = try {
            Result.success(load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val current = result
    when {
        current == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold)
        }
        current.isFailure -> ErrorPanel(current.exceptionOrNull()) { attempt++ }
        else -> content(current.getOrThrow())
    }
}

@Composable
private fun ErrorPanel(error: Throwable?, onRetry: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (error is VerificationNeeded) {
                "FanFiction.net pide verificación. Abre fanfiction.net en tu navegador, espera unos minutos y " +
                    "vuelve a intentar."
            } else {
                "No se pudo conectar con FanFiction.net (${error?.message ?: "error"})."
            },
            color = Amber,
        )
        OutlinedButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun LibraryScreen(store: SettingsHolder, onRead: (LibraryEntry) -> Unit) {
    val folder = store.settings.syncFolder
    val choose = {
        val chooser = JFileChooser(folder).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Carpeta de sincronización"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            store.update { it.copy(syncFolder = chooser.selectedFile.absolutePath) }
        }
    }
    if (folder == null) {
        Column(Modifier.padding(24.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Elige la carpeta que Syncthing comparte con tu celular", fontSize = 20.sp)
            Text(
                "Es la misma que pusiste en Tomo para Android, en Ajustes → Datos y almacenamiento → Sincronizar " +
                    "entre aparatos. Aquí aparecerá tu biblioteca.",
                color = Muted,
            )
            Button(onClick = choose) { Text("Elegir carpeta") }
        }
        return
    }
    val file = remember(folder) { newestSyncFile(File(folder)) }
    val library = remember(file?.lastModified()) {
        file?.let { runCatching { libraryOf(decodeSyncBackup(it.readBytes())) }.getOrNull() }
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(folder, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = choose) { Text("Cambiar carpeta") }
        }
        if (library == null) {
            Text(
                "Todavía no hay biblioteca en esa carpeta. Abre y cierra Tomo en el celular para que la deje ahí.",
                color = Amber,
                modifier = Modifier.padding(24.dp),
            )
            return
        }
        val (fanfics, manga) = library.partition { it.isFanfic }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (fanfics.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Fanfics") }
                items(fanfics, key = { "f" + it.url }) { entry ->
                    Cover(entry, store.settings.progress[entry.url]) { onRead(entry) }
                }
            }
            if (manga.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle("Manga")
                        Text("Por ahora el manga se lee en el celular o la tableta.", color = Muted, fontSize = 13.sp)
                    }
                }
                items(manga, key = { "m" + it.url }) { entry -> Cover(entry, null, onClick = null) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Cover(entry: LibraryEntry, readHere: Int?, onClick: (() -> Unit)?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Glass),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.cover != null) {
                AsyncImage(
                    model = entry.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(entry.title, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
            }
        }
        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        val progress = buildString {
            append("${entry.chaptersRead} de ${entry.chapters} cap.")
            if (readHere != null) append(" · aquí: cap. $readHere")
        }
        Text(progress, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SearchScreen(onOpen: (FanficWork) -> Unit) {
    var query by remember { mutableStateOf("") }
    var fandom by remember { mutableStateOf<Fandom?>(null) }
    var fandoms by remember { mutableStateOf<List<Fandom>>(emptyList()) }
    val works = remember { mutableStateListOf<FanficWork>() }
    var page by remember { mutableStateOf(1) }
    var hasNext by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun load(reset: Boolean) {
        val next = if (reset) 1 else page + 1
        loading = true
        error = null
        scope.launch {
            try {
                val search = FanficSearch(query = if (fandom == null) query else "", fandom = fandom?.filter.orEmpty())
                val result = Ffn.search(search, next)
                if (reset) works.clear()
                works += result.works
                page = next
                hasNext = result.hasNextPage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(reset = true) }
    LaunchedEffect(query) {
        if (query.trim().length >= 2 && fandoms.isEmpty()) {
            runCatching { fandoms = Ffn.fandoms() }
        }
    }
    val suggestions = remember(query, fandoms) {
        if (query.trim().length >=
            2
        ) {
            matchFandoms(fandoms, query)
        } else {
            emptyList()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 820.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Busca un fanfic o una serie (Enter para buscar)") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        fandom = null
                        load(reset = true)
                        true
                    } else {
                        false
                    }
                },
            )
        }
        if (suggestions.isNotEmpty() || fandom != null) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fandom?.let { chosen ->
                        FilterChip(
                            selected = true,
                            onClick = {
                                fandom = null
                                load(reset = true)
                            },
                            label = { Text("Serie: ${chosen.name} ✕") },
                        )
                    }
                    suggestions.forEach { suggestion ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                fandom = suggestion
                                query = ""
                                load(reset = true)
                            },
                            label = {
                                Text(
                                    listOfNotNull(
                                        suggestion.name,
                                        suggestion.kind,
                                        suggestion.stories,
                                    ).joinToString(" · "),
                                )
                            },
                        )
                    }
                }
            }
        }
        error?.let { item { ErrorPanel(it) { load(reset = works.isEmpty()) } } }
        items(works, key = { it.url }) { work -> WorkRow(work) { onOpen(work) } }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Gold)
                }
            }
        } else if (hasNext) {
            item { OutlinedButton(onClick = { load(reset = false) }) { Text("Más resultados") } }
        } else if (works.isEmpty() && error == null) {
            item { Text("Sin resultados en español.", color = Muted) }
        }
    }
}

@Composable
private fun WorkRow(work: FanficWork, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Glass)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(work.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(work.author, color = Muted, fontSize = 13.sp)
        if (work.summary.isNotBlank()) {
            Text(
                work.summary,
                fontSize = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(statsLine(work), color = Muted, fontSize = 12.sp)
    }
}

private fun statsLine(work: FanficWork): String = listOfNotNull(
    work.meta.fandoms.firstOrNull(),
    "%,d palabras".format(work.meta.words).takeIf { work.meta.words > 0 },
    "${work.meta.chaptersPosted} cap.",
    if (work.meta.complete) "Completo" else "En curso",
    work.tags.joinToString("/").ifEmpty { null },
).joinToString(" · ")

@Composable
private fun StoryScreen(url: String, onRead: (title: String, chapter: Int) -> Unit) {
    Loaded(url, { Ffn.story(url) }) { story ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 820.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(story.work.title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(story.work.author, color = Muted)
                    Text(story.work.summary)
                    Text(statsLine(story.work), color = Muted, fontSize = 13.sp)
                    Button(
                        onClick = { onRead(story.work.title, 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night),
                    ) { Text("Leer desde el principio") }
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(story.chapters, key = { it.url }) { chapter ->
                Text(
                    chapter.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onRead(story.work.title, chapter.number) }
                        .padding(12.dp),
                )
            }
        }
    }
}

/** A chapter in a centered column of about 65 characters a line, driven by the keyboard (RF-22). */
@Composable
private fun ReaderScreen(
    screen: Screen.Reader,
    onChapter: (Int) -> Unit,
    onBack: () -> Unit,
    onOpened: () -> Unit,
) {
    LaunchedEffect(screen) { onOpened() }
    val chapters = remember(screen.storyUrl) { mutableStateOf<Int?>(null) }
    LaunchedEffect(screen.storyUrl) {
        runCatching { chapters.value = Ffn.story(screen.storyUrl).chapters.size }
    }
    val total = chapters.value
    val hasNext = total == null || screen.chapter < total

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            TextButton(onClick = onBack) { Text("← Atrás (Esc)") }
            Text(
                screen.title + " · cap. ${screen.chapter}" + (total?.let { " de $it" } ?: ""),
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onChapter(screen.chapter - 1) }, enabled = screen.chapter > 1) { Text("← Anterior") }
            TextButton(onClick = { onChapter(screen.chapter + 1) }, enabled = hasNext) { Text("Siguiente →") }
        }
        Loaded(screen, { Ffn.chapter(screen.storyUrl, screen.chapter) }) { blocks ->
            ChapterText(
                blocks = blocks,
                onPrevious = { if (screen.chapter > 1) onChapter(screen.chapter - 1) },
                onNext = { if (hasNext) onChapter(screen.chapter + 1) },
            )
        }
    }
}

@Composable
private fun ChapterText(blocks: List<TextBlock>, onPrevious: () -> Unit, onNext: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(blocks) { focus.requestFocus() }
    val style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 31.sp, color = ReadingText)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val page = with(LocalDensity.current) { (maxHeight * 0.88f).toPx() }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 24.dp, bottom = 160.dp, start = 24.dp, end = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .width(680.dp)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val scroll: Float? = when (event.key) {
                        Key.DirectionDown, Key.PageDown -> page
                        Key.DirectionUp, Key.PageUp -> -page
                        Key.Spacebar -> if (event.isShiftPressed) -page else page
                        else -> null
                    }
                    when {
                        scroll != null -> scope.launch { listState.animateScrollBy(scroll) }
                        event.key == Key.DirectionRight -> onNext()
                        event.key == Key.DirectionLeft -> onPrevious()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
        ) {
            itemsIndexed(blocks) { _, block -> Block(block, style) }
            item {
                OutlinedButton(onClick = onNext, modifier = Modifier.padding(top = 24.dp)) {
                    Text("Siguiente capítulo →")
                }
            }
        }
    }
}

@Composable
private fun Block(block: TextBlock, style: TextStyle) {
    val spacing = Modifier.padding(bottom = 16.dp)
    when (block.kind) {
        TextBlock.Kind.BREAK -> Text(
            "⁂",
            style = style.copy(textAlign = TextAlign.Center, color = Muted),
            modifier = spacing.fillMaxWidth(),
        )
        TextBlock.Kind.HEADING -> Text(
            block.annotated(),
            style = style.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
            modifier = spacing,
        )
        TextBlock.Kind.QUOTE, TextBlock.Kind.NOTES -> Text(
            block.annotated(),
            style = style.copy(fontStyle = FontStyle.Italic, color = Muted),
            modifier = spacing.padding(start = 20.dp),
        )
        TextBlock.Kind.PARAGRAPH -> Text(block.annotated(), style = style, modifier = spacing)
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
