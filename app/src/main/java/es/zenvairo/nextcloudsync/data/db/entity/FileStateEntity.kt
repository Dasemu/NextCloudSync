package es.zenvairo.nextcloudsync.data.db.entity

import androidx.room.Entity

/**
 * The last known-good synced state of one file within one [SyncFolderEntity]. This is the
 * "third band" of the three-way diff: without it, a two-way compare of local vs. remote can't
 * tell a local deletion from a file that never existed locally.
 */
@Entity(tableName = "file_state", primaryKeys = ["folderId", "relativePath"])
data class FileStateEntity(
    val folderId: Long,
    val relativePath: String,
    val isDirectory: Boolean,
    val localSize: Long,
    val localMtimeMillis: Long,
    val remoteEtag: String?,
    val remoteFileId: String?,
    val lastSyncedAtMillis: Long
)
