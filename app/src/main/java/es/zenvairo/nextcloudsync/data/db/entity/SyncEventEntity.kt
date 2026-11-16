package es.zenvairo.nextcloudsync.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncEventType { UPLOADED, DOWNLOADED, DELETED_LOCAL, DELETED_REMOTE, CONFLICT, ERROR, RUN_STARTED, RUN_FINISHED }

@Entity(tableName = "sync_events")
data class SyncEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val type: SyncEventType,
    val relativePath: String?,
    val message: String?,
    val timestampMillis: Long
)
