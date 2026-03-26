package com.routepix.data.remote

import com.google.gson.annotations.SerializedName


data class TelegramResponse(
    val ok: Boolean,
    val result: TelegramMessage?
)

data class TelegramMessage(
    @SerializedName("message_id")
    val messageId: Long,
    val photo: List<TelegramPhotoSize>?,
    val document: TelegramDocument?,
    val caption: String?
)

data class TelegramPhotoSize(
    @SerializedName("file_id")
    val fileId: String,
    @SerializedName("file_unique_id")
    val fileUniqueId: String,
    val width: Int,
    val height: Int,
    @SerializedName("file_size")
    val fileSize: Long?
)

data class TelegramDocument(
    @SerializedName("file_id")
    val fileId: String,
    @SerializedName("file_unique_id")
    val fileUniqueId: String,
    @SerializedName("file_name")
    val fileName: String?,
    @SerializedName("mime_type")
    val mimeType: String?,
    @SerializedName("file_size")
    val fileSize: Long?
)


data class TelegramFileResponse(
    val ok: Boolean,
    val result: TelegramFile?
)

data class TelegramFile(
    @SerializedName("file_id")
    val fileId: String,
    @SerializedName("file_unique_id")
    val fileUniqueId: String,
    @SerializedName("file_size")
    val fileSize: Long?,
    @SerializedName("file_path")
    val filePath: String?
)

