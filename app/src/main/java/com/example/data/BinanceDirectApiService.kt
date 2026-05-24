package com.example.data

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

interface BinanceDirectApiService {

    @GET("api/v3/account")
    suspend fun getAccountInfo(
        @Header("X-MBX-APIKEY") apiKey: String,
        @Query("timestamp") timestamp: Long,
        @Query("signature") signature: String
    ): BinanceAccountResponse

    @GET("api/v3/ticker/price")
    suspend fun getPrice(
        @Query("symbol") symbol: String
    ): BinancePriceResponse

    @POST("api/v3/order")
    suspend fun createOrder(
        @Header("X-MBX-APIKEY") apiKey: String,
        @Query("symbol") symbol: String,
        @Query("side") side: String,
        @Query("type") type: String,
        @Query("quantity") quantity: String,
        @Query("timestamp") timestamp: Long,
        @Query("signature") signature: String
    ): Any

    companion object {
        private const val BASE_URL = "https://api.binance.com/"

        fun create(): BinanceDirectApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder().build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(BinanceDirectApiService::class.java)
        }
    }
}

data class BinancePriceResponse(
    val symbol: String,
    val price: String
)

data class BinanceAccountResponse(
    val balances: List<BinanceBalance>
)

data class BinanceBalance(
    val asset: String,
    val free: String,
    val locked: String
)

object BinanceSigner {
    fun sign(data: String, secret: String): String {
        val sha256_HMAC = Mac.getInstance("HmacSHA256")
        val secret_key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        sha256_HMAC.init(secret_key)
        return sha256_HMAC.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
