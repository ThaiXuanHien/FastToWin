# 2D Arcade Screenshot Regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tạo 30 ảnh chuẩn đã được duyệt và một job CI phát hiện thay đổi pixel ngoài ý muốn trên 10 màn 2D Arcade ở ba kích thước thiết bị.

**Architecture:** Compose Preview Screenshot Testing chạy host-side trong module Android `app`, render các composable production công khai từ module KMP `shared` bằng fixture cố định và wrapper theme/locale duy nhất. Baseline PNG nằm trong source control; CI chỉ chạy validate và upload report/actual/diff, còn thao tác update baseline chỉ được chạy thủ công sau khi review.

**Tech Stack:** Kotlin 2.4.10, AGP 9.1.0, Compose Multiplatform 1.11.1, Compose Preview Screenshot Testing 0.0.1-alpha15, JDK 17, Gradle 9.5.0, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-13-screenshot-regression-design.md`

## Global Constraints

- Chạy screenshot test ở Android application module `app`, biến thể `devDebug`; không áp dụng plugin vào KMP module `shared`.
- Pin `com.android.compose.screenshot` và `screenshot-validation-api` ở đúng `0.0.1-alpha15`; không dùng dynamic version.
- Bộ đầu tiên có đúng 10 màn x 3 kích thước = 30 ảnh chuẩn.
- Kích thước cố định: `320 x 568 dp`, `430 x 932 dp`, `840 x 1180 dp`; font scale `1.0`.
- Locale cố định `AppLanguage.VIETNAMESE`; theme cố định `AppThemeMode.DARK`.
- Fixture không mạng, database, Firebase, quảng cáo, clock thật, UUID ngẫu nhiên hoặc asset ngoài repository.
- CI chỉ chạy `validateDevDebugScreenshotTest`; tuyệt đối không chạy update task hoặc commit baseline.
- Web E2E và Android Compose UI test hiện có vẫn giữ nguyên trách nhiệm và phải tiếp tục pass.
- Không đánh dấu Phase 6 hoàn tất trước khi chủ dự án duyệt đủ 30 ảnh.

---

### Task 1: Đăng ký công việc và cấu hình plugin screenshot

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `gradle.properties`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: AGP `9.1.0`, Kotlin `2.4.10`, JDK 17 và module Android `app` hiện tại.
- Produces: plugin alias `libs.plugins.compose.screenshot`, library alias `libs.screenshot.validation.api`, source set `screenshotTest`, task `updateDevDebugScreenshotTest` và `validateDevDebugScreenshotTest`.

- [ ] **Step 1: Tạo GitHub Issue cho Phase 6**

Run:

```powershell
gh issue create --repo ThaiXuanHien/FastToWin `
  --title "Phase 6: thêm screenshot regression cho giao diện 2D Arcade" `
  --body-file docs/superpowers/specs/2026-09-13-screenshot-regression-design.md `
  --label "ready-for-agent"
```

Expected: GitHub trả về URL issue mới có nhãn `ready-for-agent`. Ghi số issue vào phần mô tả commit/PR khi tích hợp nhánh.

- [ ] **Step 2: Xác nhận task chưa tồn tại trước khi cấu hình**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:validateDevDebugScreenshotTest --no-daemon
```

Expected: FAIL với thông báo task `validateDevDebugScreenshotTest` không tồn tại.

- [ ] **Step 3: Thêm version, plugin và dependency alias**

Trong `gradle/libs.versions.toml`, thêm:

```toml
[versions]
screenshot = "0.0.1-alpha15"

[libraries]
screenshot-validation-api = { group = "com.android.tools.screenshot", name = "screenshot-validation-api", version.ref = "screenshot" }

