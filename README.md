# Fast To Win

Fast To Win là game tìm số 1–50 theo thời gian thực dành cho Android và iOS. Giao diện và phần lớn logic client dùng Compose Multiplatform; backend là Ktor WebSocket và PostgreSQL.

Theo dõi các bước đã làm và còn lại tại [lộ trình phát triển](docs/roadmap.md). Kiểm thử: [tổng quan](docs/testing.md) và [Web E2E](docs/web-e2e.md).

## Tính năng đấu giải riêng

- Người đã đăng nhập có thể tạo giải loại trực tiếp dành cho 4 người và mời bạn bè đang online.
- Khi đủ người, server tự tạo 2 trận bán kết; hai người thắng được đưa vào trận chung kết tự động.
- Trận đấu giải dùng cơ chế phòng 1 đấu 1 hiện có, không ảnh hưởng Elo và không cho rời/rematch giữa nhánh đấu.
- Màn đấu giải hiển thị người tham gia, trạng thái từng trận, nhánh đấu, nhà vô địch và lịch sử giải gần đây.
- Dữ liệu được lưu trong bảng `tournaments` khi backend chạy với PostgreSQL. Flyway tạo bảng này qua migration V21.

## Tài sản và phần thưởng

- Vàng dùng để mở khóa vật phẩm trong cửa hàng; Gem là tiền tệ hiếm.
- Điểm danh nhận Vàng và XP tăng dần trong chu kỳ 7 ngày; ngày 7 nhận thêm 1 Gem.
- Nhiệm vụ ngày/tuần nhận Vàng và XP; nhiệm vụ tuần khó có thể thưởng Gem.
- Kết quả trận: thắng nhận 100 Vàng + 30 XP, hòa nhận 70 Vàng + 20 XP, thua nhận 40 Vàng + 10 XP.
- Nhiệm vụ bang hoàn thành nhận 1.000 Vàng + 100 XP cho từng thành viên chưa nhận.
- Khi mùa xếp hạng kết thúc, app hiển thị tổng kết bậc cao nhất, Elo và toàn bộ phần thưởng đúng một lần; trạng thái đã xem được đồng bộ giữa các thiết bị.
- Trong **Xếp hạng → Lịch sử mùa giải**, người chơi có thể xem lại bậc cao nhất, Elo cao nhất, Elo cuối mùa, thứ hạng và phần thưởng của tối đa 50 mùa đã kết thúc.
- Các giao dịch được ghi vào `wallet_transactions` để kiểm tra lịch sử và ngăn nhận trùng.
- Migration V31 chuẩn bị `store_purchases` cho Google Play/App Store. Thanh toán thật chỉ được bật sau khi cấu hình Product ID và xác thực biên lai phía server.

## Công nghệ và cấu trúc project

| Thư mục | Vai trò |
| --- | --- |
| `app/` | Ứng dụng Android và cấu hình flavor `dev`/`prod` |
| `shared/` | Compose Multiplatform UI, state và WebSocket client dùng chung |
| `protocol/` | Message model và serialization dùng chung giữa client/server |
| `server/` | Ktor HTTP/WebSocket server, repository và Flyway migration |
| `iosApp/` | Ứng dụng iOS, Xcode project và cầu nối Swift–Kotlin |
| `webApp/` | Điểm khởi chạy Compose Multiplatform Web bằng Kotlin/Wasm, kèm Kotlin/JS fallback |

Cấu hình chính hiện tại:

- JDK 17.
- Gradle wrapper 9.5.
- Android `minSdk 29`, `compileSdk 37`, `targetSdk 37`.
- iOS 15 trở lên, simulator trên Mac Intel/Apple Silicon và thiết bị arm64.
- PostgreSQL 17 khi chạy qua Docker.
- Backend HTTP/WebSocket ở cổng `8080`.
- PostgreSQL development ở cổng `5432`.
- Mỗi tài khoản chỉ có một phiên đăng nhập hoạt động; đăng nhập ở thiết bị mới sẽ thu hồi token và đưa thiết bị cũ về màn đăng nhập.

## 1. Chuẩn bị môi trường Windows/Android

Cài các công cụ sau:

