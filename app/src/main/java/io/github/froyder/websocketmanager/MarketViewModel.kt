package io.github.froyder.websocketmanager

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: BinanceRepository
) : ViewModel() {

    private val defaultSymbols = setOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "ADAUSDT")

    private val _selectedSymbols = MutableStateFlow(defaultSymbols)
    val selectedSymbols: StateFlow<Set<String>> = _selectedSymbols.asStateFlow()

    val marketState: StateFlow<MarketState> = repository.connectionState
    val coins: StateFlow<List<CoinUi>> = repository.coins

    init {
        connect()
    }

    fun processIntent(intent: MarketIntent) {
        when (intent) {
            is MarketIntent.Connect -> connect()
            is MarketIntent.Disconnect -> repository.disconnect()
            is MarketIntent.UpdateSubscription -> updateSubscription(intent.symbols)
            is MarketIntent.Retry -> connect()
        }
    }

    private fun connect() {
        repository.connect(_selectedSymbols.value)
    }

    private fun updateSubscription(symbols: Set<String>) {
        _selectedSymbols.value = symbols
        repository.updateSubscription(symbols)
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}