# Chiến lược kiểm thử Fast To Win

## Hạ tầng hiện tại

- Ứng dụng Kotlin Multiplatform dùng Jetpack Compose/Compose Multiplatform, không dùng DI hoặc mocking framework.
- UI test Android dùng JUnit 4, AndroidJUnitRunner, Compose UI Test và Espresso trong `app/src/androidTest`.
- Logic dùng `kotlin.test` và coroutine test trong `shared/src/commonTest`.
- Backend dùng Kotlin Test/JUnit Platform, Ktor test host và coroutine test trong `server/src/test`.
- Chưa cấu hình Robolectric, screenshot testing tự động hoặc JaCoCo.
- Có bộ [Web E2E Playwright](web-e2e.md) cho Chromium, Firefox, WebKit và ma trận responsive; xem tài liệu này để phân biệt kiểm thử tự động với phần còn cần thiết bị/trình duyệt thật.

## Lệnh kiểm thử trên Windows

Chạy từ thư mục gốc project trong Terminal của Android Studio:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME="$PWD\.gradle"

.\gradlew.bat :shared:testAndroidHostTest :server:test --no-daemon
.\gradlew.bat :app:assembleDevDebugAndroidTest :app:assembleDevDebug --no-daemon
.\gradlew.bat :app:connectedDevDebugAndroidTest --no-daemon
```

Lệnh hồi quy thống nhất, tương đương các bước biên dịch và unit/integration test
chính trong CI:

```powershell
.\gradlew.bat :server:test :shared:testAndroidHostTest :app:compileDevDebugAndroidTestKotlin :app:assembleDevDebug :webApp:compileKotlinWasmJs --no-daemon
```

Có thể chỉ biên dịch APK test mà không cần thiết bị bằng:

```powershell
.\gradlew.bat :app:compileDevDebugAndroidTestKotlin --no-daemon
```

Kết quả HTML của UI test được tạo tại:

```text
app/build/reports/androidTests/connected/debug/flavors/dev/index.html
```

Lệnh `connectedDevDebugAndroidTest` chạy bộ UI test trên tất cả thiết bị/emulator đang kết nối. Kiểm tra danh sách bằng:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

Với nhiều emulator Android 17, nên chạy lần lượt từng máy để tránh lỗi mất `window focus` của Espresso khi các emulator cùng hoạt động.

## Phạm vi hồi quy chính

- Đăng nhập và nhập liệu bằng bàn phím.
- Trang chủ, điểm danh và các mốc phần thưởng.
- Tạo/tham gia phòng công khai, phòng mật khẩu và liên kết phòng.
- Màn chơi đủ 50 số, kết quả thắng/thua/hòa và chia sẻ kết quả.
- Mời bạn, xem hồ sơ, menu quản lý bạn bè và điều hướng Back.
- Xin vào nhiều bang, trạng thái đang chờ và bang chủ duyệt thành viên.
- Profile, nhiệm vụ, bộ sưu tập, trận gần đây, màn hình nhỏ, ngang và chữ lớn.
- Backend: đồng bộ trận, rời phòng, reconnect, chống xử lý trùng, bang hội, nhiệm vụ, điểm danh, ví và PostgreSQL khi test database khả dụng.

## Ma trận kiểm thử thủ công Android

Trước mỗi bản phát hành, kiểm tra lại các luồng quan trọng trên các cấu hình sau:

| Nhóm thiết bị | Kích thước gợi ý | Nội dung cần kiểm tra |
| --- | --- | --- |
| Điện thoại nhỏ | Rộng 320–360 dp | Bàn phím đăng nhập, dialog phòng, thanh điều hướng, ô số |
| Điện thoại lớn | Rộng 420–480 dp | Lối tắt trang chủ, thống kê hồ sơ, menu bạn bè |
| Tablet | Rộng từ 800 dp | Chiều rộng nội dung, bàn số 10 cột, dialog |
| Chế độ ngang | Điện thoại và tablet | Cuộn trang chủ, phòng chờ, bàn chơi, kết quả |

Kiểm tra cả cỡ chữ mặc định và cỡ chữ trợ năng lớn nhất. Dùng tên phòng/tên người chơi dài, danh sách rỗng và danh sách có nhiều dữ liệu để phát hiện nội dung bị tràn.

## Xác minh iOS và iPadOS

Thực hiện trên máy Mac Intel hoặc Apple Silicon có Xcode sau khi Android test đạt:

1. Chạy `./gradlew :shared:compileKotlinIosSimulatorArm64 --no-daemon` để bắt lỗi cầu nối Kotlin/Native trước khi mở Xcode.
2. Chạy dev server và PostgreSQL trên Mac hoặc dùng server cùng mạng LAN.
3. Mở `iosApp/iosApp.xcodeproj` và chọn scheme `iosApp`.
4. Simulator có thể dùng `GAME_SERVER_URL=ws://127.0.0.1:8080/game`.
5. Thiết bị thật cần dùng địa chỉ LAN của Mac, ví dụ `ws://192.168.1.20:8080/game`, và mở cổng 8080 trên firewall.
6. Chạy trên iPhone nhỏ, iPhone Pro Max và iPad ở cả dọc lẫn ngang.
7. Trước khi phát hành, kiểm tra thêm trên ít nhất một iPhone hoặc iPad thật.

