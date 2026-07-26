# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

This is a **practice/demo project** built to demonstrate WebSocket lifecycle management, MVI architecture, and Hilt dependency injection in a production-like Android context — originally motivated by technical interview preparation. The crypto/Binance domain is incidental; `MarketState`, `CoinUi`, and similar types are domain-specific to this demo and are not intended to be generic or reusable library abstractions.

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

- **`WebSocketManager`** (`@Singleton`) — owns the OkHttp WebSocket. Handles connect/disconnect, subscription protocol (SUBSCRIBE/UNSUBSCRIBE JSON messages per Binance API), and configurable exponential backoff reconnection via `ReconnectConfig`. Receives its `CoroutineScope` via Hilt injection (provided in `AppModule`). Exposes `state: StateFlow<MarketState>` and `messages: SharedFlow<String>`.

- **`BinanceRepository`** (`@Singleton`) — consumes `WebSocketManager.messages`, deserializes `BinanceTicker` JSON frames via kotlinx.serialization, and maintains `coins: StateFlow<List<CoinUi>>`. Subscription confirmations from Binance (non-ticker frames) are silently swallowed in the catch block.

- **`MarketViewModel`** (`@HiltViewModel`) — MVI processor. Translates `MarketIntent` sealed class events from the UI into repository calls. Disconnects on `onCleared`.

- **`MarketScreen`** — pure Compose UI collecting `marketState` and `coins` flows. Never touches the repository.

**State machine (`MarketState`):**
`Idle → Connecting → (Connected | Reconnecting(attempt) | Disconnected | Error)`

## Key implementation details

- **`ReconnectConfig`** — configures backoff via `baseDelayMs`, `maxDelayMs`, `maxAttempts`, and `jitterFactor`. Jitter strategy: `finalDelay = clamped * (1 - jitterFactor * rand)`, keeping delays in `[(1-factor)*clamped, clamped]`. Exceeding `maxAttempts` transitions synchronously to `MarketState.Error`. Default values (1s base, 30s cap, unlimited retries, no jitter) are provided in `AppModule`.
- **`readTimeout(0)`** in `AppModule` is intentional — OkHttp's default read timeout silently kills live WebSocket connections.
- **Subscription diffing** in `WebSocketManager.updateSubscription()` sends only delta UNSUBSCRIBE/SUBSCRIBE messages, not a full reconnect.
- **`isIntentionalDisconnect` flag** in `WebSocketManager` prevents reconnect loops when the user explicitly disconnects.
- **`WebSocketManager`'s `CoroutineScope`** is injected via Hilt (`AppModule.provideApplicationScope`), not created internally — this enables test-controlled dispatch.
- **`BinanceRepository` creates its own `CoroutineScope`** (with `SupervisorJob`) to observe messages independently of any ViewModel lifecycle.
- The `reconnectAttempt` counter is reset to `0` in `onOpen`, not when reconnection is scheduled.
- `_messages` uses `extraBufferCapacity = 64` to avoid dropping frames on the `SharedFlow` when the collector is slow.

## Conventions

- **State mutations**: prefer `_state.update { ... }` over `_state.value = _state.value.copy(...)` for atomic updates.
- **UI → ViewModel**: dispatch through `MarketIntent` sealed class via `processIntent()` — do not call ViewModel methods directly from Composables.

## Testing

- Use `StandardTestDispatcher(testScheduler)` with an injected `CoroutineScope` for deterministic coroutine tests — never rely on real delays or timing.
- `calculateReconnectDelay` is a pure `internal` function; test it directly without coroutines by passing a fake `Random` (anonymous object overriding `nextDouble()`).

## Dependencies

- **OkHttp** — WebSocket transport
- **Hilt + KSP** — dependency injection (no Kapt)
- **kotlinx.serialization** — JSON parsing (`@Serializable` data class `BinanceTicker`)
- **Jetpack Compose + Material3** — UI
- **Coroutines / StateFlow / SharedFlow** — reactive state and messaging
