package es.zenvairo.nextcloudsync.fuse

import android.util.Log
import es.zenvairo.nextcloudsync.data.remote.DavException
import es.zenvairo.nextcloudsync.data.remote.RemoteEntry
import es.zenvairo.nextcloudsync.data.remote.WebDavClient
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "VirtualFileTree"

/** How long a directory's PROPFIND result is reused before being re-fetched. */
private const val LISTING_TTL_MILLIS = 30_000L

/**
 * Maps the remote Nextcloud tree onto FUSE inodes, backed by [WebDavClient.propfind].
 *
 * FUSE addresses everything by inode number, so every path we hand the kernel needs a stable one for
 * as long as the kernel might refer back to it. Inodes are therefore assigned once per path and kept
 * (the mount is read-only and short-lived, so the map is never pruned - a FORGET-driven refcount
 * would be the thing to add if that changes).
 *
 * Directory listings are cached briefly: the kernel issues a LOOKUP per entry right after a READDIR,
 * and without a cache each one would be its own PROPFIND round-trip.
 *
 * Called from every worker thread in [VirtualFuseServer]'s pool, so all the maps below are
 * concurrent collections rather than plain HashMaps, and both PROPFIND paths (listing a directory,
 * fetching a single entry's attrs) dedupe concurrent requests for the same path - otherwise N
 * workers racing the same freshly-uncached directory (a LOOKUP storm right after READDIR) would
 * each fire their own PROPFIND for identical data.
 */
class VirtualFileTree(
    private val webDav: WebDavClient,
    private val rootPath: String
) {
    private data class Listing(val entries: List<RemoteEntry>, val fetchedAtMillis: Long)

    private val inodeToPath = ConcurrentHashMap<Long, String>()
    private val pathToInode = ConcurrentHashMap<String, Long>()
    private val nextInode = AtomicLong(FUSE_ROOT_ID + 1)

    private val listings = ConcurrentHashMap<String, Listing>()
    private val attrs = ConcurrentHashMap<String, VNodeAttr>()

    /** In-flight PROPFIND(depth=1) calls, keyed by directory path, so concurrent callers share one fetch. */
    private val inFlightListings = ConcurrentHashMap<String, CompletableFuture<List<RemoteEntry>>>()

    /** In-flight PROPFIND(depth=0) calls, keyed by entry path, so concurrent callers share one fetch. */
    private val inFlightAttrs = ConcurrentHashMap<String, CompletableFuture<VNodeAttr?>>()

    init {
        inodeToPath[FUSE_ROOT_ID] = rootPath
        pathToInode[rootPath] = FUSE_ROOT_ID
        attrs[rootPath] = VNodeAttr(FUSE_ROOT_ID, isDirectory = true, size = 0, mtimeSeconds = nowSeconds())
    }

    fun pathFor(inode: Long): String? = inodeToPath[inode]

    private fun inodeFor(path: String): Long = pathToInode.computeIfAbsent(path) {
        val inode = nextInode.getAndIncrement()
        inodeToPath[inode] = path
        inode
    }

    fun attrFor(inode: Long): VNodeAttr? {
        val path = inodeToPath[inode] ?: return null
        attrs[path]?.let { return it }
        // Not seen via a parent listing yet (the kernel can GETATTR an inode whose cached listing
        // has since expired), so ask the server about this one entry directly.
        return fetchAttr(path)
    }

    private fun fetchAttr(path: String): VNodeAttr? {
        var isOwner = false
        val future = inFlightAttrs.computeIfAbsent(path) { isOwner = true; CompletableFuture() }
        if (isOwner) {
            try {
                val entry = runBlocking { webDav.propfind(path, depth = 0) }.firstOrNull()
                val attr = entry?.let { toAttr(it).also { a -> attrs[path] = a } }
                future.complete(attr)
            } catch (e: Exception) {
                Log.w(TAG, "propfind(depth=0) failed for $path", e)
                future.complete(null)
            } finally {
                inFlightAttrs.remove(path, future)
            }
        }
        return try {
            future.get()
        } catch (e: Exception) {
            Log.w(TAG, "propfind(depth=0) failed for $path", e)
            null
        }
    }

    /** Children of a directory inode, or null if it isn't a directory we can list. */
    fun childrenOf(inode: Long): List<RemoteEntry>? {
        val path = inodeToPath[inode] ?: return null
        return listingOf(path)
    }

    /** Resolves one name inside a directory inode, or null if it doesn't exist. */
    fun lookup(parentInode: Long, name: String): VNodeAttr? {
        val parentPath = inodeToPath[parentInode] ?: return null
        val childPath = joinPath(parentPath, name)
        // Prefer the parent's listing: a LOOKUP storm after READDIR is the common case, and it
        // already holds everything we need.
        listingOf(parentPath)?.firstOrNull { basename(it.path) == name }?.let { entry ->
            return toAttr(entry).also { attrs[childPath] = it }
        }
        return attrFor(inodeFor(childPath))
    }

    private fun listingOf(path: String): List<RemoteEntry>? {
        val cached = listings[path]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMillis < LISTING_TTL_MILLIS) {
            return cached.entries
        }
        var isOwner = false
        val future = inFlightListings.computeIfAbsent(path) { isOwner = true; CompletableFuture() }
        if (isOwner) {
            try {
                val entries = runBlocking { webDav.propfind(path, depth = 1) }
                Log.i(TAG, "listed ${entries.size} entries under $path")
                listings[path] = Listing(entries, System.currentTimeMillis())
                entries.forEach { attrs[it.path] = toAttr(it) }
                future.complete(entries)
            } catch (e: DavException) {
                Log.w(TAG, "propfind failed for $path", e)
                future.complete(listings[path]?.entries ?: cached?.entries)
            } catch (e: Exception) {
                Log.w(TAG, "propfind failed for $path", e)
                future.complete(listings[path]?.entries ?: cached?.entries)
            } finally {
                inFlightListings.remove(path, future)
            }
        }
        return try {
            future.get()
        } catch (e: Exception) {
            cached?.entries
        }
    }

    private fun toAttr(entry: RemoteEntry) = VNodeAttr(
        inode = inodeFor(entry.path),
        isDirectory = entry.isDirectory,
        size = entry.size,
        mtimeSeconds = entry.lastModifiedMillis / 1000
    )

    private fun nowSeconds() = System.currentTimeMillis() / 1000
}

fun basename(path: String): String = path.trimEnd('/').substringAfterLast('/')

fun joinPath(parent: String, name: String): String =
    if (parent.endsWith("/")) "$parent$name" else "$parent/$name"