[plugins]
compose-screenshot = { id = "com.android.compose.screenshot", version.ref = "screenshot" }
```

Giữ các entry hiện hữu và đặt từng entry vào đúng section đang có.

- [ ] **Step 4: Áp dụng plugin ở root và app**

Trong `build.gradle.kts` root, thêm vào block `plugins`:

```kotlin
alias(libs.plugins.compose.screenshot) apply false
```

Trong `app/build.gradle.kts`, thêm alias vào block `plugins`:

```kotlin
alias(libs.plugins.compose.screenshot)
```

Trong block `android` của `app/build.gradle.kts`, thêm:

```kotlin
experimentalProperties["android.experimental.enableScreenshotTest"] = true
```

Trong block `dependencies`, thêm:

```kotlin
screenshotTestImplementation(libs.screenshot.validation.api)
screenshotTestImplementation(libs.androidx.compose.ui.tooling)
```

Trong `gradle.properties`, thêm:

```properties
android.experimental.enableScreenshotTest=true
android.compose.screenshot.maxHeapSize=4g
```

- [ ] **Step 5: Xác nhận task và source set được tạo**

Run:

```powershell
.\gradlew.bat :app:tasks --all --no-daemon | Select-String 'DevDebugScreenshotTest'
.\gradlew.bat :app:compileDevDebugScreenshotTestKotlin --no-daemon
```

Expected: task update/validate xuất hiện và screenshot source set rỗng compile thành công.

- [ ] **Step 6: Commit cấu hình**

```powershell
git add gradle/libs.versions.toml build.gradle.kts gradle.properties app/build.gradle.kts
git commit -m "test: configure compose screenshot testing"
```

---

### Task 2: Tạo device matrix, render wrapper và fixture có tính quyết định

**Files:**
- Create: `app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/ArcadeScreenshotDevices.kt`
- Create: `app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/ArcadeScreenshotFixtures.kt`

**Interfaces:**
- Consumes: `ProvideLocalization(AppLanguage)`, `FastToWinTheme(AppPreferences)`, các model `GameState`/protocol hiện tại và asset local trong `shared`.
- Produces: annotation `@ArcadeScreenshotDevices`, composable `ArcadeScreenshotFrame(content)`, object `ArcadeScreenshotFixtures` với `profile`, `homeState`, `roomBrowserState`, `activeGameState`, `rankedWinState`, `leaderboardState`, `clanState`, `tournamentState`, `notifications`, `gemPackages`.

- [ ] **Step 1: Viết preview contract tham chiếu API chưa tồn tại**

Tạo `ArcadeScreenshotDevices.kt` với một hàm probe gọi
`ArcadeScreenshotFrame { Text("FAST TO WIN") }` và gắn
`@ArcadeScreenshotDevices`. Chưa định nghĩa annotation/wrapper.

Run:

```powershell
.\gradlew.bat :app:compileDevDebugScreenshotTestKotlin --no-daemon
```

Expected: FAIL do chưa có `ArcadeScreenshotDevices` và `ArcadeScreenshotFrame`.

- [ ] **Step 2: Cài device matrix và render wrapper tối thiểu**

Nội dung cốt lõi trong `ArcadeScreenshotDevices.kt`:

```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "small_phone", widthDp = 320, heightDp = 568, showSystemUi = false)
@Preview(name = "large_phone", widthDp = 430, heightDp = 932, showSystemUi = false)
@Preview(name = "tablet", widthDp = 840, heightDp = 1180, showSystemUi = false)
annotation class ArcadeScreenshotDevices

@Composable
fun ArcadeScreenshotFrame(content: @Composable () -> Unit) {
    ProvideLocalization(AppLanguage.VIETNAMESE) {
        FastToWinTheme(
            preferences = AppPreferences(themeMode = AppThemeMode.DARK)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
            }
        }
    }
}
```

Giữ probe chưa gắn `@PreviewTest`; nó chỉ kiểm tra harness compile.

- [ ] **Step 3: Tạo fixture API với dữ liệu thật của domain**

Trong `ArcadeScreenshotFixtures.kt`, tạo đúng interface:

```kotlin
object ArcadeScreenshotFixtures {
    const val NOW_MILLIS: Long = 1_788_793_200_000L

