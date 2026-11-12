package eu.kanade.tachiyomi.tomo.text

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.core.archive.epubReader
import mihon.domain.source.interactor.UpdateMangaFromRemote
import okhttp3.Headers
import org.jsoup.Jsoup
import tachiyomi.core.metadata.tachiyomi.MangaDetails
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.LocalSource
import java.io.File

/**
 * Brings EPUB, TXT and HTML files and Archive of Our Own works into the library as text works of the Local source:
 * one folder per work holding a single EPUB (plain text and HTML are wrapped into one) and its details.
 */
@Inject
class TextImporter(
    private val context: Context,
    private val json: Json,
    private val storageManager: StorageManager,
    private val networkHelper: NetworkHelper,
    private val networkToLocalManga: NetworkToLocalManga,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val followFanfic: FollowFanfic,
) {
    class ImportException(val reason: Reason) : Exception(reason.name)

    data class Imported(val mangaId: Long, val title: String)

    enum class Reason { NO_STORAGE, UNSUPPORTED, AO3_RESTRICTED, AO3_NOT_FOUND, INVALID_LINK, NETWORK }

    /** Imports a file the user picked into the library. */
    suspend fun importFile(uri: Uri): Imported = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: uri.lastPathSegment.orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase()
        val fallbackTitle = name.substringBeforeLast('.').ifBlank { "Texto" }
        val input = context.contentResolver.openInputStream(uri) ?: throw ImportException(Reason.UNSUPPORTED)
        val epub = tempFile()
        try {
            when (extension) {
                "epub" -> input.use { stream -> epub.outputStream().use(stream::copyTo) }
                "txt", "text" -> {
                    val text = input.use { it.readBytes().decodeToString() }
                    epub.outputStream().use { writeEpub(fallbackTitle, null, textToXhtmlBody(text), it) }
                }
                "html", "htm", "xhtml" -> {
                    val document = input.use { Jsoup.parse(it, null, "") }
                    val title = document.title().ifBlank { fallbackTitle }
                    epub.outputStream().use {
                        writeEpub(title, null, document.body().html().toXhtml(), it)
                    }
                }
                else -> throw ImportException(Reason.UNSUPPORTED)
            }
            install(epub, fallbackTitle, ao3WorkId = null)
        } finally {
            epub.delete()
        }
    }

    /** Downloads the EPUB Archive of Our Own offers for the work in [link]; one request. */
    suspend fun importAo3(link: String): Imported = withContext(Dispatchers.IO) {
        val workId = ao3WorkId(link) ?: throw ImportException(Reason.INVALID_LINK)
        val epub = tempFile()
        try {
            val response = try {
                networkHelper.client
                    .newCall(
                        GET(
                            "https://archiveofourown.org/downloads/$workId/work.epub",
                            Headers.headersOf("User-Agent", "Tomo"),
                        ),
                    )
                    .await()
            } catch (_: Exception) {
                throw ImportException(Reason.NETWORK)
            }
            response.use {
                when {
                    it.code == 404 -> throw ImportException(Reason.AO3_NOT_FOUND)
                    !it.isSuccessful -> throw ImportException(Reason.NETWORK)
                    // Works for registered users only answer with the login page instead of the file
                    it.body.contentType()?.subtype?.contains("epub") != true ->
                        throw ImportException(Reason.AO3_RESTRICTED)
                }
                epub.outputStream().use { out -> it.body.byteStream().copyTo(out) }
            }
            install(epub, "AO3 $workId", ao3WorkId = workId)
        } finally {
            epub.delete()
        }
    }

    private suspend fun install(epub: File, fallbackTitle: String, ao3WorkId: Long?): Imported {
        val metadata = UniFile.fromFile(epub)!!.epubReader(context).use { reader ->
            epubMetadata(reader.getPackageDocument(reader.getPackageHref()))
        }
        val title = metadata.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val folderName = folderName(title)
        val localDir = storageManager.getLocalSourceDirectory() ?: throw ImportException(Reason.NO_STORAGE)
        val folder = localDir.findFile(folderName)?.takeIf { it.isDirectory }
            ?: localDir.createDirectory(folderName)
            ?: throw ImportException(Reason.NO_STORAGE)

        // A re-import replaces the file and details; stale metadata would otherwise win over the new details
        listOf("$folderName.epub", "details.json", "ComicInfo.xml", ".noxml").forEach { folder.findFile(it)?.delete() }
        folder.createFile("$folderName.epub")!!.openOutputStream().use { out ->
            epub.inputStream().use { it.copyTo(out) }
        }
        folder.createFile("details.json")!!.openOutputStream().use { out ->
            val details = MangaDetails(
                title = title,
                author = metadata.author,
                description = metadata.description,
                genre = metadata.tags,
            )
            out.write(json.encodeToString(MangaDetails.serializer(), details).toByteArray())
        }

        val manga = networkToLocalManga(Manga.create().copy(url = folderName, title = title, source = LocalSource.ID))
        updateMangaFromRemote(manga, fetchDetails = true, fetchChapters = true)
        followFanfic.follow(manga, ao3WorkId)
        return Imported(manga.id, title)
    }

    private fun displayName(uri: Uri): String? = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun tempFile() = File.createTempFile("tomo-import", ".epub", context.cacheDir)
}

/** A folder name every storage accepts: no path or reserved characters, not too long. */
internal fun folderName(title: String): String =
    title.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.')
        .take(120)
        .ifBlank { "Texto" }

/** HTML body as well-formed XHTML, so EPUB readers accept it. */
private fun String.toXhtml(): String {
    val document = Jsoup.parseBodyFragment(this)
    document.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
    return document.body().html()
}
