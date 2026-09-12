# Online Play Quota and Rewarded Ads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Giới hạn mỗi tài khoản ở 10 trận online mỗi ngày, trừ đúng một lượt khi trận bắt đầu và cộng 2 lượt cho mỗi quảng cáo được server xác minh hợp lệ.

**Architecture:** Server là nguồn sự thật cho ngày hạn mức, số lượt đã dùng và lượt thưởng. `PlayQuotaRepository` che giấu transaction/idempotency; `RewardedAdVerifier` che giấu nhà cung cấp quảng cáo; `GameEngine` chỉ hỏi quyền và yêu cầu trừ/cộng. Client nhận snapshot chính thức qua protocol, hiển thị hạn mức ở bước chọn loại trận và dùng gateway riêng theo Android/iOS/Web để lấy biên nhận quảng cáo.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization, Ktor WebSocket, PostgreSQL 17 + Flyway, Compose Multiplatform, Kotlin coroutines/Flow, Android Compose UI Test.

**Spec:** `docs/superpowers/specs/2026-09-08-economy-clan-quota-platform-design.md`

## Global Constraints

- Mỗi tài khoản có 10 lượt cơ bản mỗi ngày; đấu thường và đấu hạng dùng chung hạn mức.
- Mỗi quảng cáo hợp lệ cộng đúng 2 lượt và không giới hạn số quảng cáo trong ngày.
- Ngày hạn mức reset lúc 00:00 theo `Asia/Bangkok`.
- Luyện tập offline, đấu giải và hoạt động bang không tiêu thụ lượt.
- Chỉ trừ lượt khi phòng chuyển sang `PLAYING`; phòng hủy trước đó không mất lượt.
- Mã trận và mã giao dịch quảng cáo phải chống xử lý lặp.
- Tài khoản khách chỉ được luyện tập; mọi phòng và ghép trận online đều yêu cầu đăng nhập.
- Dev dùng biên nhận mô phỏng có nhãn rõ; production chưa cấu hình nhà cung cấp phải từ chối phát lượt.
- Web production không phát lượt quảng cáo và hướng dẫn người chơi dùng Android/iOS.
- Mọi nội dung người dùng nhìn thấy phải có `TextKey` và bản dịch rõ ràng trong đủ 12 ngôn ngữ.
- Không đưa số lượt còn lại vào header tài sản chung.

---

### Task 1: Khóa hợp đồng protocol và nội dung đa ngôn ngữ

**Files:**
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/GameplayCatalogTexts.kt`
- Modify: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/protocol/GameProtocolCompatibilityTest.kt`
- Create: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization/PlayQuotaLocalizationTest.kt`

**Interfaces:**
- Produces: `PlayQuotaSnapshot`, `RewardedAdProvider`, `RewardedAdAvailability`, `RewardedAdBonusStatus`.
- Produces: `ClientMessage.GetPlayQuota`, `ClientMessage.ClaimRewardedAdBonus`.
- Produces: `ServerMessage.PlayQuotaData`, `ServerMessage.RewardedAdBonusResult`.

- [ ] **Step 1: Viết test protocol đang đỏ**

Thêm round-trip serialization cho hợp đồng mong muốn:

```kotlin
@Test
fun `play quota messages preserve authoritative values`() {
    val message: ServerMessage = ServerMessage.PlayQuotaData(
        PlayQuotaSnapshot(
            quotaDate = "2026-09-12",
            baseMatches = 10,
            matchesConsumed = 8,
            bonusMatchesGranted = 2,
            remainingMatches = 4,
            nextResetAtEpochMillis = 1_789_148_400_000,
            rewardedAdAvailability = RewardedAdAvailability.DEV_SIMULATED
        )
    )
    assertEquals(message, json.decodeFromString<ServerMessage>(json.encodeToString(message)))
}