1. Git.
2. Android Studio có bundled JDK 17.
3. Trong Android Studio SDK Manager, cài:
   - Android SDK Platform 37.
   - Android SDK Build-Tools.
   - Android SDK Platform-Tools.
   - Android Emulator nếu dùng máy ảo.
4. Docker Desktop nếu cần đăng nhập, lưu hồ sơ, Elo, bạn bè, điểm danh và dữ liệu bền vững.

Docker Desktop cần được khởi động hoàn toàn trước khi chạy server. Sau khi cài mới Docker hoặc Android Studio, nên đóng và mở lại terminal để biến môi trường được cập nhật.

Clone project:

```powershell
git clone https://github.com/ThaiXuanHien/FastToWin.git
cd FastToWin
```

Mở chính thư mục `FastToWin` trong Android Studio và chờ Gradle Sync hoàn tất. Android Studio thường tự tạo `local.properties`. Nếu không, tạo file này ở thư mục gốc:

```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
```

`local.properties` là cấu hình riêng của máy và không được commit.

## 2. Chạy nhanh Android + backend + PostgreSQL

Đây là cách khuyến nghị cho một developer mới:

1. Mở Docker Desktop.
2. Mở một Android emulator hoặc kết nối thiết bị đã bật USB debugging.
3. Mở Terminal trong Android Studio tại thư mục gốc project.
4. Chạy:

```powershell
.\start-dev-server-with-db.cmd
```

Script sẽ:

- Khởi động PostgreSQL bằng Docker Compose.
- Chờ database healthy.
- Thiết lập `JAVA_HOME` từ JDK đi kèm Android Studio nếu chưa có.
- Chạy `adb reverse tcp:8080 tcp:8080` cho mọi thiết bị đang online.
- Đóng gói server và protocol thành bộ JAR đồng nhất trước khi chạy.
- Chạy Flyway migration tự động.
- Khởi động Ktor server ở `0.0.0.0:8080`.

Giữ terminal này mở trong khi phát triển. Khi thấy dòng tương tự sau, server đã chạy:

```text
Starting Fast To Win server: environment=dev, host=0.0.0.0, port=8080, storage=postgresql
```

Kiểm tra health endpoint:

```text
http://127.0.0.1:8080/health
```

Kết quả mong đợi:

```text
OK
```

### Tài khoản kiểm thử đầy đủ

Sau khi PostgreSQL đã chạy, tạo hoặc làm mới tài khoản có đầy đủ dữ liệu bằng:

```powershell
.\seed-dev-full-account.cmd
```

Thông tin đăng nhập mặc định:

```text
Email: fulltest@fasttowin.dev
Mật khẩu: 12345678
```

Tài khoản này có cấp cao, Vàng/Gem, toàn bộ mốc điểm danh và thành tích, các vật phẩm,
nhiệm vụ hoàn thành, Elo, lịch sử mùa, trận đủ chế độ, bạn bè, thông báo và bang hội mẫu.
Script chỉ chạy khi `FASTTOWIN_ENV=dev`, có thể chạy lại an toàn sau khi reset database.

Trên macOS/Linux dùng:

```bash
./seed-dev-full-account.sh
```

Có thể đổi thông tin trước khi chạy qua các biến
`FASTTOWIN_DEV_ACCOUNT_EMAIL`, `FASTTOWIN_DEV_ACCOUNT_PASSWORD` và
`FASTTOWIN_DEV_ACCOUNT_NAME`.

Trong Android Studio:

1. Chọn run configuration/module `app`.
2. Chọn build variant `devDebug`.
3. Chọn emulator/thiết bị.
4. Nhấn **Run**.

App development kết nối tới:

```text
ws://127.0.0.1:8080/game
```

Trên Android, địa chỉ này hoạt động nhờ `adb reverse` do script thiết lập.

## 3. Chạy backend thủ công

### Có PostgreSQL

```powershell
docker compose up -d --wait database

$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:FASTTOWIN_ENV='dev'
$env:DATABASE_URL='jdbc:postgresql://localhost:5432/fasttowin'
$env:DATABASE_USER='fasttowin'
$env:DATABASE_PASSWORD='fasttowin'

.\gradlew.bat :server:installDist
.\run-packaged-server.cmd
```

Flyway tự chạy các migration còn thiếu khi backend khởi động.

