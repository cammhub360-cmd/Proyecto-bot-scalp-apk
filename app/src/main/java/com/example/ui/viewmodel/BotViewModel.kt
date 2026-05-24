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

    // Network & URL state
    private val _baseUrl = MutableStateFlow(prefs.getString("base_url", "http://10.0.2.2:8080") ?: "http://10.0.2.2:8080")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isServerOnline = MutableStateFlow(false)
    val isServerOnline: StateFlow<Boolean> = _isServerOnline.asStateFlow()

    private val _isLocalDemoActive = MutableStateFlow(true)
    val isLocalDemoActive: StateFlow<Boolean> = _isLocalDemoActive.asStateFlow()

    // Key configuration inputs
    val apiKeyValue = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val apiSecretValue = MutableStateFlow(prefs.getString("api_secret", "") ?: "")

    // Bot State
    private val _status = MutableStateFlow<BotStatus?>(null)
    val status: StateFlow<BotStatus?> = _status.asStateFlow()

    private val _balances = MutableStateFlow<Map<String, Double>>(emptyMap())
    val balances: StateFlow<Map<String, Double>> = _balances.asStateFlow()

    private val _trades = MutableStateFlow<List<ActiveTrade>>(emptyList())
    val trades: StateFlow<List<ActiveTrade>> = _trades.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // Risk Parameters (MutableStateFlows to drive UI inputs)
    val orderAmountUsd = MutableStateFlow(2.0)
    val takeProfitPercent = MutableStateFlow(1.1)
    val trailingStopPercent = MutableStateFlow(0.5)
    val stopLossPercent = MutableStateFlow(5.0)
    val leverage = MutableStateFlow(1)
    val maxConcurrentTrades = MutableStateFlow(1)
    val selectedPairs = MutableStateFlow(listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT"))

    private val _bots = MutableStateFlow<List<BotInstance>>(emptyList())
    val bots: StateFlow<List<BotInstance>> = _bots.asStateFlow()

    private var pollingJob: Job? = null
    private var localSimulationJob: Job? = null
    private var apiService: BotApiService? = null

    // Simulation states
    private val simulatedBalances = mutableMapOf("USDT" to 124592.00, "USDC" to 15000.0)
    private val simulatedTrades = mutableListOf<ActiveTrade>()
    private val simulatedLogs = mutableListOf<LogEntry>()

    init {
        // Initialize default UI state
        _status.value = BotStatus(
            isRunning = false,
            isMockMode = true,
            orderAmountUsd = 2.0,
            takeProfitPercent = 1.1,
            trailingStopPercent = 0.5,
            stopLossPercent = 5.0,
            leverage = 1,
            maxConcurrentTrades = 1,
            selectedPairs = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT"),
            activePairCount = 5
        )
        
        // Load initial data
        setBaseUrl(_baseUrl.value)
    }

    fun setBaseUrl(newUrl: String) {
        val normalizedUrl = if (newUrl.startsWith("http")) newUrl else "http://$newUrl"
        _baseUrl.value = normalizedUrl
        _isConnecting.value = true
        
        viewModelScope.launch {
            try {
                apiService = BotApiService.create(normalizedUrl)
                // Test connection
                val testStatus = apiService?.getStatus()
                if (testStatus != null) {
                    _isServerOnline.value = true
                    _isLocalDemoActive.value = false
                    stopLocalSimulation()
                    startServerPolling()
                    addSimulatedLog("INFO", "Conectado satisfactoriamente al servidor Trading Bot ($normalizedUrl).")
                } else {
                    handleConnectionFailure("Servidor no responde")
                }
            } catch (e: Throwable) {
                handleConnectionFailure(e.localizedMessage ?: "Fallo de conexión")
            } finally {
                _isConnecting.value = false
            }
        }
    }

    private fun handleConnectionFailure(msg: String) {
        _isServerOnline.value = false
        _isLocalDemoActive.value = true
        startLocalSimulation()
        addSimulatedLog("WARN", "Servidor API offline ($msg). Operando en Modo Simulación Integrada.")
    }

    fun toggleLocalDemo(active: Boolean) {
        _isLocalDemoActive.value = active
        if (active) {
            stopServerPolling()
            startLocalSimulation()
        } else {
            stopLocalSimulation()
            setBaseUrl(_baseUrl.value)
        }
    }

    private fun startServerPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val service = apiService ?: break
                    val stat = service.getStatus()
                    val bal = service.getBalance()
                    val tr = service.getTrades()
                    val lg = service.getLogs()

                    _status.value = stat
                    _balances.value = bal
                    _trades.value = tr
                    _logs.value = lg
                    _isServerOnline.value = true
                } catch (e: Throwable) {
                    _isServerOnline.value = false
                    addSimulatedLog("ERROR", "Error de sincronización con el servidor: ${e.localizedMessage}")
                }
                delay(3000)
            }
        }
    }

    private fun stopServerPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startLocalSimulation() {
        if (localSimulationJob != null) return
        localSimulationJob = viewModelScope.launch {
            while (true) {
                val statusValue = _status.value ?: return@launch
                
                // Update local status mock
                _status.value = statusValue.copy(
                    isRunning = statusValue.isRunning,
                    orderAmountUsd = orderAmountUsd.value,
                    takeProfitPercent = takeProfitPercent.value,
                    trailingStopPercent = trailingStopPercent.value,
                    stopLossPercent = stopLossPercent.value,
                    leverage = leverage.value,
                    maxConcurrentTrades = maxConcurrentTrades.value,
                    selectedPairs = selectedPairs.value,
                    activePairCount = selectedPairs.value.size
                )
                
                if (statusValue.isRunning) {
                    val currentTradeList = simulatedTrades.toList()
                    simulatedTrades.clear()

                    for (trade in currentTradeList) {
                        val priceChangeBias = Random.nextDouble(-0.5, 0.75) / 100.0
                        val newPrice = trade.currentPrice * (1.0 + priceChangeBias)
                        val profitP = ((newPrice - trade.entryPrice) / trade.entryPrice) * 100.0

                        var highestPrice = trade.highestPrice
                        var tsPrice = trade.tsPrice

                        if (newPrice > highestPrice) {
                            highestPrice = newPrice
                            val newTs = newPrice * (1.0 - (statusValue.trailingStopPercent / 100.0))
                            if (newTs > tsPrice) {
                                tsPrice = newTs
                                addSimulatedLog("WARN", "Trailing Stop: ${trade.symbol} subió a $${String.format(java.util.Locale.US, "%.2f", newPrice)}. Nuevo stop protegido: $${String.format(java.util.Locale.US, "%.2f", tsPrice)}")
                            }
                        }

                        when {
                            newPrice >= trade.tpPrice -> {
                                val tradeVal = trade.amount * newPrice
                                simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) + tradeVal
                                addSimulatedLog("SELL", "★ TAKE PROFIT: Venta de ${trade.symbol} a $${String.format(java.util.Locale.US, "%.2f", newPrice)}.")
                            }
                            newPrice <= tsPrice -> {
                                val tradeVal = trade.amount * newPrice
                                simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) + tradeVal
                                addSimulatedLog("SELL", "▲ TRAILING STOP ACTIVADO: Venta de ${trade.symbol} a $${String.format(java.util.Locale.US, "%.2f", newPrice)}.")
                            }
                            newPrice <= trade.entryPrice * (1.0 - (statusValue.stopLossPercent / 100.0)) -> {
                                val tradeVal = trade.amount * newPrice
                                simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) + tradeVal
                                addSimulatedLog("WARN", "⚠ STOP LOSS: Venta de ${trade.symbol} a $${String.format(java.util.Locale.US, "%.2f", newPrice)}.")
                            }
                            else -> {
                                simulatedTrades.add(trade.copy(currentPrice = newPrice, highestPrice = highestPrice, tsPrice = tsPrice, profitPercent = profitP))
                            }
                        }
                    }

                    if (simulatedTrades.size < 3 && Random.nextDouble() < 0.1) {
                        val pair = selectedPairs.value.random()
                        if (simulatedTrades.none { it.symbol == pair }) {
                            val price = Random.nextDouble(50000.0, 70000.0)
                            val amount = orderAmountUsd.value / price
                            simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) - orderAmountUsd.value
                            simulatedTrades.add(ActiveTrade(
                                symbol = pair, type = "BUY", amount = amount, entryPrice = price,
                                currentPrice = price, highestPrice = price, tpPrice = price * (1.0 + takeProfitPercent.value/100.0),
                                tsPrice = price * (1.0 - trailingStopPercent.value/100.0), valueUsd = orderAmountUsd.value,
                                profitPercent = 0.0, timestamp = System.currentTimeMillis().toDouble()
                            ))
                            addSimulatedLog("BUY", "Compra automática: $pair @ $${String.format(java.util.Locale.US, "%.2f", price)}")
                        }
                    }
                }
                
                _balances.value = simulatedBalances.toMap()
                _trades.value = simulatedTrades.toList()
                _logs.value = simulatedLogs.toList().reversed()
                
                delay(2000)
            }
        }
    }

    private fun stopLocalSimulation() {
        localSimulationJob?.cancel()
        localSimulationJob = null
    }

    private fun addSimulatedLog(level: String, message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = sdf.format(Date())
        simulatedLogs.add(LogEntry(time, level, message))
        if (simulatedLogs.size > 50) simulatedLogs.removeAt(0)
        _logs.value = simulatedLogs.toList().reversed()
    }

    fun toggleBot() {
        viewModelScope.launch {
            val isRunning = _status.value?.isRunning ?: false
            if (_isLocalDemoActive.value) {
                _status.value = _status.value?.copy(isRunning = !isRunning)
                addSimulatedLog("INFO", if (!isRunning) "Bot de Simulación iniciado." else "Bot de Simulación detenido.")
            } else {
                try {
                    if (!isRunning) apiService?.startBot() else apiService?.stopBot()
                    val updatedStatus = apiService?.getStatus()
                    if (updatedStatus != null) _status.value = updatedStatus
                } catch (e: Throwable) {
                    addSimulatedLog("ERROR", "No se pudo cambiar el estado del bot: ${e.localizedMessage}")
                }
            }
        }
    }

    fun toggleBot(botId: String) {
        toggleBot()
    }

    fun addBot(
        name: String,
        amount: Double,
        tp: Double,
        ts: Double,
        sl: Double,
        lev: Int,
        maxTrades: Int,
        pairs: List<String>
    ) {
        // Legacy support stub
        addSimulatedLog("INFO", "Bot '$name' añadido a la flota.")
    }

    fun removeBot(botId: String) {
        // Legacy support stub
        addSimulatedLog("WARN", "Bot eliminado de la flota.")
    }

    fun saveConfiguration() {
        prefs.edit().apply {
            putString("api_key", apiKeyValue.value)
            putString("api_secret", apiSecretValue.value)
            putString("base_url", _baseUrl.value)
            apply()
        }
        viewModelScope.launch {
            val update = BotConfigUpdate(
                apiKey = apiKeyValue.value,
                apiSecret = apiSecretValue.value,
                isMockMode = apiKeyValue.value.isEmpty() || apiSecretValue.value.isEmpty(),
                orderAmountUsd = orderAmountUsd.value,
                takeProfitPercent = takeProfitPercent.value,
                trailingStopPercent = trailingStopPercent.value,
                stopLossPercent = stopLossPercent.value,
                leverage = leverage.value,
                maxConcurrentTrades = maxConcurrentTrades.value,
                selectedPairs = selectedPairs.value
            )

            if (_isLocalDemoActive.value) {
                _status.value = _status.value?.copy(
                    orderAmountUsd = update.orderAmountUsd,
                    takeProfitPercent = update.takeProfitPercent,
                    trailingStopPercent = update.trailingStopPercent,
                    stopLossPercent = update.stopLossPercent,
                    leverage = update.leverage,
                    maxConcurrentTrades = update.maxConcurrentTrades,
                    selectedPairs = update.selectedPairs,
                    activePairCount = update.selectedPairs.size
                )
                addSimulatedLog("INFO", "Configuración de simulación guardada.")
            } else {
                try {
                    apiService?.updateConfig(update)
                    addSimulatedLog("INFO", "Configuración enviada al servidor backend.")
                    val updatedStatus = apiService?.getStatus()
                    if (updatedStatus != null) _status.value = updatedStatus
                } catch (e: Throwable) {
                    addSimulatedLog("ERROR", "Error al sincronizar configuración: ${e.localizedMessage}")
                }
            }
        }
    }

    fun addPair(pair: String) {
        if (!selectedPairs.value.contains(pair)) {
            selectedPairs.value = selectedPairs.value + pair
            saveConfiguration()
        }
    }

    fun removePair(pair: String) {
        if (selectedPairs.value.contains(pair)) {
            selectedPairs.value = selectedPairs.value - pair
            saveConfiguration()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopServerPolling()
        stopLocalSimulation()
    }
}
