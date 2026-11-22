package eu.kanade.tachiyomi.tomo.sync

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreWorker
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import java.util.UUID

/**
 * Library and progress shared between the user's devices through a folder another app keeps in sync, like Syncthing
 * (RF-38, first stage). Each device leaves its own backup there when the app goes to the background and, when the app
 * opens, merges the ones of the other devices: restoring never deletes and keeps the furthest progress.
 */
@Inject
@SingleIn(AppScope::class)
class FolderSync(
    private val context: Context,
    preferenceStore: PreferenceStore,
    private val backupCreatorFactory: BackupCreator.Factory,
) : DefaultLifecycleObserver {

    // App state keys stay out of backups: another device must not get this folder, id or memory
    val folder = preferenceStore.getString(Preference.appStateKey("tomo_sync_folder"), "")
    private val device = preferenceStore.getString(Preference.appStateKey("tomo_sync_device"), "")
    private val seen = preferenceStore.getString(Preference.appStateKey("tomo_sync_seen"), "")

    private val workManager get() = WorkManager.getInstance(context)

    fun attach() = ProcessLifecycleOwner.get().lifecycle.addObserver(this)

    override fun onStart(owner: LifecycleOwner) = enqueue(export = false)

    override fun onStop(owner: LifecycleOwner) = enqueue(export = true)

    private fun enqueue(export: Boolean) {
        if (folder.get().isEmpty()) return
        val request = OneTimeWorkRequestBuilder<Worker>().setInputData(workDataOf(EXPORT to export)).build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun dir(): UniFile? = folder.get().takeIf { it.isNotEmpty() }?.let { UniFile.fromUri(context, it.toUri()) }

    private fun deviceId(): String = device.get().ifEmpty {
        UUID.randomUUID().toString().take(8).also(device::set)
    }

    private suspend fun export() {
        val dir = dir() ?: return
        // A merge in progress would be half in the file
        if (BackupRestoreWorker.isRunning(workManager)) return
        val name = syncFileName(deviceId())
        val file = dir.findFile(name) ?: dir.createFile(name) ?: return
        backupCreatorFactory.create(false).backup(file.uri, BACKUP_OPTIONS)
    }

    private suspend fun import() {
        val dir = dir() ?: return
        if (BackupRestoreWorker.isRunning(workManager)) return
        val files = dir.listFiles().orEmpty().mapNotNull { file -> file.name?.let { it to file.lastModified() } }
        val seenFiles = parseSeen(seen.get())
        // Restores run one at a time; the next device is merged the next time the app opens
        val (name, modified) = filesToImport(files, deviceId(), seenFiles).firstOrNull() ?: return
        val file = dir.findFile(name) ?: return
        seen.set(formatSeen(seenFiles + (name to modified)))
        BackupRestoreWorker.start(workManager, file.uri, RESTORE_OPTIONS, sync = true)
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val sync = applicationContext.appGraph.folderSync
            try {
                if (inputData.getBoolean(EXPORT, false)) sync.export() else sync.import()
            } catch (e: Exception) {
                // A folder that went away or a full disk: the next open or close tries again
                logcat(LogPriority.ERROR, e)
            }
            return Result.success()
        }
    }

    private companion object {
        const val WORK_NAME = "TomoFolderSync"
        const val EXPORT = "export"

        val BACKUP_OPTIONS = BackupOptions(
            appSettings = false,
            extensionStores = false,
            sourceSettings = false,
            privateSettings = false,
        )
        val RESTORE_OPTIONS = RestoreOptions(appSettings = false, extensionStores = false, sourceSettings = false)
    }
}

private const val PREFIX = "tomo-sync-"
private const val SUFFIX = ".tachibk"

internal fun syncFileName(device: String) = "$PREFIX$device$SUFFIX"

/** Backups of the other devices that changed since they were last merged here, oldest change first. */
internal fun filesToImport(
    files: List<Pair<String, Long>>,
    device: String,
    seen: Map<String, Long>,
): List<Pair<String, Long>> = files
    .filter { (name, modified) ->
        name.startsWith(PREFIX) && name.endsWith(SUFFIX) && name != syncFileName(device) &&
            modified > (seen[name] ?: 0)
    }
    .sortedBy { it.second }

internal fun parseSeen(text: String): Map<String, Long> = text.lines().mapNotNull { line ->
    val name = line.substringBeforeLast('\t', "")
    line.substringAfterLast('\t').toLongOrNull()?.takeIf { name.isNotEmpty() }?.let { name to it }
}.toMap()

internal fun formatSeen(seen: Map<String, Long>): String = seen.entries.joinToString("\n") { "${it.key}\t${it.value}" }
