package es.zenvairo.nextcloudsync.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import es.zenvairo.nextcloudsync.domain.model.ConflictPolicy
import es.zenvairo.nextcloudsync.domain.model.SyncMode

@Entity(tableName = "sync_folders")
data class SyncFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    /** Absolute path on device storage, e.g. /storage/emulated/0/DCIM/Camera */
    val localPath: String,
    /** Path relative to the WebDAV files root, e.g. /Photos/Camera */
    val remotePath: String,
    val mode: SyncMode = SyncMode.BIDIRECTIONAL,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
    /** Null = use the app-wide default from SettingsRepository. */
    val constraintsJson: String? = null,
    val enabled: Boolean = true,
    val lastSyncAtMillis: Long? = null,
    val lastSyncSucceeded: Boolean = true,
    val lastErrorMessage: String? = null
)
