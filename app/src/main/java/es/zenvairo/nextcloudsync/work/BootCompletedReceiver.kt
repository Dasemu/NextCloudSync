package es.zenvairo.nextcloudsync.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        syncScheduler.requestExpeditedSync()
        ContextCompat.startForegroundService(context, Intent(context, FolderWatchService::class.java))
        // FUSE mounts don't survive a reboot, and neither do the root processes serving them.
        VirtualRemountWorker.enqueue(context)
    }
}
