package tomo.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Where Tomo keeps its files on Linux, following the XDG folders. */
object Paths {
    private val home = System.getProperty("user.home")
    val config = File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "tomo").apply { mkdirs() }
    val cache = File(System.getenv("XDG_CACHE_HOME") ?: "$home/.cache", "tomo").apply { mkdirs() }
}

@Serializable
data class Settings(
    /** The folder Syncthing keeps in sync with the phone. */
    val syncFolder: String? = null,
    /** Last chapter read here, by story url, so the computer remembers its own progress. */
    val progress: Map<String, Int> = emptyMap(),
    /** Chapters each followed fanfic had at the last check, to tell when a new one comes out. */
    val knownChapters: Map<String, Int> = emptyMap(),
)

object SettingsStore {
    private val file = File(Paths.config, "desktop.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): Settings = runCatching { json.decodeFromString(Settings.serializer(), file.readText()) }
        .getOrDefault(Settings())

    fun save(settings: Settings) {
        // Written aside and renamed, so a crash never leaves half a file
        val temp = File(file.parentFile, "desktop.json.tmp")
        temp.writeText(json.encodeToString(Settings.serializer(), settings))
        temp.renameTo(file)
    }
}

/**
 * Fanfics of the library with more chapters than at the last check, as (title, new chapters). Works seen for the
 * first time are not news: they only start being followed.
 */
fun newChapters(current: Map<String, Pair<String, Int>>, known: Map<String, Int>): List<Pair<String, Int>> =
    current.mapNotNull { (url, titleAndCount) ->
        val (title, count) = titleAndCount
        val before = known[url] ?: return@mapNotNull null
        (count - before).takeIf { it > 0 }?.let { title to it }
    }