    fun profile(): PlayerProfileSnapshot
    fun homeState(): GameState
    fun roomBrowserState(): GameState
    fun activeGameState(): GameState
    fun rankedWinState(): GameState
    fun leaderboardState(): GameState
    fun clanState(): GameState
    fun tournamentState(): GameState
    fun notifications(): List<AppNotification>
    fun gemPackages(): List<GemPackageSnapshot>
}
```

Dựng constructor từ các fixture đã chứng minh hành vi tại
`CriticalFlowsUiTest.kt`, `ProfileSectionsUiTest.kt`, `SeasonProgressUiTest.kt`,
`ClanScreenTest.kt`, `TournamentScreenTest.kt` và `NavigationHeaderUiTest.kt`.
Không import private test helper; chuyển dữ liệu cần thiết vào source set
screenshot và đặt giá trị cụ thể:

- người hiện tại `player-1`, tên `HienTX`, cấp `42`, XP hiện tại `4_860`, XP mốc
  tiếp theo lấy đúng hàm progression production;
- ví `12_500` Vàng, `320` Gem; lượt online `8/10`;
- phòng công khai có ba item: đang chờ, có mật khẩu và gần đầy;
- trận 1 vs 1 đấu hạng, số tiếp theo `37`, điểm `28-24`, bàn đủ 50 số;
- kết quả thắng, phần thưởng server và thay đổi Elo không rỗng;
- leaderboard có top 3 cùng dòng người hiện tại;
- bang cấp/XP, quyên góp và danh sách thành viên có dữ liệu;
- giải 8 người ở `TournamentPhase.RUNNING` với bracket có dữ liệu;
- thông báo có cả đã đọc/chưa đọc và `createdAtMillis` cố định theo
  `NOW_MILLIS`;
- catalog Gem có ba gói, ID/giá cố định và không truy cập Billing SDK.

- [ ] **Step 4: Thêm invariant để fixture không trôi về màn rỗng**

Trong cùng object thêm:

```kotlin
fun requireValid() {
    check(homeState().profile != null)
    check(roomBrowserState().availableRooms.size >= 3)
    check(activeGameState().numbers.size == 50)
    check(rankedWinState().isGameOver)
    check(leaderboardState().leaderboard != null)
    check(clanState().currentClan != null)
    check(tournamentState().tournamentHub.activeTournament?.maxPlayers == 8)
    check(notifications().any { it.isRead } && notifications().any { !it.isRead })
    check(gemPackages().size == 3)
}
```

Gọi `ArcadeScreenshotFixtures.requireValid()` từ probe trước khi render.

- [ ] **Step 5: Compile harness và fixture**

Run:

```powershell
.\gradlew.bat :app:compileDevDebugScreenshotTestKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL, không warning về clock/random/network trong fixture.

- [ ] **Step 6: Commit harness và fixture**

```powershell
git add app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot
git commit -m "test: add deterministic arcade screenshot fixtures"
```

---

### Task 3: Thêm screenshot preview cho năm màn cốt lõi

**Files:**
- Create: `app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/ArcadeCoreScreenshotTest.kt`
- Modify: `app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/ArcadeScreenshotDevices.kt`

**Interfaces:**
- Consumes: `ArcadeScreenshotFixtures`, `ArcadeScreenshotFrame`, các composable public `LobbyScreen`, `GameScreen`, `ResultScreen`, `ProfileScreen`.
- Produces: 15 preview có tên hàm ổn định: `home`, `roomBrowser`, `activeGame`, `rankedWinResult`, `profileOverview`, mỗi hàm sinh ba kích thước.

- [ ] **Step 1: Viết năm preview gọi screen production**

Mẫu bắt buộc cho mỗi hàm:

```kotlin
@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun home() = ArcadeScreenshotFrame {
    LobbyScreen(
        state = ArcadeScreenshotFixtures.homeState(),
        onModeSelected = {},
        onStartMatchmaking = { _, _ -> },
        onCancelMatchmaking = {},
        onOpenRoomBrowser = {},
        onCreateRoom = { _, _, _, _ -> },
        onJoinRoom = { _, _ -> },
        onLeaveRoom = {},
        onSetReady = {},
        onKickOpponent = {},
        onRefreshRooms = {},
        onOpenProfile = {},
        onOpenLeaderboard = {},
        onOpenFriends = {},
        onOpenFriendProfile = {},
        onBackToMode = {},
        onLogout = {},
        isGuest = false,
        onUpgradeGuest = {},
        onOpenNotifications = {},
        onOpenClan = {},
        onOpenPractice = {},
        onOpenTournament = {},
        onOpenShop = {},
        onShareRoom = { _, _ -> Result.success(Unit) },
        onResolveRoomLink = {},
        onClaimDailyCheckIn = {}
    )
}
```

Tạo `roomBrowser()` bằng cùng callback set và
`ArcadeScreenshotFixtures.roomBrowserState()`. Tạo `activeGame()` gọi
`GameScreen` với `onNumberClick = {}`, `onFinish = {}` và preferences tắt sound/
vibration để render không có side effect. Tạo `rankedWinResult()` gọi
`ResultScreen` với toàn bộ callback no-op và cùng preferences. Tạo
`profileOverview()` gọi `ProfileScreen` với `serverUrl = ""`, account/session
state rỗng ổn định, `imagePicker` render nguyên content với callback no-op, và
`showBackButton = true`.

