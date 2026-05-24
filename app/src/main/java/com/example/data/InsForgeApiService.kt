package com.example.data

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface InsForgeApiService {

    @GET("api/database/records/bot_config")
    suspend fun getBotConfig(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("select") select: String = "*"
    ): List<Map<String, Any>>

    @PATCH("api/database/records/bot_config")
    suspend fun updateBotConfig(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("id") id: String, // Note: Use filter query format if needed
        @Body config: Map<String, Any>
    )

    @GET("api/database/records/open_positions")
    suspend fun getOpenPositions(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String
    ): List<ActiveTrade>

    @GET("api/database/records/system_logs")
    suspend fun getLogs(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): List<LogEntry>

    companion object {
        private const val BASE_URL = "https://qwd48mes.us-east.insforge.app/"

        fun create(): InsForgeApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder().build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(InsForgeApiService::class.java)
        }
    }
}