@Test
fun `rewarded ad claim preserves idempotency identifiers`() {
    val message: ClientMessage = ClientMessage.ClaimRewardedAdBonus(
        requestId = "request-1",
        provider = RewardedAdProvider.DEV_SIMULATED,
        providerTransactionId = "dev-transaction-1",
        proof = "FASTTOWIN_DEV_REWARDED_V1:user-1:dev-transaction-1"
    )
    assertEquals(message, json.decodeFromString<ClientMessage>(json.encodeToString(message)))
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ đúng lý do**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :protocol:jvmTest --tests "*GameProtocolCompatibilityTest*play quota*" --no-daemon
```

Expected: FAIL vì các kiểu `PlayQuotaSnapshot` và message mới chưa tồn tại.

- [ ] **Step 3: Thêm hợp đồng protocol tối thiểu**

Tăng `PROTOCOL_VERSION` từ `42` lên `43`, giữ `MIN_COMPATIBLE_PROTOCOL_VERSION = 38`, rồi thêm:

```kotlin
@Serializable
enum class RewardedAdProvider { DEV_SIMULATED, ADMOB_ANDROID, ADMOB_IOS }

@Serializable
enum class RewardedAdAvailability { DEV_SIMULATED, MOBILE_PRODUCTION, UNAVAILABLE }

@Serializable
enum class RewardedAdBonusStatus { GRANTED, ALREADY_GRANTED, INVALID, UNAVAILABLE, FAILED }

@Serializable
data class PlayQuotaSnapshot(
    val quotaDate: String,
    val baseMatches: Int = 10,
    val matchesConsumed: Int = 0,
    val bonusMatchesGranted: Int = 0,
    val remainingMatches: Int = 10,
    val nextResetAtEpochMillis: Long,
    val rewardedAdAvailability: RewardedAdAvailability = RewardedAdAvailability.UNAVAILABLE
)
```

Thêm message:

```kotlin
@Serializable
@SerialName("get_play_quota")
data object GetPlayQuota : ClientMessage()

@Serializable
@SerialName("claim_rewarded_ad_bonus")
data class ClaimRewardedAdBonus(
    val requestId: String,
    val provider: RewardedAdProvider,
    val providerTransactionId: String,
    val proof: String
) : ClientMessage()

@Serializable
@SerialName("play_quota_data")
data class PlayQuotaData(val quota: PlayQuotaSnapshot) : ServerMessage()

@Serializable
@SerialName("rewarded_ad_bonus_result")
data class RewardedAdBonusResult(
    val requestId: String,
    val status: RewardedAdBonusStatus,
    val quota: PlayQuotaSnapshot
) : ServerMessage()
```

- [ ] **Step 4: Viết test bản dịch đủ 12 ngôn ngữ**

Test phải kiểm tra trực tiếp các key mới, không chấp nhận fallback:

```kotlin
private val quotaKeys = setOf(
    TextKey.OnlineMatchesRemaining,
    TextKey.OnlineQuotaExhaustedTitle,
    TextKey.OnlineQuotaExhaustedMessage,
    TextKey.WatchAdForTwoMatches,
    TextKey.RewardedAdGranted,
    TextKey.RewardedAdCancelled,
    TextKey.RewardedAdUnavailable,
    TextKey.RewardedAdUseMobile,
    TextKey.OnlineAccountRequired
)

@Test
fun `play quota copy is explicit in every supported language`() {
    SupportedLanguage.entries.filterNot { it == SupportedLanguage.SYSTEM }.forEach { language ->
        quotaKeys.forEach { key ->
            assertTrue(LocalizationCatalog.hasExplicitText(language, key), "$language misses $key")
        }
    }
}
```

- [ ] **Step 5: Chạy test bản dịch để xác nhận đỏ rồi thêm nội dung**

Run:

```powershell
.\gradlew.bat :protocol:jvmTest --tests "*PlayQuotaLocalizationTest" --no-daemon
```

Expected trước triển khai: FAIL vì thiếu key. Thêm bản dịch rõ ràng cho cả 12 ngôn ngữ trong `GameplayCatalogTexts.kt`, sau đó chạy lại và kỳ vọng PASS.

- [ ] **Step 6: Chạy toàn bộ protocol test**

```powershell
.\gradlew.bat :protocol:jvmTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Tạo checkpoint**

```powershell
git add protocol/src/commonMain protocol/src/commonTest
git commit -m "feat: define online play quota protocol"
```

Chỉ chạy lệnh commit khi người dùng yêu cầu commit.

---

### Task 2: Tạo luật ngày hạn mức và seam xác minh quảng cáo

**Files:**
- Create: `server/src/main/kotlin/com/hienthai/fastowin/server/PlayQuota.kt`
- Create: `server/src/main/kotlin/com/hienthai/fastowin/server/RewardedAdVerifier.kt`
- Create: `server/src/test/kotlin/com/hienthai/fastowin/server/PlayQuotaRulesTest.kt`
- Create: `server/src/test/kotlin/com/hienthai/fastowin/server/RewardedAdVerifierTest.kt`

**Interfaces:**
- Consumes: protocol types from Task 1.
- Produces: `PlayQuotaRepository`, `PlayQuotaConsumption`, `PlayQuotaGrant`, `InMemoryPlayQuotaRepository`.
- Produces: `RewardedAdVerifier`, `DevRewardedAdVerifier`, `RejectingRewardedAdVerifier`.

- [ ] **Step 1: Viết test đỏ cho múi giờ và phép tính lượt**

```kotlin
@Test
fun `quota resets at Bangkok midnight`() {
    val beforeReset = Instant.parse("2026-09-11T16:59:59Z").toEpochMilli()
    val afterReset = Instant.parse("2026-09-11T17:00:00Z").toEpochMilli()
    assertEquals(LocalDate.parse("2026-09-11"), PlayQuotaRules.quotaDate(beforeReset))
    assertEquals(LocalDate.parse("2026-09-12"), PlayQuotaRules.quotaDate(afterReset))
    assertEquals(afterReset, PlayQuotaRules.nextResetAtEpochMillis(beforeReset))
}

@Test
fun `remaining quota includes all verified bonuses`() {
    assertEquals(4, PlayQuotaRules.remaining(matchesConsumed = 10, bonusMatchesGranted = 4))
    assertEquals(0, PlayQuotaRules.remaining(matchesConsumed = 12, bonusMatchesGranted = 2))
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

```powershell
.\gradlew.bat :server:test --tests "*PlayQuotaRulesTest" --no-daemon
```

Expected: FAIL vì `PlayQuotaRules` chưa tồn tại.

- [ ] **Step 3: Tạo module luật và repository contract**

```kotlin
object PlayQuotaRules {
    const val BASE_MATCHES = 10
    const val REWARDED_AD_MATCHES = 2
    private val zone = ZoneId.of("Asia/Bangkok")

    fun quotaDate(nowMillis: Long): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    fun nextResetAtEpochMillis(nowMillis: Long): Long = quotaDate(nowMillis).plusDays(1)
        .atStartOfDay(zone).toInstant().toEpochMilli()
    fun remaining(matchesConsumed: Int, bonusMatchesGranted: Int): Int =
        (BASE_MATCHES + bonusMatchesGranted - matchesConsumed).coerceAtLeast(0)
}

enum class PlayQuotaConsumptionStatus { CONSUMED, ALREADY_CONSUMED, EXHAUSTED }

data class PlayQuotaConsumption(
    val status: PlayQuotaConsumptionStatus,
    val quotas: Map<String, PlayQuotaSnapshot>,
    val exhaustedUserIds: Set<String> = emptySet()
)

data class PlayQuotaCheck(
    val allowed: Boolean,
    val quotas: Map<String, PlayQuotaSnapshot>,
    val exhaustedUserIds: Set<String> = emptySet()
)

data class PlayQuotaGrant(
    val status: RewardedAdBonusStatus,
    val quota: PlayQuotaSnapshot
)

interface PlayQuotaRepository {
    suspend fun getSnapshot(userId: String, nowMillis: Long): PlayQuotaSnapshot
    suspend fun canPlay(userIds: Set<String>, nowMillis: Long): PlayQuotaCheck
    suspend fun consumeMatch(userIds: Set<String>, matchId: String, nowMillis: Long): PlayQuotaConsumption
    suspend fun grantRewardedAd(
        userId: String,
        provider: RewardedAdProvider,
        providerTransactionId: String,
        nowMillis: Long
    ): PlayQuotaGrant
}
```

`InMemoryPlayQuotaRepository` dùng cùng luật, khóa bằng `Mutex`, lưu theo `(userId, quotaDate)`, lưu `userId + matchId` và `provider + transactionId` để test/dev không database vẫn chống lặp.

- [ ] **Step 4: Viết test đỏ cho verifier dev và production**

```kotlin
@Test
fun `dev verifier only accepts clearly marked receipt for the same account`() = runTest {
    val verifier = DevRewardedAdVerifier()
    val valid = verifier.verify(
        userId = "user-1",
        provider = RewardedAdProvider.DEV_SIMULATED,
        providerTransactionId = "transaction-1",
        proof = "FASTTOWIN_DEV_REWARDED_V1:user-1:transaction-1"
    )
    assertNotNull(valid)
    assertNull(verifier.verify("user-2", RewardedAdProvider.DEV_SIMULATED, "transaction-1", valid.proof))
    assertNull(RejectingRewardedAdVerifier.verify("user-1", RewardedAdProvider.ADMOB_ANDROID, "x", "x"))
}
```

- [ ] **Step 5: Tạo seam verifier tối thiểu**

```kotlin
data class VerifiedRewardedAd(
    val provider: RewardedAdProvider,
    val providerTransactionId: String,
    val proof: String
)

fun interface RewardedAdVerifier {
    suspend fun verify(
        userId: String,
        provider: RewardedAdProvider,
        providerTransactionId: String,
        proof: String
    ): VerifiedRewardedAd?
}
```

`DevRewardedAdVerifier` chỉ nhận đúng chuỗi `FASTTOWIN_DEV_REWARDED_V1:<userId>:<transactionId>` và provider `DEV_SIMULATED`. `RejectingRewardedAdVerifier` luôn trả `null`.

- [ ] **Step 6: Chạy test module server thuần**

```powershell
.\gradlew.bat :server:test --tests "*PlayQuotaRulesTest" --tests "*RewardedAdVerifierTest" --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Tạo checkpoint**

```powershell
git add server/src/main/kotlin/com/hienthai/fastowin/server/PlayQuota.kt server/src/main/kotlin/com/hienthai/fastowin/server/RewardedAdVerifier.kt server/src/test/kotlin/com/hienthai/fastowin/server
git commit -m "feat: add play quota domain module"
```

Chỉ chạy lệnh commit khi người dùng yêu cầu commit.

---

### Task 3: Lưu hạn mức PostgreSQL nguyên tử và chống xử lý lặp

**Files:**
- Create: `server/src/main/resources/db/migration/V48__add_daily_play_quotas.sql`
- Create: `server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayQuotaRepository.kt`
- Create: `server/src/test/kotlin/com/hienthai/fastowin/server/PostgresPlayQuotaRepositoryTest.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/Database.kt`

**Interfaces:**
- Consumes: `PlayQuotaRepository` từ Task 2.
- Produces: `PostgresPlayQuotaRepository(DataSource)` và `DatabaseRuntime.playQuotaRepository`.

- [ ] **Step 1: Viết integration test PostgreSQL đang đỏ**

Test tạo hai tài khoản thật rồi xác nhận:

```kotlin
val first = repository.consumeMatch(setOf(userA, userB), "match-1", NOW)
assertEquals(PlayQuotaConsumptionStatus.CONSUMED, first.status)
assertEquals(9, first.quotas.getValue(userA).remainingMatches)
assertEquals(9, first.quotas.getValue(userB).remainingMatches)

val duplicate = repository.consumeMatch(setOf(userA, userB), "match-1", NOW)
assertEquals(PlayQuotaConsumptionStatus.ALREADY_CONSUMED, duplicate.status)
assertEquals(9, duplicate.quotas.getValue(userA).remainingMatches)

repeat(9) { index -> repository.consumeMatch(setOf(userA), "solo-$index", NOW) }
val rejected = repository.consumeMatch(setOf(userA, userB), "match-exhausted", NOW)
assertEquals(PlayQuotaConsumptionStatus.EXHAUSTED, rejected.status)
assertEquals(setOf(userA), rejected.exhaustedUserIds)
assertEquals(9, rejected.quotas.getValue(userB).remainingMatches)
```

Thêm test quảng cáo: một transaction mới cộng 2; transaction lặp không cộng; ngày sau có lại 10 lượt cơ bản.

- [ ] **Step 2: Chạy integration test để xác nhận đỏ**

```powershell
$env:TEST_DATABASE_URL='jdbc:postgresql://localhost:5432/fasttowin'
$env:TEST_DATABASE_USER='fasttowin'
$env:TEST_DATABASE_PASSWORD='fasttowin'
.\gradlew.bat :server:test --tests "*PostgresPlayQuotaRepositoryTest" --no-daemon
```

Expected: FAIL vì migration/repository chưa tồn tại.

- [ ] **Step 3: Thêm migration V48**

```sql
CREATE TABLE daily_play_quotas (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quota_date DATE NOT NULL,
    matches_consumed INTEGER NOT NULL DEFAULT 0 CHECK (matches_consumed >= 0),
    bonus_matches_granted INTEGER NOT NULL DEFAULT 0 CHECK (bonus_matches_granted >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, quota_date)
);

CREATE TABLE play_quota_match_consumptions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    match_id UUID NOT NULL,
    quota_date DATE NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, match_id)
);

CREATE INDEX play_quota_match_consumptions_date_idx
    ON play_quota_match_consumptions (user_id, quota_date DESC);

CREATE TABLE rewarded_ad_grants (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_transaction_id VARCHAR(256) NOT NULL,
    matches_granted INTEGER NOT NULL CHECK (matches_granted = 2),
    verified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_transaction_id)
);
```

- [ ] **Step 4: Cài đặt transaction repository**

`consumeMatch` phải:

1. Parse toàn bộ `userId`/`matchId` sang UUID trước khi mở transaction.
2. Sort user ID để khóa cùng thứ tự, tránh deadlock.
3. Upsert row ngày hiện tại cho mọi user.
4. Khóa các row bằng `SELECT ... FOR UPDATE`.
5. Nếu toàn bộ `(user, match)` đã có, trả `ALREADY_CONSUMED`.
6. Nếu chỉ một phần đã có, rollback và ném lỗi invariant; không sửa dữ liệu.
7. Nếu bất kỳ user nào hết lượt, trả `EXHAUSTED` và không tăng ai.
8. Insert receipt theo từng user và tăng `matches_consumed` trong cùng transaction.

`grantRewardedAd` insert receipt trước; unique conflict trả `ALREADY_GRANTED`; insert thành công cộng chính xác `PlayQuotaRules.REWARDED_AD_MATCHES` vào row ngày hiện tại trong cùng transaction.

- [ ] **Step 5: Wire repository vào DatabaseRuntime**

Thêm:

```kotlin
val playQuotaRepository: PlayQuotaRepository
```

và khởi tạo `PostgresPlayQuotaRepository(dataSource)` trong `DatabaseRuntime.open`.

- [ ] **Step 6: Chạy test PostgreSQL và Flyway validation**

```powershell
.\gradlew.bat :server:test --tests "*PostgresPlayQuotaRepositoryTest" --tests "*ProductionConfigurationTest" --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Tạo checkpoint**

```powershell
git add server/src/main/resources/db/migration/V48__add_daily_play_quotas.sql server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayQuotaRepository.kt server/src/main/kotlin/com/hienthai/fastowin/server/Database.kt server/src/test/kotlin/com/hienthai/fastowin/server/PostgresPlayQuotaRepositoryTest.kt
git commit -m "feat: persist daily online play quota"
```

Chỉ chạy lệnh commit khi người dùng yêu cầu commit.

---

### Task 4: Bắt buộc đăng nhập và trừ lượt tại đúng ranh giới bắt đầu trận

**Files:**
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt`

**Interfaces:**
- Consumes: `PlayQuotaRepository.canPlay`, `PlayQuotaRepository.consumeMatch`.
- Produces: lỗi `ACCOUNT_REQUIRED` và `PLAY_QUOTA_EXHAUSTED`; mọi lần bắt đầu trận online đều đi qua `consumeOnlineMatchOrReject`.

- [ ] **Step 1: Viết test đỏ cho tài khoản khách**

```kotlin
@Test
fun `guest cannot create join or queue for online play`() = runTest {
    val engine = GameEngine()
    val guest = engine.connectGuest("Guest", null)
    assertEquals("ACCOUNT_REQUIRED", engine.handle(guest.playerId, createRoom()).singleErrorCode())
    assertEquals("ACCOUNT_REQUIRED", engine.handle(guest.playerId, joinRoom()).singleErrorCode())
    assertEquals("ACCOUNT_REQUIRED", engine.handle(guest.playerId, joinMatchmaking()).singleErrorCode())
}
```

- [ ] **Step 2: Chạy test guest để xác nhận đỏ**

```powershell
.\gradlew.bat :server:test --tests "*GameEngineTest.guest cannot create join or queue for online play" --no-daemon
```

Expected: FAIL vì phòng đấu thường hiện vẫn cho guest.

- [ ] **Step 3: Chặn guest trước mọi mutation online**

Thêm helper duy nhất:

```kotlin
private fun requireOnlineAccount(playerId: String): Delivery? =
    if (isAccountSession(playerId)) null
    else error(playerId, "ACCOUNT_REQUIRED", localized(TextKey.OnlineAccountRequired))
```

Áp dụng trước `CreateRoom`, `JoinRoom`, `JoinMatchmaking`, `SetReady`, `RequestRematch` và `RespondRematch`. `ListRooms`, luyện tập và đấu giải không bị chặn.

- [ ] **Step 4: Viết test đỏ cho điểm trừ lượt**

Các test dùng fake `PlayQuotaRepository` ghi lại `matchId`:

```kotlin
@Test
fun `waiting room costs nothing and first playing transition consumes once`() = runTest {
    val quota = RecordingPlayQuotaRepository(remaining = 10)
    val fixture = twoAccountRoom(GameEngine(playQuotaRepository = quota))
    assertTrue(quota.consumedMatchIds.isEmpty())
    fixture.readyHost()
    assertTrue(quota.consumedMatchIds.isEmpty())
    fixture.readyGuest()
    assertEquals(1, quota.consumedMatchIds.size)
    fixture.readyGuest()
    assertEquals(1, quota.consumedMatchIds.size)
}

@Test
fun `casual rematch consumes a new match while tournament consumes none`() = runTest {
    // Kết thúc casual, cả hai accept đấu lại: thêm đúng một matchId mới.
    // Bắt đầu tournament: consumedMatchIds vẫn không đổi.
}
```

- [ ] **Step 5: Chạy test để xác nhận đỏ**

```powershell
.\gradlew.bat :server:test --tests "*GameEngineTest*consumes*" --no-daemon
```

Expected: FAIL vì engine chưa dùng quota repository.

- [ ] **Step 6: Thêm ranh giới kiểm tra và trừ lượt**

Thêm constructor dependency:

```kotlin
private val playQuotaRepository: PlayQuotaRepository = InMemoryPlayQuotaRepository(),
private val rewardedAdAvailability: RewardedAdAvailability = RewardedAdAvailability.UNAVAILABLE,
```

`CreateRoom`, `JoinRoom`, `JoinMatchmaking` gọi `canPlay(setOf(playerId), nowMillis())` trước khi thêm user vào phòng/hàng chờ. Nếu hết lượt, trả đồng thời `PlayQuotaData` và `Error(code = "PLAY_QUOTA_EXHAUSTED")`.

Ngay trước ba lời gọi `room.startMatch(...)` cho phòng thường/xếp hạng:

```kotlin
val consumption = playQuotaRepository.consumeMatch(
    userIds = room.activePlayerIds(),
    matchId = requireNotNull(room.matchId),
    nowMillis = nowMillis()
)
if (consumption.status == PlayQuotaConsumptionStatus.EXHAUSTED) {
    room.readyPlayerIds.clear()
    return quotaRejectedResult(room, consumption)
}
room.startMatch(nowMillis(), timeAttackMillis)
```

Các điểm phải dùng helper này:

- Ghép trận tự động.
- Hai người cùng sẵn sàng trong phòng.
- Đấu lại tạo `matchId` mới.

Luồng đấu giải giữ lời gọi `startMatch` riêng và không gọi quota. Việc kiểm tra/trừ diễn ra trong cùng vùng tuần tự hóa hiện tại của `GameEngine`, nên không có command rời phòng chen giữa transaction trừ lượt và chuyển phase.

- [ ] **Step 7: Thêm test exhausted và idempotency ở engine**

Test xác nhận: người hết lượt không vào queue/phòng; cả hai không bị trừ nếu một người hết; từ chối đấu lại vì hết lượt vẫn ở màn kết quả; rời phòng sau `PLAYING` không hoàn lượt; retry cùng `matchId` không trừ lại.

- [ ] **Step 8: Chạy nhóm engine test**

```powershell
.\gradlew.bat :server:test --tests "*GameEngineTest*quota*" --tests "*GameEngineTest*guest cannot*" --tests "*GameEngineTest*consumes*" --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Tạo checkpoint**

```powershell
git add server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt
git commit -m "feat: enforce quota for online match starts"
```

Chỉ chạy lệnh commit khi người dùng yêu cầu commit.

---

### Task 5: Xử lý nhận lượt quảng cáo và cấu hình môi trường server

**Files:**
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/Main.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/ProductionConfigurationTest.kt`

**Interfaces:**
- Consumes: `RewardedAdVerifier.verify`, `PlayQuotaRepository.grantRewardedAd`.
- Produces: `configuredRewardedAdVerifier(environment)` và response chính thức cho client.

- [ ] **Step 1: Viết test đỏ cho claim hợp lệ, trùng và sai**

```kotlin
@Test
fun `verified rewarded ad grants two matches exactly once`() = runTest {
    val quota = InMemoryPlayQuotaRepository()
    val engine = GameEngine(
        playQuotaRepository = quota,
        rewardedAdVerifier = DevRewardedAdVerifier(),
        rewardedAdAvailability = RewardedAdAvailability.DEV_SIMULATED
    )
    val account = connectedAccount(engine)
    val claim = devClaim(account.playerId, "transaction-1")
    val first = engine.handle(account.playerId, claim).singleRewardedResult()
    val duplicate = engine.handle(account.playerId, claim.copy(requestId = "request-2")).singleRewardedResult()
    assertEquals(RewardedAdBonusStatus.GRANTED, first.status)
    assertEquals(12, first.quota.remainingMatches)
    assertEquals(RewardedAdBonusStatus.ALREADY_GRANTED, duplicate.status)
    assertEquals(12, duplicate.quota.remainingMatches)
}
```

Thêm test proof sai, provider sai và guest đều không cộng lượt.

- [ ] **Step 2: Chạy test để xác nhận đỏ**

```powershell
.\gradlew.bat :server:test --tests "*GameEngineTest*rewarded ad*" --no-daemon
```

Expected: FAIL vì engine chưa xử lý message.

- [ ] **Step 3: Cài đặt handler server**

`GetPlayQuota` chỉ hoạt động cho account. `ClaimRewardedAdBonus` kiểm tra request/transaction/proof không rỗng và giới hạn chiều dài, gọi verifier trước, sau đó mới gọi repository. Không tin số lượt từ client.

Pseudo-flow bắt buộc:

```kotlin
val verified = rewardedAdVerifier.verify(playerId, command.provider, command.providerTransactionId, command.proof)
    ?: return rewardedResult(command.requestId, RewardedAdBonusStatus.INVALID, currentQuota(playerId))
val grant = playQuotaRepository.grantRewardedAd(
    playerId,
    verified.provider,
    verified.providerTransactionId,
    nowMillis()
)
return HandleResult(listOf(Delivery(
    ServerMessage.RewardedAdBonusResult(command.requestId, grant.status, withAvailability(grant.quota)),
    setOf(playerId)
)))
```

- [ ] **Step 4: Wire dev/production trong Main**

```kotlin
val playQuotaRepository = database?.playQuotaRepository ?: InMemoryPlayQuotaRepository()
val rewardedAdVerifier = if (environment == "dev") DevRewardedAdVerifier() else RejectingRewardedAdVerifier
val rewardedAdAvailability = if (environment == "dev") {
    RewardedAdAvailability.DEV_SIMULATED
} else {
    RewardedAdAvailability.UNAVAILABLE
}
```

Không thêm secret quảng cáo giả vào protocol hoặc source. Khi AdMob production được chọn sau này, chỉ thay adapter/config ở seam này.

- [ ] **Step 5: Chạy test server và production configuration**

```powershell
.\gradlew.bat :server:test --tests "*GameEngineTest*rewarded ad*" --tests "*ProductionConfigurationTest" --no-daemon
```

Expected: BUILD SUCCESSFUL; production không chấp nhận proof dev.

- [ ] **Step 6: Tạo checkpoint**

```powershell
git add server/src/main/kotlin/com/hienthai/fastowin/server server/src/test/kotlin/com/hienthai/fastowin/server
git commit -m "feat: verify rewarded ad play bonuses"
```

Chỉ chạy lệnh commit khi người dùng yêu cầu commit.

---

### Task 6: Đồng bộ quota vào state/controller dùng chung

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/localization/LocalizedMessageMapper.kt`
- Modify: `shared/src/commonTest/kotlin/com/hienthai/fastowin/state/GameStateTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/hienthai/fastowin/localization/LocalizedMessageMapperTest.kt`

**Interfaces:**
- Consumes: message protocol Task 1.
- Produces: `GameState.playQuota`, `GameState.rewardedAdBonusResult`, `GameController.requestPlayQuota()`, `GameController.claimRewardedAdBonus(receipt)`.

- [ ] **Step 1: Viết test state/controller đang đỏ**

```kotlin
@Test
fun `quota messages replace local snapshot with server truth`() {
    val controller = controllerFixture()
    controller.handleForTest(ServerMessage.PlayQuotaData(quota(remaining = 3)))
    assertEquals(3, controller.state.value.playQuota?.remainingMatches)
    controller.handleForTest(
        ServerMessage.RewardedAdBonusResult("request-1", RewardedAdBonusStatus.GRANTED, quota(remaining = 5))
    )
    assertEquals(5, controller.state.value.playQuota?.remainingMatches)
    assertEquals(RewardedAdBonusStatus.GRANTED, controller.state.value.rewardedAdBonusResult?.status)
}
```

Thêm test `PLAY_QUOTA_EXHAUSTED` bật dialog nhưng không điều hướng sai, và `ACCOUNT_REQUIRED` đưa guest vào luồng đăng nhập/nâng cấp hiện có.

- [ ] **Step 2: Chạy test để xác nhận đỏ**

```powershell
.\gradlew.bat :shared:testAndroidHostTest --tests "*GameStateTest*quota*" --tests "*LocalizedMessageMapperTest*quota*" --no-daemon
```

Expected: FAIL vì state/mapper chưa có quota.

- [ ] **Step 3: Cập nhật state và exhaustive message handler**

Thêm vào `GameState`:

```kotlin
val playQuota: PlayQuotaSnapshot? = null,
val showPlayQuotaExhaustedDialog: Boolean = false,
val rewardedAdBonusResult: ServerMessage.RewardedAdBonusResult? = null,
```

`SessionReady` của account gửi `GetPlayQuota`; `PlayQuotaData` thay toàn bộ snapshot. `RewardedAdBonusResult` thay snapshot rồi hiển thị notice theo status. `PLAY_QUOTA_EXHAUSTED` mở dialog và gửi `GetPlayQuota` để loại bỏ dữ liệu stale.

- [ ] **Step 4: Thêm command controller**

```kotlin
fun requestPlayQuota() = send(ClientMessage.GetPlayQuota)

fun claimRewardedAdBonus(receipt: RewardedAdReceipt) = send(
    ClientMessage.ClaimRewardedAdBonus(
        requestId = receipt.requestId,
        provider = receipt.provider,
        providerTransactionId = receipt.providerTransactionId,
        proof = receipt.proof
    )
)

fun dismissPlayQuotaDialog() {
    _state.update { it.copy(showPlayQuotaExhaustedDialog = false) }
}
```

- [ ] **Step 5: Chạy shared test**

```powershell
.\gradlew.bat :shared:testAndroidHostTest --tests "*GameStateTest*quota*" --tests "*LocalizedMessageMapperTest*quota*" --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Tạo checkpoint**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/state shared/src/commonMain/kotlin/com/hienthai/fastowin/localization shared/src/commonTest
git commit -m "feat: sync play quota client state"
```

Chỉ chạy lệnh commit khi người dùng yêu cầu commit.

---

### Task 7: Tạo gateway quảng cáo đa nền tảng với adapter dev an toàn

**Files:**
- Create: `shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/RewardedAds.kt`
- Create: `shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/RewardedAds.android.kt`
- Create: `shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/RewardedAds.ios.kt`
- Create: `shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/RewardedAds.wasm.kt`
- Create: `shared/src/commonTest/kotlin/com/hienthai/fastowin/platform/RewardedAdsTest.kt`

**Interfaces:**
- Produces: `RewardedAdReceipt`, `RewardedAdGatewayState`, `RewardedAdGateway`, `rememberRewardedAdGateway()`.
- Consumed by: `FastToWinApp` trong Task 8.

- [ ] **Step 1: Viết test đỏ cho việc tạo biên nhận dev**

```kotlin
@Test
fun `dev receipt is bound to account and transaction`() {
    val receipt = devRewardedAdReceipt(
        userId = "user-1",
        transactionId = "transaction-1",
        requestId = "request-1"
    )
    assertEquals(RewardedAdProvider.DEV_SIMULATED, receipt.provider)
    assertEquals("FASTTOWIN_DEV_REWARDED_V1:user-1:transaction-1", receipt.proof)
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

```powershell
.\gradlew.bat :shared:testAndroidHostTest --tests "*RewardedAdsTest" --no-daemon
```

Expected: FAIL vì helper/gateway chưa tồn tại.

- [ ] **Step 3: Tạo common contract**

```kotlin
data class RewardedAdReceipt(
    val requestId: String,
    val provider: RewardedAdProvider,
    val providerTransactionId: String,
    val proof: String
)

data class RewardedAdGatewayState(
    val availability: RewardedAdAvailability = RewardedAdAvailability.UNAVAILABLE,
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val notice: LocalizedText? = null,
    val error: LocalizedText? = null
)

interface RewardedAdGateway {
    val state: StateFlow<RewardedAdGatewayState>
    val receipts: Flow<RewardedAdReceipt>
    fun configure(availability: RewardedAdAvailability)
    fun show(userId: String)
    fun close()
}

@Composable
expect fun rememberRewardedAdGateway(): RewardedAdGateway
```

- [ ] **Step 4: Tạo actual theo nền tảng**

- Android/iOS/Web khi `DEV_SIMULATED`: tạo UUID request/transaction và phát receipt có prefix `FASTTOWIN_DEV_REWARDED_V1`.
- Android/iOS khi `MOBILE_PRODUCTION` nhưng chưa có SDK/provider: `isReady = false`, error `RewardedAdUnavailable`.
- Web khi `MOBILE_PRODUCTION` hoặc `UNAVAILABLE`: không phát receipt, dùng `RewardedAdUseMobile`.
- `show` khi thiếu user ID không phát receipt.
- `close` dọn coroutine/listener của gateway.

- [ ] **Step 5: Chạy common compile cho ba target**

```powershell
.\gradlew.bat :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :webApp:compileKotlinWasmJs --no-daemon
```

Expected trên macOS: cả ba target PASS. Trên Windows: chạy Android + Wasm; iOS compile được CI macOS xác nhận.

- [ ] **Step 6: Tạo checkpoint**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/RewardedAds.kt shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/RewardedAds.android.kt shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/RewardedAds.ios.kt shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/RewardedAds.wasm.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/platform/RewardedAdsTest.kt
git commit -m "feat: add multiplatform rewarded ad gateway"
```

Chỉ chạy lệnh commit khi người dùng yêu cầu commit.

---

### Task 8: Hiển thị lượt còn lại và dialog hết lượt trong UI 2D Arcade

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/HomeScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/LobbyScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/PlayQuotaDialog.kt`
- Create: `app/src/androidTest/java/com/hienthai/fastowin/PlayQuotaUiTest.kt`

**Interfaces:**
- Consumes: `GameState.playQuota`, `RewardedAdGateway`.
- Produces: component quota thích ứng, semantics tags ổn định cho test.

- [ ] **Step 1: Viết Android UI test đang đỏ**

```kotlin
@Test
fun matchTypePicker_showsSharedRemainingQuota() {
    composeRule.setContent {
        MatchTypePickerDialog(
            title = "Chọn loại trận",
            quota = quota(remaining = 3),
            onDismiss = {},
            onSelect = {}
        )
    }
    composeRule.onNodeWithTag("online_quota_remaining").assertTextContains("3/10")
}

@Test
fun exhaustedQuota_showsRewardedAdActionWithoutOverflow() {
    setAdaptiveContent(320.dp, 568.dp, fontScale = 1.6f) {
        PlayQuotaExhaustedDialog(
            quota = quota(remaining = 0, availability = RewardedAdAvailability.DEV_SIMULATED),
            rewardedAdState = RewardedAdGatewayState(isReady = true),
            onWatchAd = {},
            onDismiss = {}
        )
    }
    composeRule.onNodeWithTag("watch_rewarded_ad").assertIsDisplayed().assertIsEnabled()
    assertNodeWithinRoot("play_quota_dialog")
}
```

Thêm test Web/unavailable chỉ hiện hướng dẫn mobile và không hiện nút xem quảng cáo có thể bấm.

- [ ] **Step 2: Chạy test để xác nhận đỏ**

```powershell
.\gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.PlayQuotaUiTest" --no-daemon
```

Expected: FAIL vì component/tag chưa tồn tại.

- [ ] **Step 3: Cài component quota thích ứng**

`MatchTypePickerDialog` nhận `quota: PlayQuotaSnapshot?` và hiện một hàng nhỏ ngay trên hai lựa chọn:

```kotlin
Text(
    text = localized(
        TextKey.OnlineMatchesRemaining,
        "remaining" to (quota?.remainingMatches ?: 0),
        "total" to ((quota?.baseMatches ?: 10) + (quota?.bonusMatchesGranted ?: 0))
    ),
    modifier = Modifier.testTag("online_quota_remaining")
)
```

Không đặt quota trong header. Nút Casual/Ranked vẫn cùng layout hiện tại; khi `remainingMatches == 0`, click mở `PlayQuotaExhaustedDialog` thay vì gửi command.

Dialog dùng `ArcadeDialog`, `widthIn(max = 420.dp)`, `verticalScroll`, padding theo component hiện có. Nút quảng cáo ghi rõ `+2 lượt`; trạng thái loading/claiming disable nút. Nút đóng luôn có thể bấm.

- [ ] **Step 4: Nối gateway trong FastToWinApp**

Theo pattern `StoreBillingGateway` hiện có:

```kotlin
val rewardedAdGateway = rememberRewardedAdGateway()
val rewardedAdState by rewardedAdGateway.state.collectAsState()

LaunchedEffect(state.playQuota?.rewardedAdAvailability) {
    rewardedAdGateway.configure(
        state.playQuota?.rewardedAdAvailability ?: RewardedAdAvailability.UNAVAILABLE
    )
}

LaunchedEffect(rewardedAdGateway) {
    rewardedAdGateway.receipts.collect(controller::claimRewardedAdBonus)
}
```

`onWatchAd` chỉ gọi `rewardedAdGateway.show(requireNotNull(state.profile?.userId))`; không tự tăng local quota.

- [ ] **Step 5: Chạy UI test và compile Wasm**

```powershell
.\gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.PlayQuotaUiTest" :webApp:compileKotlinWasmJs --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Tạo checkpoint**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui app/src/androidTest/java/com/hienthai/fastowin/PlayQuotaUiTest.kt
git commit -m "feat: show online play quota and ad bonus UI"
```

Chỉ chạy lệnh commit khi người dùng yêu cầu commit.

---

### Task 9: Kiểm thử luồng thật, tương thích và responsive đa nền tảng

**Files:**
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/GameWebSocketTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/PlayQuotaUiTest.kt`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: toàn bộ Phase 4.
- Produces: bằng chứng WebSocket, Android responsive, Web compile và iOS compile không hồi quy.

- [ ] **Step 1: Viết WebSocket integration test đang đỏ**

Luồng test đăng ký hai account, kết nối WebSocket, tạo/join phòng, ready cả hai và xác nhận:

```kotlin
assertEquals(10, accountA.getPlayQuota().remainingMatches)
assertEquals(10, accountB.getPlayQuota().remainingMatches)
accountA.createRoom()
accountB.joinRoom(accountA.roomId)
accountA.setReady(true)
accountB.setReady(true)
assertIs<ServerMessage.GameStarted>(accountA.receive())
assertEquals(9, accountA.getPlayQuota().remainingMatches)
assertEquals(9, accountB.getPlayQuota().remainingMatches)
```

Thêm case guest nhận `ACCOUNT_REQUIRED`, hủy phòng chờ giữ nguyên 10, proof dev hợp lệ tăng từ 0 lên 2 và proof lặp vẫn 2.

- [ ] **Step 2: Chạy integration test để xác nhận đỏ rồi hoàn thiện fixture/handler**

```powershell
.\gradlew.bat :server:test --tests "*GameWebSocketTest*play quota*" --no-daemon
```

Expected ban đầu: FAIL ở luồng chưa nối; sau khi bổ sung fixture/handler: PASS.

- [ ] **Step 3: Mở rộng responsive test**

Chạy cùng dialog quota trên:

- 320×568dp, fontScale 1.6.
- 430×932dp.
- 840×1180dp tablet.
- 720×400dp ngang, fontScale 1.3.

Mỗi case xác nhận dialog nằm trong root, nút đóng hiển thị và nội dung có thể scroll tới nút quảng cáo.

- [ ] **Step 4: Cập nhật CI**

Đảm bảo job JVM chạy protocol/server/shared quota tests; job Android compile instrumentation tests; job Web compile Wasm; job macOS compile iOS. Không thêm test quảng cáo production phụ thuộc tài khoản nhà cung cấp.

- [ ] **Step 5: Cập nhật README dev test**

Ghi rõ:

- Dev server dùng quảng cáo mô phỏng có nhãn.
- Production hiện không phát bonus quảng cáo cho tới khi cấu hình provider/server verification.
- Quota reset theo Bangkok và kiểm tra bằng server time.
- Cách chạy `PostgresPlayQuotaRepositoryTest` với ba biến `TEST_DATABASE_*`.

- [ ] **Step 6: Chạy regression suite**

```powershell
.\gradlew.bat :protocol:jvmTest :server:test :shared:testAndroidHostTest :app:compileDevDebugAndroidTestKotlin :webApp:compileKotlinWasmJs --no-daemon
```

Sau đó trên thiết bị Android:

```powershell
.\gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.PlayQuotaUiTest,com.hienthai.fastowin.CriticalFlowsUiTest,com.hienthai.fastowin.SeasonProgressUiTest" --no-daemon
```

Trên macOS/CI:

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 --no-daemon
```

Expected: tất cả BUILD SUCCESSFUL.

- [ ] **Step 7: Kiểm tra diff và tạo checkpoint cuối**

```powershell
git diff --check
git status --short
git add protocol server shared app webApp README.md .github/workflows/ci.yml docs/superpowers/plans/2026-09-12-online-play-quota-rewarded-ads.md
git commit -m "feat: complete daily online play quota"
```

Chỉ chạy lệnh commit khi người dùng yêu cầu commit.

---

## Manual Acceptance Checklist

- [ ] Account còn lượt tạo/join phòng thường và xếp hạng được.
- [ ] Guest bấm chơi online được chuyển sang yêu cầu đăng nhập; luyện tập vẫn hoạt động.
- [ ] Tạo rồi hủy phòng trước khi ready không mất lượt.
- [ ] Hai người ready làm mỗi người giảm đúng một lượt.
- [ ] Chủ động rời sau khi bắt đầu vẫn giữ lượt đã trừ và xử thua theo luật hiện tại.
- [ ] Đấu lại casual giảm thêm một lượt; hết lượt thì không bắt đầu rematch.
- [ ] Đấu giải không giảm lượt.
- [ ] Hết lượt mở dialog đúng, không điều hướng sai và không tràn trên màn nhỏ/Web.
- [ ] Dev xem quảng cáo mô phỏng cộng đúng 2 lượt; gửi lại cùng transaction không cộng lần hai.
- [ ] Web production không giả lập thưởng và hiển thị hướng dẫn dùng mobile.
- [ ] Sau 00:00 Asia/Bangkok, snapshot chuyển ngày và có lại 10 lượt cơ bản.
