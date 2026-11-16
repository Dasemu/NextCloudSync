package es.zenvairo.nextcloudsync.fuse

import android.system.Os
import android.util.Log
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "VirtualFuseServer"

/** Largest payload we tell the kernel we can accept in a single write (the `max_write` we negotiate). */
private const val MAX_WRITE = 128 * 1024

/**
 * The kernel rejects any read() on /dev/fuse whose buffer is smaller than
 * `max_write + FUSE_BUFFER_HEADER_SIZE` with EINVAL - before delivering any request, so it looks
 * exactly like a broken fd. Hence the read buffer must be strictly larger than [MAX_WRITE].
 */
private const val FUSE_BUFFER_HEADER_SIZE = 4096
private const val READ_BUFFER_SIZE = MAX_WRITE + FUSE_BUFFER_HEADER_SIZE

/** Worker threads processing dispatched requests. Bounded so a burst of opens can't spawn unbounded threads. */
private const val WORKER_THREADS = 6

/**
 * Serves a read-only view of the remote Nextcloud tree over the FUSE protocol: [VirtualFileTree]
 * supplies metadata straight from PROPFIND (so listings cost no transfer) and [HydrationCache]
 * downloads a file's bytes the first time it is opened.
 *
 * There is exactly one reader: [loop] blocks in `Os.read(fuseFd, ...)` on the calling thread and
 * must stay single-threaded - that's what the kernel expects on the other end of /dev/fuse. Each
 * request it receives is copied out of the shared read buffer (the next read() reuses that buffer,
 * so anything handed to a worker has to be its own copy first) and handed to a small worker pool,
 * so a slow op (a large-file download on OPEN, a PROPFIND) blocks only the request that triggered
 * it. The FUSE protocol allows replies out of order - each request's `unique` is echoed back in its
 * reply header, so the kernel matches them up regardless of order - but the replies themselves must
 * not interleave on the wire, so [writeOut] is serialized.
 *
 * Callers should run this on a dedicated background thread and call [stop] to make [loop] return so
 * the loop can exit.
 */
