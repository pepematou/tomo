package eu.kanade.tachiyomi.tomo

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import java.io.File

/**
 * How many pages (or text blocks) each opened chapter has, which Mihon does not store, so Home can say "página 36 de
 * 52" and show the progress of the chapter being read (RF-34).
 */
@Inject
@SingleIn(AppScope::class)
class ChapterLengths(
    context: Context,
    private val json: Json,
) {
    private val file = File(context.filesDir, "chapter_lengths.json")
    private var lengths: Map<Long, Int> = load()

    operator fun get(chapterId: Long): Int? = lengths[chapterId]

    @Synchronized
    fun put(chapterId: Long, length: Int) {
        if (length <= 0 || lengths[chapterId] == length) return
        // Only the latest chapters matter to Home; keep the file small
        lengths = (lengths + (chapterId to length)).entries.toList().takeLast(MAX_ENTRIES).associate { it.toPair() }
        try {
            file.writeText(json.encodeToString(lengths))
        } catch (_: Exception) {}
    }

    private fun load(): Map<Long, Int> = try {
        json.decodeFromString<Map<Long, Int>>(file.readText())
    } catch (_: Exception) {
        emptyMap()
    }

    companion object {
        private const val MAX_ENTRIES = 500
    }
}
