package io.github.froyder.websocketmanager

data class CoinUi(
    val symbol: String,
    val price: String,
    val priceChangePercent: String,
    val isPositive: Boolean
)