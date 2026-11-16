package es.zenvairo.nextcloudsync.fuse

import android.util.Log
import es.zenvairo.nextcloudsync.data.remote.WebDavClient
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "HydrationCache"

/**
 * Downloads a remote file the first time it is opened and serves subsequent reads from local disk.
 *
 * "Hydration" is the whole point of the virtual-files mode: the tree shows real sizes without any
 * transfer, and bytes only move when something actually opens a file.
 *
 * Downloads happen on whichever FUSE worker thread handled the OPEN, so a large file only blocks
 * that request rather than the whole mount. That is still the simple, correct-first version;
 * streaming range requests would be the way to serve reads before the download finishes.
 */
class HydrationCache(
    private val webDav: WebDavClient,
    private val cacheDir: File,
    private val maxBytes: Long
) {
    init {
        cacheDir.mkdirs()
    }

    /**
     * In-flight downloads, keyed by remote path, so two concurrent OPENs of the same file share one
     * download instead of both writing (via `WebDavClient.download`'s own `.part`-then-rename) into
     * the same destination at once.
     */
    private val inFlightHydrations = ConcurrentHashMap<String, CompletableFuture<File?>>()

    /** Local copy of [remotePath], downloading it if not already cached. Null if the fetch failed. */
    fun hydrate(remotePath: String): File? {
        val target = cacheFileFor(remotePath)
        if (target.isFile) {
            target.setLastModified(System.currentTimeMillis()) // touch: drives LRU eviction
            return target
        }
        var isOwner = false
        val future = inFlightHydrations.computeIfAbsent(remotePath) { isOwner = true; CompletableFuture() }
        if (isOwner) {
            try {
                runBlocking { webDav.download(remotePath, target) }
                evictIfOverBudget()
                future.complete(target.takeIf { it.isFile })
            } catch (e: Exception) {
                Log.w(TAG, "hydration failed for $remotePath", e)
                target.delete()
                future.complete(null)
            } finally {
                inFlightHydrations.remove(remotePath, future)
            }
        }
        return try {
            future.get()
        } catch (e: Exception) {
            Log.w(TAG, "hydration failed for $remotePath", e)
            null
        }
    }

    /** Flat cache dir, so the remote path is encoded into the filename rather than nested as dirs. */
    private fun cacheFileFor(remotePath: String): File {
        val safe = remotePath.trim('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(cacheDir, "${remotePath.hashCode().toUInt().toString(16)}_${safe.takeLast(80)}")
    }

    private fun evictIfOverBudget() {
        val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        // Oldest access first - hydrate() touches on every hit, so this is a real LRU order.
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }
}
