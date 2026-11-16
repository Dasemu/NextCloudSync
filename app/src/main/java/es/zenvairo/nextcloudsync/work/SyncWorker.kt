package es.zenvairo.nextcloudsync.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import es.zenvairo.nextcloudsync.NOTIFICATION_CHANNEL_SYNC
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.data.db.dao.SyncEventDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncFolderDao
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventEntity
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventType
import es.zenvairo.nextcloudsync.data.prefs.SettingsRepository
import es.zenvairo.nextcloudsync.domain.sync.PathFilter
import es.zenvairo.nextcloudsync.domain.sync.SyncEngineFactory
import kotlinx.coroutines.flow.first

/**
 * Runs one sync pass across all enabled folders. Scheduled periodically via [SyncScheduler] and
 * also enqueued as expedited work on file-change/charger/boot triggers. Per-folder network/battery
 * checks that WorkManager's [androidx.work.Constraints] can't express (exact %, quiet hours, SSID)
 * are evaluated here via [ConstraintsEvaluator] and cause a retry rather than a hard failure.
 *
 * Promotes itself to a foreground service (dataSync type) only while actually transferring, and
 * only if [ForegroundBudgetTracker] says the Android 15+ 6h/24h dataSync budget isn't exhausted -
 * otherwise it runs as plain background work rather than risking a system-forced timeout mid-sync.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncFolderDao: SyncFolderDao,
    private val syncEventDao: SyncEventDao,
    private val settingsRepository: SettingsRepository,
    private val syncEngineFactory: SyncEngineFactory,
    private val constraintsEvaluator: ConstraintsEvaluator,
    private val budgetTracker: ForegroundBudgetTracker
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val engine = syncEngineFactory.create() ?: return Result.success()
        val folders = syncFolderDao.getEnabled()
        if (folders.isEmpty()) return Result.success()

        if (budgetTracker.hasBudgetRemaining()) {
            runCatching { setForeground(buildForegroundInfo(0, folders.size)) }
        }

        val startedAt = System.currentTimeMillis()
        val defaultConstraints = settingsRepository.defaultConstraints.first()
        var anyRetryNeeded = false
        var anyFailure = false

        folders.forEachIndexed { index, folder ->
            val constraints = folder.constraintsJson?.let { runCatching { settingsRepository.decode(it) }.getOrNull() }
                ?: defaultConstraints

            if (!constraintsEvaluator.canRunNow(constraints)) {
                anyRetryNeeded = true
                return@forEachIndexed
            }

            runCatching { setForeground(buildForegroundInfo(index + 1, folders.size)) }
            syncEventDao.insert(
                SyncEventEntity(
                    folderId = folder.id,
                    type = SyncEventType.RUN_STARTED,
                    relativePath = null,
                    message = null,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            try {
                val summary = engine.syncFolder(folder, PathFilter(constraints))
                syncFolderDao.updateSyncResult(
                    folder.id,
                    System.currentTimeMillis(),
                    succeeded = summary.errors.isEmpty(),
                    error = summary.errors.firstOrNull()
                )
                if (summary.errors.isNotEmpty()) anyFailure = true
            } catch (e: Exception) {
                syncFolderDao.updateSyncResult(folder.id, System.currentTimeMillis(), succeeded = false, error = e.message)
                anyFailure = true
            } finally {
                syncEventDao.insert(
                    SyncEventEntity(
                        folderId = folder.id,
                        type = SyncEventType.RUN_FINISHED,
                        relativePath = null,
                        message = null,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
            }
        }

        budgetTracker.recordUsage(System.currentTimeMillis() - startedAt)

        return when {
            anyFailure || anyRetryNeeded -> Result.retry()
            else -> Result.success()
        }
    }

    private fun buildForegroundInfo(current: Int, total: Int): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(applicationContext.getString(R.string.notification_syncing_title, applicationContext.getString(R.string.app_name)))
            .setContentText(if (total > 0) applicationContext.getString(R.string.notification_syncing_progress, current, total) else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "periodic_sync"
        const val UNIQUE_EXPEDITED_NAME = "expedited_sync"
        const val TAG = "nextcloud_sync"
        const val NOTIFICATION_ID = 42
    }
}
