package eu.kanade.tachiyomi.tomo

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ChapterLengthsTest {

    @Test
    fun `lengths survive a restart and ignore empty chapters`(@TempDir dir: File) {
        val context = mockk<Context> { every { filesDir } returns dir }
        ChapterLengths(context, Json).apply {
            put(1, 52)
            put(2, 0)
        }
        val reloaded = ChapterLengths(context, Json)
        reloaded[1] shouldBe 52
        reloaded[2] shouldBe null
    }
}
