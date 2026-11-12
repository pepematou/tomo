package eu.kanade.tachiyomi.tomo.text

import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.tomo.ao3.Ao3Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga

/**
 * Following a fanfic (RF-30c, RF-30f): it joins the library in the "Fanfics" category, which is created the first
 * time and shows up as its own shelf, gets a generated cover and counts as a text work.
 */
@Inject
class FollowFanfic(
    private val networkToLocalManga: NetworkToLocalManga,
    private val getManga: GetManga,
    private val updateManga: UpdateManga,
    private val getCategories: GetCategories,
    private val createCategoryWithName: CreateCategoryWithName,
    private val setMangaCategories: SetMangaCategories,
    private val coverCache: CoverCache,
    private val textWorks: TextWorks,
    private val fanficDownloader: FanficDownloader,
) {
    /** Follows a work from search results, which isn't in the database yet; returns the series id. */
    suspend fun follow(work: SManga, sourceId: Long): Long {
        val manga = networkToLocalManga(work.toDomainManga(sourceId))
        follow(manga)
        return manga.id
    }

    suspend fun follow(manga: Manga, ao3WorkId: Long? = null) = withContext(Dispatchers.IO) {
        if (!textWorks.isText(manga.id)) textWorks.add(manga.id, TextWorks.TextWork(ao3WorkId = ao3WorkId))
        ensureCover(manga)
        updateManga.awaitUpdateFavorite(manga.id, true)
        setMangaCategories.await(manga.id, listOf(fanficsCategoryId()))
        fanficDownloader.enqueue()
    }

    /** The series of a fanfic site's work in the database (without following it), so its ficha can open. */
    suspend fun openWork(sourceId: Long, url: String): Long {
        val manga = networkToLocalManga(
            Manga.create().copy(url = url, title = url.substringAfterLast('/'), source = sourceId),
        )
        if (!textWorks.isText(manga.id)) {
            val ao3WorkId = if (sourceId == Ao3Source.ID) url.substringAfterLast('/').toLongOrNull() else null
            textWorks.add(manga.id, TextWorks.TextWork(ao3WorkId = ao3WorkId))
        }
        return manga.id
    }

    suspend fun unfollow(mangaId: Long) {
        updateManga.awaitUpdateFavorite(mangaId, false)
    }

    /** Text works have no cover image; one is drawn from the title once, unless the user set their own. */
    suspend fun ensureCover(manga: Manga) = withContext(Dispatchers.IO) {
        val current = getManga.await(manga.id) ?: manga
        if (coverCache.getCustomCoverFile(current.id).exists()) return@withContext
        coverCache.setCustomCoverToCache(current, renderTextCover(current.title, current.author).inputStream())
        updateManga.awaitUpdateCoverLastModified(current.id)
    }

    private suspend fun fanficsCategoryId(): Long {
        getCategories.await().find { it.name == CATEGORY }?.let { return it.id }
        createCategoryWithName.await(CATEGORY)
        return getCategories.await().first { it.name == CATEGORY }.id
    }

    companion object {
        const val CATEGORY = "Fanfics"
    }
}
