package tomo.desktop

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.File
import java.util.zip.GZIPInputStream

/** FanFiction.net's source id in Tomo for Android (`FfnSource.ID`), to tell its fanfics apart in the library. */
const val FFN_SOURCE_ID = 7_316_030_304L

/*
 * The part of a Tomo/Mihon backup the computer needs. Field numbers are the ones of the app's
 * `data/backup/models`; everything else in the file is skipped.
 */
@Serializable
class SyncBackup(@ProtoNumber(1) val manga: List<SyncManga> = emptyList())

@Serializable
class SyncManga(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(16) val chapters: List<SyncChapter> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
)

@Serializable
class SyncChapter(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(4) val read: Boolean = false,
)

/** A series of the library as the computer shows it. */
data class LibraryEntry(
    val title: String,
    val cover: String?,
    val isFanfic: Boolean,
    val url: String,
    val chaptersRead: Int,
    val chapters: Int,
    /** Chapter to continue with: the first unread one, for FanFiction.net works. */
    val nextChapter: Int?,
)

@OptIn(ExperimentalSerializationApi::class)
fun decodeSyncBackup(gzipped: ByteArray): SyncBackup =
    ProtoBuf.decodeFromByteArray(SyncBackup.serializer(), GZIPInputStream(gzipped.inputStream()).readBytes())

/** The newest `tomo-sync-*.tachibk` of the folder, the one with the latest progress. */
fun newestSyncFile(folder: File): File? = folder.listFiles { file ->
    file.name.startsWith("tomo-sync-") && file.name.endsWith(".tachibk")
}?.maxByOrNull { it.lastModified() }

fun libraryOf(backup: SyncBackup): List<LibraryEntry> = backup.manga.filter { it.favorite }.map { manga ->
    val isFanfic = manga.source == FFN_SOURCE_ID
    val unread = manga.chapters.filterNot { it.read }.mapNotNull { chapterNumber(it.url) }
    LibraryEntry(
        title = manga.title,
        cover = manga.thumbnailUrl,
        isFanfic = isFanfic,
        url = manga.url,
        chaptersRead = manga.chapters.count { it.read },
        chapters = manga.chapters.size,
        nextChapter = if (isFanfic) unread.minOrNull() ?: 1 else null,
    )
}.sortedBy { it.title.lowercase() }

/** "/s/123/4" → 4. */
fun chapterNumber(url: String): Int? = Regex("""^/s/\d+/(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