### Không có PostgreSQL

```powershell
.\start-dev-server.cmd
```

Hoặc:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:FASTTOWIN_ENV='dev'
.\gradlew.bat :server:installDist
.\run-packaged-server.cmd
```

Chế độ này dùng bộ nhớ và phù hợp để thử phòng/game cơ bản. Tài khoản, lịch sử, bạn bè, Elo, điểm danh và dữ liệu khác sẽ không được lưu bền vững.

Nếu emulator được khởi động lại trong lúc server vẫn chạy, chạy lại:

```powershell
.\connect-dev-device.cmd
```

## 4. Chạy trên điện thoại Android thật

### Qua USB

1. Bật Developer options và USB debugging.
2. Kết nối điện thoại, chấp nhận khóa RSA.
3. Chạy:

```powershell
.\connect-dev-device.cmd
```

Sau đó Run app từ Android Studio.

### Qua mạng LAN

Máy tính và điện thoại phải cùng mạng. Lấy IPv4 của máy chạy server, mở firewall cho TCP 8080 và build với URL LAN:

```powershell
.\gradlew.bat :app:assembleDevDebug -PFASTTOWIN_DEV_WS_URL=ws://192.168.1.10:8080/game
```

Thay `192.168.1.10` bằng IP thật. APK được tạo tại:

```text
app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

## 5. Xem PostgreSQL development

Thông tin kết nối mặc định trong `compose.yaml`:

```text
Host: 127.0.0.1
Port: 5432
Database: fasttowin
User: fasttowin
Password: fasttowin
```

Có thể dùng Database Inspector của IntelliJ/Android Studio bản hỗ trợ database, DBeaver hoặc pgAdmin. Hoặc mở `psql` trong container:

```powershell
docker compose exec database psql -U fasttowin -d fasttowin
```

Một số lệnh hữu ích trong `psql`:

```sql
\dt
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, email, created_at FROM users ORDER BY created_at DESC;
SELECT * FROM daily_check_ins ORDER BY created_at DESC;
SELECT tournament_id, status, created_at, finished_at FROM tournaments ORDER BY created_at DESC;
```

Dừng container nhưng giữ dữ liệu:

```powershell
docker compose stop
```

Không chạy `docker compose down -v` trừ khi chủ động muốn xóa toàn bộ database local.

## 6. Cài và chạy trên macOS/iOS

iOS chỉ build/run trên macOS. Yêu cầu cơ bản:

- Mac Intel hoặc Apple Silicon.
- Xcode tương thích Firebase Apple SDK 12 và iOS 15+ simulator hoặc iPhone/iPad đã provisioning.
- JDK 17.
- Android Studio/Android SDK vì shared module có Android target.
- Docker Desktop nếu chạy PostgreSQL trên cùng máy.

Sau khi clone, có thể khởi động PostgreSQL và backend bằng script dành cho macOS:

```bash
chmod +x gradlew start-dev-server-with-db.sh
./start-dev-server-with-db.sh
```

Sau đó:

1. Mở `iosApp/iosApp.xcodeproj` bằng Xcode.
2. Chọn scheme `iosApp`.
3. Chọn iOS simulator và nhấn Run.

iOS Simulator dùng mặc định:

```text
ws://127.0.0.1:8080/game
```

Với iPhone/iPad thật, thay `GAME_SERVER_URL` trong Debug build settings bằng địa chỉ LAN của Mac, ví dụ `ws://192.168.1.20:8080/game`, đồng thời mở firewall cổng 8080.

Hướng dẫn đầy đủ từ cài Xcode/JDK/Docker, chạy simulator và thiết bị thật, kiểm thử hai người chơi đến xử lý lỗi nằm trong [IOS_SETUP.md](IOS_SETUP.md). Xem thêm ma trận thiết bị tại [docs/testing.md](docs/testing.md).

## 7. Build và test

Hướng dẫn cấu hình và test mua Gem: [docs/store-billing.md](docs/store-billing.md).

