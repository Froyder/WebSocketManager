package io.github.froyder.websocketmanager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BinanceTicker(
    @SerialName("s") val symbol: String,
    @SerialName("c") val lastPrice: String,
    @SerialName("P") val priceChangePercent: String
)