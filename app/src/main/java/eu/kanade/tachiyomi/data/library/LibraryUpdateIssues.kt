package eu.kanade.tachiyomi.data.library

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
 * Series the last library updates skipped or failed on, each with the reason shown to the user.
 *
 * Kept in a JSON file instead of the database so the fork adds no SQLDelight migration that could clash with
 * upstream's numbering.
 */
@Inject
@SingleIn(AppScope::class)
class LibraryUpdateIssues(
    context: Context,
    private val json: Json,
) {
    private val file = File(context.filesDir, "library_update_issues.json")

    private val _issues = MutableStateFlow(load())
    val issues: StateFlow<List<Issue>> = _issues.asStateFlow()

    /** Replaces the issues of the series in [checkedIds] with [found]; issues of other series are kept. */
    @Synchronized
    fun replace(checkedIds: Set<Long>, found: List<Issue>) {
        val updated = _issues.value.filterNot { it.mangaId in checkedIds } + found
        _issues.value = updated
        try {
            file.writeText(json.encodeToString(updated))
        } catch (_: Exception) {}
    }

    private fun load(): List<Issue> = try {
        json.decodeFromString<List<Issue>>(file.readText())
    } catch (_: Exception) {
        emptyList()
    }

    @Serializable
    data class Issue(
        val mangaId: Long,
        val title: String,
        val kind: Kind,
        val reason: String,
        /** Set when the source asked for a Cloudflare check that only WebView can pass. */
        val webViewUrl: String? = null,
        val sourceId: Long = 0,
        val action: Action = Action.NONE,
    )

    enum class Kind { SKIPPED, ERROR }

    /** What the user can do to fix the issue. */
    enum class Action { NONE, INSTALL_EXTENSION, MIGRATE, WEBVIEW }
}

/** Series the last update could not reach because their extension is missing or their site is gone. */
fun List<LibraryUpdateIssues.Issue>.sourceDownIds(): Set<Long> =
    filter {
        it.action == LibraryUpdateIssues.Action.MIGRATE ||
            it.action == LibraryUpdateIssues.Action.INSTALL_EXTENSION
    }
        .mapTo(mutableSetOf()) { it.mangaId }