class VirtualFuseServer(
    private val fuseFd: FileDescriptor,
    private val ownerUid: Int,
    private val ownerGid: Int,
    private val tree: VirtualFileTree,
    private val cache: HydrationCache
) {
    @Volatile private var running = true

    /** Open file handles by the `fh` we hand the kernel, so READ doesn't re-resolve the path. */
    private val openFiles = ConcurrentHashMap<Long, RandomAccessFile>()
    private val nextHandle = AtomicLong(1L)

    /** Guards `Os.write(fuseFd, ...)` so two workers' replies can't interleave their bytes. */
    private val writeLock = Any()

    fun stop() {
        running = false
        runCatching { Os.close(fuseFd) }
    }

    fun loop() {
        val pool: ExecutorService = Executors.newFixedThreadPool(WORKER_THREADS)
        try {
            val readBuf = ByteArray(READ_BUFFER_SIZE)
            while (running) {
                // Os.read (a thin pread/read binding) rather than FileInputStream: the stream layer's
                // IoBridge does its own bookkeeping around the fd that /dev/fuse rejects with EINVAL.
                val n = try {
                    Os.read(fuseFd, readBuf, 0, readBuf.size)
                } catch (e: Exception) {
                    if (running) Log.w(TAG, "read() failed, stopping", e)
                    break
                }
                if (n <= 0) break

                // Copy the request out of readBuf before handing it off: a worker may still be
                // parsing/using it by the time this loop comes back around and overwrites readBuf
                // with the next read(). This copy is the only thing that makes it safe to dispatch
                // asynchronously.
                val requestBytes = readBuf.copyOf(n)
                pool.execute { handle(requestBytes) }
            }
        } finally {
            // Let already-dispatched work finish (its reply still has to reach the kernel) rather
            // than abandoning it; awaitTermination bounds how long shutdown can take if something
            // is stuck.
            pool.shutdown()
            runCatching { pool.awaitTermination(30, TimeUnit.SECONDS) }
            openFiles.values.forEach { runCatching { it.close() } }
            openFiles.clear()
        }
    }

    private fun handle(requestBytes: ByteArray) {
        val buf = ByteBuffer.wrap(requestBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val header = FuseInHeader.parse(buf)
        try {
            dispatch(header, buf)
        } catch (e: Exception) {
            Log.w(TAG, "op ${header.opcode} failed", e)
            runCatching { replyError(header.unique, Errno.EIO) }
        }
    }

    private fun dispatch(header: FuseInHeader, body: ByteBuffer) {
        when (header.opcode) {
            FuseOpcode.INIT -> onInit(header, body)
            FuseOpcode.LOOKUP -> onLookup(header, body)
            FuseOpcode.GETATTR -> onGetattr(header)
            FuseOpcode.OPENDIR -> onOpenDir(header)
            FuseOpcode.OPEN -> onOpen(header)
            FuseOpcode.READ -> onRead(header, body)
            FuseOpcode.READDIR -> onReaddir(header, body)
            FuseOpcode.RELEASE -> onRelease(header, body)
            FuseOpcode.RELEASEDIR, FuseOpcode.FLUSH -> replyEmpty(header.unique)
            FuseOpcode.FORGET -> Unit // no reply
            FuseOpcode.DESTROY -> onDestroy(header)
            else -> replyError(header.unique, Errno.ENOSYS)
        }
    }

    private fun onInit(header: FuseInHeader, body: ByteBuffer) {
        val init = FuseInitIn.parse(body)
        val negotiatedMinor = minOf(init.minor, 39)
        val reply = newLeBuffer(64)
        FuseReplyBodies.initOut(reply, minor = negotiatedMinor, maxReadahead = init.maxReadahead, maxWrite = MAX_WRITE)
        replyOk(header.unique, reply)
    }

    private fun onLookup(header: FuseInHeader, body: ByteBuffer) {
        val nameBytes = ByteArray(body.remaining())
        body.get(nameBytes)
        val nulIndex = nameBytes.indexOf(0)
        val effectiveLength = if (nulIndex >= 0) nulIndex else nameBytes.size
        val name = String(nameBytes, 0, effectiveLength, Charsets.UTF_8)

        val attr = tree.lookup(header.nodeId, name) ?: return replyError(header.unique, Errno.ENOENT)
        val reply = newLeBuffer(128)
        FuseReplyBodies.entryOut(reply, attr, ownerUid, ownerGid)
        replyOk(header.unique, reply)
    }

    private fun onGetattr(header: FuseInHeader) {
        val attr = tree.attrFor(header.nodeId) ?: return replyError(header.unique, Errno.ENOENT)
        val reply = newLeBuffer(104)
        FuseReplyBodies.attrOut(reply, attr, ownerUid, ownerGid)
        replyOk(header.unique, reply)
    }

    private fun onOpenDir(header: FuseInHeader) {
        val reply = newLeBuffer(16)
        FuseReplyBodies.openOut(reply, fh = header.nodeId)
        replyOk(header.unique, reply)
    }

    /**
     * Hydrates the file up front so every subsequent READ is a local disk read. This is the slow
     * op the worker pool exists for: it runs on whichever worker picked up this OPEN, so a big
     * download here only blocks that request, not GETATTRs/LOOKUPs for everything else in flight.
     */
    private fun onOpen(header: FuseInHeader) {
        val path = tree.pathFor(header.nodeId) ?: return replyError(header.unique, Errno.ENOENT)
        val local = cache.hydrate(path) ?: return replyError(header.unique, Errno.EIO)
        val handle = nextHandle.getAndIncrement()
        openFiles[handle] = RandomAccessFile(local, "r")
        val reply = newLeBuffer(16)
        FuseReplyBodies.openOut(reply, fh = handle)
        replyOk(header.unique, reply)
    }

    private fun onRelease(header: FuseInHeader, body: ByteBuffer) {
        val releaseIn = FuseReadIn.parse(body)
        openFiles.remove(releaseIn.fh)?.let { runCatching { it.close() } }
        replyEmpty(header.unique)
    }

    private fun onRead(header: FuseInHeader, body: ByteBuffer) {
        val readIn = FuseReadIn.parse(body)
        val file = openFiles[readIn.fh] ?: return replyError(header.unique, Errno.EBADF)
        val wanted = minOf(readIn.size, MAX_WRITE)
        val chunk = ByteArray(wanted)
        // RandomAccessFile isn't thread-safe for concurrent seek+read on the same handle, but the
        // kernel never issues two READs against the same fh concurrently (it serializes per-file),
        // so each handle only ever sees one worker touching it at a time in practice. Still,
        // synchronize per-file so that invariant isn't load-bearing for correctness.
        synchronized(file) {
            file.seek(readIn.offset)
            var filled = 0
            while (filled < wanted) {
                val read = file.read(chunk, filled, wanted - filled)
                if (read <= 0) break
                filled += read
            }
            val reply = newLeBuffer(filled)
            reply.put(chunk, 0, filled)
            replyOk(header.unique, reply)
        }
    }

    private fun onReaddir(header: FuseInHeader, body: ByteBuffer) {
        val readIn = FuseReadIn.parse(body)
        val children = tree.childrenOf(header.nodeId) ?: return replyError(header.unique, Errno.ENOENT)

        // The kernel re-issues READDIR with the offset of the last entry we emitted until we return
        // an empty reply, so we resume from `offset` rather than restarting the listing.
        val reply = newLeBuffer(readIn.size)
        var offset = readIn.offset
        while (offset < children.size) {
            val entry = children[offset.toInt()]
            val name = basename(entry.path)
            val size = FuseReplyBodies.direntSize(name)
            if (reply.position() + size > readIn.size) break
            val attr = tree.lookup(header.nodeId, name)
            FuseReplyBodies.dirent(
                reply,
                inode = attr?.inode ?: 0,
                nextOffset = offset + 1,
                isDirectory = entry.isDirectory,
                name = name
            )
            offset++
        }
        replyOk(header.unique, reply)
    }

    private fun onDestroy(header: FuseInHeader) {
        replyEmpty(header.unique)
        running = false
        // Same trick as stop(): closing the fd is what makes the blocked Os.read() on the reader
        // thread return, since setting `running` alone can't interrupt it. Safe to race with an
        // external stop() call - the second close() just fails and is swallowed.
        runCatching { Os.close(fuseFd) }
    }

    private fun replyOk(unique: Long, body: ByteBuffer) {
        body.flip()
        val bodyLen = body.remaining()
        val out = newLeBuffer(16 + bodyLen)
        out.putInt(16 + bodyLen)
        out.putInt(0)
        out.putLong(unique)
        out.put(body)
        writeOut(out)
    }

    private fun replyEmpty(unique: Long) = replyOk(unique, ByteBuffer.allocate(0))

    private fun replyError(unique: Long, errno: Int) {
        val out = newLeBuffer(16)
        out.putInt(16)
        out.putInt(errno)
        out.putLong(unique)
        writeOut(out)
    }

    private fun writeOut(buf: ByteBuffer) {
        buf.flip()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        // One reply is one Os.write() call, and this lock is what keeps two workers' writes from
        // interleaving their bytes on the fd - the kernel has no framing to recover from that.
        synchronized(writeLock) {
            try {
                Os.write(fuseFd, bytes, 0, bytes.size)
            } catch (e: Exception) {
                if (running) Log.w(TAG, "write() failed, ${bytes.size} bytes", e)
            }
        }
    }
}
