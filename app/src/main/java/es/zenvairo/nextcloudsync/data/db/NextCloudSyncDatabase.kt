package es.zenvairo.nextcloudsync.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import es.zenvairo.nextcloudsync.data.db.dao.ConflictDao
import es.zenvairo.nextcloudsync.data.db.dao.FileStateDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncEventDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncFolderDao
import es.zenvairo.nextcloudsync.data.db.dao.VirtualFolderDao
import es.zenvairo.nextcloudsync.data.db.entity.ConflictEntity
import es.zenvairo.nextcloudsync.data.db.entity.FileStateEntity
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventEntity
import es.zenvairo.nextcloudsync.data.db.entity.SyncFolderEntity
import es.zenvairo.nextcloudsync.data.db.entity.VirtualFolderEntity

@Database(
    entities = [
        SyncFolderEntity::class,
        FileStateEntity::class,
        ConflictEntity::class,
        SyncEventEntity::class,
        VirtualFolderEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NextCloudSyncDatabase : RoomDatabase() {
    abstract fun syncFolderDao(): SyncFolderDao
    abstract fun fileStateDao(): FileStateDao
    abstract fun conflictDao(): ConflictDao
    abstract fun syncEventDao(): SyncEventDao
    abstract fun virtualFolderDao(): VirtualFolderDao
}
