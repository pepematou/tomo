package eu.kanade.tachiyomi.tomo.alternatives

import eu.kanade.tachiyomi.tomo.alternatives.AlternativeFinder.Match
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class AlternativeFinderTest {

    private fun alternative(sourceId: Long, lastChapter: Double = 10.0, match: Match = Match.POSSIBLE) =
        AlternativeFinder.Alternative(
            sourceId = sourceId,
            sourceName = "Source $sourceId",
            lang = "es",
            url = "/$sourceId",
            title = "Title",
            thumbnailUrl = null,
            author = null,
            lastChapter = lastChapter,
            lastUpload = 0,
            chapterCount = 0,
            memo = JsonObject(emptyMap()),
            match = match,
        )

    @Test
    fun `same author ignores case, accents, punctuation and name order`() {
        matchOf("Takeru Hokazono", "HOKAZONO, Takeru") shouldBe Match.SAME_AUTHOR
        matchOf("José Pérez", "jose perez") shouldBe Match.SAME_AUTHOR
        matchOf("Chugong", "Chugong, Jang Sung-rak") shouldBe Match.SAME_AUTHOR
    }

    @Test
    fun `different or unknown authors are only a possible match`() {
        matchOf("Takeru Hokazono", "Eiichiro Oda") shouldBe Match.POSSIBLE
        matchOf(null, "Eiichiro Oda") shouldBe Match.POSSIBLE
        matchOf("Takeru Hokazono", "") shouldBe Match.POSSIBLE
    }

    @Test
    fun `chapter delta counts only chapters ahead`() {
        chapterDelta(284.0, alternative(1, lastChapter = 290.0)) shouldBe 6
        chapterDelta(284.0, alternative(1, lastChapter = 280.0)) shouldBe 0
        chapterDelta(-1.0, alternative(1, lastChapter = 290.0)) shouldBe 0
    }

    @Test
    fun `best alternative prefers the same author, then the source order`() {
        val possible = alternative(1)
        val sameAuthor = alternative(2, match = Match.SAME_AUTHOR)
        bestAlternative(listOf(possible, sameAuthor)) shouldBe sameAuthor
        bestAlternative(listOf(possible, alternative(3))) shouldBe possible
        bestAlternative(emptyList()) shouldBe null
    }

    @Test
    fun `cached searches last a day`() {
        val now = 100.hours.inWholeMilliseconds
        AlternativeFinder.isFresh(now - 23.hours.inWholeMilliseconds, now) shouldBe true
        AlternativeFinder.isFresh(now - 25.hours.inWholeMilliseconds, now) shouldBe false
        AlternativeFinder.isFresh(0, now) shouldBe false
    }

    @Test
    fun `options switch only their own migration flags`() {
        val options = AlternativeFinder.MigrateOptions(
            keepProgress = true,
            keepCategories = false,
            deleteDownloads = true,
            move = true,
        )
        options.applyTo(setOf(MigrationFlag.CATEGORY, MigrationFlag.NOTES)) shouldBe
            setOf(MigrationFlag.CHAPTER, MigrationFlag.REMOVE_DOWNLOAD, MigrationFlag.NOTES)
    }
}
