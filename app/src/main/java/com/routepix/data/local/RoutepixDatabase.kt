package com.routepix.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [QueuedPhoto::class], version = 5, exportSchema = false)
abstract class RoutepixDatabase : RoomDatabase() {

    abstract fun queuedPhotoDao(): QueuedPhotoDao

    companion object {
        @Volatile
        private var INSTANCE: RoutepixDatabase? = null

        /**
         * Adds the aiLabels column introduced in v4.
         * ALTER TABLE is safe for nullable columns with no DEFAULT constraint.
         * Existing rows will have aiLabels = NULL, which is handled gracefully everywhere.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queued_photos ADD COLUMN aiLabels TEXT")
            }
        }

        /**
         * Adds the faceCount column introduced in v5.
         * Existing rows get faceCount = 0 (no faces detected / legacy photo).
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queued_photos ADD COLUMN faceCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): RoutepixDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoutepixDatabase::class.java,
                    "routepix_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }
    }
}