- [ ] **Step 2: Xóa probe harness**

Xóa hàm probe khỏi `ArcadeScreenshotDevices.kt`; file chỉ giữ annotation và
wrapper. Như vậy chỉ năm hàm gắn `@PreviewTest`, sinh đúng 15 ảnh ở checkpoint
này.

- [ ] **Step 3: Compile preview**

Run:

```powershell
.\gradlew.bat :app:compileDevDebugScreenshotTestKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL; không cần emulator.

- [ ] **Step 4: Generate tạm và kiểm đếm 15 ảnh**

Run:

```powershell
.\gradlew.bat :app:updateDevDebugScreenshotTest --no-daemon
(Get-ChildItem app\src\screenshotTestDevDebug\reference -Recurse -Filter *.png).Count
```

Expected: `15`. Nếu một screen phụ thuộc API Android không được Layoutlib hỗ
trợ, cô lập dependency đó bằng callback/platform seam có default production;
không thay screen bằng bản mock tự vẽ.

- [ ] **Step 5: Validate năm màn**

Run:

```powershell
.\gradlew.bat :app:validateDevDebugScreenshotTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit preview cốt lõi, chưa commit baseline tạm**

```powershell
git add app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot
git commit -m "test: cover core arcade screens with screenshot previews"
```

Giữ PNG chưa stage để review chung sau khi đủ 30 ảnh.

---

### Task 4: Thêm screenshot preview cho năm màn tính năng

**Files:**
- Create: `app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/ArcadeFeatureScreenshotTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ShopScreen.kt`

**Interfaces:**
- Consumes: fixture/harness Task 2 và các composable public `ShopScreen`, `LeaderboardScreen`, `ClanScreen`, `TournamentScreen`, `NotificationsScreen`.
- Produces: `ShopTab`, tham số `initialTab: ShopTab = ShopTab.GEMS` và 15 preview còn lại có tên ổn định `shopGoldCatalog`, `playerLeaderboard`, `clanDetail`, `runningTournament`, `notificationInbox`.

- [ ] **Step 1: Viết preview Cửa hàng dùng API tab chưa tồn tại**

Tạo `shopGoldCatalog()` với `initialTab = ShopTab.GOLD`, rồi compile:

```powershell
.\gradlew.bat :app:compileDevDebugScreenshotTestKotlin --no-daemon
```

Expected: FAIL do chưa có `ShopTab` và tham số `initialTab`.

- [ ] **Step 2: Thêm state khởi tạo tab có default tương thích**

Trong `ShopScreen.kt`, thêm:

```kotlin
enum class ShopTab { GEMS, GOLD }
```

Thêm vào cuối nhóm tham số không-callback của `ShopScreen`:

```kotlin
initialTab: ShopTab = ShopTab.GEMS,
```

Thay state chuỗi bằng enum:

```kotlin
var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
val tabs = listOf(
    ShopTab.GEMS to localized(TextKey.GemTab),
    ShopTab.GOLD to localized(TextKey.GoldTab)
)
```

Đổi các phép so sánh/assignment `"GEMS"` và `"GOLD"` trong màn sang hai enum
tương ứng. Default vẫn là Gem nên không đổi hành vi production hiện tại.

- [ ] **Step 3: Hoàn thiện preview Cửa hàng và Xếp hạng**

```kotlin
@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun shopGoldCatalog() = ArcadeScreenshotFrame {
    ShopScreen(
        progression = ArcadeScreenshotFixtures.profile().progression,
        onClose = {},
        gemPackages = ArcadeScreenshotFixtures.gemPackages(),
        billingState = StoreBillingState(StorePlatform.GOOGLE_PLAY),
        isAccount = true,
        initialTab = ShopTab.GOLD
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun playerLeaderboard() = ArcadeScreenshotFrame {
    LeaderboardScreen(
        state = ArcadeScreenshotFixtures.leaderboardState(),
        onBack = {},
        onRefresh = {},
        onOpenFriendProfile = {}
    )
}
```

- [ ] **Step 4: Viết preview Bang, Đấu giải và Thông báo**

Tạo ba hàm còn lại với callback no-op. `ClanScreen` nhận `currentUserId`,
`myClanId` và `currentClan` từ `clanState`; truyền số dư profile và
`showBackButton = true`. `TournamentScreen` nhận `tournamentState`.
`NotificationsScreen` nhận `notifications()` và không kích hoạt thao tác xóa/
đọc.

