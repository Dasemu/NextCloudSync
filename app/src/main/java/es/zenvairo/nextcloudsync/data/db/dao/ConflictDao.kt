package es.zenvairo.nextcloudsync.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import es.zenvairo.nextcloudsync.data.db.entity.ConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictDao {
    @Query("SELECT * FROM conflicts WHERE resolved = 0 ORDER BY detectedAtMillis DESC")
    fun observeUnresolved(): Flow<List<ConflictEntity>>

    @Query("SELECT COUNT(*) FROM conflicts WHERE resolved = 0 AND folderId = :folderId")
    fun observeUnresolvedCountForFolder(folderId: Long): Flow<Int>

    @Insert
    suspend fun insert(conflict: ConflictEntity): Long

    @Query("UPDATE conflicts SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long)

    @Query("SELECT * FROM conflicts WHERE folderId = :folderId AND relativePath = :relativePath AND resolved = 0 LIMIT 1")
    suspend fun findOpen(folderId: Long, relativePath: String): ConflictEntity?
}
