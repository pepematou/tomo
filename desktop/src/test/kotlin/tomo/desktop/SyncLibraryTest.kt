package tomo.desktop

import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalSerializationApi::class)
class SyncLibraryTest {

    // Like the app's backup: more fields than the computer reads, which must be skipped
    @Serializable
    class FullBackup(
        @ProtoNumber(1) val manga: List<FullManga>,
        @ProtoNumber(2) val categories: List<String> = listOf("Leyendo"),
    )

    @Serializable
    class FullManga(
        @ProtoNumber(1) val source: Long,
        @ProtoNumber(2) val url: String,
        @ProtoNumber(3) val title: String,
        @ProtoNumber(7) val genre: List<String> = listOf("Romance"),
        @ProtoNumber(16) val chapters: List<FullChapter> = emptyList(),
        @ProtoNumber(100) val favorite: Boolean = true,
    )

    @Serializable
    class FullChapter(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(4) val read: Boolean,
        @ProtoNumber(6) val lastPageRead: Long = 3,
    )

    private fun gzip(bytes: ByteArray) =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    @Test
    fun `reads the library of a phone backup`() {
        val backup = FullBackup(
            listOf(
                FullManga(
                    FFN_SOURCE_ID,
                    "/s/11",
                    "Bajo la lluvia",
                    chapters = listOf(
                        FullChapter("/s/11/1", "1. Inicio", read = true),
                        FullChapter("/s/11/2", "2. Medio", read = false),
                        FullChapter("/s/11/3", "3. Final", read = false),
                    ),
                ),
                FullManga(2499283573021220255, "/manga/1", "A manga", favorite = true),
                FullManga(1, "/manga/2", "Not followed", favorite = false),
            ),
        )
        val bytes = gzip(ProtoBuf.encodeToByteArray(FullBackup.serializer(), backup))

        val library = libraryOf(decodeSyncBackup(bytes))

        library.map { it.title } shouldBe listOf("A manga", "Bajo la lluvia")
        val fanfic = library.last()
        fanfic.isFanfic shouldBe true
        fanfic.chaptersRead shouldBe 1
        fanfic.chapters shouldBe 3
        fanfic.nextChapter shouldBe 2
        library.first().nextChapter shouldBe null
    }
}
