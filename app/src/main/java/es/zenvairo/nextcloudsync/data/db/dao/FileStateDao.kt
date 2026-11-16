package es.zenvairo.nextcloudsync.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.zenvairo.nextcloudsync.data.db.entity.FileStateEntity

@Dao
interface FileStateDao {
    @Query("SELECT * FROM file_state WHERE folderId = :folderId")
    suspend fun getAllForFolder(folderId: Long): List<FileStateEntity>

    @Query("SELECT * FROM file_state WHERE folderId = :folderId AND relativePath = :relativePath")
    suspend fun get(folderId: Long, relativePath: String): FileStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: FileStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<FileStateEntity>)

    @Query("DELETE FROM file_state WHERE folderId = :folderId AND relativePath = :relativePath")
    suspend fun delete(folderId: Long, relativePath: String)

    @Query("DELETE FROM file_state WHERE folderId = :folderId")
    suspend fun deleteAllForFolder(folderId: Long)
}