Thiết lập JDK trong PowerShell nếu cần:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
```

Build APK development:

```powershell
.\gradlew.bat :app:assembleDevDebug --no-daemon
```

Chạy unit test backend và shared:

```powershell
.\gradlew.bat :server:test :shared:testAndroidHostTest --no-daemon
```

Chạy Compose UI test; cần ít nhất một emulator/thiết bị online:

```powershell
.\gradlew.bat :app:connectedDevDebugAndroidTest --no-daemon
```

Chạy integration test PostgreSQL:

```powershell
$env:TEST_DATABASE_URL='jdbc:postgresql://localhost:5432/fasttowin'
$env:TEST_DATABASE_USER='fasttowin'
$env:TEST_DATABASE_PASSWORD='fasttowin'
.\gradlew.bat :server:test --rerun-tasks --no-daemon
```

Báo cáo Compose UI test:

```text
app/build/reports/androidTests/connected/debug/flavors/dev/index.html
```

### Chạy phiên bản web thử nghiệm

Để chạy PostgreSQL, backend, cài/mở Android và mở web bằng một lệnh trên Windows, hãy mở emulator hoặc cắm thiết bị trước rồi chạy:

```powershell
.\start-dev-all.cmd
```

Script dùng lại backend nếu `http://127.0.0.1:8080/health` đang hoạt động, mở Android trên mọi thiết bị online và mở web tại `http://localhost:8081`. Log nền nằm trong `.artifacts/dev-all`; nhấn `Ctrl+C` để dừng các tiến trình do script khởi động. Dùng `.\start-dev-all.cmd -NoBrowser` nếu không muốn tự mở trình duyệt.

Khởi động backend ở cổng `8080`, sau đó mở terminal thứ hai:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun
```

Web development chạy tại `http://localhost:8081` và mặc định kết nối `ws://127.0.0.1:8080/game`. Có thể thay backend trong `webApp/src/wasmJsMain/resources/config.js` mà không sửa UI dùng chung.

Để kiểm tra riêng bản JavaScript fallback trên trình duyệt không hỗ trợ WasmGC:

```powershell
.\gradlew.bat :webApp:jsBrowserDevelopmentRun
```

Bản JavaScript development chạy tại `http://localhost:8082`.

Build bộ phân phối tương thích để trình duyệt tự chọn Wasm hoặc JavaScript:

```powershell
.\gradlew.bat :webApp:composeCompatibilityBrowserDistribution
```

Bản web đã có manifest, icon thường/maskable và service worker nên có thể cài như
một PWA. Khi trình duyệt hỗ trợ, vào **Tài khoản → Cài đặt ứng dụng → Cài Fast To Win**
để mở trình cài đặt; Safari và một số trình duyệt sẽ hiển thị hướng dẫn cài thủ công.
Service worker lưu app shell và các tài nguyên tĩnh đã tải; API, WebSocket và dữ liệu
trận vẫn cần backend hoạt động. Khi health check không thể tới máy chủ, app hiển thị
trạng thái Offline riêng và vẫn cho phép mở Luyện tập offline, không nhầm với bảo trì.
Khi phát hiện bản mới, app chỉ hỏi cập nhật ở trạng thái an toàn, không chen vào
lúc đang ghép trận, ở trong phòng hoặc thi đấu.

### Bật Web Push

Fast To Win dùng chung Firebase Cloud Messaging cho Android, iOS và web. Trong Firebase
Console, tạo một **Web App** trong cùng project, sau đó vào **Cloud Messaging → Web
Push certificates** để tạo VAPID key. Điền public client config vào
`webApp/src/wasmJsMain/resources/config.js`:

```javascript
globalThis.FASTTOWIN_CONFIG = {
    serverUrl: "wss://api.example.com/game",
    firebase: {
        apiKey: "...",
        authDomain: "your-project.firebaseapp.com",
        projectId: "your-project",
        storageBucket: "your-project.appspot.com",
        messagingSenderId: "...",
        appId: "..."
    },
    vapidKey: "YOUR_PUBLIC_VAPID_KEY"
};
```

