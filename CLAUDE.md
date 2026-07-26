# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & test commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew test --tests "io.github.froyder.websocketmanager.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

## Architecture

This is a single-module, single-screen Android app. All source lives in `app/src/main/java/io/github/froyder/websocketmanager/`.

**Data flow (strict MVI, unidirectional):**
```
MarketScreen → MarketIntent → MarketViewModel → BinanceRepository → WebSocketManager
                                    ↑                                      |
                               StateFlow                            SharedFlow (raw JSON)
```

**Layer responsibilities:**

- **`WebSocketManager`** (`@Singleton`) — owns the OkHttp WebSocket. Handles connect/disconnect, subscription protocol (SUBSCRIBE/UNSUBSCRIBE JSON messages per Binance API), and exponential backoff reconnection (1s, 2s, 4s… capped at 30s). Exposes `state: StateFlow<MarketState>` and `messages: SharedFlow<String>`.

- **`BinanceRepository`** (`@Singleton`) — consumes `WebSocketManager.messages`, deserializes `BinanceTicker` JSON frames via kotlinx.serialization, and maintains `coins: StateFlow<List<CoinUi>>`. Subscription confirmations from Binance (non-ticker frames) are silently swallowed in the catch block.

- **`MarketViewModel`** (`@HiltViewModel`) — MVI processor. Translates `MarketIntent` sealed class events from the UI into repository calls. Disconnects on `onCleared`.

- **`MarketScreen`** — pure Compose UI collecting `marketState` and `coins` flows. Never touches the repository.

**State machine (`MarketState`):**
`Idle → Connecting → (Connected | Reconnecting(attempt) | Disconnected | Error)`

## Key implementation details

- **`readTimeout(0)`** in `AppModule` is intentional — OkHttp's default read timeout silently kills live WebSocket connections.
- **Subscription diffing** in `WebSocketManager.updateSubscription()` sends only delta UNSUBSCRIBE/SUBSCRIBE messages, not a full reconnect.
- **`isIntentionalDisconnect` flag** in `WebSocketManager` prevents reconnect loops when the user explicitly disconnects.
- **`BinanceRepository` creates its own `CoroutineScope`** (with `SupervisorJob`) to observe messages independently of any ViewModel lifecycle.
- The `reconnectAttempt` counter is reset to `0` in `onOpen`, not when reconnection is scheduled.
- `_messages` uses `extraBufferCapacity = 64` to avoid dropping frames on the `SharedFlow` when the collector is slow.

## Dependencies
    
- **OkHttp** — WebSocket transport
- **Hilt + KSP** — dependency injection (no Kapt)
- **kotlinx.serialization** — JSON parsing (`@Serializable` data class `BinanceTicker`)
- **Jetpack Compose + Material3** — UI
- **Coroutines / StateFlow / SharedFlow** — reactive state and messaging