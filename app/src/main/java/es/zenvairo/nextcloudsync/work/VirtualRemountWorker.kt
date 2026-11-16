package es.zenvairo.nextcloudsync.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import es.zenvairo.nextcloudsync.data.db.dao.VirtualFolderDao
import es.zenvairo.nextcloudsync.domain.vfs.VirtualFolderManager
import es.zenvairo.nextcloudsync.root.RootAvailability
import kotlinx.coroutines.flow.first

private const val TAG = "VirtualRemountWorker"
const val VIRTUAL_REMOUNT_WORK = "virtual_remount"

/**
 * Re-establishes the enabled virtual folders' mounts after a reboot - FUSE mounts and the root
 * processes serving them do not survive one.
 *
 * This only works if the root manager is set to grant this app root without asking: a permission
 * prompt can't be answered from a boot-time worker with no UI. When root isn't there the failure is
 * recorded on each folder so the reason is visible in the virtual folders screen, and the user
 * mounts manually.
 */
@HiltWorker
class VirtualRemountWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val virtualFolderDao: VirtualFolderDao,
    private val virtualFolderManager: VirtualFolderManager,
    private val rootAvailability: RootAvailability
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Skip anything already mounted: the root processes outlive the app's own, so this worker
        // must be safe to run when some or all of the mounts are still up. Without this it would
        // tear down a live mount only to rebuild it. That isn't hypothetical - this ROM re-delivers
        // BOOT_COMPLETED every time a force-stopped package is launched again, not just at boot,
        // so this runs far more often than its name suggests.
        val folders = virtualFolderDao.observeAll().first()
            .filter { it.enabled && !virtualFolderManager.isMounted(it) }
        if (folders.isEmpty()) return Result.success()

        // A boot-time probe is the one case where asking for root unprompted is right: the user
        // already asked for these mounts by enabling the folders.
        rootAvailability.invalidate()
        if (!rootAvailability.isAvailable()) {
            Log.i(TAG, "root not granted at boot, leaving ${folders.size} folder(s) unmounted")
            folders.forEach {
                virtualFolderDao.updateError(it.id, "root was not granted at boot; mount manually")
            }
            return Result.success()
        }

        folders.forEach { virtualFolderManager.mount(it) }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                VIRTUAL_REMOUNT_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<VirtualRemountWorker>().build()
            )
        }
    }
}
