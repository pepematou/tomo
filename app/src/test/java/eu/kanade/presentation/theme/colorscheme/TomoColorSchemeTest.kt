package eu.kanade.presentation.theme.colorscheme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Test

class TomoColorSchemeTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun `text meets WCAG AA contrast on every surface it is drawn on`() {
        with(TomoColorScheme.darkScheme) {
            listOf(
                onSurface to surface,
                onSurface to surfaceContainer,
                onSurface to surfaceContainerHigh,
                onSurfaceVariant to surfaceContainer,
                onSurfaceVariant to surfaceContainerHigh,
                onPrimary to primary,
                onSecondary to secondary,
                onSecondaryContainer to secondaryContainer,
                onTertiary to tertiary,
                onTertiaryContainer to tertiaryContainer,
                onErrorContainer to errorContainer,
            ).forEach { (text, background) ->
                contrast(text, background) shouldBeGreaterThanOrEqual 4.5
            }
        }
    }
}
