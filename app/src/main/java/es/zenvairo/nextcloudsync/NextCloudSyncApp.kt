package es.zenvairo.nextcloudsync

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

const val NOTIFICATION_CHANNEL_SYNC = "sync_activity"
const val NOTIFICATION_CHANNEL_ALERTS = "sync_alerts"

@HiltAndroidApp
class NextCloudSyncApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_SYNC,
                getString(R.string.notification_channel_sync_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notification_channel_sync_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ALERTS,
                getString(R.string.notification_channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = getString(R.string.notification_channel_alerts_description) }
        )
    }
}
