package io.github.froyder.websocketmanager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.toMutableList

@Singleton
class BinanceRepository @Inject constructor(
    private val webSocketManager: WebSocketManager
) {
    private val _coins = MutableStateFlow<List<CoinUi>>(emptyList())
    val coins: StateFlow<List<CoinUi>> = _coins.asStateFlow()

    val connectionState: StateFlow<MarketState> = webSocketManager.state

    private val json = Json { ignoreUnknownKeys = true }

    init {
        observeMessages()
    }

    private fun observeMessages() {
        webSocketManager.messages
            .onEach { message -> handleMessage(message) }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    private fun handleMessage(message: String) {
        try {
            val ticker = json.decodeFromString<BinanceTicker>(message)
            updateCoin(ticker)
        } catch (e: Exception) {
            // ignore non-ticker messages (e.g. subscription confirmations)
        }
    }

    private fun updateCoin(ticker: BinanceTicker) {
        val current = _coins.value.toMutableList()
        val index = current.indexOfFirst { it.symbol == ticker.symbol }
        val updated = CoinUi(
            symbol = ticker.symbol,
            price = ticker.lastPrice,
            priceChangePercent = ticker.priceChangePercent,
            isPositive = ticker.priceChangePercent.toDoubleOrNull()?.let { it >= 0 } ?: true
        )
        if (index >= 0) current[index] = updated else current.add(updated)
        _coins.value = current
    }

    fun connect(symbols: Set<String>) = webSocketManager.connect(symbols)
    fun disconnect() = webSocketManager.disconnect()
    fun updateSubscription(symbols: Set<String>) = webSocketManager.updateSubscription(symbols)
}