Các luồng bắt buộc gồm đăng ký/đăng nhập và khôi phục phiên, điều hướng và pull-to-refresh, trận 50 số giữa hai thiết bị, kết quả/đấu lại/Elo, thao tác bạn bè, bàn phím, safe area, xoay màn hình và chữ lớn.

Riêng ảnh đại diện iOS, kiểm tra thêm:

1. Mở Hồ sơ → chỉnh sửa → **Tải ảnh lên** và xác nhận photo picker chỉ hiển thị ảnh.
2. Thử ảnh JPEG, PNG và HEIC; ảnh dọc/ngang phải đúng chiều, không méo và tải lên thành công.
3. Sau khi lưu, avatar phải đổi đồng thời ở Hồ sơ, Trang chủ và các màn có thông tin người chơi.
4. Đóng/mở lại ứng dụng và đăng nhập trên thiết bị còn lại để xác nhận ảnh được lấy lại từ server.
5. Hủy photo picker không được thay avatar hiện tại hoặc làm treo màn chỉnh sửa.

Riêng thông báo iOS, kiểm tra trên thiết bị thật đã cấu hình APNs:

1. Khi chưa cấp quyền, Cài đặt hiển thị trạng thái chờ và chỉ hỏi quyền sau khi bật.
2. Tắt trong app phải xóa token khỏi tài khoản; bật lại phải đăng ký được token mới.
3. Từ chối quyền rồi bật lại phải mở trang Settings của iOS.
4. Nhận đủ lời mời phòng, lời mời giải đấu, nhiệm vụ và nhắc điểm danh khi app ở nền.
5. Chạm thông báo phải mở đúng phòng/màn tương ứng; thông báo không có đích mở danh sách Thông báo.

## Kiểm tra tự động trên CI

GitHub Actions chạy với mọi push, pull request và khi kích hoạt thủ công:

- Unit/integration test của server và shared.
- Biên dịch Android UI test để phát hiện lỗi import/dependency trước khi khởi động emulator.
- Build APK dev và biên dịch Web/Wasm.
- Chạy Compose UI test trên Android emulator.
- Chạy Web E2E game trên Chromium, responsive ở bốn kích thước, chữ lớn/nội dung dài/viewport bàn phím, smoke test Firefox/WebKit và bundle Kotlin/JS fallback với backend in-memory riêng; lưu trace/ảnh khi lỗi.
- Lưu APK cùng báo cáo test thành artifact.

## Kết quả gần nhất

Ngày 28/08/2026, toàn bộ bộ hồi quy tự động chạy thành công:

- Pixel 10a Android 17: 82/82 UI test đạt, không skip.
- Shared và server: 136/136 test đạt.
- APK dev/debug và APK UI test đều build thành công.

Sau khi thay đổi test, luôn chạy lại toàn bộ các lệnh phía trên. Các luồng WebSocket/PostgreSQL thực tế trên hai tài khoản và bản iOS vẫn cần smoke test thủ công trước mỗi bản phát hành.
