package eu.kanade.presentation.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.glass
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

private const val SHELF_LIMIT = 30
private const val OPEN_MS = 550

/** Library as shelves, one per category, with each series as a spine that slides out to show its cover (RF-06). */
@Composable
fun TomoShelfContent(
    categories: List<Category>,
    itemsFor: (Category) -> List<LibraryItem>,
    sourceDownIds: Set<Long>,
    contentPadding: PaddingValues,
    onSeeAll: (Int) -> Unit,
    onOpenManga: (Long) -> Unit,
    onContinueReading: (LibraryItem) -> Unit,
    onCreateCategory: () -> Unit,
) {
    // "categoryId:mangaId" of the book that is out
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        categories.forEachIndexed { index, category ->
            val items = itemsFor(category)
            if (items.isEmpty()) return@forEachIndexed
            item(key = "shelf-${category.id}") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = Tomo.screenPadding),
                    ) {
                        Text(text = category.visualName, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = pluralStringResource(MR.plurals.tomo_library_count, items.size, items.size),
                            fontSize = 13.sp,
                            color = Tomo.textSecondary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clickable(role = Role.Button) { onSeeAll(index) }
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(text = stringResource(MR.strings.tomo_see_all), fontSize = 13.sp, color = Tomo.gold)
                        }
                    }
                    Column {
                        LazyRow(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(
                                start = Tomo.screenPadding,
                                end = Tomo.screenPadding,
                                top = 24.dp,
                            ),
                        ) {
                            items(items.take(SHELF_LIMIT), key = { it.id }) { item ->
                                val key = "${category.id}:${item.id}"
                                Book(
                                    item = item,
                                    isOpen = open == key,
                                    sourceDown = item.id in sourceDownIds,
                                    onClick = { open = if (open == key) null else key },
                                )
                            }
                            if (items.size > SHELF_LIMIT) {
                                item(key = "more") {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(56.dp)
                                            .glass(CircleShape, fill = Tomo.glassStrong)
                                            .clickable(role = Role.Button) { onSeeAll(index) },
                                    ) {
                                        Text(text = "+${items.size - SHELF_LIMIT}", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        // The plank the books stand on
                        Box(
                            Modifier
                                .padding(horizontal = Tomo.screenPadding)
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(
                                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Tomo.glass)),
                                    RoundedCornerShape(3.dp),
                                ),
                        )
                    }
                    val openItem = items.find { open == "${category.id}:${it.id}" }
                    if (openItem != null) {
                        OpenBookCard(
                            item = openItem,
                            action = shelfAction(openItem, openItem.id in sourceDownIds),
                            onOpenManga = { onOpenManga(openItem.id) },
                            onContinueReading = { onContinueReading(openItem) },
                        )
                    }
                }
            }
        }
        item(key = "new-shelf") {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(horizontal = Tomo.screenPadding)
                    .fillMaxWidth()
                    .border(1.dp, Tomo.glassBorder, RoundedCornerShape(22.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.tomo_shelf_new_category),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Tomo.textSecondary,
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(44.dp)
                        .glass(CircleShape, fill = Tomo.glassStrong)
                        .clickable(role = Role.Button, onClick = onCreateCategory)
                        .padding(horizontal = 18.dp),
                ) {
                    Text(
                        text = stringResource(MR.strings.tomo_create_category),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun Book(item: LibraryItem, isOpen: Boolean, sourceDown: Boolean, onClick: () -> Unit) {
    val manga = item.libraryManga.manga
    val height = spineHeight(item.id).dp
    // Compose scales these by the system animation setting, so "remove animations" makes them instant
    val spec = tween<Dp>(OPEN_MS)
    val width by animateDpAsState(
        targetValue = if (isOpen) height * 2 / 3 else spineWidth(item.libraryManga.totalChapters).dp,
        animationSpec = spec,
        label = "bookWidth",
    )
    val lift by animateDpAsState(if (isOpen) (-18).dp else 0.dp, spec, label = "bookLift")
    val turn by animateFloatAsState(
        targetValue = if (isOpen) 0f else 1f,
        animationSpec = tween(OPEN_MS),
        label = "bookTurn",
    )
    val shape = RoundedCornerShape(if (isOpen) 10.dp else 4.dp)
    val description = listOfNotNull(
        manga.title,
        item.unreadCount.takeIf { it > 0 }?.let {
            pluralStringResource(MR.plurals.tomo_unread_left, it.toInt(), it.toInt())
        },
        stringResource(MR.strings.tomo_filter_source_down).takeIf { sourceDown },
    ).joinToString(", ")
    Box(
        modifier = Modifier
            .offset(y = lift)
            .width(width)
            .height(height)
            .then(
                if (isOpen) {
                    Modifier
                        .shadow(24.dp, shape, ambientColor = Tomo.gold, spotColor = Tomo.gold)
                        .border(2.dp, Tomo.gold, shape)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        AsyncImage(
            model = manga.asMangaCover(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                // Slight turn while it slides out, as if the book rotated from its spine to its cover
                .graphicsLayer {
                    rotationY = turn * 25f
                    cameraDistance = 12f * density
                },
        )
        if (turn > 0.01f) {
            // Spine shading and title, faded away as the cover turns to face the user
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = turn }
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.White.copy(alpha = 0.22f),
                            0.2f to Color.Transparent,
                            0.7f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black.copy(alpha = 0.6f),
                        ),
                    ),
            ) {
                Text(
                    text = manga.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(height - 24.dp)
                        .rotate(90f),
                )
                if (item.unreadCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(8.dp)
                            .background(Tomo.gold, CircleShape),
                    )
                }
                if (sourceDown) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .size(8.dp)
                            .background(Tomo.amber, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenBookCard(
    item: LibraryItem,
    action: ShelfAction,
    onOpenManga: () -> Unit,
    onContinueReading: () -> Unit,
) {
    val manga = item.libraryManga
    val read = manga.readCount.toInt()
    val total = manga.totalChapters.toInt()
    val (status, color) = when (action) {
        ShelfAction.SOURCE_DOWN -> stringResource(MR.strings.tomo_shelf_source_down) to Tomo.amber
        ShelfAction.CONTINUE -> pluralStringResource(
            MR.plurals.tomo_unread_left,
            manga.unreadCount.toInt(),
            manga.unreadCount.toInt(),
        ) + " · " + stringResource(MR.strings.tomo_shelf_read_of, read, total) to Tomo.gold
        ShelfAction.CAUGHT_UP -> stringResource(MR.strings.tomo_caught_up) + " · " +
            stringResource(MR.strings.tomo_shelf_read_of, read, total) to Tomo.mint
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = Tomo.screenPadding)
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.manga.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = status, fontSize = 12.sp, color = color, modifier = Modifier.padding(top = 2.dp))
        }
        val (label, primary, onClick) = when (action) {
            ShelfAction.SOURCE_DOWN -> Triple(stringResource(MR.strings.tomo_available_in), false, onOpenManga)
            ShelfAction.CONTINUE -> Triple(
                stringResource(MR.strings.tomo_action_keep_reading_short),
                true,
                onContinueReading,
            )
            ShelfAction.CAUGHT_UP -> Triple(stringResource(MR.strings.tomo_see_series), false, onOpenManga)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 12.dp)
                .height(44.dp)
                .then(
                    when {
                        primary -> Modifier.background(Tomo.text, CircleShape)
                        action == ShelfAction.SOURCE_DOWN -> Modifier.background(Tomo.amber, CircleShape)
                        else -> Modifier.glass(CircleShape, fill = Tomo.glassStrong)
                    },
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp),
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (primary || action == ShelfAction.SOURCE_DOWN) Tomo.night else Tomo.text,
            )
        }
    }
}

internal enum class ShelfAction { SOURCE_DOWN, CONTINUE, CAUGHT_UP }

/** What the card of a book taken out of the shelf offers: migrate a dead source, keep reading, or open it. */
internal fun shelfAction(item: LibraryItem, sourceDown: Boolean): ShelfAction = when {
    sourceDown -> ShelfAction.SOURCE_DOWN
    item.libraryManga.unreadCount > 0 -> ShelfAction.CONTINUE
    else -> ShelfAction.CAUGHT_UP
}

/** Thicker spines for longer series, from 26 to 44 dp. */
internal fun spineWidth(totalChapters: Long): Int = (26 + (totalChapters / 16).coerceIn(0, 18)).toInt()

/** Heights between 180 and 223 dp that vary from book to book but stay the same for each series. */
internal fun spineHeight(mangaId: Long): Int = 180 + Math.floorMod(mangaId * 29, 44L).toInt()
