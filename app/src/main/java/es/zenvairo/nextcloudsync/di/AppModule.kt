package es.zenvairo.nextcloudsync.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import es.zenvairo.nextcloudsync.data.db.MIGRATION_1_2
import es.zenvairo.nextcloudsync.data.db.NextCloudSyncDatabase
import es.zenvairo.nextcloudsync.data.db.dao.ConflictDao
import es.zenvairo.nextcloudsync.data.db.dao.FileStateDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncEventDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncFolderDao
import es.zenvairo.nextcloudsync.data.db.dao.VirtualFolderDao
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NextCloudSyncDatabase =
        Room.databaseBuilder(context, NextCloudSyncDatabase::class.java, "nextcloudsync.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideSyncFolderDao(db: NextCloudSyncDatabase): SyncFolderDao = db.syncFolderDao()

    @Provides
    fun provideFileStateDao(db: NextCloudSyncDatabase): FileStateDao = db.fileStateDao()

    @Provides
    fun provideConflictDao(db: NextCloudSyncDatabase): ConflictDao = db.conflictDao()

    @Provides
    fun provideSyncEventDao(db: NextCloudSyncDatabase): SyncEventDao = db.syncEventDao()

    @Provides
    fun provideVirtualFolderDao(db: NextCloudSyncDatabase): VirtualFolderDao = db.virtualFolderDao()
}
