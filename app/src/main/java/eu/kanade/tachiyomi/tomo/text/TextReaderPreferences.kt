package eu.kanade.tachiyomi.tomo.text

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/** How the text reader looks: typeface, size, spacing, margins, alignment, theme and paging. */
@Inject
@SingleIn(AppScope::class)
class TextReaderPreferences(preferenceStore: PreferenceStore) {

    val font = preferenceStore.getEnum("tomo_text_font", Font.SERIF)

    val fontSize = preferenceStore.getInt("tomo_text_font_size", 18)

    /** Line height as a multiple of the font size, in tenths: 16 is 1.6. */
    val lineHeight = preferenceStore.getInt("tomo_text_line_height", 16)

    val margin = preferenceStore.getInt("tomo_text_margin", 20)

    val justify = preferenceStore.getBoolean("tomo_text_justify", false)

    val theme = preferenceStore.getEnum("tomo_text_theme", Theme.DARK)

    val paged = preferenceStore.getBoolean("tomo_text_paged", false)

    enum class Font { SERIF, SANS, MONO }

    enum class Theme { LIGHT, SEPIA, DARK, BLACK }
}
