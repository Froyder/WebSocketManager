package io.github.froyder.websocketmanager

import kotlin.math.pow
import kotlin.random.Random

data class ReconnectConfig(
    val baseDelayMs: Long = 1_000L,
    val maxDelayMs: Long = 30_000L,
    val maxAttempts: Int = Int.MAX_VALUE,
    val jitterFactor: Double = 0.0
) {
    init {
        require(baseDelayMs > 0) { "baseDelayMs must be positive" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be >= baseDelayMs" }
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be in [0.0, 1.0]" }
    }
}

internal fun calculateReconnectDelay(
    attempt: Int,
    config: ReconnectConfig,
    random: Random = Random.Default
): Long {
    val exponential = (config.baseDelayMs * 2.0.pow(attempt - 1)).toLong()
    val clamped = minOf(exponential, config.maxDelayMs)
    val jitter = (clamped * config.jitterFactor * random.nextDouble()).toLong()
    return clamped - jitter
}