- [ ] **Step 5: Compile và generate đủ 30 ảnh**

Run:

```powershell
.\gradlew.bat :app:compileDevDebugScreenshotTestKotlin --no-daemon
.\gradlew.bat :app:updateDevDebugScreenshotTest --no-daemon
(Get-ChildItem app\src\screenshotTestDevDebug\reference -Recurse -Filter *.png).Count
```

Expected: BUILD SUCCESSFUL và count bằng `30`, không nhiều hơn hoặc ít hơn.

- [ ] **Step 6: Validate toàn bộ preview**

Run:

```powershell
.\gradlew.bat :app:validateDevDebugScreenshotTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit code preview tính năng, chưa commit PNG**

```powershell
git add app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ShopScreen.kt
git commit -m "test: cover feature arcade screens with screenshot previews"
```

---

### Task 5: Review và chốt 30 ảnh chuẩn

**Files:**
- Create: `app/src/screenshotTestDevDebug/reference/*.png` (đúng 30 file)

**Interfaces:**
- Consumes: 30 preview từ Task 3 và Task 4.
- Produces: bộ golden PNG được chủ dự án duyệt và có thể tái lập bằng Gradle.

- [ ] **Step 1: Kiểm tra danh sách và kích thước ảnh**

Run:

```powershell
$refs = Get-ChildItem app\src\screenshotTestDevDebug\reference -Recurse -Filter *.png
$refs.Count
$refs | Sort-Object Name | Select-Object Name,Length
```

Expected: 30 PNG, mỗi tên chứa đúng một trong 10 tên hàm và biến thể thiết bị do
plugin sinh.

- [ ] **Step 2: Trình bày đủ ảnh để chủ dự án review**

Mở thư mục reference trong panel file của Codex và trình bày ảnh theo năm nhóm
mỗi lượt để ảnh đủ lớn: Trang chủ/Phòng, Ván/Kết quả, Hồ sơ/Cửa hàng, Xếp hạng/
Bang, Đấu giải/Thông báo. Mỗi nhóm hiển thị theo thứ tự small, large, tablet.

Expected: chủ dự án xác nhận ảnh đúng UI 2D Arcade hoặc chỉ rõ ảnh cần sửa. Mọi
sửa UI được thực hiện trong code production/fixture rồi chạy update lại đúng
preview liên quan; không chỉnh tay PNG.

- [ ] **Step 3: Dừng tại checkpoint cho đến khi có phê duyệt ảnh**

Không stage hoặc commit `reference/*.png` trước câu trả lời phê duyệt rõ ràng từ
chủ dự án.

- [ ] **Step 4: Validate lại sau phê duyệt**

Run:

```powershell
.\gradlew.bat :app:validateDevDebugScreenshotTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit baseline được duyệt**

```powershell
git add app/src/screenshotTestDevDebug/reference
git commit -m "test: approve arcade screenshot baselines"
```

---

### Task 6: Tích hợp CI và chứng minh diff artifact

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: Gradle validate task và 30 baseline đã duyệt.
- Produces: job `android-screenshot-test` cùng artifact `arcade-screenshot-report-${{ github.run_number }}-${{ github.sha }}` giữ 7 ngày.

- [ ] **Step 1: Thêm job validate host-side**

Thêm job độc lập sau `build-and-test`:

```yaml
  android-screenshot-test:
    name: Android screenshot regression
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - name: Checkout source
        uses: actions/checkout@v7
      - name: Set up Java 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v6
        with:
          cache-provider: basic
      - name: Make Gradle wrapper executable
        run: chmod +x gradlew
      - name: Install Android SDK 37
        run: |
          "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37.0" "build-tools;36.1.0"
      - name: Validate 2D Arcade screenshots
        run: ./gradlew :app:validateDevDebugScreenshotTest --no-daemon --no-configuration-cache
      - name: Upload screenshot report and diffs
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: arcade-screenshot-report-${{ github.run_number }}-${{ github.sha }}
          path: |
            app/build/reports/screenshotTest/
            app/build/outputs/screenshotTest-results/
          retention-days: 7
          if-no-files-found: warn
```

- [ ] **Step 2: Kiểm tra YAML và validate bình thường**

Run:

```powershell
.\gradlew.bat :app:validateDevDebugScreenshotTest --no-daemon
git diff --check
```

Expected: validate pass và không có whitespace error.

- [ ] **Step 3: Chứng minh một thay đổi pixel làm test fail**

Dùng `apply_patch` đổi tạm màu `Surface` trong `ArcadeScreenshotFrame` từ
`MaterialTheme.colorScheme.background` thành `ArcadePalette.Coral800`, rồi chạy:

```powershell
.\gradlew.bat :app:validateDevDebugScreenshotTest --no-daemon
```

Expected: FAIL và
`app/build/reports/screenshotTest/preview/debug/dev/index.html` tồn tại. Kiểm tra
report/output có reference, actual và diff cho các preview.

- [ ] **Step 4: Hoàn tác đúng thay đổi thử nghiệm và validate lại**

Dùng `apply_patch` đổi `ArcadePalette.Coral800` trở lại
`MaterialTheme.colorScheme.background`, rồi chạy lại validate.

Expected: BUILD SUCCESSFUL. Xác nhận `git diff` không còn thay đổi thử nghiệm.

- [ ] **Step 5: Commit CI**

```powershell
git add .github/workflows/ci.yml
git commit -m "ci: validate arcade screenshot baselines"
```

---

### Task 7: Tài liệu, roadmap và hồi quy cuối

**Files:**
- Modify: `docs/testing.md`
- Modify: `docs/roadmap.md`

**Interfaces:**
- Consumes: quy trình update/validate và CI job đã hoạt động.
- Produces: hướng dẫn contributor hoàn chỉnh và trạng thái Phase 6 chính xác.

- [ ] **Step 1: Viết hướng dẫn screenshot vào tài liệu test**

Thêm mục `Screenshot regression 2D Arcade` vào `docs/testing.md`, ghi chính xác:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:validateDevDebugScreenshotTest --no-daemon
.\gradlew.bat :app:updateDevDebugScreenshotTest --no-daemon
```

Giải thích validate là lệnh mặc định; update chỉ dùng khi UI thay đổi có chủ ý,
reference nằm ở `app/src/screenshotTestDevDebug/reference`, report nằm ở
`app/build/reports/screenshotTest/preview/debug/dev/index.html`, và plugin đang
alpha. Thêm quy tắc không chỉnh PNG thủ công và phải review đủ ảnh trước commit.

- [ ] **Step 2: Cập nhật roadmap đúng bằng chứng**

Chỉ đổi năm checkbox Phase 6 trong `docs/roadmap.md` thành `[x]` sau khi đủ các
bằng chứng: 30 ảnh được duyệt, local validate pass, failure diff đã được kiểm
chứng và CI của đúng commit xanh.

- [ ] **Step 3: Chạy regression hợp nhất**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:TEST_DATABASE_URL='jdbc:postgresql://localhost:5432/fasttowin'
$env:TEST_DATABASE_USER='fasttowin'
$env:TEST_DATABASE_PASSWORD='fasttowin'
.\gradlew.bat `
  :protocol:jvmTest `
  :server:test `
  :shared:checkLocalizedUiText `
  :shared:testAndroidHostTest `
  :app:compileDevDebugAndroidTestKotlin `
  :app:assembleDevDebug `
  :app:validateDevDebugScreenshotTest `
  :webApp:compileKotlinWasmJs `
  --no-daemon
```

Expected: BUILD SUCCESSFUL. Nếu PostgreSQL test bị skip do database không có,
khởi động compose database và chạy lại; không báo hoàn tất dựa trên bộ test bị
skip ngoài chủ ý.

- [ ] **Step 4: Kiểm tra trạng thái và số baseline**

Run:

```powershell
(Get-ChildItem app\src\screenshotTestDevDebug\reference -Recurse -Filter *.png).Count
git diff --check
git status --short
```

Expected: `30`, không whitespace error, chỉ còn hai file docs cần commit.

- [ ] **Step 5: Commit tài liệu**

```powershell
git add docs/testing.md docs/roadmap.md
git commit -m "docs: document arcade screenshot regression"
```

- [ ] **Step 6: Push nhánh và theo dõi CI**

Run:

```powershell
git push -u origin codex/screenshot-regression
gh run list --repo ThaiXuanHien/FastToWin --branch codex/screenshot-regression --limit 1
```

Expected: CI của đúng HEAD có build/test, Android UI, Web E2E, iOS và Android
screenshot regression đều xanh. Chỉ sau đó mới đề xuất merge vào `master`.
