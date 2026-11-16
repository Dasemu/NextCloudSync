package es.zenvairo.nextcloudsync.domain.sync

/** Snapshot of one side (local or remote) of a file, as seen right now. */
data class SideSnapshot(
    val exists: Boolean,
    val isDirectory: Boolean = false,
    val size: Long = 0,
    /** Local: mtime millis. Remote: unused for comparison (etag is authoritative). */
    val mtimeMillis: Long = 0,
    /** Remote only: WebDAV etag. Null for local snapshots. */
    val etag: String? = null,
    /** Remote only: Nextcloud stable file id, used to detect renames. */
    val fileId: String? = null
)

enum class SyncAction {
    NO_OP,
    DOWNLOAD,
    UPLOAD,
    DELETE_LOCAL,
    DELETE_REMOTE,
    CONFLICT,
    FORGET // both sides deleted: drop from the index, nothing to transfer
}

data class PlannedChange(
    val relativePath: String,
    val action: SyncAction,
    val isDirectory: Boolean = false
)
