package io.github.froyder.websocketmanager

sealed class MarketState {
    object Idle : MarketState()
    object Connecting : MarketState()
    data class Connected(val coins: List<CoinUi>) : MarketState()
    data class Reconnecting(val attempt: Int) : MarketState()
    data class Disconnected(val reason: String) : MarketState()
    data class Error(val message: String) : MarketState()
}