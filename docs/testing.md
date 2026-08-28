# Chiến lược kiểm thử Fast To Win

## Hạ tầng hiện tại

- Ứng dụng Kotlin Multiplatform dùng Jetpack Compose/Compose Multiplatform, không dùng DI hoặc mocking framework.
- UI test Android dùng JUnit 4, AndroidJUnitRunner, Compose UI Test và Espresso trong `app/src/androidTest`.
- Logic dùng `kotlin.test` và coroutine test trong `shared/src/commonTest`.
- Backend dùng Kotlin Test/JUnit Platform, Ktor test host và coroutine test trong `server/src/test`.
- Chưa cấu hình Robolectric, screenshot testing tự động hoặc JaCoCo.

## Lệnh kiểm thử trên Windows

Chạy từ thư mục gốc project trong Terminal của Android Studio:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME="$PWD\.gradle"

.\gradlew.bat :shared:testAndroidHostTest :server:test --no-daemon
.\gradlew.bat :app:assembleDevDebugAndroidTest :app:assembleDevDebug --no-daemon
.\gradlew.bat :app:connectedDevDebugAndroidTest --no-daemon
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

Thực hiện trên máy Mac Apple Silicon có Xcode sau khi Android test đạt:

1. Chạy dev server và PostgreSQL trên Mac hoặc dùng server cùng mạng LAN.
2. Mở `iosApp/iosApp.xcodeproj` và chọn scheme `iosApp`.
3. Simulator có thể dùng `GAME_SERVER_URL=ws://127.0.0.1:8080/game`.
4. Thiết bị thật cần dùng địa chỉ LAN của Mac, ví dụ `ws://192.168.1.20:8080/game`, và mở cổng 8080 trên firewall.
5. Chạy trên iPhone nhỏ, iPhone Pro Max và iPad ở cả dọc lẫn ngang.
6. Trước khi phát hành, kiểm tra thêm trên ít nhất một iPhone hoặc iPad thật.

Các luồng bắt buộc gồm đăng ký/đăng nhập và khôi phục phiên, điều hướng và pull-to-refresh, trận 50 số giữa hai thiết bị, kết quả/đấu lại/Elo, thao tác bạn bè, bàn phím, safe area, xoay màn hình và chữ lớn.

## Kết quả gần nhất

Ngày 28/08/2026, toàn bộ bộ hồi quy tự động chạy thành công:

- Pixel 10a Android 17: 82/82 UI test đạt, không skip.
- Shared và server: 136/136 test đạt.
- APK dev/debug và APK UI test đều build thành công.

Sau khi thay đổi test, luôn chạy lại toàn bộ các lệnh phía trên. Các luồng WebSocket/PostgreSQL thực tế trên hai tài khoản và bản iOS vẫn cần smoke test thủ công trước mỗi bản phát hành.