Firebase Web config và VAPID public key không phải private credentials. Không đưa
`firebase-adminsdk.json`, service-account key hoặc VAPID private key vào web client.
Backend production dùng:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS='D:\secrets\firebase-adminsdk.json'
$env:FASTTOWIN_WEB_BASE_URL='https://play.example.com'
$env:FASTTOWIN_PUSH_ZONE='Asia/Ho_Chi_Minh'
$env:FASTTOWIN_DAILY_PUSH_HOUR='19'
```

Người chơi bật **Cài đặt → Thông báo trên thiết bị** để cấp quyền. Server gửi lời
mời phòng, lời mời giải đấu, thông báo nhiệm vụ vừa hoàn thành và một lời nhắc điểm
danh sau giờ đã cấu hình. Bốn nhóm này có công tắc riêng trong Cài đặt; lựa chọn
được lưu theo tài khoản và đồng bộ giữa các nền tảng. Migration V38 lưu trạng thái
chống gửi nhắc điểm danh trùng trong cùng ngày, còn V39 lưu tùy chọn từng nhóm.
Khi đăng xuất hoặc đăng nhập ở thiết bị khác, token cũ được xóa khỏi tài khoản.
Các script development tự đặt `FASTTOWIN_WEB_BASE_URL=http://localhost:8081` để
thông báo thử mở đúng màn hình web. Backend chỉ chấp nhận HTTP cho địa chỉ loopback;
mọi domain production vẫn phải dùng HTTPS.

