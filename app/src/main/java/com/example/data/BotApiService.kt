package com.example.data

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface BotApiService {
    @GET("status")
    suspend fun getStatus(): BotStatus

    @GET("balance")
    suspend fun getBalance(): Map<String, Double>

    @GET("trades")
    suspend fun getTrades(): List<ActiveTrade>

    @GET("logs")
    suspend fun getLogs(): List<LogEntry>

    @POST("start")
    suspend fun startBot(): GenericResponse

    @POST("stop")
    suspend fun stopBot(): GenericResponse

    @POST("config")
    suspend fun updateConfig(@Body config: BotConfigUpdate): GenericResponse

    companion object {
        fun create(baseUrl: String): BotApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .build()

            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            return Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(BotApiService::class.java)
        }
    }
}
