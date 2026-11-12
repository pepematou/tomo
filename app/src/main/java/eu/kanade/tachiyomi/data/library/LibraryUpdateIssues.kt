package eu.kanade.tachiyomi.data.library

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.tomo.text.TEXT_SOURCE_IDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.manga.interactor.GetLibraryManga
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
    getLibraryManga: GetLibraryManga,
    @AppCoroutineScope scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "library_update_issues.json")

    private val _issues = MutableStateFlow(load())

    /** Issues of series still in the library: removing or migrating a series clears its notice at once. */
    val issues: StateFlow<List<Issue>> = combine(
        _issues,
        getLibraryManga.subscribe().map { library -> library.mapTo(HashSet()) { it.id } },
        ::inLibrary,
    )
        .stateIn(scope, SharingStarted.Eagerly, _issues.value)

    /** Replaces the issues of the series in [checkedIds] with [found]; issues of other series are kept. */
    @Synchronized
    fun replace(checkedIds: Set<Long>, found: List<Issue>) {
        val updated = _issues.value.filterNot { it.mangaId in checkedIds } + found
        _issues.value = updated
        try {
            file.writeText(json.encodeToString(updated))
        } catch (_: Exception) {}
    }

    /** Clears the notice of a series, e.g. once it was migrated to a working source. */
    fun remove(mangaId: Long) = replace(setOf(mangaId), emptyList())

    // shortcut: issues of removed series stay in the file (only hidden); prune them if the file ever grows large
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

/**
 * Series the last update could not reach because their extension is missing or their site is gone. Fanfics are left
 * out: migrating them to a manga source makes no sense, and the fanfic screens explain the outage instead (RF-30i).
 */
fun List<LibraryUpdateIssues.Issue>.sourceDownIds(): Set<Long> =
    filter {
        it.sourceId !in TEXT_SOURCE_IDS &&
            (
                it.action == LibraryUpdateIssues.Action.MIGRATE ||
                    it.action == LibraryUpdateIssues.Action.INSTALL_EXTENSION
                )
    }
        .mapTo(mutableSetOf()) { it.mangaId }

internal fun inLibrary(
    issues: List<LibraryUpdateIssues.Issue>,
    libraryIds: Set<Long>,
): List<LibraryUpdateIssues.Issue> =
    issues.filter { it.mangaId in libraryIds }
