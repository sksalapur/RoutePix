package com.routepix.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueuedPhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: QueuedPhoto): Long

    @Query("DELETE FROM queued_photos WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM queued_photos WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getAllForTrip(tripId: String): Flow<List<QueuedPhoto>>

    @Query("SELECT * FROM queued_photos WHERE md5Hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): QueuedPhoto?

    @Query("SELECT * FROM queued_photos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): QueuedPhoto?

    @Query("SELECT * FROM queued_photos WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getPendingForTrip(tripId: String): List<QueuedPhoto>
}

