package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import eu.kanade.presentation.tomo.Tomo
import eu.kanade.presentation.util.Tab

/** Space the floating bar takes above the system navigation bar, for content to scroll clear of it. */
internal val GlassNavigationBarInset = 68.dp + 12.dp + 8.dp

private val BarShape = RoundedCornerShape(34.dp)

/** Tomo's floating navigation bar: blurs the content scrolling behind it, as frosted glass. */
@Composable
internal fun GlassNavigationBar(
    tabs: List<Tab>,
    selected: Tab,
    hazeState: HazeState,
    onClick: (Tab) -> Unit,
    badge: @Composable (Tab) -> (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(elevation = 16.dp, shape = BarShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(BarShape)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle {
                    blurRadius(24.dp)
                    backgroundColor(colors.background)
                    colorEffects(listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.13f))))
                    // Without blur (Android 11 and older) a denser fill keeps the labels legible
                    fallbackColorEffect(HazeColorEffect.tint(colors.surfaceContainerHigh.copy(alpha = 0.95f)))
                },
            )
            .border(1.dp, Tomo.glassBorder, BarShape)
            .padding(6.dp),
    ) {
        // Badges are gold in the design, not the theme's error color
        MaterialTheme(colorScheme = colors.copy(error = Tomo.gold, onError = Tomo.night)) {
            tabs.forEach { tab -> BarItem(tab, tab::class == selected::class, onClick, badge) }
        }
    }
}

@Composable
private fun RowScope.BarItem(
    tab: Tab,
    isSelected: Boolean,
    onClick: (Tab) -> Unit,
    badge: @Composable (Tab) -> (@Composable () -> Unit)?,
) {
    val tint = if (isSelected) Tomo.text else Tomo.textFaint
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(BarShape)
            .then(if (isSelected) Modifier.background(Tomo.glassStrong) else Modifier)
            .selectable(selected = isSelected, role = Role.Tab, onClick = { onClick(tab) }),
    ) {
        BadgedBox(badge = { badge(tab)?.invoke() }) {
            Icon(
                painter = tab.options.icon!!,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = tab.options.title,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
