package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.tomo.GlassPill
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.tomo.glass
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.library.sourceDownIds
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.map
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.BarChart
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Public
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.SwapHoriz
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Series header of the dark glass design: the cover, blurred, colors the top of the screen; the cover itself sits
 * centered over it with the title, author, status pills and when the series was last checked.
 */
@Composable
fun TomoMangaHeader(
    manga: Manga,
    sourceName: String,
    chapterCount: Int,
    appBarPadding: Dp,
    onCoverClick: () -> Unit,
) {
    val cover = manga.asMangaCover()
    // Mihon names a source whose extension is missing by its numeric id
    val sourceMissing = sourceName == manga.source.toString()
    val sourceLabel = if (sourceMissing) stringResource(MR.strings.tomo_extension_not_installed) else sourceName
    Box(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(60.dp)
                // Masks the blurred cover so it fades out into whatever background lies below it
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(0.4f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Tomo.night.copy(alpha = 0.35f)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = appBarPadding + 8.dp, start = Tomo.screenPadding, end = Tomo.screenPadding),
        ) {
            MangaCover.Book(
                data = cover,
                contentDescription = manga.title,
                shape = RoundedCornerShape(14.dp),
                onClick = onCoverClick,
                modifier = Modifier
                    .width(150.dp)
                    .shadow(
                        elevation = 32.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = Tomo.glowBlue,
                        spotColor = Tomo.glowBlue,
                    ),
            )
            Text(
                text = manga.title,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            val creator = listOfNotNull(manga.author, manga.artist).distinct().joinToString()
            Text(
                text = creator.ifBlank { sourceLabel },
                color = Tomo.textSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassPill(statusText(manga.status))
                GlassPill(sourceLabel, color = if (sourceMissing) Tomo.amber else null)
                GlassPill(pluralStringResource(MR.plurals.manga_num_chapters, chapterCount, chapterCount))
            }
            manga.lastUpdate?.let { checked ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Check,
                        contentDescription = null,
                        tint = Tomo.mint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(
                            MR.strings.tomo_checked_ago,
                            relativeTimeSpanString(checked.toEpochMilliseconds()).replaceFirstChar { it.lowercase() },
                        ),
                        color = Tomo.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/** "En biblioteca", tracking and WebView as glass pills, plus the update interval when it can be edited. */
@Composable
fun TomoMangaActions(
    favorite: Boolean,
    trackingCount: Int,
    onAddToLibraryClicked: () -> Unit,
    onTrackingClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onAvailableClicked: (() -> Unit)?,
    mangaId: Long,
) {
    val context = LocalContext.current
    // Amber when the series' extension is missing or its site is gone
    val sourceDown by remember(mangaId) {
        context.appGraph.libraryUpdateIssues.issues.map { mangaId in it.sourceDownIds() }
    }.collectAsState(false)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tomo.screenPadding, vertical = 16.dp),
    ) {
        ActionPill(
            icon = if (favorite) MaterialSymbols.Rounded.Check else MaterialSymbols.Rounded.Add,
            iconTint = if (favorite) Tomo.mint else Tomo.text,
            text = stringResource(if (favorite) MR.strings.in_library else MR.strings.add_to_library),
            onClick = onAddToLibraryClicked,
        )
        ActionPill(
            icon = MaterialSymbols.Rounded.BarChart,
            text = if (trackingCount == 0) {
                stringResource(MR.strings.manga_tracking_tab)
            } else {
                pluralStringResource(MR.plurals.num_trackers, trackingCount, trackingCount)
            },
            onClick = onTrackingClicked,
        )
        if (onWebViewClicked != null) {
            ActionPill(
                icon = MaterialSymbols.Rounded.Public,
                text = stringResource(MR.strings.action_web_view),
                onClick = onWebViewClicked,
            )
        }
        if (onAvailableClicked != null) {
            ActionPill(
                icon = MaterialSymbols.Rounded.SwapHoriz,
                iconTint = if (sourceDown) Tomo.amber else Tomo.text,
                text = stringResource(MR.strings.tomo_available_in),
                onClick = onAvailableClicked,
            )
        }
        if (onEditIntervalClicked != null) {
            ActionPill(
                icon = MaterialSymbols.Rounded.Schedule,
                text = stringResource(MR.strings.tomo_update_frequency),
                onClick = onEditIntervalClicked,
            )
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, text: String, onClick: () -> Unit, iconTint: Color = Tomo.text) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(48.dp)
            .glass(CircleShape, fill = Tomo.glassStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun statusText(status: Long): String = stringResource(
    when (status) {
        SManga.ONGOING.toLong() -> MR.strings.ongoing
        SManga.COMPLETED.toLong() -> MR.strings.completed
        SManga.LICENSED.toLong() -> MR.strings.licensed
        SManga.PUBLISHING_FINISHED.toLong() -> MR.strings.publishing_finished
        SManga.CANCELLED.toLong() -> MR.strings.cancelled
        SManga.ON_HIATUS.toLong() -> MR.strings.on_hiatus
        else -> MR.strings.unknown
    },
)
