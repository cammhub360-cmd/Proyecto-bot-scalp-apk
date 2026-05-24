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

    private var botJob: Job? = null
    private val binanceApi = BinanceDirectApiService.create()

    // Simulation/State caches
    private val simulatedBalances = mutableMapOf("USDT" to 1000.0, "USDC" to 500.0)
    private val activeTradesCache = mutableListOf<ActiveTrade>()
    private val logsCache = mutableListOf<LogEntry>()

    init {
        _status.value = BotStatus(
            isRunning = false, isMockMode = _isLocalDemoActive.value,
            orderAmountUsd = 2.0, takeProfitPercent = 1.1, trailingStopPercent = 0.5,
            stopLossPercent = 5.0, leverage = 1, maxConcurrentTrades = 1,
            selectedPairs = selectedPairs.value, activePairCount = selectedPairs.value.size
        )
        
        startBotEngine()
    }

    private fun startBotEngine() {
        botJob?.cancel()
        botJob = viewModelScope.launch {
            while (true) {
                if (_isLocalDemoActive.value) {
                    runSimulationStep()
                } else {
                    runRealBinanceStep()
                }
                delay(3000)
            }
        }
    }

    private suspend fun runRealBinanceStep() {
        val key = apiKeyValue.value
        val secret = apiSecretValue.value
        if (key.isEmpty() || secret.isEmpty()) {
            addLog("WARN", "Faltan API Keys para el modo REAL. Cambiando a Simulación.")
            _isLocalDemoActive.value = true
            return
        }

        try {
            val timestamp = System.currentTimeMillis()
            val query = "timestamp=$timestamp"
            val signature = BinanceSigner.sign(query, secret)
            
            val account = binanceApi.getAccountInfo(key, timestamp, signature)
            val newBalances = account.balances.filter { it.free.toDouble() > 0 || it.locked.toDouble() > 0 }
                .associate { it.asset to it.free.toDouble() + it.locked.toDouble() }
            
            _balances.value = newBalances
            _isServerOnline.value = true
            
            // Logic for real trading would go here (Order placement, TP/SL monitoring)
            // For now, we fetch price for the first pair to show activity
            val pair = selectedPairs.value.first().replace("/", "")
            val priceRes = binanceApi.getPrice(pair)
            addLog("INFO", "Precio Real $pair: ${priceRes.price} USDT | Conexión Binance OK")

        } catch (e: Exception) {
            _isServerOnline.value = false
            addLog("ERROR", "Error de conexión con Binance: ${e.localizedMessage}")
        }
    }

    private fun runSimulationStep() {
        val statusVal = _status.value ?: return
        if (statusVal.isRunning) {
            // Simulated price movement
            val currentList = activeTradesCache.toList()
            activeTradesCache.clear()
            for (trade in currentList) {
                val change = Random.nextDouble(-0.4, 0.6) / 100.0
                val newPrice = trade.currentPrice * (1.0 + change)
                val profit = ((newPrice - trade.entryPrice) / trade.entryPrice) * 100.0
                
                if (newPrice >= trade.tpPrice) {
                    simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) + (trade.amount * newPrice)
                    addLog("SELL", "★ TAKE PROFIT: Venta ${trade.symbol} @ ${String.format("%.2f", newPrice)}")
                } else if (newPrice <= trade.tsPrice) {
                    simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) + (trade.amount * newPrice)
                    addLog("SELL", "▲ TRAILING STOP: Venta ${trade.symbol} @ ${String.format("%.2f", newPrice)}")
                } else {
                    activeTradesCache.add(trade.copy(currentPrice = newPrice, profitPercent = profit))
                }
            }

            // New trade entry
            if (activeTradesCache.size < 2 && Random.nextDouble() < 0.15) {
                val p = selectedPairs.value.random()
                val price = Random.nextDouble(50000.0, 70000.0)
                val amt = orderAmountUsd.value / price
                simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) - orderAmountUsd.value
                activeTradesCache.add(ActiveTrade(
                    p, "BUY", amt, price, price, price, price * 1.011, price * 0.995, orderAmountUsd.value, 0.0, System.currentTimeMillis().toDouble()
                ))
                addLog("BUY", "Compra Sim: $p @ ${String.format("%.2f", price)}")
            }
        }
        _balances.value = simulatedBalances.toMap()
        _trades.value = activeTradesCache.toList()
    }

    private fun addLog(level: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logsCache.add(LogEntry(time, level, message))
        if (logsCache.size > 50) logsCache.removeAt(0)
        _logs.value = logsCache.toList().reversed()
    }

    fun toggleBot() {
        val current = _status.value?.isRunning ?: false
        _status.value = _status.value?.copy(isRunning = !current)
        addLog("INFO", if (!current) "Sistema INICIADO" else "Sistema DETENIDO")
    }

    fun toggleLocalDemo(active: Boolean) {
        _isLocalDemoActive.value = active
        prefs.edit().putBoolean("is_demo", active).apply()
        _status.value = _status.value?.copy(isMockMode = active)
        addLog("INFO", "Cambiando a modo ${if (active) "SIMULACIÓN" else "REAL"}")
    }

    fun saveConfiguration() {
        prefs.edit().apply {
            putString("api_key", apiKeyValue.value)
            putString("api_secret", apiSecretValue.value)
            apply()
        }
        _status.value = _status.value?.copy(
            orderAmountUsd = orderAmountUsd.value,
            takeProfitPercent = takeProfitPercent.value,
            trailingStopPercent = trailingStopPercent.value
        )
        addLog("INFO", "Configuración guardada localmente.")
        if (!_isLocalDemoActive.value) {
            viewModelScope.launch { runRealBinanceStep() }
        }
    }

    fun toggleBot(id: String) = toggleBot()
    fun addBot(name: String, amount: Double, tp: Double, ts: Double, sl: Double, lev: Int, maxTrades: Int, pairs: List<String>) {}
    fun removeBot(id: String) {}
}
