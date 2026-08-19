# Cài đặt và chạy Fast To Win trên macOS/iOS

Tài liệu này dành cho developer mới clone project và muốn chạy backend, Android, iOS Simulator hoặc iPhone/iPad thật trên macOS. Project dùng Compose Multiplatform và Xcode gọi Gradle để build framework Kotlin; **không cần cài CocoaPods**.

## 1. Yêu cầu phần cứng và hệ điều hành

- Khuyến nghị Mac Apple Silicon (M1 trở lên). Project hiện cấu hình `iosSimulatorArm64` cho simulator Apple Silicon và `iosArm64` cho thiết bị thật.
- macOS có thể cài phiên bản Xcode tương thích với iOS cần kiểm thử.
- Tối thiểu khoảng 30 GB trống cho Xcode, Android SDK, Gradle cache và Docker image.
- iOS deployment target của app là iOS 14.

## 2. Cài công cụ

### 2.1. Git và Xcode Command Line Tools

Mở Terminal và chạy:

```bash
xcode-select --install
git --version
```

Nếu hộp thoại báo Command Line Tools đã được cài thì có thể bỏ qua. Không cần cài Git bằng Homebrew nếu lệnh `git --version` đã hoạt động.

### 2.2. Xcode

1. Cài Xcode từ App Store hoặc Apple Developer.
2. Mở Xcode một lần để Xcode cài các thành phần bổ sung.
3. Vào **Xcode > Settings > Components** và cài iOS Simulator cần dùng.
4. Sau đó chạy:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -license accept
xcodebuild -version
```

Nếu Xcode được đổi tên hoặc cài ở vị trí khác, thay đường dẫn trong lệnh `xcode-select`.

### 2.3. Android Studio, JDK 17 và Android SDK

Dù chỉ chạy iOS, module Kotlin Multiplatform vẫn có Android target, vì vậy nên cài Android Studio để có cả JDK 17 và Android SDK.

1. Cài bản Android Studio dành cho Apple Silicon.
2. Trong **Android Studio > Settings > Languages & Frameworks > Android SDK**, cài:
   - Android SDK Platform 37;
   - Android SDK Build-Tools;
   - Android SDK Platform-Tools;
   - Android Emulator nếu cần kiểm thử Android.
3. Kiểm tra JDK đi kèm Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
"$JAVA_HOME/bin/java" -version
```

Để giữ cấu hình cho các Terminal mới, thêm vào `~/.zshrc`:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

Sau đó nạp lại cấu hình:

```bash
source ~/.zshrc
java -version
adb version
```

Project yêu cầu JDK 17. Không dùng JDK quá cũ mà macOS có thể tự chọn mặc định.

### 2.4. Docker Desktop

Docker cần thiết để chạy PostgreSQL và kiểm thử đầy đủ đăng nhập, hồ sơ, Elo, bạn bè, lịch sử, nhiệm vụ và điểm danh.

1. Cài Docker Desktop bản Apple Silicon.
2. Mở Docker Desktop và chờ trạng thái engine đã chạy.
3. Kiểm tra:

```bash
docker version
docker compose version
```

Có thể bỏ Docker nếu chỉ thử phòng và gameplay bằng dữ liệu trong bộ nhớ, nhưng các tính năng tài khoản sẽ không được lưu bền vững.

## 3. Clone và chuẩn bị project

```bash
git clone https://github.com/ThaiXuanHien/FastToWin.git
cd FastToWin
chmod +x gradlew start-dev-server-with-db.sh
```

Tạo `local.properties` ở thư mục gốc nếu Android Studio chưa tự tạo:

```bash
printf 'sdk.dir=%s\n' "$HOME/Library/Android/sdk" > local.properties
```

`local.properties` là file riêng của máy và đã được Git bỏ qua.

Xác minh Gradle và tải dependency lần đầu:

```bash
./gradlew --version
./gradlew :server:test :shared:testAndroidHostTest
```

Lần đầu có thể mất vài phút vì Gradle tải plugin và thư viện. Máy cần kết nối Internet.

## 4. Khởi động PostgreSQL và backend

### Cách nhanh

Từ thư mục gốc project:

```bash
./start-dev-server-with-db.sh
```

Script sẽ:

- tìm JDK 17 từ Android Studio hoặc macOS;
- khởi động PostgreSQL bằng Docker Compose;
- chờ database healthy;
- cấu hình môi trường development;
- chạy Flyway migration;
- khởi động Ktor server ở `0.0.0.0:8080`.

Giữ Terminal này mở trong suốt quá trình kiểm thử.

### Chạy thủ công

```bash
docker compose up -d --wait database

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export FASTTOWIN_ENV=dev
export DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/fasttowin
export DATABASE_USER=fasttowin
export DATABASE_PASSWORD=fasttowin

./gradlew :server:run
```

Khi thấy dòng sau, backend đã sẵn sàng:

```text
Starting Fast To Win server: environment=dev, host=0.0.0.0, port=8080, storage=postgresql
```

Mở Terminal thứ hai để kiểm tra:

```bash
curl http://127.0.0.1:8080/health
```

