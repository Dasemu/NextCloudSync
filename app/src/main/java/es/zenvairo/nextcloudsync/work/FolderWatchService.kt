package es.zenvairo.nextcloudsync.work

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import es.zenvairo.nextcloudsync.NOTIFICATION_CHANNEL_SYNC
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.data.db.dao.SyncFolderDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val DEBOUNCE_MILLIS = 10_000L
private const val WATCH_MASK = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MODIFY or
    FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE

/**
 * Keeps a lightweight recursive [FileObserver] on every enabled synced folder so local edits
 * trigger an expedited sync almost immediately, instead of waiting for the next periodic run.
 * This is the event-driven alternative to polling and is what actually saves battery in practice.
 */
@AndroidEntryPoint
class FolderWatchService : Service() {

    @Inject lateinit var syncFolderDao: SyncFolderDao
    @Inject lateinit var syncScheduler: SyncScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val observers = mutableListOf<RecursiveFileObserver>()
    private var debounceJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { attachObservers() }
        return START_STICKY
    }

    override fun onDestroy() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun attachObservers() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        val folders = syncFolderDao.getEnabled()
        for (folder in folders) {
            val root = File(folder.localPath)
            if (!root.exists()) continue
            val observer = RecursiveFileObserver(root) { onLocalChange() }
            observer.startWatching()
            observers += observer
        }
    }

    private fun onLocalChange() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            syncScheduler.requestExpeditedSync()
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .setSilent(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    companion object {
        const val NOTIFICATION_ID = 43
    }
}

/** Watches [root] and every subdirectory beneath it, re-attaching to new subdirectories as they appear. */
private class RecursiveFileObserver(
    private val root: File,
    private val onChange: () -> Unit
) {
    private val childObservers = mutableMapOf<String, FileObserver>()
    private val selfObserver = createObserver(root)

    fun startWatching() {
        selfObserver.startWatching()
        root.listFiles()?.filter { it.isDirectory }?.forEach { attachChild(it) }
    }

    fun stopWatching() {
        selfObserver.stopWatching()
        childObservers.values.forEach { it.stopWatching() }
        childObservers.clear()
    }

    private fun attachChild(dir: File) {
        if (childObservers.containsKey(dir.path)) return
        val observer = createObserver(dir)
        observer.startWatching()
        childObservers[dir.path] = observer
        dir.listFiles()?.filter { it.isDirectory }?.forEach { attachChild(it) }
    }

    @Suppress("DEPRECATION")
    private fun createObserver(dir: File): FileObserver = object : FileObserver(dir.path, WATCH_MASK) {
        override fun onEvent(event: Int, path: String?) {
            val masked = event and FileObserver.ALL_EVENTS
            if (masked == FileObserver.CREATE || masked == FileObserver.MOVED_TO) {
                path?.let { name ->
                    val child = File(dir, name)
                    if (child.isDirectory) attachChild(child)
                }
            }
            onChange()
        }
    }
}
