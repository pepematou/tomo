package eu.kanade.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R

// One static file per weight: Android caches a variable font by resource and ignores its weight axis
internal val Onest = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold),
)

private fun TextStyle.onest() = copy(fontFamily = Onest)

/** Material's type scale in Onest, with the sizes of Tomo's design for screen titles, sections and body text. */
internal val TomoTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.onest(),
        displayMedium = displayMedium.onest(),
        displaySmall = displaySmall.onest(),
        headlineLarge = headlineLarge.onest(),
        headlineMedium = headlineMedium.onest()
            .copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
        headlineSmall = headlineSmall.onest()
            .copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
        titleLarge = titleLarge.onest()
            .copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
        titleMedium = titleMedium.onest(),
        titleSmall = titleSmall.onest(),
        bodyLarge = bodyLarge.onest().copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = bodyMedium.onest(),
        bodySmall = bodySmall.onest().copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = labelLarge.onest(),
        labelMedium = labelMedium.onest(),
        labelSmall = labelSmall.onest(),
    )
}
