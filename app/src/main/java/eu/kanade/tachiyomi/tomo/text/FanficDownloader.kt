package eu.kanade.tachiyomi.tomo.text

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.source.service.SourceManager
import java.io.File

/** Chapters of fanfics kept on the phone as HTML, so they read without connection or while their site is down. */
@Inject
@SingleIn(AppScope::class)
class TextCache(context: Context) {
    private val dir = File(context.filesDir, "text_cache")

    /** Bumped on every chapter saved, so screens counting downloaded chapters refresh. */
    private val _saved = MutableStateFlow(0)
    val saved: StateFlow<Int> = _saved.asStateFlow()

    fun has(chapterId: Long): Boolean = file(chapterId).exists()

    fun read(chapterId: Long): String? = file(chapterId).takeIf { it.exists() }?.readText()

    fun write(chapterId: Long, html: String) {
        dir.mkdirs()
        // Written aside and renamed, so a cut-off download never leaves half a chapter
        val temp = File(dir, "$chapterId.tmp")
        temp.writeText(html)
        temp.renameTo(file(chapterId))
        _saved.update { it + 1 }
    }

    private fun file(chapterId: Long) = File(dir, "$chapterId.html")
}

/**
 * Downloads every chapter of the fanfics the user follows that is not on the phone yet (RF-30h). Runs after following
 * a work and after each library update; sources' own rate limits keep it polite.
 */
@Inject
@SingleIn(AppScope::class)
class FanficDownloader(
    private val context: Context,
    private val getFavorites: GetFavorites,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val sourceManager: SourceManager,
    private val textCache: TextCache,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
) {
    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<Worker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        // Appended, so a work followed during a run is picked up by the next one
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    suspend fun downloadAll() {
        val unreachable = mutableSetOf<Long>()
        getFavorites.await().filter { it.source in TEXT_SOURCE_IDS }.forEach { manga ->
            val source = sourceManager.get(manga.source) as? TextSource ?: return@forEach
            // A work followed from search has no chapter list until it is fetched once
            if (getChaptersByMangaId.await(manga.id).isEmpty() && manga.source !in unreachable) {
                updateMangaFromRemote(source, manga, fetchChapters = true)
                    .onFailure { if (classifyFailure(it) in SITE_WIDE) unreachable += manga.source }
            }
            for (chapter in getChaptersByMangaId.await(manga.id)) {
                if (manga.source in unreachable) return@forEach
                if (textCache.has(chapter.id)) continue
                try {
                    textCache.write(chapter.id, source.getChapterHtml(chapter.url))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Could not download chapter ${chapter.id}" }
                    // A site that is down or offline fails every chapter; the next run tries again
                    if (classifyFailure(e) in SITE_WIDE) unreachable += manga.source
                }
            }
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            applicationContext.appGraph.fanficDownloader.downloadAll()
            return Result.success()
        }
    }

    private companion object {
        const val WORK_NAME = "TomoFanficDownload"

        val SITE_WIDE = setOf(
            SourceUnavailableException.Reason.DOWN,
            SourceUnavailableException.Reason.OFFLINE,
            SourceUnavailableException.Reason.VERIFICATION,
        )
    }
}
