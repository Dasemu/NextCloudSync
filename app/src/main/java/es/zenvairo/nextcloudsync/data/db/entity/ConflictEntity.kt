package es.zenvairo.nextcloudsync.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conflicts")
data class ConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val relativePath: String,
    val detectedAtMillis: Long,
    val localSize: Long,
    val localMtimeMillis: Long,
    val remoteEtag: String?,
    val resolved: Boolean = false
)
