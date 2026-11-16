package es.zenvairo.nextcloudsync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import es.zenvairo.nextcloudsync.domain.model.NetworkTrigger
import es.zenvairo.nextcloudsync.domain.model.SyncConstraints
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    /** Re-applies the periodic sync request, e.g. after the user changes global trigger settings. */
    fun reschedulePeriodic(constraints: SyncConstraints) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            constraints.intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES
        )
            .setConstraints(toWorkManagerConstraints(constraints))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(SyncWorker.TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Fired by [FolderWatchService], boot, or charger-connect events - runs as soon as constraints allow. */
    fun requestExpeditedSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(SyncWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_EXPEDITED_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(SyncWorker.TAG)
    }

    private fun toWorkManagerConstraints(constraints: SyncConstraints): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                when (constraints.networkTrigger) {
                    NetworkTrigger.ANY -> NetworkType.CONNECTED
                    NetworkTrigger.UNMETERED -> NetworkType.UNMETERED
                    NetworkTrigger.WIFI_ONLY -> NetworkType.UNMETERED
                }
            )
            .setRequiresBatteryNotLow(constraints.minBatteryPercent > 0)
            .setRequiresCharging(constraints.chargingOnly)
            .build()
}
