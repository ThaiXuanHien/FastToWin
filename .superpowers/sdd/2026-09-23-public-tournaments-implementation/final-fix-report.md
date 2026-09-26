# Final review fix wave — public tournaments

Date: 2026-09-26. Branch: `codex/public-tournaments-pwa-fixes`. Starting HEAD: `cc98a58`.

## Findings and root causes

1. An already-sent discovery request was abandoned when a WebSocket closed cleanly. `GameSocketClient` moved to `RECONNECTING` without emitting `ServerMessage.Error`, while `GameController` only cleared discovery loading for transport error codes. `SessionReady` refreshed the tournament hub but not public discovery. The controller now cancels a pending filter debounce and clears abandoned list loading on disconnected/reconnecting/terminal states. When a ready session arrives while discovery is open, it immediately requests the latest filters once; the refresh cancels any pending debounce.
2. The host-facing invitation path returned `PLAYER_ALREADY_JOINED`, whose localization addresses the joining player as “you.” The invitation path now returns `FRIEND_ALREADY_JOINED_TOURNAMENT`, mapped to explicit English and Vietnamese friend-context copy. The self-join code and copy are unchanged.
3. `PostgresTournamentEntryReversalTest` returned normally without `TEST_DATABASE_URL`, appearing as a pass. It now uses the same JUnit assumption as `PostgresTournamentAdmissionTest`, yielding an explicit skip.

## Test-first evidence

Before production edits, the focused command ran `:shared:testAndroidHostTest --tests '*GameStateTest*public tournament clean close*'`, `:server:test --tests '*GameEngineTest*host inviting an existing participant*'`, and `:protocol:jvmTest --tests '*SocialScreenLocalizationTest*already joined errors distinguish*'` with `--continue --no-daemon --no-configuration-cache`. The reconnect test failed at the still-true loading assertion (`GameStateTest.kt:200`); the invitation test failed on the old server code; protocol test compilation failed on the absent `ServerFriendAlreadyJoinedTournament` key. These were the expected missing-behavior failures.

After implementation, the focused run covering all public-tournament shared tests, the new server case, the reversal test, and all `SocialScreenLocalizationTest` cases completed `BUILD SUCCESSFUL in 58s` (exit 0). The reversal test's JUnit result was skipped, not passed.

The reconnect regression uses the real controller and socket client with a scripted transport. It sends an initial discovery request, closes that session cleanly, observes loading clear, changes the filters twice, receives a new `SessionReady`, and verifies exactly one immediate request with the trimmed latest query (`Finals`, ORDER, PAID). Advancing past the debounce does not add another request; list data clears loading. The friend regression invokes the real server engine and checks the host-facing code after the friend joins. The localization regression asserts distinct full EN/VI messages for self and friend contexts.

## Integrated verification

Environment: Android Studio JBR; local Android SDK; `TEMP`/`TMP=D:\HienTX\Work\Android\FastToWin\.tmp`; `JAVA_TOOL_OPTIONS` removed. Java/adb execution required sandbox escalation.

| Check | Result |
| --- | --- |
| `:protocol:jvmTest :server:test :shared:testAndroidHostTest :app:compileDevDebugAndroidTestKotlin :webApp:compileKotlinWasmJs --no-daemon --no-configuration-cache` | Exit 0. JUnit XML: protocol 88/88 pass; server 246 pass, 2 skip; shared 151/151 pass. Total **485 pass, 2 skip, 0 failures/errors**. AndroidTest Kotlin and Wasm compiled. |
| `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.TournamentScreenTest --no-daemon --no-configuration-cache` | Exit 0. Instrumentation XML: **22 pass, 0 skip, 0 failures/errors** on `emulator-5554`. |
| `git diff --check` | Exit 0; only Git LF→CRLF working-copy warnings. |

Both PostgreSQL integration skips are `PostgresTournamentAdmissionTest` and `PostgresTournamentEntryReversalTest`; `TEST_DATABASE_URL` was unset. The Android run printed a logcat excerpt for an Android test-process `TopResumedActivityChangeItem` crash after returning exit 0, despite 22 passing test cases. This is an emulator stability caveat, not a reproduced assertion failure. Existing Android SDK XML-version and Wasm experimental-interop warnings remain.

## Boundary

Changes are limited to the controller reconnect lifecycle and its scripted test, host invitation error code and bilingual localization/tests, and the PostgreSQL test skip guard. No database schema, UI layout, or unrelated source checkout changes were made.
