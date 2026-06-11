package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class TelegramResponse<T>(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "result") val result: T?,
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class StickerSet(
    @Json(name = "name") val name: String,
    @Json(name = "title") val title: String,
    @Json(name = "is_animated") val isAnimated: Boolean = false,
    @Json(name = "is_video") val isVideo: Boolean = false,
    @Json(name = "stickers") val stickers: List<TelegramSticker> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TelegramSticker(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "emoji") val emoji: String? = "😀",
    @Json(name = "file_size") val fileSize: Long? = 0
)

@JsonClass(generateAdapter = true)
data class TelegramFile(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "file_size") val fileSize: Long? = 0,
    @Json(name = "file_path") val filePath: String? = null
)

interface TelegramApiService {
    @GET("bot{token}/getStickerSet")
    suspend fun getStickerSet(
        @Path("token") token: String,
        @Query("name") name: String
    ): TelegramResponse<StickerSet>

    @GET("bot{token}/getFile")
    suspend fun getFile(
        @Path("token") token: String,
        @Query("file_id") fileId: String
    ): TelegramResponse<TelegramFile>

    @GET("file/bot{token}/{filePath}")
    suspend fun downloadFile(
        @Path("token") token: String,
        @Path("filePath") filePath: String
    ): ResponseBody

    companion object {
        fun create(): TelegramApiService {
            return Retrofit.Builder()
                .baseUrl("https://api.telegram.org/")
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(TelegramApiService::class.java)
        }
    }
}
