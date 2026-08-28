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

## Kết quả gần nhất

Ngày 28/08/2026, toàn bộ bộ hồi quy tự động chạy thành công:

- Pixel 10a Android 17: 82/82 UI test đạt, không skip.
- Shared và server: 136/136 test đạt.
- APK dev/debug và APK UI test đều build thành công.

Sau khi thay đổi test, luôn chạy lại toàn bộ các lệnh phía trên. Các luồng WebSocket/PostgreSQL thực tế trên hai tài khoản và bản iOS vẫn cần smoke test thủ công trước mỗi bản phát hành.
