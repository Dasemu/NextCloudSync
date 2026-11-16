package es.zenvairo.nextcloudsync.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the virtual-folders table. Purely additive - the sync engine's tables are untouched, so an
 * existing install keeps its folders, file state and conflict history.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `virtual_folders` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `displayName` TEXT NOT NULL,
                `remotePath` TEXT NOT NULL,
                `cacheMaxBytes` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                `lastErrorMessage` TEXT
            )
            """.trimIndent()
        )
    }
}
