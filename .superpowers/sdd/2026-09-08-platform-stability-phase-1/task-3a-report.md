# Task 3A report — bounded stalled WebSocket shutdown

## Implementation

- Implementation commit: `c9a7461e2904208493e252ebd23ffce6aafabe37` (`fix: harden stalled websocket generation teardown`).
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

RED on baseline `718a623` used virtual time (1,001 ms), compiled successfully, and failed exactly these three liveness assertions: `22 GameSocketClientTest tests completed, 3 failed` (19 baseline + 3 new). No failure was a compile error. The earlier count of 24 was incorrect.

GREEN focused run: `:shared:testAndroidHostTest --tests "*GameSocketClientTest"` passed all 24 socket-client tests, including the two review regressions for cancellation-ignoring outer teardown and late callbacks. The original Task 3A RED/GREEN count remains 22 (19 baseline + 3 new).

## Review follow-up

- Outer transport teardown is now a separately signalled terminal boundary: the owner can detach the completed session and continue retry/stop while the transport's outer cleanup remains suspended. The test fake ignores cancellation until explicitly released, so it exercises the bounded timeout rather than cooperative cancellation.
- A cancellation-ignoring handshake can invoke its session callback after the shutdown deadline. The mailbox and run generation gates reject that callback before attach, state, message, send, or replacement close; cleanup is isolated in an owned detached scope.
- Terminal error emission is also guarded by the attempt gate, so a stale callback cannot emit after `terminal()` rejects it.

## Full verification

With local JBR, Android SDK, Gradle cache, and loopback temp directory configured:

```powershell
./gradlew.bat :shared:testAndroidHostTest --tests "*ReconnectStateMachineTest" --tests "*GameSocketClientTest" :app:compileDevDebugKotlin :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon
```

Result: `BUILD SUCCESSFUL in 24s` (47 actionable tasks). XML results show 24 `GameSocketClientTest` and 5 `ReconnectStateMachineTest`, with zero failures, errors, or skips. `git diff --check` was clean before commit.

## Residual risk

A transport/session that ignores cancellation can still retain its own underlying platform resources until that library or OS eventually releases them. The client deliberately does not join that orphan work: its explicitly owned generation scope is cancelled, and the generation gate prevents it from affecting current connection state or a later replacement. Live network/iOS runtime behavior remains outside these deterministic host tests.
