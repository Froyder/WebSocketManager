package io.github.froyder.websocketmanager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val config: ReconnectConfig,
    private val scope: CoroutineScope
) {

    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private var isIntentionalDisconnect = false

    private val _state = MutableStateFlow<MarketState>(MarketState.Idle)
    val state: StateFlow<MarketState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var currentSymbols: Set<String> = emptySet()

    fun connect(symbols: Set<String>) {
        isIntentionalDisconnect = false
        currentSymbols = symbols
        openSocket()
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        reconnectAttempt = 0
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _state.value = MarketState.Disconnected("User disconnected")
    }

    fun updateSubscription(newSymbols: Set<String>) {
        val toUnsubscribe = currentSymbols - newSymbols
        val toSubscribe = newSymbols - currentSymbols
        currentSymbols = newSymbols

        if (toUnsubscribe.isNotEmpty()) {
            webSocket?.send(buildSubscribeMessage("UNSUBSCRIBE", toUnsubscribe))
        }
        if (toSubscribe.isNotEmpty()) {
            webSocket?.send(buildSubscribeMessage("SUBSCRIBE", toSubscribe))
        }
    }

    private fun openSocket() {
        _state.value = if (reconnectAttempt == 0) {
            MarketState.Connecting
        } else {
            MarketState.Reconnecting(reconnectAttempt)
        }

        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws")
            .build()

        webSocket = okHttpClient.newWebSocket(request, createListener())
    }

    private fun createListener() = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempt = 0
            webSocket.send(buildSubscribeMessage("SUBSCRIBE", currentSymbols))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _messages.tryEmit(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isIntentionalDisconnect) return
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (isIntentionalDisconnect) return
            scheduleReconnect()
        }
    }

    internal fun scheduleReconnect() {
        reconnectAttempt++
        if (reconnectAttempt > config.maxAttempts) {
            _state.value = MarketState.Error("Max reconnection attempts (${config.maxAttempts}) reached")
            return
        }

        val delayMs = calculateReconnectDelay(reconnectAttempt, config)
        _state.value = MarketState.Reconnecting(reconnectAttempt)

        scope.launch {
            delay(delayMs.milliseconds)
            if (!isIntentionalDisconnect) openSocket()
        }
    }

    private fun buildSubscribeMessage(method: String, symbols: Set<String>): String {
        val params = symbols.map { "\"${it.lowercase()}@ticker\"" }
        return """{"method":"$method","params":[${params.joinToString(",")}],"id":1}"""
    }
}