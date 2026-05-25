package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BotStatus(
    @Json(name = "isRunning") val isRunning: Boolean,
    @Json(name = "isMockMode") val isMockMode: Boolean,
    @Json(name = "orderAmountUsd") val orderAmountUsd: Double,
    @Json(name = "takeProfitPercent") val takeProfitPercent: Double,
    @Json(name = "trailingStopPercent") val trailingStopPercent: Double,
    @Json(name = "stopLossPercent") val stopLossPercent: Double = 5.0,
    @Json(name = "leverage") val leverage: Int = 1,
    @Json(name = "maxConcurrentTrades") val maxConcurrentTrades: Int = 1,
    @Json(name = "selectedPairs") val selectedPairs: List<String>,
    @Json(name = "activePairCount") val activePairCount: Int
)

@JsonClass(generateAdapter = true)
data class LogEntry(
    @Json(name = "time") val time: String,
    @Json(name = "level") val level: String, // "BUY", "SELL", "INFO", "WARN", "ERROR"
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class ActiveTrade(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "type") val type: String,
    @Json(name = "entryPrice") val entryPrice: Double,
    @Json(name = "currentPrice") val currentPrice: Double,
    @Json(name = "highestPrice") val highestPrice: Double,
    @Json(name = "amount") val amount: Double,
    @Json(name = "tpPrice") val tpPrice: Double,
    @Json(name = "tsPrice") val tsPrice: Double,
    @Json(name = "valueUsd") val valueUsd: Double,
    @Json(name = "profitPercent") val profitPercent: Double,
    @Json(name = "timestamp") val timestamp: Double
)

data class BotInstance(
    val id: String,
    val name: String,
    val orderAmountUsd: Double,
    val takeProfitPercent: Double,
    val trailingStopPercent: Double,
    val stopLossPercent: Double,
    val leverage: Int,
    val maxConcurrentTrades: Int,
    val selectedPairs: List<String>,
    var isRunning: Boolean = false
)

@JsonClass(generateAdapter = true)
data class BotConfigUpdate(
    @Json(name = "apiKey") val apiKey: String,
    @Json(name = "apiSecret") val apiSecret: String,
    @Json(name = "isMockMode") val isMockMode: Boolean,
    @Json(name = "orderAmountUsd") val orderAmountUsd: Double,
    @Json(name = "takeProfitPercent") val takeProfitPercent: Double,
    @Json(name = "trailingStopPercent") val trailingStopPercent: Double,
    @Json(name = "stopLossPercent") val stopLossPercent: Double = 5.0,
    @Json(name = "leverage") val leverage: Int = 1,
    @Json(name = "maxConcurrentTrades") val maxConcurrentTrades: Int = 1,
    @Json(name = "selectedPairs") val selectedPairs: List<String>
)

@JsonClass(generateAdapter = true)
data class BotConfigDb(
    @Json(name = "id") val id: Int,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "binance_api_key") val binanceApiKey: String?,
    @Json(name = "binance_secret_key") val binanceSecretKey: String?,
    @Json(name = "investment_per_trade") val investmentPerTrade: Double,
    @Json(name = "selected_pairs") val selectedPairs: List<String>
)

@JsonClass(generateAdapter = true)
data class BotConfigUpdateDb(
    @Json(name = "is_active") val isActive: Boolean? = null,
    @Json(name = "binance_api_key") val binanceApiKey: String? = null,
    @Json(name = "binance_secret_key") val binanceSecretKey: String? = null,
    @Json(name = "investment_per_trade") val investmentPerTrade: Double? = null,
    @Json(name = "selected_pairs") val selectedPairs: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class BotConfigRecord(
    @Json(name = "id") val id: Int,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "binance_api_key") val binanceApiKey: String?,
    @Json(name = "binance_secret_key") val binanceSecretKey: String?,
    @Json(name = "investment_per_trade") val investmentPerTrade: Double,
    @Json(name = "selected_pairs") val selectedPairs: List<String>
)

@JsonClass(generateAdapter = true)
data class BotConfigPatch(
    @Json(name = "is_active") val isActive: Boolean? = null,
    @Json(name = "binance_api_key") val binanceApiKey: String? = null,
    @Json(name = "binance_secret_key") val binanceSecretKey: String? = null,
    @Json(name = "investment_per_trade") val investmentPerTrade: Double? = null
)

@JsonClass(generateAdapter = true)
data class GenericResponse(
    @Json(name = "status") val status: String,
    @Json(name = "message") val message: String
)
