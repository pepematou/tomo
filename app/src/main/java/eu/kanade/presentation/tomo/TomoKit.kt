package eu.kanade.presentation.tomo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.MangaCover
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBack
import tachiyomi.domain.manga.model.MangaCover as MangaCoverData

/** Tokens of Tomo's dark glass design (plan-tomo.md §6). */
object Tomo {
    val night = Color(0xFF080B14)
    val glass = Color.White.copy(alpha = 0.07f)
    val glassStrong = Color.White.copy(alpha = 0.13f)
    val glassBorder = Color.White.copy(alpha = 0.16f)
    val text = Color(0xFFF4F6FB)
    val textSecondary = Color(0xFFA3ABBD)
    val textFaint = Color(0xFF8A93A6)
    val gold = Color(0xFFF2C46D)
    val mint = Color(0xFF6EE7C1)
    val amber = Color(0xFFFFB45C)

    val glowBlue = Color(0xFF2F4FB0)
    val glowViolet = Color(0xFF6A3A9A)
    val glowTeal = Color(0xFF1F6B6B)

    val screenPadding = 16.dp
}

/** Night background with two large, soft color glows, as in every mockup screen. */
@Composable
fun TomoBackground(
    modifier: Modifier = Modifier,
    topGlow: Color = Tomo.glowBlue,
    sideGlow: Color = Tomo.glowViolet,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(topGlow.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.05f),
                        radius = size.width * 0.95f,
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(size.width * 0.15f, size.height * 0.05f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(sideGlow.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(size.width * 1.0f, size.height * 0.35f),
                        radius = size.width * 0.85f,
                    ),
                    radius = size.width * 0.85f,
                    center = Offset(size.width * 1.0f, size.height * 0.35f),
                )
            },
        content = content,
    )
}

/** Screen header: a small line above a large title, with round glass buttons on the right. */
@Composable
fun TomoHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigateUp: (() -> Unit)? = null,
    navigateUpDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tomo.screenPadding, vertical = 12.dp),
    ) {
        if (navigateUp != null) {
            GlassIconButton(
                icon = MaterialSymbols.AutoMirroredRounded.ArrowBack,
                contentDescription = navigateUpDescription,
                onClick = navigateUp,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(text = subtitle, color = Tomo.textSecondary, fontSize = 14.sp, lineHeight = 18.sp)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

/** 48 dp round glass button with an accessible name. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Tomo.glassStrong)
            .border(1.dp, Tomo.glassBorder, CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Tomo.text)
    }
}

/** Glass surface: translucent white fill and a 1 dp light border, so the background glows show through. */
fun Modifier.glass(shape: Shape, fill: Color = Tomo.glass, border: Color = Tomo.glassBorder): Modifier =
    clip(shape).background(fill).border(1.dp, border, shape)

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(radius))
            .padding(contentPadding),
        content = content,
    )
}

/** White, full-width call to action: "Seguir leyendo", "Leer cap. 47", "Actualizar todas". */
@Composable
fun TomoPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Tomo.text, contentColor = Tomo.night),
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Gold pill for what is new: "+2", "3 por actualizar". */
@Composable
fun GoldBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Tomo.night,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(CircleShape)
            .background(Tomo.gold)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Small glass pill used for metadata: "En curso", "Manga". */
@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .glass(CircleShape, fill = Tomo.glassStrong)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        leading?.invoke()
        Text(text = text, fontSize = 13.sp, color = Tomo.text)
    }
}

/** Cover with its badges and, below it, a title and a muted line such as "Cap. 156". */
@Composable
fun CoverCard(
    cover: MangaCoverData,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    topStart: (@Composable () -> Unit)? = null,
    topEnd: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(modifier = modifier) {
        Box {
            MangaCover.Book(
                data = cover,
                contentDescription = title,
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Tomo.glassBorder, RoundedCornerShape(10.dp)),
            )
            topStart?.let { Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { it() } }
            topEnd?.let { Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { it() } }
        }
        Text(
            text = title,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 12.sp, color = Tomo.textFaint, maxLines = 1)
        }
    }
}

/** Section title row with an optional trailing link, e.g. "Novedades ... Ver todo". */
@Composable
fun TomoSectionTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tomo.screenPadding, vertical = 8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        CompositionLocalProvider(LocalContentColor provides Tomo.textSecondary) {
            trailing?.invoke()
        }
    }
}

/** Gold progress bar on a faint glass track. */
@Composable
fun TomoProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(Tomo.gold),
        )
    }
}

internal val CoverCardWidth = 108.dp
internal val CoverCardSpacing = 12.dp
