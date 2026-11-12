package eu.kanade.tachiyomi.tomo.text

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Series imported as text (fanfics, novels): the "Texto" type, and where each came from. Kept in a JSON file so the
 * fork adds no database migration.
 */
@Inject
@SingleIn(AppScope::class)
class TextWorks(
    context: Context,
    private val json: Json,
) {
    private val file = File(context.filesDir, "text_works.json")

    private val _works = MutableStateFlow(load())
    val works: StateFlow<Map<Long, TextWork>> = _works.asStateFlow()

    fun isText(mangaId: Long): Boolean = mangaId in _works.value

    @Synchronized
    fun add(mangaId: Long, work: TextWork) {
        val updated = _works.value + (mangaId to work)
        _works.value = updated
        try {
            file.writeText(json.encodeToString(updated))
        } catch (_: Exception) {}
    }

    private fun load(): Map<Long, TextWork> = try {
        json.decodeFromString<Map<Long, TextWork>>(file.readText())
    } catch (_: Exception) {
        emptyMap()
    }

    @Serializable
    data class TextWork(
        /** Set for works imported from Archive of Our Own, to check them for new chapters later. */
        val ao3WorkId: Long? = null,
    )
}
