package es.zenvairo.nextcloudsync.fuse

/**
 * Minimal subset of the kernel FUSE wire protocol (see `include/uapi/linux/fuse.h`), targeting
 * the conservative baseline major=7 minor=7 - the original 2.6.14-era protocol, supported by
 * every FUSE-capable kernel in existence, with no optional extensions (writeback cache, async
 * reads, etc.) negotiated, keeping every struct layout fixed-size and simple.
 */
object FuseOpcode {
    const val LOOKUP = 1
    const val FORGET = 2 // no reply expected
    const val GETATTR = 3
    const val OPEN = 14
    const val READ = 15
    const val RELEASE = 18
    const val FLUSH = 25
    const val INIT = 26
    const val READDIR = 28
    const val OPENDIR = 27
    const val RELEASEDIR = 29
    const val DESTROY = 38
}

object Errno {
    const val OK = 0
    const val ENOENT = -2
    const val EIO = -5
    const val EBADF = -9
    const val ENOSYS = -38
}

object FuseMode {
    const val S_IFDIR = 0x4000
    const val S_IFREG = 0x8000
}

const val FUSE_ROOT_ID = 1L
