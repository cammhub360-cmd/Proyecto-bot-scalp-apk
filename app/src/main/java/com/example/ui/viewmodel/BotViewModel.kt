package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class BotViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)

    // InsForge Credentials
    private val INSFORGE_API_KEY = "ik_2a55ab09c8ca9cf17de40d97310225db" // Service Key for autonomous op
    private val INSFORGE_BEARER = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3OC0xMjM0LTU2NzgtOTBhYi1jZGVmMTIzNDU2NzgiLCJlbWFpbCI6ImFub25AaW5zZm9yZ2UuY29tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg4NTgzNjB9.ZHOPUJ6yu8oK7sd2bcw402a150aJg9biWwLoCS2i-tY"

    // Key configuration inputs
    val apiKeyValue = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val apiSecretValue = MutableStateFlow(prefs.getString("api_secret", "") ?: "")

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isServerOnline = MutableStateFlow(false)
    val isServerOnline: StateFlow<Boolean> = _isServerOnline.asStateFlow()

    private val _isLocalDemoActive = MutableStateFlow(prefs.getBoolean("is_demo", true))
    val isLocalDemoActive: StateFlow<Boolean> = _isLocalDemoActive.asStateFlow()

    // Bot State
    private val _status = MutableStateFlow<BotStatus?>(null)
    val status: StateFlow<BotStatus?> = _status.asStateFlow()

    private val _balances = MutableStateFlow<Map<String, Double>>(emptyMap())
    val balances: StateFlow<Map<String, Double>> = _balances.asStateFlow()

    private val _trades = MutableStateFlow<List<ActiveTrade>>(emptyList())
    val trades: StateFlow<List<ActiveTrade>> = _trades.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // Risk Parameters
    val orderAmountUsd = MutableStateFlow(2.0)
    val takeProfitPercent = MutableStateFlow(1.1)
    val trailingStopPercent = MutableStateFlow(0.5)
    val stopLossPercent = MutableStateFlow(5.0)
    val leverage = MutableStateFlow(1)
    val maxConcurrentTrades = MutableStateFlow(1)
    val selectedPairs = MutableStateFlow(listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT"))

    private var pollerJob: Job? = null
    private val insForgeApi = InsForgeApiService.create()

    // Local Simulation state
    private val simBalances = mutableMapOf("USDT" to 124592.0, "USDC" to 15000.0)
    private val simTrades = mutableListOf<ActiveTrade>()
    private val simLogs = mutableListOf<LogEntry>()

    init {
        _status.value = BotStatus(
            isRunning = false, isMockMode = _isLocalDemoActive.value,
            orderAmountUsd = 2.0, takeProfitPercent = 1.1, trailingStopPercent = 0.5,
            stopLossPercent = 5.0, leverage = 1, maxConcurrentTrades = 1,
            selectedPairs = selectedPairs.value, activePairCount = selectedPairs.value.size
        )
        
        startPoller()
    }

    private fun startPoller() {
        pollerJob?.cancel()
        pollerJob = viewModelScope.launch {
            while (true) {
                if (_isLocalDemoActive.value) {
                    updateSimulation()
                } else {
                    updateFromInsForge()
                }
                delay(3000)
            }
        }
    }

    private suspend fun updateFromInsForge() {
        try {
            val configs = insForgeApi.getBotConfig(INSFORGE_API_KEY, INSFORGE_BEARER)
            if (configs.isNotEmpty()) {
                val config = configs.first()
                val isRunning = config["is_active"] as? Boolean ?: false
                
                // Fetch dynamic data
                val openTrades = insForgeApi.getOpenPositions(INSFORGE_API_KEY, INSFORGE_BEARER)
                val systemLogs = insForgeApi.getLogs(INSFORGE_API_KEY, INSFORGE_BEARER)
                
                _status.value = _status.value?.copy(
                    isRunning = isRunning,
                    isMockMode = false
                )
                _trades.value = openTrades
                _logs.value = systemLogs
                _isServerOnline.value = true
                
                // Mock balances for UI if not available from InsForge yet
                _balances.value = simBalances.toMap()
            }
        } catch (e: Exception) {
            _isServerOnline.value = false
            addLocalLog("ERROR", "InsForge Sync: ${e.localizedMessage}")
        }
    }

    private fun updateSimulation() {
        val s = _status.value ?: return
        if (s.isRunning) {
            val nextTrades = mutableListOf<ActiveTrade>()
            for (t in simTrades) {
                val change = Random.nextDouble(-0.3, 0.5) / 100.0
                val newPrice = t.currentPrice * (1.0 + change)
                if (newPrice >= t.tpPrice) {
                    simBalances["USDT"] = (simBalances["USDT"] ?: 0.0) + (t.amount * newPrice)
                    addLocalLog("SELL", "★ TP ALCANZADO: Venta ${t.symbol} @ ${String.format("%.2f", newPrice)}")
                } else if (newPrice <= t.tsPrice) {
                    simBalances["USDT"] = (simBalances["USDT"] ?: 0.0) + (t.amount * newPrice)
                    addLocalLog("SELL", "▲ TS ACTIVADO: Venta ${t.symbol} @ ${String.format("%.2f", newPrice)}")
                } else {
                    nextTrades.add(t.copy(currentPrice = newPrice, profitPercent = ((newPrice - t.entryPrice) / t.entryPrice) * 100.0))
                }
            }
            simTrades.clear()
            simTrades.addAll(nextTrades)

            if (simTrades.size < 3 && Random.nextDouble() < 0.1) {
                val p = selectedPairs.value.random()
                val price = Random.nextDouble(60000.0, 70000.0)
                val amt = orderAmountUsd.value / price
                simBalances["USDT"] = (simBalances["USDT"] ?: 0.0) - orderAmountUsd.value
                simTrades.add(ActiveTrade(p, "BUY", amt, price, price, price, price * 1.011, price * 0.995, orderAmountUsd.value, 0.0, System.currentTimeMillis().toDouble()))
                addLocalLog("BUY", "Compra Sim: $p @ ${String.format("%.2f", price)}")
            }
        }
        _balances.value = simBalances.toMap()
        _trades.value = simTrades.toList()
        _logs.value = simLogs.toList().reversed()
    }

    private fun addLocalLog(level: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        simLogs.add(LogEntry(time, level, message))
        if (simLogs.size > 50) simLogs.removeAt(0)
        _logs.value = simLogs.toList().reversed()
    }

    fun toggleBot() {
        val current = _status.value?.isRunning ?: false
        val next = !current
        _status.value = _status.value?.copy(isRunning = next)
        
        if (!_isLocalDemoActive.value) {
            viewModelScope.launch {
                try {
                    insForgeApi.updateBotConfig(INSFORGE_API_KEY, INSFORGE_BEARER, "1", mapOf("is_active" to next))
                    addLocalLog("INFO", "Comando enviado a InsForge: ${if (next) "INICIAR" else "DETENER"}")
                } catch (e: Exception) {
                    addLocalLog("ERROR", "Error al enviar comando: ${e.localizedMessage}")
                }
            }
        } else {
            addLocalLog("INFO", if (next) "Simulación INICIADA" else "Simulación DETENIDA")
        }
    }

    fun toggleLocalDemo(active: Boolean) {
        _isLocalDemoActive.value = active
        prefs.edit().putBoolean("is_demo", active).apply()
        _status.value = _status.value?.copy(isMockMode = active)
        addLocalLog("INFO", "Modo ${if (active) "SIMULACIÓN" else "REAL (InsForge)"}")
    }

    fun saveConfiguration() {
        prefs.edit().apply {
            putString("api_key", apiKeyValue.value)
            putString("api_secret", apiSecretValue.value)
            apply()
        }
        
        if (!_isLocalDemoActive.value) {
            viewModelScope.launch {
                try {
                    insForgeApi.updateBotConfig(INSFORGE_API_KEY, INSFORGE_BEARER, "1", mapOf(
                        "binance_api_key" to apiKeyValue.value,
                        "binance_secret_key" to apiSecretValue.value,
                        "investment_per_trade" to orderAmountUsd.value
                    ))
                    addLocalLog("INFO", "Configuración sincronizada con InsForge Backend.")
                } catch (e: Exception) {
                    addLocalLog("ERROR", "Error al sincronizar llaves: ${e.localizedMessage}")
                }
            }
        }
        addLocalLog("INFO", "Configuración guardada en dispositivo.")
    }

    fun addPair(pair: String) { if (!selectedPairs.value.contains(pair)) { selectedPairs.value = selectedPairs.value + pair } }
    fun removePair(pair: String) { if (selectedPairs.value.contains(pair)) { selectedPairs.value = selectedPairs.value - pair } }
    fun toggleBot(id: String) = toggleBot()
    fun addBot(name: String, amount: Double, tp: Double, ts: Double, sl: Double, lev: Int, maxTrades: Int, pairs: List<String>) {}
    fun removeBot(id: String) {}
}