Kết quả phải là:

```text
OK
```

### Chạy không có PostgreSQL

```bash
export FASTTOWIN_ENV=dev
./gradlew :server:run
```

Chế độ này chỉ phù hợp thử gameplay cơ bản. Dữ liệu sẽ mất khi dừng server.

## 5. Chạy iOS Simulator bằng Xcode

1. Khởi động backend và xác nhận `/health` trả `OK`.
2. Mở project:

```bash
open iosApp/iosApp.xcodeproj
```

3. Trong thanh chọn scheme của Xcode:
   - chọn scheme **iosApp**;
   - chọn một iPhone hoặc iPad Simulator;
   - dùng build configuration **Debug**.
4. Nhấn **Run** hoặc `Cmd + R`.

Xcode tự chạy Gradle task:

```text
:shared:embedAndSignAppleFrameworkForXcode
```

Framework được tạo ở `shared/build/xcode-frameworks`. Không cần chạy CocoaPods hay thêm framework thủ công.

iOS Simulator Debug mặc định kết nối:

```text
ws://127.0.0.1:8080/game
```

Simulator dùng mạng của Mac nên không cần `adb reverse` và không cần đổi IP.

## 6. Chạy Android trên cùng máy Mac

1. Mở project root bằng Android Studio.
2. Chờ Gradle Sync hoàn tất.
3. Mở Android emulator.
4. Trong Terminal chạy:

```bash
adb reverse tcp:8080 tcp:8080
```

5. Chọn module `app`, build variant `devDebug`, sau đó nhấn Run.

Android Debug cũng dùng `ws://127.0.0.1:8080/game`; `adb reverse` chuyển cổng emulator về backend trên Mac.

## 7. Chạy trên iPhone/iPad thật

### 7.1. Cấu hình ký ứng dụng

1. Kết nối thiết bị với Mac và chọn **Trust** khi được hỏi.
2. Trong Xcode, chọn target **iosApp > Signing & Capabilities**.
3. Bật **Automatically manage signing**.
4. Chọn Apple Development Team của bạn.
5. Nếu bundle ID đã được dùng bởi tài khoản khác, đổi `PRODUCT_BUNDLE_IDENTIFIER` Debug thành một giá trị duy nhất, ví dụ `com.tenban.fasttowin.dev`.

### 7.2. Dùng IP LAN của Mac

`127.0.0.1` trên iPhone là chính iPhone, không phải Mac. Mac và iPhone phải cùng Wi-Fi.

Lấy IP Wi-Fi của Mac:

```bash
ipconfig getifaddr en0
```

Nếu không có kết quả, thử:

```bash
ipconfig getifaddr en1
```

Trong Xcode, mở **iosApp target > Build Settings**, tìm `GAME_SERVER_URL` và đổi giá trị **Debug** thành IP vừa lấy, ví dụ:

```text
ws://192.168.1.20:8080/game
```

Sau đó chọn iPhone/iPad và nhấn `Cmd + R`. Khi iOS hỏi quyền truy cập mạng cục bộ, chọn **Allow**.

Nếu macOS Firewall đang bật, cho phép Java nhận kết nối đến hoặc mở TCP 8080 trong mạng riêng. Kiểm tra từ thiết bị bằng Safari:

```text
http://192.168.1.20:8080/health
```

Trang phải hiển thị `OK` trước khi mở app.

## 8. Kiểm thử hai người chơi

Không đăng nhập cùng một tài khoản trên hai máy vì backend chỉ cho một WebSocket game hoạt động cho mỗi tài khoản.

Có thể dùng một trong các cách:

- một iOS Simulator và một Android emulator;
- hai iOS Simulator khác nhau;
- một simulator và một thiết bị thật;
- hai thiết bị thật cùng Wi-Fi.

Với hai simulator, tạo hai simulator trong **Xcode > Window > Devices and Simulators**. Run app trên simulator thứ nhất, sau đó chọn simulator thứ hai và Run lại; app trên máy thứ nhất vẫn có thể tiếp tục chạy.

Checklist kiểm thử đề xuất:

1. Đăng ký hai tài khoản email khác nhau.
2. Máy 1 tạo phòng công khai, máy 2 tham gia không nhập mật khẩu.
3. Tạo phòng riêng tư và kiểm tra mật khẩu sai/đúng.
4. Hai bên bấm sẵn sàng và kiểm tra đếm ngược 3–2–1.
5. Kiểm tra số đã chọn bị vô hiệu hóa đồng thời ở hai máy.
6. Chơi một trận có tỷ số khác nhau và xác nhận chỉ bên điểm cao hơn thắng.
7. Chơi trận thường và xác nhận Elo không thay đổi.
8. Chơi xếp hạng và xác nhận Elo cùng tiến độ phân hạng được cập nhật.
9. Tắt/mở lại app để kiểm tra đăng nhập và reconnect.
10. Kiểm tra portrait, landscape, chữ lớn, iPhone nhỏ và iPad.

## 9. Build và test bằng Terminal trên macOS

Kiểm tra liên kết phòng và thử thách trên simulator đang mở:

