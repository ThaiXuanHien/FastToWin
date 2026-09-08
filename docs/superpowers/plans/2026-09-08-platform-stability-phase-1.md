# Platform Stability Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thiện Giai đoạn 1 bằng cơ chế kết nối lại có trạng thái rõ ràng, phục hồi trận từ snapshot server, loại bỏ xem lại trận, sửa nền và tương tác Web, tinh chỉnh thẻ mùa, đồng thời chặn mọi chuỗi UI mới chưa đủ 12 ngôn ngữ.

**Architecture:** Tách chính sách reconnect thành lõi Kotlin thuần để kiểm thử xác định, còn `GameSocketClient` chỉ quản lý transport Ktor và gửi sự kiện vào state machine. Server vẫn là nguồn dữ liệu duy nhất: sau khi xác thực, resume trả về snapshot đầy đủ hoặc kết quả thua chính thức; Compose giữ nguyên màn hiện tại và phủ trạng thái reconnect lên trên. Những thay đổi Web, UI và localization dùng các component/cổng kiểm tra chung hiện có để không tạo nhánh giao diện riêng theo nền tảng.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1, Ktor WebSocket, kotlinx.coroutines, kotlinx.serialization, Android Compose UI Test, Kotlin Test, Playwright Chromium/Firefox/WebKit.

**Spec:** `docs/superpowers/specs/2026-09-08-economy-clan-quota-platform-design.md`

## Global Constraints

- State machine kết nối có đúng sáu trạng thái: `DISCONNECTED`, `CONNECTING`, `AUTHENTICATING`, `CONNECTED`, `RECONNECTING`, `TERMINAL`.
- Mỗi lần thử kết nối phải tạo một WebSocket session mới; không gửi dữ liệu qua session đã đóng.
- Lịch retry sau mất kết nối là 1, 2, 4, 8 giây, sau đó 5 giây mỗi lần; server giữ phòng đúng 30 giây.
- Tài khoản phải được xác thực trước khi server sử dụng resume token; khách vẫn resume bằng token hiện có.
- Snapshot server thay thế toàn bộ room, board, target, điểm, số sai, phase và sequence cục bộ; không merge trạng thái optimistic cũ.
- Màn hình đang mở được giữ nguyên dưới lớp phủ reconnect; thao tác game bị khóa cho đến khi snapshot chính thức được áp dụng.
- `Thử lại` bắt đầu một chu kỳ transport mới ngay lập tức. Session tài khoản hết hạn chuyển sang Đăng nhập. Phòng hết hạn tạo kết quả thua chính thức trước khi quay về sảnh.
- Thay đổi protocol chỉ được bổ sung field có mặc định; server mới chấp nhận client version 38–41 và client mới phải đọc được phản hồi server version 40–41 trong giai đoạn rolling update.
- Bỏ UI phát lại trận nhưng giữ `match_events`, `MatchDetailSnapshot.events`, lịch sử/chi tiết trận, chia sẻ kết quả và Mời đấu lại.
- Web dùng nền `#071824` cho `html`, `body` và `#fastToWinRoot`; vùng ngoài khung nội dung không được trắng.
- Nội dung Web giữ chiều rộng tối đa 430dp; dialog tối đa 420dp và không vượt viewport sau khi trừ 10dp mỗi cạnh.
- Pull-to-refresh trên Web chỉ dùng cử chỉ kéo khi thiết bị có touch; desktop dùng action làm mới nằm trong nội dung phù hợp con trỏ.
- `System` cộng 12 catalog `vi`, `en`, `zh-Hans`, `ja`, `ko`, `es`, `pt-BR`, `fr`, `de`, `id`, `th`, `ru`; mọi key mới có bản dịch và placeholder giống English.
- Không dịch tên người chơi, tên/mật khẩu phòng, tên/mô tả bang hoặc nội dung do người dùng nhập.
- Không stage hoặc sửa các file `.idea/**` và thay đổi đang có trong `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TournamentScreen.kt`.

---

## File Structure

Các file mới giữ phần chính sách thuần tách khỏi transport và Compose:

```text
shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/
  ReconnectStateMachine.kt       # trạng thái, event, quyết định và lịch retry thuần Kotlin
shared/src/commonTest/kotlin/com/hienthai/fastowin/data/network/
  ReconnectStateMachineTest.kt   # ma trận chuyển trạng thái và thời gian retry
shared/src/commonTest/kotlin/com/hienthai/fastowin/state/
  AuthoritativeSnapshotTest.kt   # chứng minh snapshot thay trạng thái optimistic
app/src/androidTest/java/com/hienthai/fastowin/
  ReconnectOverlayUiTest.kt      # lớp phủ, khóa thao tác và nút Thử lại
```

`GameSocketClient.kt` sở hữu đúng một active session và factory tạo attempt mới.
`GameController.kt` chuyển event mạng thành `GameState` và áp dụng snapshot.
`GameEngine.kt` xác thực account resume token, kết thúc trận quá hạn và phát kết
quả. `FastToWinApp.kt` chỉ quyết định lớp phủ và điều hướng terminal.

---

### Task 1: State machine và lịch reconnect xác định

