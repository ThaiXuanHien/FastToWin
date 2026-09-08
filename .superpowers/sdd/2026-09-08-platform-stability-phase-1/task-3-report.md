# Task 3 report — transport mới cho mỗi reconnect attempt

## Files changed

- shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/GameSocketClient.kt
- shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt
- shared/src/commonTest/kotlin/com/hienthai/fastowin/data/network/GameSocketClientTest.kt

## TDD evidence

RED tests were added for discarding closed attempts and immediate retry. The initial focused Gradle invocation could not start the test runtime because Windows denied Java/Gradle process startup; this was followed by escalated runs, which reached Gradle but failed with `Unable to establish loopback connection` before compilation.

## Implementation

- Added injectable RetrySleeper and RetryJitter seams plus recording transport/session seams.
- Each reconnect iteration enters a fresh transport webSocket block and clears the active session in finally.
- Added conflated retryRequests and retryNow(), cancelling the active attempt before selecting an immediate new attempt.
- Added account resume-token hello handling, token persistence, invalid-resume clearing, and terminal account/session error handling.
- Added GameController.retryConnection() without starting another message collector.

## Verification

- `git diff --check`: no whitespace errors.
- Focused Gradle and requested Android/Wasm/JS compiler command: blocked by environment loopback failure (`java.io.IOException: Unable to establish loopback connection`).

## Concerns

The Gradle daemon cannot establish its loopback pipe in this Windows sandbox, so compile/test GREEN evidence requires rerunning the brief commands in a host environment where Gradle Java loopback is available.

## Fix round 1

- INVALID_RESUME_TOKEN now clears the persisted token, closes the current attempt, and permits exactly one auth-only retry; a second invalid token is terminal.
- INVALID_ACCESS_TOKEN is terminal per the reconnect contract; replacement close remains terminal.
- RECONNECTING is published before every backoff, and retryNow detaches the session before asynchronously closing it to prevent stale sends.
- Retry seams are no longer exposed by the public constructor; GameSocketClient is internal, and the Ktor close extension is imported for JS/Wasm.
- Focused tests/compiler command was requested again; environment result is recorded below after execution.

## Fix round 2

- INVALID_RESUME_TOKEN now sets an explicit immediate-retry flag, bypassing normal backoff; a second rejection is terminal.
- Lifecycle mutex serializes send and close/disconnect. retryNow detaches the session before scheduling its mutex-protected close, preventing stale sends.
- Corrected generated Error construction to use the named messageKey field (restoring localization-key assertions).
- Added lifecycle retry seams remain internal and GameSocketClient remains internal to avoid exposing test-only types.
- Controller verification reported Android, Wasm and JS compilation passing; focused tests previously failed only the localization-key assertion and closed-attempt expectation, both addressed here. A direct rerun from this agent remained blocked by Gradle loopback startup.

## Fix round 3

Lifecycle ownership now routes session replacement, send, disconnect, retry close, and transport cleanup through the same Mutex. Invalid resume closes under that guard and bypasses normal backoff for its one auth-only retry.

Added deterministic test methods: `repeated retry taps are conflated without parallel attempts`, `invalid resume clears token and immediately sends account hello without token`, `second invalid resume becomes terminal and does not loop`, `invalid access token terminal no retry`, `session expired terminal no retry`, `replacement close terminal no retry`, `account SessionReady saves token used next reconnect`, `guest SessionReady saves token used next reconnect`, and `send racing retry and disconnect never sends after close`. Scheduler tests are annotated with `ExperimentalCoroutinesApi`.

The focused test/compile command was attempted after this round; this agent environment still fails Gradle startup with `Unable to establish loopback connection` before task execution. Controller's preceding fresh run had Android, Wasm and JS compile PASS.

## Fix round 4

The three test-source generic inference errors reported at lines 37, 162 and 220 were corrected with explicit `ClientMessage`/`ServerMessage` list element types. The controller could not rerun RED after those fixes because its external execution tool hit a usage limit. RED behavioral evidence remains the controller's earlier fresh run: `closed attempt is discarded before retry` and `repeated retry taps are conflated without parallel attempts` failed against the prior implementation; the review also identified stale hello/send and retry-close ownership races.

