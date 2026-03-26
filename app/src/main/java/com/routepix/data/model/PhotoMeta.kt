package com.routepix.data.model


data class PhotoMeta(
    val photoId: String = "",
    val tripId: String = "",
    val uploaderUid: String = "",
    val telegramFileId: String = "",
    val timestamp: Long = 0L,
    val lat: Double? = null,
    val lng: Double? = null,
    val placeName: String? = null,
    val tag: String? = null
)

