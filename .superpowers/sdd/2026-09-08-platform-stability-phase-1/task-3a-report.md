# Task 3A report — bounded stalled WebSocket shutdown

## Implementation

- Implementation commit: `2118e53f2c8596d14ecfd2684ae5634cfa920709` (`fix: bound stalled websocket shutdown`).
- `GameSocketClient` moves each generation's transport invocation into a generation-owned `CoroutineScope` that inherits the caller dispatcher but not the owner `Job`. This is not `GlobalScope`; it is cancelled when the generation completes.
- The reconnect owner waits only for a bounded virtual-time-compatible shutdown window. A transport that does not cooperate with cancellation is cancelled, abandoned, and permanently generation-gated rather than joined indefinitely.
- Session closes likewise run as owned work with the same bounded deadline. `disconnect()` acknowledges only after a clean transport end or the timed-out detached outcome; it then releases global connect ownership for reset/reconnect.
- The old generation cannot attach `currentMailbox`, publish client state/messages, or send through a replacement after detachment. Ktor cleanup no longer enters an unbounded `NonCancellable` close/join path.

## Root cause

The connect owner called `transport.webSocket()` directly. If the transport suspended before entering its session block, the owner could not select the generation's stop signal, while `disconnect()` waited on `finished`. Once a session was active, `closeAttempt()` directly awaited `attempt.close()`; the Ktor adapter also used `NonCancellable` cleanup. Either non-cooperative operation could retain `connectMutex`, preventing `resetGame()` from clearing its guard and opening a new run.

## TDD evidence

Three new deterministic actual-`GameSocketClient` tests were written before the implementation:

1. `disconnect completes when transport never enters websocket block`
2. `disconnect completes when session close never returns`
3. `reset can reconnect after stalled transport teardown`

RED on baseline `718a623` used virtual time (1,001 ms), compiled successfully, and failed exactly these three liveness assertions: `24 tests completed, 3 failed`. No failure was a compile error.

GREEN focused run: `:shared:testAndroidHostTest --tests "*GameSocketClientTest"` passed all 22 socket-client tests.

## Full verification

With local JBR, Android SDK, Gradle cache, and loopback temp directory configured:

```powershell
./gradlew.bat :shared:testAndroidHostTest --tests "*ReconnectStateMachineTest" --tests "*GameSocketClientTest" :app:compileDevDebugKotlin :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon
```

Result: `BUILD SUCCESSFUL in 26s` (47 actionable tasks). XML results show 22 `GameSocketClientTest` and 5 `ReconnectStateMachineTest`, with zero failures, errors, or skips. `git diff --check` was clean before commit.

## Residual risk

A transport/session that ignores cancellation can still retain its own underlying platform resources until that library or OS eventually releases them. The client deliberately does not join that orphan work: its explicitly owned generation scope is cancelled, and the generation gate prevents it from affecting current connection state or a later replacement. Live network/iOS runtime behavior remains outside these deterministic host tests.
