# WebSocket Market Tracker

A real-time cryptocurrency price tracker for Android, built as a focused practice project to implement **WebSocket lifecycle management**, **MVI architecture**, and **Hilt dependency injection** in a working production-like context.

## Why this project exists

After researching WebSocket integration for technical interviews, I wanted to build something that goes beyond a tutorial — a project where the architecture decisions are intentional and defensible. The crypto domain is secondary; the engineering patterns are the point.

## Architecture

The app is structured in clean layers with strict unidirectional data flow:

```
MarketScreen → MarketIntent → MarketViewModel → BinanceRepository → WebSocketManager
                                    ↑                                      |
                               StateFlow                            SharedFlow (raw JSON)
```

**`WebSocketManager`** — owns the raw OkHttp WebSocket connection. Handles connect, disconnect, subscribe/unsubscribe message protocol, and exponential backoff reconnection on failure. Scoped as `@Singleton`.

**`BinanceRepository`** — translates raw Binance JSON frames into UI models. Maintains a `StateFlow<List<CoinUi>>` that the ViewModel exposes to the screen.

**`MarketViewModel`** — the MVI processor. Accepts `MarketIntent` objects from the UI and drives state transitions. The UI never touches the repository directly.

**`MarketScreen`** — pure Compose UI. Collects state flows, renders connection status, coin list, and symbol selector. Fires intents on user interaction.

## MVI State Machine

```kotlin
sealed class MarketState {
    object Idle : MarketState()
    object Connecting : MarketState()
    data class Connected(val coins: List<CoinUi>) : MarketState()
    data class Reconnecting(val attempt: Int) : MarketState()
    data class Disconnected(val reason: String) : MarketState()
    data class Error(val message: String) : MarketState()
}
```

## Key engineering decisions

**WebSocket subscription diffing** — when the user changes the coin selection, `UpdateSubscription` only sends UNSUBSCRIBE for removed symbols and SUBSCRIBE for added ones, without reconnecting the socket. This is a meaningful lifecycle operation that most toy projects skip.

**Exponential backoff reconnection** — on connection failure the app retries after 1s, 2s, 4s... capped at 30s. The `isIntentionalDisconnect` flag prevents reconnection loops when the user explicitly disconnects.

**Hilt scoping** — `WebSocketManager` and `BinanceRepository` are `@Singleton` (one connection for the app lifetime), `MarketViewModel` is `@HiltViewModel` (tied to the screen lifecycle). `OkHttpClient` is provided explicitly since it's a third-party class.

**`readTimeout(0)`** — OkHttp's default read timeout would silently close a live WebSocket. Disabled explicitly to keep the connection alive.

## Tech stack

- Kotlin
- Jetpack Compose
- OkHttp WebSocket
- Kotlinx Serialization
- Hilt
- Coroutines / StateFlow / SharedFlow
- Binance public WebSocket feed (no auth required)

## Data source

Uses the [Binance public WebSocket stream](https://binance-docs.github.io/apidocs/spot/en/#individual-symbol-mini-ticker-stream) — free, no API key, always live. Subscribes to individual symbol mini-ticker streams for real-time price and 24h change data.
