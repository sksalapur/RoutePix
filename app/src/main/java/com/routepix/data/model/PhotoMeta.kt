package com.routepix.data.model

import com.google.firebase.firestore.PropertyName

data class PhotoMeta(
    val photoId: String = "",
    val tripId: String = "",
    val uploaderUid: String = "",
    val telegramFileId: String = "",
    val telegramDocumentId: String? = null,
    val timestamp: Long = 0L,
    val lat: Double? = null,
    val lng: Double? = null,
    val placeName: String? = null,
    val tag: String? = null,
    val md5Hash: String? = null,
    val sizeBytes: Long? = null,
    /** Top AI-detected concepts, comma-separated (e.g. "Mountain,Sky,Snow"). Null for legacy photos. */
    val aiLabels: String? = null,
    @get:PropertyName("isMotionPhoto")
    @set:PropertyName("isMotionPhoto")
    var isMotionPhoto: Boolean = false,
    /** Number of faces detected by on-device ML Kit Face Detection. 0 for legacy/no-face photos. */
    val faceCount: Int = 0,
    /** Emoji reactions. Key = emoji (e.g. "🔥"), value = list of UIDs who reacted. */
    val reactions: Map<String, List<String>> = emptyMap()
)
