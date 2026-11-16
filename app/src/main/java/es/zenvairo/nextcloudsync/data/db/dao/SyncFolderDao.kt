package es.zenvairo.nextcloudsync.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import es.zenvairo.nextcloudsync.data.db.entity.SyncFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncFolderDao {
    @Query("SELECT * FROM sync_folders ORDER BY displayName")
    fun observeAll(): Flow<List<SyncFolderEntity>>

    @Query("SELECT * FROM sync_folders WHERE id = :id")
    suspend fun getById(id: Long): SyncFolderEntity?

    @Query("SELECT * FROM sync_folders WHERE enabled = 1")
    suspend fun getEnabled(): List<SyncFolderEntity>

    @Insert
    suspend fun insert(folder: SyncFolderEntity): Long

    @Update
    suspend fun update(folder: SyncFolderEntity)

    @Delete
    suspend fun delete(folder: SyncFolderEntity)

    @Query("UPDATE sync_folders SET lastSyncAtMillis = :atMillis, lastSyncSucceeded = :succeeded, lastErrorMessage = :error WHERE id = :id")
    suspend fun updateSyncResult(id: Long, atMillis: Long, succeeded: Boolean, error: String?)
}
