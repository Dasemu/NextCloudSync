package es.zenvairo.nextcloudsync.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A remote folder exposed as a read-only FUSE mount (the root-only "virtual folders" mode).
 *
 * Deliberately separate from [SyncFolderEntity]: a virtual folder transfers nothing until a file is
 * opened and has no local counterpart to reconcile, so it shares none of the sync engine's state.
 */
@Entity(tableName = "virtual_folders")
data class VirtualFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    /** Path relative to the WebDAV files root, e.g. /Photos - "/" for the whole account. */
    val remotePath: String,
    /** Cap on the on-device hydration cache for this folder, evicted LRU once exceeded. */
    val cacheMaxBytes: Long = 512L * 1024 * 1024,
    /** Whether to mount it; mounting always needs root granted at that moment. */
    val enabled: Boolean = true,
    val lastErrorMessage: String? = null
)
