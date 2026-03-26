package com.routepix.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QueuedPhoto::class], version = 2, exportSchema = false)
abstract class RoutepixDatabase : RoomDatabase() {

    abstract fun queuedPhotoDao(): QueuedPhotoDao

    companion object {
        @Volatile
        private var INSTANCE: RoutepixDatabase? = null

        fun getInstance(context: Context): RoutepixDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoutepixDatabase::class.java,
                    "routepix_database"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}

