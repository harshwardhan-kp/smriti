package com.smriti.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecordEntity::class, TaskEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SmritiDb : RoomDatabase() {

    abstract fun recordDao(): RecordDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN enrichmentState TEXT NOT NULL DEFAULT 'DONE'")
                db.execSQL("ALTER TABLE records ADD COLUMN enrichmentAttempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE records ADD COLUMN enrichedAt INTEGER")
                db.execSQL("ALTER TABLE records ADD COLUMN enrichmentModel TEXT")
                db.execSQL("ALTER TABLE records ADD COLUMN enrichmentError TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: SmritiDb? = null

        fun get(context: Context): SmritiDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmritiDb::class.java,
                    "smriti.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}