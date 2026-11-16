package es.zenvairo.nextcloudsync.data.db

import androidx.room.TypeConverter
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventType
import es.zenvairo.nextcloudsync.domain.model.ConflictPolicy
import es.zenvairo.nextcloudsync.domain.model.SyncMode

class Converters {
    @TypeConverter
    fun fromSyncMode(value: SyncMode): String = value.name

    @TypeConverter
    fun toSyncMode(value: String): SyncMode = SyncMode.valueOf(value)

    @TypeConverter
    fun fromConflictPolicy(value: ConflictPolicy): String = value.name

    @TypeConverter
    fun toConflictPolicy(value: String): ConflictPolicy = ConflictPolicy.valueOf(value)

    @TypeConverter
    fun fromSyncEventType(value: SyncEventType): String = value.name

    @TypeConverter
    fun toSyncEventType(value: String): SyncEventType = SyncEventType.valueOf(value)
}