**Files:**
- Create: `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/ReconnectStateMachine.kt`
- Create: `shared/src/commonTest/kotlin/com/hienthai/fastowin/data/network/ReconnectStateMachineTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/GameSocketClient.kt:26-33`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt:27-33`

**Interfaces:**
- Produces: `SocketConnectionState`, `ReconnectEvent`, `ReconnectDecision`, `ReconnectStateMachine.reduce(event)`, `reconnectDelayMillis(attempt)`.
- Consumes: không phụ thuộc Ktor, Compose, clock thật hoặc random.

- [ ] **Step 1: Viết test đỏ cho đủ trạng thái và lịch retry**

```kotlin
class ReconnectStateMachineTest {
    @Test
    fun `unexpected close retries with bounded schedule`() {
        val machine = ReconnectStateMachine()
        machine.reduce(ReconnectEvent.Start)
        machine.reduce(ReconnectEvent.TransportOpened)
        machine.reduce(ReconnectEvent.Authenticated)

        assertEquals(ReconnectDecision.RetryAfter(1_000), machine.reduce(ReconnectEvent.TransportLost))
        assertEquals(ReconnectDecision.RetryAfter(2_000), machine.reduce(ReconnectEvent.AttemptFailed))
        assertEquals(ReconnectDecision.RetryAfter(4_000), machine.reduce(ReconnectEvent.AttemptFailed))
        assertEquals(ReconnectDecision.RetryAfter(8_000), machine.reduce(ReconnectEvent.AttemptFailed))
        assertEquals(ReconnectDecision.RetryAfter(5_000), machine.reduce(ReconnectEvent.AttemptFailed))
        assertEquals(SocketConnectionState.RECONNECTING, machine.state)
    }

    @Test
    fun `manual retry resets attempt and requests immediate fresh transport`() {
        val machine = ReconnectStateMachine()
        machine.reduce(ReconnectEvent.Start)
        machine.reduce(ReconnectEvent.AttemptFailed)
        machine.reduce(ReconnectEvent.AttemptFailed)
        assertEquals(ReconnectDecision.ConnectNow, machine.reduce(ReconnectEvent.ManualRetry))
        assertEquals(0, machine.attempt)
    }

    @Test
    fun `expired or replaced account session is terminal`() {
        val machine = ReconnectStateMachine()
        assertEquals(ReconnectDecision.Stop, machine.reduce(ReconnectEvent.SessionExpired))
        assertEquals(SocketConnectionState.TERMINAL, machine.state)
        assertEquals(ReconnectDecision.Stop, machine.reduce(ReconnectEvent.ManualRetry))
    }
}
```

- [ ] **Step 2: Chạy test và xác nhận RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
./gradlew.bat :shared:testAndroidHostTest --tests "*ReconnectStateMachineTest" --no-daemon
```

Expected: compilation fails because `ReconnectStateMachine`, `ReconnectEvent`
and `ReconnectDecision` do not exist.

- [ ] **Step 3: Cài đặt lõi state machine nhỏ nhất**

```kotlin
enum class SocketConnectionState {
    DISCONNECTED, CONNECTING, AUTHENTICATING, CONNECTED, RECONNECTING, TERMINAL
}

sealed interface ReconnectEvent {
    data object Start : ReconnectEvent
    data object TransportOpened : ReconnectEvent
    data object Authenticated : ReconnectEvent
    data object TransportLost : ReconnectEvent
    data object AttemptFailed : ReconnectEvent
    data object ManualRetry : ReconnectEvent
    data object SessionExpired : ReconnectEvent
    data object Stop : ReconnectEvent
}

sealed interface ReconnectDecision {
    data object None : ReconnectDecision
    data object ConnectNow : ReconnectDecision
    data class RetryAfter(val delayMillis: Long) : ReconnectDecision
    data object Stop : ReconnectDecision
}

internal fun reconnectDelayMillis(attempt: Int): Long = when (attempt) {
    0 -> 1_000L
    1 -> 2_000L
    2 -> 4_000L
    3 -> 8_000L
    else -> 5_000L
}
```

Implement `reduce` as the only mutation point. `ManualRetry` is ignored in
`TERMINAL`; `Stop` moves to `DISCONNECTED`; `Authenticated` resets `attempt`.
Move `SocketConnectionState` out of `GameSocketClient.kt`. Add `TERMINAL` to
`ConnectionStatus` so controller mappings remain exhaustive.

- [ ] **Step 4: Chạy test state machine và test socket hiện tại**

```powershell
./gradlew.bat :shared:testAndroidHostTest --tests "*ReconnectStateMachineTest" --tests "*GameSocketClientTest" --no-daemon
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/ReconnectStateMachine.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/GameSocketClient.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/data/network/ReconnectStateMachineTest.kt
git commit -m "refactor: model socket reconnect states"
```

---

### Task 2: Resume account an toàn và kết quả chính thức khi hết thời hạn phòng

**Files:**
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt:7-11,631-643,931-937`
- Create: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/protocol/GameProtocolCompatibilityTest.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt:83-137,1962-2045,3160-3168,3737-3745`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/GameWebSocketTest.kt`

**Interfaces:**
- Consumes: `ConnectAccount.accessToken` remains the sole account identity credential.
- Produces: additive `ConnectAccount.resumeToken: String? = null`; `SessionReady.resumeToken` returns an opaque reconnect token for account and guest; `ConnectedPlayer` carries the token selected by server.
- Produces: expired playing room emits `GameFinished` with the connected opponent as winner and stores one pending finished snapshot for the disconnected player; the next valid `SessionReady.currentGame` returns that `FINISHED` snapshot exactly once. Waiting room still emits `RoomClosed(code = "ROOM_DISCONNECTED_TOO_LONG")`.

- [ ] **Step 1: Viết test protocol tương thích cộng thêm**

Create `GameProtocolCompatibilityTest.kt` with the exact legacy payload:

```kotlin
@Test
fun `version 40 connect account decodes without resume token`() {
    val raw = """{"type":"connect_account","accessToken":"access","protocolVersion":40}"""
    val decoded = ProtocolJson.decodeFromString<ClientMessage>(raw)
    assertEquals(ClientMessage.ConnectAccount("access", null, 40), decoded)
}

