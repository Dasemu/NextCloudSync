package es.zenvairo.nextcloudsync.fuse

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Parsed `struct fuse_in_header` (40 bytes) - present at the start of every kernel request. */
data class FuseInHeader(
    val len: Int,
    val opcode: Int,
    val unique: Long,
    val nodeId: Long,
    val uid: Int,
    val gid: Int,
    val pid: Int
) {
    companion object {
        const val SIZE = 40

        fun parse(buf: ByteBuffer): FuseInHeader {
            val len = buf.int
            val opcode = buf.int
            val unique = buf.long
            val nodeId = buf.long
            val uid = buf.int
            val gid = buf.int
            val pid = buf.int
            buf.int // padding
            return FuseInHeader(len, opcode, unique, nodeId, uid, gid, pid)
        }
    }
}

/** `struct fuse_read_in` (40 bytes) - shared shape used by both READ and READDIR requests. */
data class FuseReadIn(val fh: Long, val offset: Long, val size: Int) {
    companion object {
        fun parse(buf: ByteBuffer): FuseReadIn {
            val fh = buf.long
            val offset = buf.long
            val size = buf.int
            buf.int // read_flags
            buf.long // lock_owner
            buf.int // flags
            buf.int // padding
            return FuseReadIn(fh, offset, size)
        }
    }
}

data class FuseInitIn(val major: Int, val minor: Int, val maxReadahead: Int) {
    companion object {
        fun parse(buf: ByteBuffer): FuseInitIn {
            val major = buf.int
            val minor = buf.int
            val maxReadahead = buf.int
            // flags and any newer trailing fields ignored.
            return FuseInitIn(major, minor, maxReadahead)
        }
    }
}

/** One entry's metadata, mirrors enough of `struct fuse_attr` (88 bytes) for a read-only tree. */
data class VNodeAttr(
    val inode: Long,
    val isDirectory: Boolean,
    val size: Long,
    val mtimeSeconds: Long
)

/** Builds FUSE reply payloads. Every writer returns just the *body* bytes after the out_header. */
object FuseReplyBodies {

    fun attr(buf: ByteBuffer, attr: VNodeAttr, ownerUid: Int, ownerGid: Int) {
        val mode = (if (attr.isDirectory) FuseMode.S_IFDIR else FuseMode.S_IFREG) or 0x1ED // 0755
        buf.putLong(attr.inode)                 // ino
        buf.putLong(if (attr.isDirectory) 0L else attr.size) // size
        buf.putLong((attr.size + 511) / 512)     // blocks
        buf.putLong(attr.mtimeSeconds)           // atime
        buf.putLong(attr.mtimeSeconds)           // mtime
        buf.putLong(attr.mtimeSeconds)           // ctime
        buf.putInt(0); buf.putInt(0); buf.putInt(0) // {a,m,c}timensec
        buf.putInt(mode)
        buf.putInt(if (attr.isDirectory) 2 else 1) // nlink
        buf.putInt(ownerUid)
        buf.putInt(ownerGid)
        buf.putInt(0) // rdev
        buf.putInt(4096) // blksize
        buf.putInt(0) // padding
    }

    fun entryOut(buf: ByteBuffer, attr: VNodeAttr, ownerUid: Int, ownerGid: Int, entryValidSeconds: Long = 1) {
        buf.putLong(attr.inode)       // nodeid
        buf.putLong(0)                // generation
        buf.putLong(entryValidSeconds) // entry_valid
        buf.putLong(entryValidSeconds) // attr_valid
        buf.putInt(0)                 // entry_valid_nsec
        buf.putInt(0)                 // attr_valid_nsec
        attr(buf, attr, ownerUid, ownerGid)
    }

    fun attrOut(buf: ByteBuffer, attr: VNodeAttr, ownerUid: Int, ownerGid: Int, attrValidSeconds: Long = 1) {
        buf.putLong(attrValidSeconds) // attr_valid
        buf.putInt(0)                 // attr_valid_nsec
        buf.putInt(0)                 // dummy padding
        attr(buf, attr, ownerUid, ownerGid)
    }

    fun openOut(buf: ByteBuffer, fh: Long) {
        buf.putLong(fh)
        buf.putInt(0) // open_flags
        buf.putInt(0) // padding
    }

    /**
     * Full modern `fuse_init_out` (64 bytes on current kernels), not just the bare major/minor/
     * max_readahead/flags baseline. A minimal 16-byte reply was tried first (declaring minor=7,
     * the original no-extensions protocol) but this device's kernel kept sending newer-shaped
     * requests regardless (e.g. GETATTR arrived with a full 16-byte fuse_getattr_in body, which
     * only exists from minor 9 onward) and every reply whose size depended on the negotiated
     * minor - just GETATTR, empirically - was then rejected by the kernel with EINVAL. Matching
     * the kernel's own expectations here instead of the historical bare-minimum reply avoids that.
     */
    fun initOut(buf: ByteBuffer, minor: Int, maxReadahead: Int, maxWrite: Int) {
        buf.putInt(7)             // major
        buf.putInt(minor)
        buf.putInt(maxReadahead)  // echo back
        buf.putInt(0)             // flags - no optional features requested
        buf.putShort(0)           // max_background
        buf.putShort(0)           // congestion_threshold
        buf.putInt(maxWrite)
        buf.putInt(1_000_000_000) // time_gran: 1 second, in nanoseconds
        buf.putShort(0)           // max_pages
        buf.putShort(0)           // map_alignment
        buf.putInt(0)             // flags2
        buf.putInt(0)             // max_stack_depth
        buf.putShort(0)           // request_timeout
        repeat(11) { buf.putShort(0) } // unused
    }

    /** One `struct fuse_dirent`, padded to an 8-byte boundary as the kernel requires. */
    fun dirent(buf: ByteBuffer, inode: Long, nextOffset: Long, isDirectory: Boolean, name: String) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        buf.putLong(inode)
        buf.putLong(nextOffset)
        buf.putInt(nameBytes.size)
        buf.putInt(if (isDirectory) FuseMode.S_IFDIR else FuseMode.S_IFREG)
        buf.put(nameBytes)
        val padding = (8 - (nameBytes.size % 8)) % 8
        repeat(padding) { buf.put(0) }
    }

    fun direntSize(name: String): Int {
        val nameLen = name.toByteArray(Charsets.UTF_8).size
        val padding = (8 - (nameLen % 8)) % 8
        return 8 + 8 + 4 + 4 + nameLen + padding
    }
}

fun newLeBuffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
