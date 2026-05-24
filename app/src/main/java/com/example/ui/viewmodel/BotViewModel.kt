package com.example.ui.viewmodel

import android.app.Application
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

    // Network & URL state
    private val _baseUrl = MutableStateFlow("http://10.0.2.2:8080")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    // Core Trading States
    private val _status = MutableStateFlow<BotStatus?>(null)
    val status: StateFlow<BotStatus?> = _status.asStateFlow()

    private val _balances = MutableStateFlow<Map<String, Double>>(emptyMap())
    val balances: StateFlow<Map<String, Double>> = _balances.asStateFlow()

    private val _trades = MutableStateFlow<List<ActiveTrade>>(emptyList())
    val trades: StateFlow<List<ActiveTrade>> = _trades.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isServerOnline = MutableStateFlow(false)
    val isServerOnline: StateFlow<Boolean> = _isServerOnline.asStateFlow()

    private val _isLocalDemoActive = MutableStateFlow(true) // Start with demo mode active for immediate visual feedback
    val isLocalDemoActive: StateFlow<Boolean> = _isLocalDemoActive.asStateFlow()

    // Temp Form inputs for easy setup
    val apiKeyValue = MutableStateFlow("")
    val apiSecretValue = MutableStateFlow("")
    val orderAmountUsd = MutableStateFlow(2.0)
    val takeProfitPercent = MutableStateFlow(1.0)
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

    // For local simulation
    private val simulatedBalances = mutableMapOf(
        "USDT" to 120.0,
        "USDC" to 85.0,
        "BTC" to 0.00045,
        "ETH" to 0.012,
        "SOL" to 0.18,
        "BNB" to 0.04
    )
    private val simulatedTrades = mutableListOf<ActiveTrade>()
    private val simulatedLogs = mutableListOf<LogEntry>()

    fun addBot(name: String, amount: Double, tp: Double, ts: Double, sl: Double, lev: Int, maxTrades: Int, pairs: List<String>) {
        val newBot = BotInstance(
            id = UUID.randomUUID().toString(),
            name = name,
            orderAmountUsd = amount,
            takeProfitPercent = tp,
            trailingStopPercent = ts,
            stopLossPercent = sl,
            leverage = lev,
            maxConcurrentTrades = maxTrades,
            selectedPairs = pairs,
            isRunning = true
        )
        _bots.value = _bots.value + newBot
        addSimulatedLog("INFO", "Bot creado y en ejecución: \$name")
    }

    fun removeBot(botId: String) {
        val bot = _bots.value.find { it.id == botId }
        _bots.value = _bots.value.filter { it.id != botId }
        bot?.let {
            addSimulatedLog("WARN", "Bot eliminado: \${it.name}")
        }
    }

    fun toggleBot(botId: String) {
        _bots.value = _bots.value.map { 
            if (it.id == botId) {
                val state = if (!it.isRunning) "iniciado" else "detenido"
                addSimulatedLog("INFO", "Bot \${it.name} \$state")
                it.copy(isRunning = !it.isRunning) 
            } else it 
        }
    }

    init {
        // Load initial logs
        addSimulatedLog("INFO", "Dispositivo configurado. Modo demostración activo.")
        addSimulatedLog("INFO", "Para conectar a un servidor real Docker/PC, ingresa la IP en Ajustes.")
        
        // Default Bot
        val defaultBot = BotInstance(
            id = UUID.randomUUID().toString(),
            name = "Quantum-Alpha-01",
            orderAmountUsd = 2.0,
            takeProfitPercent = 1.0,
            trailingStopPercent = 0.5,
            stopLossPercent = 5.0,
            leverage = 1,
            maxConcurrentTrades = 1,
            selectedPairs = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT"),
            isRunning = true
        )
        _bots.value = listOf(defaultBot)

        // Start simulated polling right away
        startLocalSimulation()
        
        // Run network check to see if local PC API is available
        setBaseUrl("http://10.0.2.2:8080")
    }

    fun setBaseUrl(newUrl: String) {
        viewModelScope.launch {
            _baseUrl.value = newUrl
            _isConnecting.value = true
            try {
                apiService = BotApiService.create(newUrl)
                // Test ping
                val remoteStatus = apiService?.getStatus()
                if (remoteStatus != null) {
                    _status.value = remoteStatus
                    _isServerOnline.value = true
                    _isLocalDemoActive.value = false // Deactivate client side simulation since server is active!
                    stopLocalSimulation()
                    startServerPolling()
                    addSimulatedLog("INFO", "Conectado satisfactoriamente al servidor Trading Bot ($newUrl).")
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
        // If we fall back to local demo, ensure it runs
        startLocalSimulation()
        addSimulatedLog("WARN", "Servidor API offline ($msg). Operando en Modo Simulación Integrada.")
    }

    // Toggle backend connectivity manually
    fun toggleLocalDemo(active: Boolean) {
        _isLocalDemoActive.value = active
        if (active) {
            stopServerPolling()
            startLocalSimulation()
            addSimulatedLog("INFO", "Modo Simulación local re-activado de forma manual.")
        } else {
            stopLocalSimulation()
            setBaseUrl(_baseUrl.value)
        }
    }

    // Server Polling Routine
    private fun startServerPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val service = apiService ?: break
                    // Parallel calls to speed up response times
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
                    addSimulatedLog("ERROR", "Servidor API desconectado durante polling: ${e.localizedMessage}")
                    _isLocalDemoActive.value = true
                    startLocalSimulation()
                    break
                }
                delay(3000)
            }
        }
    }

    private fun stopServerPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // --- CLIENT SIDE SIMULATOR (No Server required for visual reviews!) ---
    private fun startLocalSimulation() {
        if (localSimulationJob?.isActive == true) return
        
        // Sync values to form
        _status.value = BotStatus(
            isRunning = true,
            isMockMode = true,
            orderAmountUsd = orderAmountUsd.value,
            takeProfitPercent = takeProfitPercent.value,
            trailingStopPercent = trailingStopPercent.value,
            stopLossPercent = stopLossPercent.value,
            leverage = leverage.value,
            maxConcurrentTrades = maxConcurrentTrades.value,
            selectedPairs = selectedPairs.value,
            activePairCount = selectedPairs.value.size
        )
        _balances.value = simulatedBalances.toMap()
        _trades.value = simulatedTrades.toList()
        _logs.value = simulatedLogs.toList()

        localSimulationJob = viewModelScope.launch {
            while (true) {
                // Periodically update active trade prices
                val updated = mutableListOf<ActiveTrade>()
                val statusValue = _status.value ?: BotStatus(
                    isRunning = true,
                    isMockMode = true,
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
                        val priceChangeBias = Random.nextDouble(-0.5, 0.75) / 100.0 // upward bias for fun
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

                        // Exit triggers
                        when {
                            newPrice >= trade.tpPrice -> {
                                val tradeVal = trade.amount * newPrice
                                val oldVal = trade.amount * trade.entryPrice
                                val netP = tradeVal - oldVal
                                simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) + tradeVal
                                addSimulatedLog("SELL", "★ TAKE PROFIT (1.0% Hit): Venta de ${trade.symbol} a \$${String.format(java.util.Locale.US, "%.2f", newPrice)}. Retorno: \$${String.format(java.util.Locale.US, "%.2f", tradeVal)} (+\$${String.format(java.util.Locale.US, "%.2f", netP)})")
                            }
                            newPrice <= tsPrice -> {
                                val tradeVal = trade.amount * newPrice
                                val oldVal = trade.amount * trade.entryPrice
                                val netP = tradeVal - oldVal
                                simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) + tradeVal
                                val sign = if (netP >= 0) "+" else ""
                                addSimulatedLog("SELL", "▲ TRAILING STOP ACTIVADO (${sign}${String.format(java.util.Locale.US, "%.2f", profitP)}%): Venta de ${trade.symbol} a \$${String.format(java.util.Locale.US, "%.2f", newPrice)} para proteger saldo.")
                            }
                            newPrice <= trade.entryPrice * (1.0 - (statusValue.stopLossPercent / 100.0)) -> {
                                val tradeVal = trade.amount * newPrice
                                val oldVal = trade.amount * trade.entryPrice
                                val netP = tradeVal - oldVal
                                simulatedBalances["USDT"] = (simulatedBalances["USDT"] ?: 0.0) + tradeVal
                                addSimulatedLog("WARN", "⚠ STOP LOSS ALCANZADO: Venta de ${trade.symbol} a \$${String.format(java.util.Locale.US, "%.2f", newPrice)} (${String.format(java.util.Locale.US, "%.2f", profitP)}%).")
                            }
                            else -> {
                                simulatedTrades.add(
                                    trade.copy(
                                        currentPrice = newPrice,
                                        highestPrice = highestPrice,
                                        tsPrice = tsPrice,
                                        valueUsd = trade.amount * newPrice,
                                        profitPercent = profitP
                                    )
                                )
                            }
                        }
                    }

                    // Occasionally launch a new trade if balance allows (min $2 USDT)
                    val availableUsdt = simulatedBalances["USDT"] ?: 0.0
                    val currentOrderAmt = statusValue.orderAmountUsd
                    if (simulatedTrades.size < 4 && availableUsdt >= currentOrderAmt && Random.nextDouble() < 0.2) {
                        val remainingPairs = statusValue.selectedPairs.filter { p -> simulatedTrades.none { it.symbol == p } }
                        if (remainingPairs.isNotEmpty()) {
                            val targetPair = remainingPairs.random()
                            val baseCoin = targetPair.split("/")[0]
                            val entryPrice = when (baseCoin) {
                                "BTC" -> Random.nextDouble(64000.0, 68000.0)
                                "ETH" -> Random.nextDouble(3100.0, 3400.0)
                                "SOL" -> Random.nextDouble(160.0, 190.0)
                                "BNB" -> Random.nextDouble(540.0, 580.0)
                                "XRP" -> Random.nextDouble(0.5, 0.6)
                                else -> Random.nextDouble(10.0, 100.0)
                            }
                            val buyAmount = currentOrderAmt / entryPrice
                            simulatedBalances["USDT"] = availableUsdt - currentOrderAmt
                            
                            val freshTrade = ActiveTrade(
                                symbol = targetPair,
                                type = "BUY",
                                entryPrice = entryPrice,
                                currentPrice = entryPrice,
                                highestPrice = entryPrice,
                                amount = buyAmount,
                                tpPrice = entryPrice * (1.0 + (statusValue.takeProfitPercent / 100.0)),
                                tsPrice = entryPrice * (1.0 - (statusValue.trailingStopPercent / 100.0)),
                                valueUsd = currentOrderAmt,
                                profitPercent = 0.0,
                                timestamp = System.currentTimeMillis() / 1000.0
                            )
                            simulatedTrades.add(freshTrade)
                            addSimulatedLog("BUY", "Orden Compra: ${targetPair} ejecutada a $${String.format(java.util.Locale.US, "%.2f", entryPrice)}. Monto: $${String.format(java.util.Locale.US, "%.2f", currentOrderAmt)}")
                        }
                    }
                }

                // Update flows
                _balances.value = simulatedBalances.toMap()
                _trades.value = simulatedTrades.toList()
                _logs.value = simulatedLogs.toList()
                
                // Keep simulated status object updated
                _status.value = BotStatus(
                    isRunning = statusValue.isRunning,
                    isMockMode = true,
                    orderAmountUsd = orderAmountUsd.value,
                    takeProfitPercent = takeProfitPercent.value,
                    trailingStopPercent = trailingStopPercent.value,
                    stopLossPercent = stopLossPercent.value,
                    leverage = leverage.value,
                    maxConcurrentTrades = maxConcurrentTrades.value,
                    selectedPairs = selectedPairs.value,
                    activePairCount = selectedPairs.value.size
                )
                delay(2000)
            }
        }
    }

    private fun stopLocalSimulation() {
        localSimulationJob?.cancel()
        localSimulationJob = null
    }

    private fun addSimulatedLog(level: String, text: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formattedTime = sdf.format(Date())
        val log = LogEntry(time = formattedTime, level = level, message = text)
        simulatedLogs.add(0, log)
        if (simulatedLogs.size > 100) {
            simulatedLogs.removeAt(simulatedLogs.lastIndex)
        }
        _logs.value = simulatedLogs.toList()
    }

    // Server-side action bindings with simulated fallbacks
    fun startBot() {
        viewModelScope.launch {
            if (_isLocalDemoActive.value) {
                val current = _status.value
                if (current != null) {
                    _status.value = current.copy(isRunning = true)
                }
                addSimulatedLog("INFO", "★ Bot Simulado iniciado de forma local.")
            } else {
                try {
                    apiService?.startBot()
                    addSimulatedLog("INFO", "Iniciando bot real por comando REST...")
                } catch (e: Throwable) {
                    addSimulatedLog("ERROR", "No se pudo arrancar el bot remoto: ${e.localizedMessage}")
                }
            }
        }
    }

    fun stopBot() {
        viewModelScope.launch {
            if (_isLocalDemoActive.value) {
                val current = _status.value
                if (current != null) {
                    _status.value = current.copy(isRunning = false)
                }
                addSimulatedLog("WARN", "▲ Bot Simulado detenido de forma local.")
            } else {
                try {
                    apiService?.stopBot()
                    addSimulatedLog("WARN", "Deteniendo bot real por comando REST...")
                } catch (e: Throwable) {
                    addSimulatedLog("ERROR", "No se pudo detener el bot remoto: ${e.localizedMessage}")
                }
            }
        }
    }

    fun saveConfiguration() {
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
                // Update local simulation configuration parameters
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
                addSimulatedLog("INFO", "Configuración modificada: Compra $${update.orderAmountUsd} USDT | Aplancamiento x${update.leverage} | TP ${update.takeProfitPercent}% | TS ${update.trailingStopPercent}% | SL ${update.stopLossPercent}%")
            } else {
                try {
                    apiService?.updateConfig(update)
                    addSimulatedLog("INFO", "Nueva configuración de trading cargada en el servidor backend.")
                    // Refresh status
                    val updatedStatus = apiService?.getStatus()
                    if (updatedStatus != null) {
                        _status.value = updatedStatus
                    }
                } catch (e: Throwable) {
                    addSimulatedLog("ERROR", "Fallo al subir configuración al host: ${e.localizedMessage}")
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
        if (selectedPairs.value.contains(pair) && selectedPairs.value.size > 1) {
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