@Test
fun `version 40 session ready decodes without resume token`() {
    val raw = """{"type":"session_ready","playerId":"player-1","protocolVersion":40}"""
    val decoded = ProtocolJson.decodeFromString<ServerMessage>(raw)
    assertEquals(ServerMessage.SessionReady("player-1", null, null, 40), decoded)
}
```

Raise `PROTOCOL_VERSION` to 41 and keep `MIN_COMPATIBLE_PROTOCOL_VERSION = 38`.

- [ ] **Step 2: Viết integration test đỏ cho account reconnect và hết hạn**

In the existing `registered account reconnects to the same active game snapshot`
Ktor test, require a non-null token from the first `SessionReady` and send it on
the second socket:

```kotlin
val hostReady = host.receiveMessage<ServerMessage.SessionReady>()
val accountResumeToken = assertNotNull(hostReady.resumeToken)
resumedHost.sendMessage(
    ClientMessage.ConnectAccount(authSession.accessToken, accountResumeToken)
)
val resumed = resumedHost.receiveMessage<ServerMessage.SessionReady>()
assertEquals(hostReady.playerId, resumed.playerId)
assertEquals(3, assertNotNull(resumed.currentGame).currentTarget)
```

In `registered account authenticates websocket with access token`, replace the
old `assertEquals(null, ready.resumeToken)` with
`assertNotNull(ready.resumeToken)` so the original authentication test enforces
the new additive contract too.

Add this concrete expiry test to `GameEngineTest.kt`:

```kotlin
@Test
fun `expired playing room records official forfeit`() = runTest {
    var now = 1_000L
    val savedMatches = mutableListOf<CompletedMatch>()
    val engine = GameEngine(
        matchResultRepository = MatchResultRepository { savedMatches += it },
        nowMillis = { now }
    )
    val host = engine.connectGuest("Host", null)
    val guest = engine.connectGuest("Guest", null)
    val room = engine.handle(
        host.playerId,
        ClientMessage.CreateRoom("Reconnect expiry", null, ProtocolGameMode.ORDER)
    ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
    startRoom(engine, host.playerId, guest.playerId, room.roomId)

    engine.markDisconnected(host.playerId)
    now += 30_001L
    val finished = engine.cleanupExpiredSessions().map(Delivery::message)
        .filterIsInstance<ServerMessage.GameFinished>().single().game
    val persisted = savedMatches.single()
    val resumed = engine.connectGuest("Host", host.resumeToken)

    assertEquals(guest.playerId, finished.winnerPlayerId)
    assertEquals(MatchOutcome.LOSS, persisted.players.single { it.playerId == host.playerId }.outcome)
    assertEquals(MatchOutcome.WIN, persisted.players.single { it.playerId == guest.playerId }.outcome)
    assertEquals(RoomPhase.FINISHED, assertNotNull(resumed.currentGame).phase)
    assertEquals(guest.playerId, resumed.currentGame?.winnerPlayerId)
    assertEquals(null, engine.connectGuest("Host", host.resumeToken).currentGame)
}
```

- [ ] **Step 3: Chạy test và xác nhận RED**

```powershell
./gradlew.bat :protocol:jvmTest --tests "*GameProtocolCompatibilityTest" :server:test --tests "*GameWebSocketTest*registered account reconnects*" --tests "*GameEngineTest*expired playing room*" --no-daemon
```

Expected: the first test fails because account `SessionReady.resumeToken` is
null; the second fails because non-tournament expired rooms are closed without
persisting a match result or retaining the one-shot finished snapshot.

- [ ] **Step 4: Thêm token account mà không đổi dấu hiệu account hiện tại**

Keep `GuestSession.resumeToken == null` as the existing account marker. Add a
separate field:

```kotlin
private data class GuestSession(
    val playerId: String,
    var resumeToken: String?,
    var displayName: String,
    var accountResumeToken: String? = null,
    var avatarId: String? = null,
    var frameId: String = "frame_default",
    var isConnected: Boolean = true,
    var disconnectedAtMillis: Long? = null,
    var latencyMillis: Long? = null
)
```

Extend the wire message additively:

```kotlin
data class ConnectAccount(
    val accessToken: String,
    val resumeToken: String? = null,
    val protocolVersion: Int = PROTOCOL_VERSION
) : ClientMessage()
```

In `Application.kt`, authenticate `accessToken` first. Only after successful
authentication pass `resumeToken` into `GameEngine.connectAccount`. Inside the
engine, compare a supplied token with the existing session for that authenticated
user. A missing token remains valid for protocol 40 clients; a non-null mismatch
returns `ServerMessage.Error(code = "INVALID_RESUME_TOKEN")` without exposing a
room. Issue a fresh secure opaque token for the first valid account connection
and return it through `ConnectedPlayer`/`SessionReady`.

- [ ] **Step 5: Persist a normal-match forfeit before room removal**

Add `pendingReconnectResultsByPlayerId: MutableMap<String, GameSnapshot>` next
to `rooms`. In `cleanupExpiredSessions`, for every `PLAYING` room choose the still-connected
opponent as `forcedWinnerId`, set phase to `FINISHED`, append the disconnected
player to `finishedPlayerIds`, create `CompletedMatch`, and deliver
`GameFinished(room.snapshot())`. Store that same final snapshot under the
disconnected player ID before removing the room. `connectIdentity` returns
`roomFor(playerId)?.snapshot() ?: pendingReconnectResultsByPlayerId.remove(playerId)`
as `ConnectedPlayer.currentGame`, making the official result one-shot. Reuse the current `takeCompletedMatch()` and
match repository path so Elo, reward eligibility and history remain centralized.
Do not award the forfeiting player because the existing active-leave rule is
`0 Vàng, 0 XP`.

- [ ] **Step 6: Chạy server unit và WebSocket integration tests**

```powershell
./gradlew.bat :server:test --tests "*GameEngineTest" --tests "*GameWebSocketTest" --no-daemon
```

Expected: all engine and real Ktor WebSocket tests pass, including legacy
protocol versions and account-session replacement.

- [ ] **Step 7: Commit**

```powershell
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt protocol/src/commonTest/kotlin/com/hienthai/fastowin/protocol/GameProtocolCompatibilityTest.kt server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt server/src/test/kotlin/com/hienthai/fastowin/server/GameWebSocketTest.kt
git commit -m "fix: resume active matches authoritatively"
```

---

### Task 3: Transport mới cho mỗi attempt và nút retry thật sự khởi động lại

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/GameSocketClient.kt`
- Modify: `shared/src/commonTest/kotlin/com/hienthai/fastowin/data/network/GameSocketClientTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt:182-230,1650-1705`

**Interfaces:**
- Consumes: `ReconnectStateMachine` and additive account resume token from Tasks 1–2.
- Produces: `GameSocketClient.retryNow(): Unit`; every attempt runs one `client.webSocket` block and clears its session in `finally`.
- Produces: `GameController.retryConnection(): Unit` for the UI.

- [ ] **Step 1: Viết test đỏ cho vòng đời attempt**

Refactor the client to accept deterministic seams with production defaults:

```kotlin
internal fun interface RetrySleeper { suspend fun sleep(delayMillis: Long) }
internal fun interface RetryJitter { fun nextMillis(): Long }
```

Then add tests:

```kotlin
@Test
fun `closed attempt is discarded before retry`() = runTest {
    val transport = RecordingSocketTransport(failAttempts = 1)
    val client = testSocketClient(transport)
    backgroundScope.launch { client.connect("Hiền") }
    advanceUntilIdle()

    assertEquals(2, transport.createdSessionIds.distinct().size)
    assertNull(transport.sentAfterClose)
}

@Test
fun `retry now cancels pending delay and opens immediately`() = runTest {
    val transport = RecordingSocketTransport(failAttempts = Int.MAX_VALUE)
    val client = testSocketClient(transport, sleeper = ControllableRetrySleeper())
    backgroundScope.launch { client.connect("Hiền") }
    runCurrent()
    client.retryNow()
    runCurrent()
    assertEquals(2, transport.createdSessionIds.size)
}
```

Define `RecordingSocketTransport`, `ControllableRetrySleeper` and
`testSocketClient` as private test fixtures in `GameSocketClientTest.kt`; each
session receives a monotonically increasing integer ID and rejects sends after
`close()`.

- [ ] **Step 2: Chạy test và xác nhận RED**

```powershell
./gradlew.bat :shared:testAndroidHostTest --tests "*GameSocketClientTest" --no-daemon
```

Expected: compilation fails because retry seams and `retryNow()` do not exist.

- [ ] **Step 3: Cài đặt attempt lifecycle và retry signal**

Use a conflated channel so repeated taps collapse into one request:

```kotlin
private val retryRequests = Channel<Unit>(Channel.CONFLATED)

fun retryNow() {
    if (machine.state != SocketConnectionState.TERMINAL) retryRequests.trySend(Unit)
}
```

For each loop iteration, enter a new `client.webSocket(serverUrl)` block. Never
copy the previous `DefaultClientWebSocketSession`. Race the scheduled delay with
`retryRequests.receive()` using `select`; manual retry calls
`machine.reduce(ReconnectEvent.ManualRetry)`. Build account hello as
`ClientMessage.ConnectAccount(accessToken, resumeToken)` and save every non-null
token returned by `SessionReady`.

Map `INVALID_ACCESS_TOKEN`, `SESSION_EXPIRED` and the close reason
`SESSION_REPLACED_CLOSE_REASON` to `ReconnectEvent.SessionExpired`; do not retry
them. On `INVALID_RESUME_TOKEN`, delete the saved resume token and immediately
open one account-authenticated attempt without it; this is not terminal. Network,
timeout and other non-terminal protocol errors remain retryable.

- [ ] **Step 4: Nối controller với retry mới**

```kotlin
fun retryConnection() {
    _uiState.update { it.copy(error = null, message = null) }
    socket.retryNow()
}
```

Map `SocketConnectionState.TERMINAL` to `ConnectionStatus.TERMINAL`. Preserve the
current socket collectors; do not start a second message collector on manual
retry.

- [ ] **Step 5: Chạy test và compiler đa nền tảng**

```powershell
./gradlew.bat :shared:testAndroidHostTest --tests "*ReconnectStateMachineTest" --tests "*GameSocketClientTest" :app:compileDevDebugKotlin :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon
```

Expected: tests and Android/JS/Wasm compilation pass.

- [ ] **Step 6: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/GameSocketClient.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/data/network/GameSocketClientTest.kt
git commit -m "fix: recreate websocket on reconnect"
```

---

### Task 4: Snapshot authoritative, reconnect overlay và điều hướng terminal

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt:201-225,901-936`
- Create: `shared/src/commonTest/kotlin/com/hienthai/fastowin/state/AuthoritativeSnapshotTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/GameScreen.kt`
- Create: `app/src/androidTest/java/com/hienthai/fastowin/ReconnectOverlayUiTest.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/ResidualCatalogTexts.kt`

**Interfaces:**
- Consumes: `GameController.retryConnection()` and six connection states.
- Produces: `GameState.applyAuthoritativeSnapshot(game, playerId)`; test tags `reconnect_overlay`, `reconnect_retry`, `reconnect_blocker`.
- Produces: callback from terminal connection state to existing auth logout/session-expired flow.

- [ ] **Step 1: Viết test đỏ cho thay thế snapshot**

```kotlin
class AuthoritativeSnapshotTest {
    @Test
    fun `server snapshot replaces every optimistic match field`() {
        val local = GameState(
            numbers = (1..50).toList(),
            currentRoomId = "room-1",
            currentMatchId = "match-1",
            isMatchStarted = true,
            currentTarget = 4,
            player = PlayerState(
                id = "player-1",
                name = "Hiền",
                score = 3,
                wrongSelections = 2,
                selectedNumbers = listOf(1, 2, 9)
            ),
            latestGameSequence = 18
        )
        val server = GameSnapshot(
            roomId = "room-1",
            matchId = "match-1",
            roomName = "Phòng reconnect",
            hostId = "player-1",
            gameMode = ProtocolGameMode.ORDER,
            players = listOf(
                PlayerSnapshot(
                    id = "player-1",
                    name = "Hiền",
                    score = 2,
                    wrongSelections = 1,
                    currentTarget = 3,
                    selectedNumbers = listOf(1, 2)
                )
            ),
            numbers = (1..50).toList(),
            selectedNumbers = listOf(1, 2),
            currentTarget = 3,
            phase = RoomPhase.PLAYING,
            sequence = 20
        )

        val restored = local.applyAuthoritativeSnapshot(server, "player-1")
        assertEquals(listOf(1, 2), restored.player.selectedNumbers)
        assertEquals(3, restored.currentTarget)
        assertEquals(2, restored.player.score)
        assertEquals(1, restored.player.wrongSelections)
        assertEquals(20, restored.latestGameSequence)
    }
}
```

- [ ] **Step 2: Viết UI test đỏ cho overlay**

```kotlin
@Test
fun reconnectOverlay_keepsGameVisible_blocksBoard_andRetries() {
    var retries = 0
    val state = GameState(
        numbers = (1..50).toList(),
        currentRoomId = "room-1",
        currentMatchId = "match-1",
        isMatchStarted = true,
        connectionStatus = ConnectionStatus.RECONNECTING,
        player = PlayerState(id = "player-1", name = "Hiền"),
        opponent = PlayerState(id = "player-2", name = "Hiếu")
    )
    composeRule.setContent {
        ProvideLocalization(AppLanguage.VIETNAMESE) {
            FastToWinTheme {
                Box {
                    GameScreen(state = state, onNumberClick = {}, onFinish = {})
                    ReconnectOverlay(onRetry = { retries++ })
                }
            }
        }
    }
    composeRule.onNodeWithTag("game_board").assertExists()
    composeRule.onNodeWithTag("reconnect_overlay").assertIsDisplayed()
    composeRule.onNodeWithTag("reconnect_blocker").assertIsDisplayed()
    composeRule.onNodeWithTag("reconnect_retry").performClick()
    composeRule.runOnIdle { assertEquals(1, retries) }
}
```

- [ ] **Step 3: Chạy test và xác nhận RED**

```powershell
./gradlew.bat :shared:testAndroidHostTest --tests "*AuthoritativeSnapshotTest" :app:compileDevDebugAndroidTestKotlin --no-daemon
```

Expected: compilation fails on `applyAuthoritativeSnapshot` and
`ReconnectOverlay`.

- [ ] **Step 4: Áp dụng snapshot bằng một hàm duy nhất**

Make `startGameWithSnapshot` and `applyWaitingSnapshot` delegate to
`applyAuthoritativeSnapshot`. The function must assign, not union, the server's
`numbers`, `selectedNumbers`, player lists, individual targets, scores,
`wrongSelections`, phase, timers and sequence. Clear pending optimistic feedback
and restart the timer from `remainingMillis` only after the copy completes.

- [ ] **Step 5: Thêm overlay không thay route**

Wrap the current screen content in a `Box`. When status is `RECONNECTING`, draw a
full-size semi-transparent blocker above it with localized `ReconnectingMatch`
and a full-width `ArcadePrimaryButton` tagged `reconnect_retry`. Do not mutate
`screenStateKey`, lobby stage, profile section or app route. Board click handling
remains guarded by `ConnectionStatus.CONNECTED`.

Extract the overlay as an `internal @Composable fun ReconnectOverlay(onRetry:
() -> Unit, modifier: Modifier = Modifier)` in `FastToWinApp.kt` so the UI test
uses the production component instead of duplicating its layout.

When status becomes `TERMINAL`, call the existing account-session-expired callback
once and clear saved auth state, so `AuthStage` selects the Login screen. When a
valid session resumes with `SessionReady.currentGame.phase == RoomPhase.FINISHED`,
route through the existing Result screen and show the authoritative winner. A
later user action may return to the lobby. Only `currentGame == null` with no
pending final snapshot returns directly to the room browser.

- [ ] **Step 6: Thêm đủ 12 bản dịch cho chuỗi mới**

Add keys `ReconnectAction`, `ReconnectBlockingHint` and
`MatchExpiredOfficialLoss` to `TextKey`. Add all three entries to each of the 12
catalogs through `ResidualCatalogTexts.kt`, with matching named placeholders.
Do not place a Vietnamese literal in Compose or controller code.

- [ ] **Step 7: Chạy test host và UI tập trung**

```powershell
./gradlew.bat :shared:testAndroidHostTest --tests "*AuthoritativeSnapshotTest" --tests "*GameStateTest" :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.ReconnectOverlayUiTest" --no-daemon
```

Expected: snapshot tests and reconnect overlay test pass.

- [ ] **Step 8: Commit**

```powershell
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/ResidualCatalogTexts.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/GameScreen.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/state/AuthoritativeSnapshotTest.kt app/src/androidTest/java/com/hienthai/fastowin/ReconnectOverlayUiTest.kt
git commit -m "feat: restore matches from server snapshots"
```

---

### Task 5: Bỏ phát lại nhưng giữ dữ liệu sự kiện và chi tiết trận

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ProfileScreen.kt:2653-2825`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt:183-184`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/ProfileCatalogTexts.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/ProfileSectionsUiTest.kt:558-590`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/PostgresMatchResultRepositoryTest.kt:90-120,250-275`

**Interfaces:**
- Preserves: `MatchEventSnapshot`, `MatchDetailSnapshot.events`, `ClientMessage.GetMatchDetail`, `ServerMessage.MatchDetailData`, DB table `match_events` and result sharing.
- Removes: `MatchReplayControls`, replay index/timer state, `NoReplayData`, `ReplayTurn`, `CorrectSelection`, `WrongSelectionNeed`, `StopReplay`, `Replay` visible actions.
- Preserves: rematch protocol and Result screen invitation flow; “Mời đấu lại” is not replay.
- Preserves: `ReplaySameBoard` in offline Practice because it restarts a playable board and does not replay recorded match events.

- [ ] **Step 1: Đổi UI test thành hợp đồng chi tiết trận không phát lại**

```kotlin
@Test
fun matchDetail_smallPhoneShowsSummaryWithoutReplayControlsAndCloses() {
    var closes = 0
    val profile = profileFixture()
    val detail = MatchDetailSnapshot(
        summary = profile.recentMatches.first(),
        durationMillis = 45_000L,
        events = listOf(
            MatchEventSnapshot(1, "Hiền", true, 1, 1, true, 1L),
            MatchEventSnapshot(2, "Hiếu", false, 3, 2, false, 2L)
        )
    )
    setAdaptiveContent(320.dp, 568.dp, fontScale = 1.4f) {
        ProfileSectionScreen(
            state = GameState(profile = profile, matchDetail = detail),
            profile = profile,
            section = ProfileSection.RECENT_MATCHES,
            isExternalProfile = false,
            canEdit = true,
            onBack = {},
            onRefresh = {},
            onOpenMatchDetail = {},
            onCloseMatchDetail = { closes++ },
            onEquipCosmetics = { _, _ -> },
            onClaimMissionReward = {},
            onSave = { _, _ -> },
            onOpenNotifications = {}
        )
    }

    composeRule.onNodeWithTag("match_detail_scoreboard").assertIsDisplayed()
    composeRule.onNodeWithTag("match_detail_metrics").assertIsDisplayed()
    composeRule.onNodeWithText("Lượt 1/2").assertDoesNotExist()
    composeRule.onNodeWithText("PHÁT LẠI").assertDoesNotExist()
    composeRule.onNodeWithText("ĐÓNG").assertIsDisplayed().performClick()
    composeRule.runOnIdle { assertEquals(1, closes) }
}
```

- [ ] **Step 2: Chạy UI test và xác nhận RED**

```powershell
./gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.ProfileSectionsUiTest#matchDetail_smallPhoneShowsSummaryWithoutReplayControlsAndCloses" --no-daemon
```

Expected: the test fails because replay text is still rendered and the summary
containers do not yet have stable tags.

- [ ] **Step 3: Xóa đúng lớp UI phát lại**

Remove `replayIndex`, `isPlaying`, the replay `LaunchedEffect`, event-by-event
panel and `MatchReplayControls` from `MatchDetailDialog`. Keep scoreboard,
duration, reaction metrics and close action. Tag the existing scoreboard and
metrics containers `match_detail_scoreboard` and `match_detail_metrics`.

Remove replay-only text keys and their catalog values only after all call sites
are gone. Do not remove event serialization or repository queries.

- [ ] **Step 4: Chạy UI test và regression lưu sự kiện**

```powershell
./gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.ProfileSectionsUiTest#matchDetail_smallPhoneShowsSummaryWithoutReplayControlsAndCloses" :server:test --tests "*PostgresMatchResultRepositoryTest" --no-daemon
```

Expected: UI no longer exposes replay; repository tests still prove rows are
written to and read from `match_events`.

- [ ] **Step 5: Quét dấu vết phát lại và xác nhận ngoại lệ rematch**

```powershell
rg -n -i "replay|phát lại|phat lai" shared protocol server app webApp --glob '*.kt'
```

Expected: no match-event replay UI/control/key match. `ReplaySameBoard` remains
only in offline Practice, while matches containing `rematch` or Vietnamese
“Mời đấu lại” remain because they belong to approved playable flows.

- [ ] **Step 6: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ProfileScreen.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/ProfileCatalogTexts.kt app/src/androidTest/java/com/hienthai/fastowin/ProfileSectionsUiTest.kt
git commit -m "refactor: remove match replay controls"
```

---

### Task 6: Nền tối, dialog và làm mới phù hợp trên Web

**Files:**
- Modify: `webApp/src/wasmJsMain/resources/styles.css`
- Modify: `webApp/src/wasmJsMain/resources/index.html`
- Modify: `webApp/src/wasmJsMain/kotlin/com/hienthai/fastowin/web/Main.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/ArcadeComponents.kt:241-275`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/FastToWinPullRefresh.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/PlatformInput.kt`
- Modify: `shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/PlatformInput.android.kt`
- Modify: `shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/PlatformInput.ios.kt`
- Modify: `shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/PlatformInput.wasm.kt`
- Modify: `e2e/tests/responsive.spec.mjs`
- Modify: `e2e/tests/adaptive-input.spec.mjs`

**Interfaces:**
- Consumes: existing `FastToWinPullRefresh` call sites without signature changes.
- Produces: `PlatformRefreshInput(touchPullEnabled, pointerRefreshEnabled)` from platform actuals; optional localized pointer refresh action inside `FastToWinPullRefresh`.
- Produces: Web root test contract `body` background RGB `7,24,36`, content max width 430 CSS px equivalent, dialog width <= viewport minus 20px.

- [ ] **Step 1: Viết Playwright test đỏ cho nền và dialog**

```javascript
test('web shell stays dark outside the mobile canvas', async ({ actors }) => {
  const player = await actors('Dark shell');
  const { page } = player;
  await page.setViewportSize({ width: 1440, height: 900 });
  await login(player);
  await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(7, 24, 36)');
  const root = await page.locator('#fastToWinRoot').boundingBox();
  expect(root.width).toBe(1440);
  const home = await tag(page, 'home_screen').boundingBox();
  expect(home.width).toBeLessThanOrEqual(430);
  expect(Math.abs((home.x + home.width / 2) - 720)).toBeLessThanOrEqual(2);
});

test('dialog remains inside a narrow viewport', async ({ actors }) => {
  const player = await actors('Narrow dialog');
  const { page } = player;
  await page.setViewportSize({ width: 320, height: 568 });
  await login(player);
  await click(page, tag(page, 'bottom_tab:account'));
  await click(page, tag(page, 'profile_settings'));
  await click(page, tag(page, 'language_setting'));
  const dialog = await tag(page, 'arcade_dialog').boundingBox();
  expect(dialog.x).toBeGreaterThanOrEqual(10);
  expect(dialog.x + dialog.width).toBeLessThanOrEqual(310);
});
```

- [ ] **Step 2: Viết input test đỏ cho touch và desktop**

```javascript
test('refresh affordance follows the browser input mode', async ({ actors }) => {
  const player = await actors('Refresh input');
  const { page } = player;
  await login(player);
  await click(page, tag(page, 'bottom_tab:rooms'));
  const hasTouch = await page.evaluate(() => navigator.maxTouchPoints > 0);
  if (hasTouch) {
    await expect(tag(page, 'pointer_refresh')).toHaveCount(0);
  } else {
    await expect(tag(page, 'pointer_refresh')).toBeAttached();
    await click(page, tag(page, 'pointer_refresh'));
    await expect(tag(page, 'room_list')).toBeAttached();
  }
});
```

- [ ] **Step 3: Chạy E2E và xác nhận RED**

```powershell
Set-Location e2e
pnpm test -- --grep "web shell|dialog remains|refresh affordance"
Set-Location ..
```

Expected: at least pointer refresh and dialog test fail before the component
contract is added.

- [ ] **Step 4: Khóa nền tối và chiều rộng responsive**

Keep the existing `#071824` background on `:root`, `html`, `body` and
`#fastToWinRoot`; add `background-color: #071824` to all four selectors so a
browser that does not render gradients cannot expose white. Keep the Compose
content centered at `widthIn(max = 430.dp)` and full viewport height. Add
`data-testid="fast-to-win-root"` to the host element only if Playwright needs a
stable root selector; do not change route behavior.

Tag the dialog surface `arcade_dialog`; calculate its width with the existing
10dp horizontal inset and `widthIn(max = 420.dp)`. Preserve IME padding and
height cap.

- [ ] **Step 5: Phân biệt touch refresh và pointer refresh**

Replace the Boolean expect/actual with:

```kotlin
data class PlatformRefreshInput(
    val touchPullEnabled: Boolean,
    val pointerRefreshEnabled: Boolean
)

expect fun platformRefreshInput(): PlatformRefreshInput
```

Android/iOS return `(true, false)`. Web returns `(browserHasTouchInput(),
!browserHasTouchInput())`. `FastToWinPullRefresh` renders `PullToRefreshBox` only
for touch, and a compact localized action tagged `pointer_refresh` above content
only for desktop pointer. Clicking it invokes the same `onRefresh` callback and
respects `isRefreshing`.

- [ ] **Step 6: Chạy Wasm/JS compiler và E2E responsive**

```powershell
./gradlew.bat :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon
Set-Location e2e
pnpm test -- responsive.spec.mjs adaptive-input.spec.mjs
pnpm test:js-fallback
Set-Location ..
```

Expected: compilers, responsive/input tests and JS fallback smoke pass.

- [ ] **Step 7: Commit**

```powershell
git add webApp/src/wasmJsMain/resources/styles.css webApp/src/wasmJsMain/resources/index.html webApp/src/wasmJsMain/kotlin/com/hienthai/fastowin/web/Main.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/ArcadeComponents.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/FastToWinPullRefresh.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/PlatformInput.kt shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/PlatformInput.android.kt shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/PlatformInput.ios.kt shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/PlatformInput.wasm.kt e2e/tests/responsive.spec.mjs e2e/tests/adaptive-input.spec.mjs
git commit -m "fix: polish responsive web surfaces"
```

---

### Task 7: Tăng khoảng cách trên nhãn bậc mùa

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/HomeScreen.kt:180-205`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/ArcadeShellUiTest.kt`

**Interfaces:**
- Consumes: localized season name and tier text already rendered in Home.
- Produces: test tags `home_season_tier` and `home_season_card`; minimum vertical gap 8dp between greeting/header block and `Đấu sĩ • Mùa khởi đầu`.

- [ ] **Step 1: Viết UI test đỏ đo khoảng cách**

```kotlin
@Test
fun homeSeasonTier_hasBreathingRoomAbove() {
    setHomeContent(sampleSignedInState())
    val headerBottom = composeRule.onNodeWithTag("home_header_content").fetchSemanticsNode().boundsInRoot.bottom
    val tierTop = composeRule.onNodeWithTag("home_season_tier").fetchSemanticsNode().boundsInRoot.top
    composeRule.runOnIdle { assertTrue(tierTop - headerBottom >= with(composeRule.density) { 8.dp.toPx() }) }
}
```

- [ ] **Step 2: Chạy test và xác nhận RED**

```powershell
./gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.ArcadeShellUiTest#homeSeasonTier_hasBreathingRoomAbove" --no-daemon
```

Expected: test fails because the current gap is below 8dp or the new tags do not
exist.

- [ ] **Step 3: Thêm khoảng cách mà không đổi nội dung**

Add `Modifier.padding(top = 8.dp)` at the season tier container boundary, not to
the whole Home list. Add the two test tags and preserve current typography,
localized `season.nameKey/nameArgs`, scroll state and compact layout.

- [ ] **Step 4: Chạy test ở phone nhỏ và tablet hiện có**

```powershell
./gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.ArcadeShellUiTest#homeSeasonTier_hasBreathingRoomAbove,com.hienthai.fastowin.ResponsiveUiTest" --no-daemon
```

Expected: spacing test and responsive suite pass without overflow.

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/HomeScreen.kt app/src/androidTest/java/com/hienthai/fastowin/ArcadeShellUiTest.kt
git commit -m "fix: space home season rank label"
```

---

### Task 8: Cổng localization và xác minh cuối Giai đoạn 1

**Files:**
- Modify: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization/LocalizationCatalogTest.kt`
- Modify: `shared/build.gradle.kts`
- Modify: `docs/superpowers/specs/2026-09-08-economy-clan-quota-platform-design.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: all Phase 1 text keys, server error codes and UI sources.
- Produces: `checkLocalizedUiText` rejects Vietnamese UI literals and direct server fallback rendering; catalog test rejects blank/fallback Phase 1 translations.

- [ ] **Step 1: Viết test đỏ chống fallback cho key Giai đoạn 1**

```kotlin
@Test
fun phaseOneKeysAreTranslatedWithoutEnglishFallback() {
    val keys = setOf(
        TextKey.ReconnectingMatch,
        TextKey.ReconnectAction,
        TextKey.ReconnectBlockingHint,
        TextKey.MatchExpiredOfficialLoss
    )
    val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH)
    allLocalizationCatalogs
        .filterKeys { it != AppLanguage.ENGLISH }
        .forEach { (language, catalog) ->
            keys.forEach { key ->
                assertTrue(catalog.texts.getValue(key).isNotBlank(), "$language/$key")
                assertNotEquals(english.texts.getValue(key), catalog.texts.getValue(key), "$language/$key")
            }
        }
}
```

- [ ] **Step 2: Chạy catalog test và xác nhận RED nếu còn fallback**

```powershell
./gradlew.bat :protocol:jvmTest --tests "*LocalizationCatalogTest" --no-daemon
```

Expected: the test names the exact language/key still equal to English; after
Tasks 4 and 6 it passes without catalog fallback.

- [ ] **Step 3: Mở rộng scanner cho chuỗi người dùng mới chạm tới**

Keep the existing lexical scanner and allow only logs, stable IDs/error codes,
product IDs, developer labels, fixtures and `legacyFallback(...)`. Add a negative
fixture proving a Vietnamese `Text("...")` in a reconnect overlay is rejected,
and a positive fixture proving `localized(TextKey.ReconnectingMatch)` is accepted.
Ensure `checkLocalizedUiText` scans changed network/state/platform files as its
current `localizedUiSourceSegments` contract specifies.

- [ ] **Step 4: Ghi trạng thái triển khai và hướng dẫn kiểm thử tay**

In the approved spec, change its status to “Giai đoạn 1 đã triển khai” only after
all commands in Step 5 pass. In README add this exact manual reconnect checklist:

```text
1. Chạy server, Web và hai thiết bị bằng start-dev-all.cmd.
2. Cho hai tài khoản vào một trận và bấm ít nhất ba số.
3. Tắt mạng thiết bị A trong 10 giây, xác nhận màn chơi còn dưới lớp phủ.
4. Bật mạng, xác nhận target, điểm và số sai của hai máy trùng nhau.
5. Lặp lại nhưng ngắt quá 30 giây, xác nhận A thấy kết quả thua và B thấy thắng.
6. Thu hồi session A, xác nhận A về thẳng màn Đăng nhập và không tự reconnect.
```

- [ ] **Step 5: Chạy cổng kiểm tra đầy đủ trên Windows**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
./gradlew.bat :protocol:jvmTest :server:test :shared:testAndroidHostTest :shared:checkLocalizedUiText :app:compileDevDebugAndroidTestKotlin :app:assembleDevDebug :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon
./gradlew.bat :app:connectedDevDebugAndroidTest --no-daemon
Set-Location e2e
pnpm test
pnpm test:js-fallback
Set-Location ..
git diff --check
```

