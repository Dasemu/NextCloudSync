package es.zenvairo.nextcloudsync.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import es.zenvairo.nextcloudsync.data.db.entity.VirtualFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VirtualFolderDao {
    @Query("SELECT * FROM virtual_folders ORDER BY displayName")
    fun observeAll(): Flow<List<VirtualFolderEntity>>

    @Query("SELECT * FROM virtual_folders WHERE id = :id")
    suspend fun getById(id: Long): VirtualFolderEntity?

    @Insert
    suspend fun insert(folder: VirtualFolderEntity): Long

    @Update
    suspend fun update(folder: VirtualFolderEntity)

    @Delete
    suspend fun delete(folder: VirtualFolderEntity)

    @Query("UPDATE virtual_folders SET lastErrorMessage = :error WHERE id = :id")
    suspend fun updateError(id: Long, error: String?)
}