`GameSocketClient` now owns transport lifecycle in one reconnect event loop. Each attempt has a private mailbox; public sends enqueue into the mailbox, and the owner alone sends/ closes the associated session. Retry and stop are selected alongside incoming frames and backoff, retry signals are conflated and drained around close, and token authentication is cancellable while waiting for retry/stop. This prevents a retry close job from closing a replacement and prevents hello/send work from using a detached session.

Focused Gradle verification remains `UNVERIFIED`: the requested Gradle process is blocked in this agent environment by Windows process/loopback restrictions, and the controller's second RED run was blocked by usage limit. `git diff --check` passes.

## Fix round 5 — acknowledged shutdown and generation ownership

- Confirmed the reviewer race: `disconnect()` returned after putting a stop into a client-wide channel; cancelling that owner before it consumed the signal let a replacement inherit the stop. A second `connect()` could also open while the first transport was still closing.
- A client-wide `connectMutex` now serializes complete connect lifetimes, including cancellation teardown. Each owner has its own state machine, conflated retry/stop signals, stopping flag and completion acknowledgement. `disconnect()` captures only that owner, signals it and awaits its acknowledgement without taking either mutex. No signals or acknowledgements are shared across generations.
- Cancellation detaches and cancels/drains the attempt's mailbox in `NonCancellable`; only the matching mailbox can be detached. All transport teardown completes before the owner acknowledgement and connect mutex release. The Ktor adapter closes and cancels/joins its session and response jobs in `NonCancellable`, because Ktor 3.5.2's default response cleanup joins in the possibly-cancelled caller context. `close()` prevents later connects and signals only the current owner.
- `GameController.resetGame()` retains the old session handle until shutdown acknowledgement and `cancelAndJoin()` finish. A reset guard prevents duplicate resets or `ensureSocketSession()` calls from starting another owner during reset. The public controller API remains unchanged and all changed code remains common multiplatform Kotlin.
- Added four actual-client deterministic tests: acknowledged disconnect followed by a live new connect; disconnect/cancellation/restart before the old stop is consumed; old cancellation cleanup cannot detach/close a replacement mailbox; and controller-style disconnect/join/restart. Gated cleanup asserts `maxConcurrentLiveSessions == 1`, replacement liveness, and successful replacement sends. All 15 pre-existing round-4 socket tests/assertions remain intact.

### Round 5 RED/GREEN evidence

- Initial RED build exposed two pre-existing round-4 compile errors: an invalid `onReceiveCatching` extension import (the API is a channel member), and the missing `return result` in `handleMessage`. Fixed only those syntax errors before behavioral RED.
- Behavioral RED: focused Gradle execution ran 24 tests, with the three new acknowledgement/ownership regression tests failing against the old lifecycle implementation. The controller-style joined-restart test already passed; no assertion was weakened.
- Final GREEN: `./gradlew.bat :shared:testAndroidHostTest --tests '*GameSocketClientTest' --tests '*ReconnectStateMachineTest' :app:compileDevDebugKotlin :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon` returned `BUILD SUCCESSFUL in 30s` (47 actionable tasks). XML results: **19 GameSocketClient tests + 5 ReconnectStateMachine tests, zero failures/errors/skips**. Android, Wasm and JS compilation passed.
- Process-only setup: `JAVA_HOME=C:/Program Files/Android/Android Studio/jbr`, `ANDROID_HOME=C:/Users/Hien Hue/AppData/Local/Android/Sdk`, `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.artifacts/jdk-tmp`. Default sandbox execution denied Java startup; approved escalated execution succeeded this round. No project environment/configuration files were changed.
- Final `git diff --check`: PASS. Verification above covers deterministic injected-transport host tests and multiplatform compilation, not an iOS runtime or a live-device network test. No subagents were spawned, per the task brief.