Trên iOS, project Xcode đã tích hợp Firebase Messaging, quyền Push Notifications,
đăng ký APNs/FCM và điều hướng đến đúng màn khi chạm thông báo. Để nhận thông báo
trên thiết bị thật, cần tải APNs Authentication Key lên Firebase Console và chọn
Apple Development Team có quyền Push Notifications. Chi tiết nằm trong
[IOS_SETUP.md](IOS_SETUP.md#73-bật-thông-báo-push-trên-ios).

Khi phát hành một bản web mới có thay đổi client, tăng phiên bản
`SHELL_CACHE` trong `webApp/src/wasmJsMain/resources/service-worker.js`. Sau đó
build lại bộ phân phối tương thích và triển khai toàn bộ thư mục output cùng lúc.
Máy chủ web production cần:

- HTTPS và backend dùng `wss://`.
- Trả đúng MIME type cho `.wasm`, `.js`, `.webmanifest` và ảnh PNG.
- Không cache lâu `service-worker.js`, `index.html` và `config.js`.
- Cho phép tải Firebase SDK từ `https://www.gstatic.com` nếu triển khai Content Security Policy.
- Trả `index.html` cho mọi URL SPA, gồm `/rooms`, `/friends`, `/tournament`,
  `/account/*`, `/room/*` và `/challenge/*`.

Khi production, đổi URL trong `config.js` sang `wss://`, đặt domain web được phép cho backend rồi khởi động server:

```powershell
$env:FASTTOWIN_WEB_ORIGINS='https://play.example.com'
```

Chi tiết phần đã tương thích và các giới hạn còn lại: [docs/web-wasm-audit.md](docs/web-wasm-audit.md).

### Kiểm thử liên kết phòng và thử thách trên Android

Cài bản dev rồi gửi một liên kết thử nghiệm vào emulator:

```powershell
.\gradlew.bat :app:installDevDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -W `
  -a android.intent.action.VIEW `
  -d "fasttowin://room/<ROOM_ID>" `
  com.hienthai.fastowin.dev
```

Phòng công khai sẽ được tham gia sau khi danh sách phòng mới tải xong. Phòng riêng tư mở hộp nhập mật khẩu; mật khẩu không được lưu trong liên kết.

Mở trực tiếp một bàn thử thách hợp lệ:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -W `
  -a android.intent.action.VIEW `
  -d "fasttowin://challenge/FTW-CL-12345678-DF" `
  com.hienthai.fastowin.dev
```

Nếu chưa đăng nhập, app giữ liên kết qua màn xác thực rồi mở bàn sau đó. Chế độ chưa đạt cấp mở khóa sẽ hiển thị thông báo và không bắt đầu thử thách.

## 8. Môi trường dev và production

| Thành phần | Development | Production |
| --- | --- | --- |
| Android | Flavor `dev` | Flavor `prod` |
| iOS | Configuration `Debug` | Configuration `Release` |
| Backend | `FASTTOWIN_ENV=dev` | `FASTTOWIN_ENV=prod` |
| WebSocket | Có thể dùng `ws://` local | Bắt buộc cấu hình `wss://` thật |

Build Android production:

```powershell
.\gradlew.bat :app:assembleProdRelease -PFASTTOWIN_PROD_WS_URL=wss://api.example.com/game
```

Backend production yêu cầu PostgreSQL và không được dùng mật khẩu development:

```powershell
$env:FASTTOWIN_ENV='prod'
$env:PORT='8080'
$env:DATABASE_URL='jdbc:postgresql://database-host:5432/fasttowin'
$env:DATABASE_USER='fasttowin_app'
$env:DATABASE_PASSWORD='replace-with-a-secret'
.\gradlew.bat :server:installDist
.\run-packaged-server.cmd
```

Endpoint production mặc định trong project chỉ là placeholder và không thể kết nối. Không phát hành app trước khi thay bằng `wss://` hợp lệ.

### Bật/tắt chế độ bảo trì

Màn bảo trì chỉ xuất hiện khi backend chủ động trả về `maintenance: true` tại `GET /status`. Mất mạng hoặc mất kết nối WebSocket không kích hoạt màn này.

Trước khi khởi động server, bật bảo trì và đặt thông báo:

```powershell
$env:FASTTOWIN_MAINTENANCE='true'
$env:FASTTOWIN_MAINTENANCE_MESSAGE='Đang nâng cấp dữ liệu mùa giải. Vui lòng quay lại sau.'
.\start-dev-server-with-db.cmd
```

Trong thời gian bảo trì, app tự kiểm tra trạng thái mỗi 60 giây, khóa thao tác và chặn Back. Để mở lại dịch vụ, dừng server, tắt cờ rồi khởi động lại:

```powershell
$env:FASTTOWIN_MAINTENANCE='false'
Remove-Item Env:FASTTOWIN_MAINTENANCE_MESSAGE -ErrorAction SilentlyContinue
.\start-dev-server-with-db.cmd
```

## 9. Xử lý lỗi thường gặp

### `JAVA_HOME is not set`

Trong Android Studio Terminal:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
```

Xác nhận:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -version
```

### `Address already in use: bind`

Cổng 8080 đang có một server khác sử dụng:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

Dừng đúng process cũ sau khi kiểm tra PID:

```powershell
Stop-Process -Id <PID>
```

Không chạy hai server cùng lúc.

### `NoClassDefFoundError` trong module `protocol`

Lỗi này có thể xảy ra nếu build app/protocol trong lúc server đang chạy trực tiếp bằng `:server:run`.
Dừng server bằng `Ctrl+C`, sau đó chạy lại script khuyến nghị:

```powershell
.\start-dev-server-with-db.cmd
```

Script chạy server từ bộ JAR đã đóng gói để các class protocol luôn cùng phiên bản.

### App báo không thể kết nối `ws://127.0.0.1:8080`

Kiểm tra theo thứ tự:

1. Mở `http://127.0.0.1:8080/health` và xác nhận trả `OK`.
2. Xác nhận emulator/thiết bị đang online.
3. Chạy lại `.\connect-dev-device.cmd`.
4. Rebuild app nếu vừa thay URL hoặc protocol.
5. Nếu dùng điện thoại qua Wi-Fi, không dùng `127.0.0.1`; dùng IP LAN của máy chạy server.

### `docker` không được nhận diện

- Mở Docker Desktop và chờ trạng thái Running.
- Mở terminal mới.
- Kiểm tra `docker version` và `docker compose version`.
- Script Windows cũng thử tìm Docker tại thư mục cài mặc định của Docker Desktop.

### Protocol không tương thích

Client và server phải lấy từ cùng commit. Sau khi `git pull`, hãy dừng server cũ, khởi động lại backend rồi rebuild app.

### Cổng PostgreSQL 5432 bị chiếm

Kiểm tra process/container khác đang dùng cổng hoặc đổi port mapping trong `compose.yaml` và cập nhật `DATABASE_URL` tương ứng.

## Tài liệu chi tiết

- [BACKEND_SETUP.md](BACKEND_SETUP.md): kiến trúc backend, API, bảo mật và production.
- [IOS_SETUP.md](IOS_SETUP.md): chạy ứng dụng iOS.
- [docs/testing.md](docs/testing.md): test tự động và ma trận kiểm thử đa thiết bị.
