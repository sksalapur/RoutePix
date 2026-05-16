package com.routepix.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [QueuedPhoto::class], version = 4, exportSchema = false)
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

        fun getInstance(context: Context): RoutepixDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoutepixDatabase::class.java,
                    "routepix_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
        }
    }
}


