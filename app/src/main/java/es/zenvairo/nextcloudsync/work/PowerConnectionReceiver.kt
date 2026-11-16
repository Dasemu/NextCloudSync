package es.zenvairo.nextcloudsync.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PowerConnectionReceiver : BroadcastReceiver() {

    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return
        syncScheduler.requestExpeditedSync()
    }
}
