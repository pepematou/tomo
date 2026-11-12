package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Tomo's "dark glass" theme. The translucent glass layers of the design are pre-blended over the
 * night background, since a ColorScheme holds opaque colors.
 */
internal object TomoColorScheme : BaseColorScheme() {

    private val night = Color(0xFF080B14)
    private val glass = Color(0xFF191C24) // white 7% over night
    private val glassStrong = Color(0xFF282B33) // white 13% over night
    private val glassBorder = Color(0xFF30323A) // white 16% over night
    private val text = Color(0xFFF4F6FB)
    private val textSecondary = Color(0xFFA3ABBD)
    private val gold = Color(0xFFF2C46D)
    private val mint = Color(0xFF6EE7C1)
    private val amber = Color(0xFFFFB45C)

    override val darkScheme = darkColorScheme(
        primary = text,
        onPrimary = night,
        primaryContainer = glassStrong,
        onPrimaryContainer = text,
        inversePrimary = night,
        secondary = gold, // Unread badge
        onSecondary = night,
        secondaryContainer = Color(0xFF373026), // gold 20% over night: navigation bar selector pill
        onSecondaryContainer = gold,
        tertiary = mint, // Downloaded badge
        onTertiary = night,
        tertiaryContainer = Color(0xFF172C2E), // mint 15% over night
        onTertiaryContainer = mint,
        background = night,
        onBackground = text,
        surface = night,
        onSurface = text,
        surfaceVariant = glassStrong,
        onSurfaceVariant = textSecondary,
        surfaceTint = text,
        inverseSurface = text,
        inverseOnSurface = night,
        outline = glassBorder,
        outlineVariant = glassBorder,
        error = amber,
        onError = night,
        errorContainer = amber,
        onErrorContainer = night,
        surfaceContainerLowest = night,
        surfaceContainerLow = glass,
        surfaceContainer = glass,
        surfaceContainerHigh = glassStrong,
        surfaceContainerHighest = glassStrong,
    )

    // The design is dark only
    override val lightScheme = darkScheme
}