```bash
xcrun simctl openurl booted 'fasttowin://room/<ROOM_ID>'
xcrun simctl openurl booted 'fasttowin://challenge/FTW-CL-12345678-DF'
```

Nếu app chưa chạy, lệnh sẽ khởi động app. Nếu app đang chạy, liên kết được chuyển vào phiên hiện tại. Phòng riêng tư vẫn yêu cầu nhập mật khẩu trong app. Liên kết thử thách giữ nguyên cơ chế mở khóa chế độ theo cấp độ người chơi.

Unit test backend và shared:

```bash
./gradlew :server:test :shared:testAndroidHostTest --no-daemon
```

Build Android development APK:

```bash
./gradlew :app:assembleDevDebug --no-daemon
```

Build framework iOS Simulator để kiểm tra Kotlin/Native trước khi mở Xcode:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --no-daemon
```

Build app iOS bằng command line, không cần ký cho simulator:

```bash
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Đổi tên simulator theo danh sách trên máy:

```bash
xcrun simctl list devices available
```

Integration test PostgreSQL:

```bash
docker compose up -d --wait database

export TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/fasttowin
export TEST_DATABASE_USER=fasttowin
export TEST_DATABASE_PASSWORD=fasttowin

./gradlew :server:test --rerun-tasks --no-daemon
```

## 10. Xem và quản lý database

Thông tin development mặc định:

```text
Host: 127.0.0.1
Port: 5432
Database: fasttowin
User: fasttowin
Password: fasttowin
```

Mở `psql` ngay trong container:

```bash
docker compose exec database psql -U fasttowin -d fasttowin
```

Một số lệnh:

```sql
\dt
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, email, created_at FROM users ORDER BY created_at DESC;
SELECT room_name, game_mode, match_type, ended_at FROM matches ORDER BY ended_at DESC LIMIT 20;
```

Dừng database nhưng giữ dữ liệu:

```bash
docker compose stop
```

Không dùng `docker compose down -v` nếu không chủ động muốn xóa toàn bộ dữ liệu local.

## 11. Xử lý lỗi thường gặp

### `JAVA_HOME is not set`

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
"$JAVA_HOME/bin/java" -version
```

Nếu cài Temurin/OpenJDK 17 riêng:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### `SDK location not found`

```bash
printf 'sdk.dir=%s\n' "$HOME/Library/Android/sdk" > local.properties
```

Đồng thời kiểm tra Android SDK đã được cài từ Android Studio.

### `xcode-select: error` hoặc Gradle không tìm thấy Xcode

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
xcodebuild -version
```

### `Address already in use: 8080`

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Kiểm tra đúng PID rồi dừng process server cũ:

```bash
kill <PID>
```

Không chạy hai backend cùng lúc.

### App luôn hiển thị đang kết nối

Kiểm tra theo thứ tự:

1. `curl http://127.0.0.1:8080/health` phải trả `OK`.
2. Client và server phải được build từ cùng commit.
3. Dừng app, dừng server cũ, chạy lại backend rồi Clean Build Folder trong Xcode.
4. Simulator dùng `127.0.0.1`; thiết bị thật phải dùng IP LAN của Mac.
5. Với Android emulator, chạy lại `adb reverse tcp:8080 tcp:8080`.

### Xcode báo không tìm thấy framework `Shared`

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --no-daemon
rm -rf ~/Library/Developer/Xcode/DerivedData/iosApp-*
```

Sau đó mở lại project và Run. Xcode build phase sẽ tạo framework đúng vị trí.

### Xcode script bị sandbox chặn

Target hiện đã cấu hình `ENABLE_USER_SCRIPT_SANDBOXING = NO`. Nếu tạo configuration mới, bảo đảm **User Script Sandboxing** cũng đặt là **No** để build phase có thể gọi Gradle.

### Thiết bị thật không kết nối được WebSocket

- Không dùng `127.0.0.1`.
- Kiểm tra Safari trên thiết bị mở được `http://<IP-MAC>:8080/health`.
- Cho phép quyền Local Network của app trong **Settings > Privacy & Security > Local Network**.
- Kiểm tra Mac và thiết bị không dùng VPN hoặc guest Wi-Fi chặn kết nối nội bộ.
- Kiểm tra macOS Firewall.

### Protocol không tương thích

Sau mỗi lần `git pull` có thay đổi protocol:

1. dừng backend cũ;
2. chạy lại `./start-dev-server-with-db.sh`;
3. Clean Build Folder trong Xcode;
4. gỡ app cũ khỏi simulator/thiết bị nếu vẫn còn lỗi;
5. Run lại app.

## 12. Production

Debug local dùng `ws://`. Release hiện trỏ đến placeholder và không thể kết nối. Trước khi phát hành cần:

- triển khai backend thật với PostgreSQL;
- đặt reverse proxy TLS;
- đổi `GAME_SERVER_URL` Release thành `wss://.../game`;
- dùng database credential riêng;
- cấu hình signing, bundle ID, version và App Store Connect.

Không dùng thông tin PostgreSQL development trong production.
