package io.github.froyder.websocketmanager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackoffTest {

    // ── calculateReconnectDelay ──────────────────────────────────────────────

    @Test
    fun `attempt 1 returns baseDelayMs when no jitter`() {
        val config = ReconnectConfig(baseDelayMs = 1_000L, jitterFactor = 0.0)
        assertEquals(1_000L, calculateReconnectDelay(1, config))
    }

    @Test
    fun `exponential growth across attempts`() {
        val config = ReconnectConfig(baseDelayMs = 1_000L, maxDelayMs = 60_000L, jitterFactor = 0.0)
        assertEquals(1_000L, calculateReconnectDelay(1, config))
        assertEquals(2_000L, calculateReconnectDelay(2, config))
        assertEquals(4_000L, calculateReconnectDelay(3, config))
        assertEquals(8_000L, calculateReconnectDelay(4, config))
    }

    @Test
    fun `delay is clamped at maxDelayMs`() {
        val config = ReconnectConfig(baseDelayMs = 1_000L, maxDelayMs = 30_000L, jitterFactor = 0.0)
        assertEquals(30_000L, calculateReconnectDelay(10, config))
    }

    @Test
    fun `jitterFactor zero produces deterministic delay`() {
        val config = ReconnectConfig(baseDelayMs = 1_000L, jitterFactor = 0.0)
        assertEquals(
            calculateReconnectDelay(3, config, Random(42)),
            calculateReconnectDelay(3, config, Random(42))
        )
    }

    @Test
    fun `jitter reduces delay below clamped value`() {
        val config = ReconnectConfig(baseDelayMs = 1_000L, maxDelayMs = 30_000L, jitterFactor = 0.5)
        // attempt=3 → clamped=4000; jitter = (4000 * 0.5 * 0.5).toLong() = 1000 → finalDelay = 3000
        val halfRandom = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = 0.5
        }
        assertEquals(3_000L, calculateReconnectDelay(3, config, halfRandom))
    }

    @Test
    fun `jitter stays within bounds over many iterations`() {
        val config = ReconnectConfig(baseDelayMs = 1_000L, maxDelayMs = 30_000L, jitterFactor = 0.3)
        val random = Random(seed = 99L)
        val attempt = 4  // unclamped = 8000, fits under maxDelayMs
        val clamped = 8_000L
        val minExpected = (clamped * (1.0 - config.jitterFactor)).toLong()
        repeat(1_000) {
            val delay = calculateReconnectDelay(attempt, config, random)
            assertTrue("delay $delay below minimum $minExpected", delay >= minExpected)
            assertTrue("delay $delay above clamped $clamped", delay <= clamped)
        }
    }

    @Test
    fun `jitterFactor 1 allows delay down to zero`() {
        val config = ReconnectConfig(baseDelayMs = 1_000L, jitterFactor = 1.0)
        val nearOneRandom = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = 0.9999999999
        }
        val delay = calculateReconnectDelay(1, config, nearOneRandom)
        assertTrue("delay $delay should be near 0", delay < 2L)
    }

    // ── ReconnectConfig validation ───────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `baseDelayMs zero throws`() {
        ReconnectConfig(baseDelayMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `baseDelayMs negative throws`() {
        ReconnectConfig(baseDelayMs = -1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `maxDelayMs less than baseDelayMs throws`() {
        ReconnectConfig(baseDelayMs = 5_000L, maxDelayMs = 1_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `maxAttempts zero throws`() {
        ReconnectConfig(maxAttempts = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `jitterFactor above 1 throws`() {
        ReconnectConfig(jitterFactor = 1.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `jitterFactor negative throws`() {
        ReconnectConfig(jitterFactor = -0.1)
    }

    // ── WebSocketManager max-attempts state transition ───────────────────────

    @Test
    fun `exceeding maxAttempts transitions to Error state`() = runTest {
        // StandardTestDispatcher never runs coroutines until time is explicitly advanced.
        // Assertions after each call therefore prove the state change is synchronous —
        // no advanceTimeBy/advanceUntilIdle is needed or called.
        val manager = WebSocketManager(
            okHttpClient = okhttp3.OkHttpClient(),
            config = ReconnectConfig(maxAttempts = 2),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        manager.scheduleReconnect()  // attempt 1 ≤ 2 → Reconnecting (coroutine queued, not run)
        assertTrue(manager.state.value is MarketState.Reconnecting)

        manager.scheduleReconnect()  // attempt 2 ≤ 2 → Reconnecting (coroutine queued, not run)
        assertTrue(manager.state.value is MarketState.Reconnecting)

        // attempt 3 > 2 → Error set synchronously, returns before scope.launch
        manager.scheduleReconnect()
        assertTrue(
            "Expected Error state, got ${manager.state.value}",
            manager.state.value is MarketState.Error
        )

        // runTest drains the scheduler after this block; disconnect() sets
        // isIntentionalDisconnect=true so the queued coroutines skip openSocket().
        manager.disconnect()
    }
}
