package com.routepix.data.model


data class Trip(
    val tripId: String = "",
    val adminUid: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val memberUids: List<String> = emptyList(),
    val telegramBotToken: String? = null,
    val telegramChatId: String? = null
)

