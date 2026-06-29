package io.github.froyder.websocketmanager

sealed class MarketIntent {
    object Connect : MarketIntent()
    object Disconnect : MarketIntent()
    data class UpdateSubscription(val symbols: Set<String>) : MarketIntent()
    object Retry : MarketIntent()
}