Expected: every Gradle task, Android UI suite, Wasm E2E and JS fallback suite
passes; `git diff --check` prints nothing.

- [ ] **Step 6: Kiểm tra phạm vi và dữ liệu được giữ lại**

```powershell
rg -n -i "replay|phát lại|phat lai" shared protocol server app webApp --glob '*.kt'
rg -n "MatchEventSnapshot|match_events|GetMatchDetail|MatchDetailData|RematchStatus" protocol server shared --glob '*.kt' --glob '*.sql'
git status --short
```

Expected: no replay controls remain; match-event persistence, match detail and
rematch identifiers remain; only intended Phase 1 files plus the user's known
`.idea/**` and `TournamentScreen.kt` edits appear.

- [ ] **Step 7: Chạy macOS CI trước khi đánh dấu hoàn tất**

Push the implementation branch, then require GitHub Actions jobs `Build and
test`, `Web E2E`, `Android UI test`, and `iOS simulator build` to pass. The
manual two-device test result must be recorded separately because CI does not
prove radio/network interruption behavior on physical devices.

- [ ] **Step 8: Commit tài liệu xác minh**

```powershell
git add protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization/LocalizationCatalogTest.kt shared/build.gradle.kts docs/superpowers/specs/2026-09-08-economy-clan-quota-platform-design.md README.md
git commit -m "test: gate platform stability phase one"
```
