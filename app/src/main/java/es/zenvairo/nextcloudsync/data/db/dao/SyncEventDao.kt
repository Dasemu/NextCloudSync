package es.zenvairo.nextcloudsync.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncEventDao {
    @Query("SELECT * FROM sync_events ORDER BY timestampMillis DESC LIMIT 200")
    fun observeRecent(): Flow<List<SyncEventEntity>>

    @Insert
    suspend fun insert(event: SyncEventEntity): Long

    @Query("DELETE FROM sync_events WHERE timestampMillis < :beforeMillis")
    suspend fun pruneOlderThan(beforeMillis: Long)

    @Query("SELECT COUNT(*) FROM sync_events WHERE type = 'RUN_STARTED' AND timestampMillis >= :sinceMillis")
    suspend fun countRunsSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM sync_events WHERE type = 'RUN_FINISHED' AND timestampMillis >= :sinceMillis")
    suspend fun countCompletedRunsSince(sinceMillis: Long): Int
}
