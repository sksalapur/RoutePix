package com.routepix.data.model


data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val telegramBotToken: String? = null,
    val telegramChatId: String? = null,
    val backupOnCellular: Boolean = false
)
