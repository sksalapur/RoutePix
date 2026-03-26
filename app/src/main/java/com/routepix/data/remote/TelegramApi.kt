package com.routepix.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface TelegramApi {

    
    @Multipart
    @POST("/bot{token}/sendPhoto")
    suspend fun sendPhoto(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part photo: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): TelegramResponse

    @Multipart
    @POST("/bot{token}/sendDocument")
    suspend fun sendDocument(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part document: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): TelegramResponse

    
    @GET("/bot{token}/getFile")
    suspend fun getFile(
        @Path("token") token: String,
        @Query("file_id") fileId: String
    ): TelegramFileResponse
}

