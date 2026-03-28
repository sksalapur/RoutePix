package com.routepix.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey


import androidx.room.Index

@Entity(
    tableName = "queued_photos",
    indices = [Index(value = ["tripId", "md5Hash"], unique = true)]
)
data class QueuedPhoto(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val localUri: String,
    val tripId: String,
    val timestamp: Long,
    val lat: Double? = null,
    val lng: Double? = null,
    val tag: String? = null,
    val md5Hash: String,
    val isMotionPhoto: Boolean = false
)
