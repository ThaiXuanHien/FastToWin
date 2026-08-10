# Fast To Win backend MVP

Backend hiện tại là Ktor WebSocket server chạy JVM và giữ dữ liệu trong bộ nhớ. Server là nơi duy nhất tạo phòng, xác minh mật khẩu, sinh bàn 50 số, kiểm tra lượt bấm và tính điểm.

## Môi trường

| Nền tảng | Development | Production |
|---|---|---|
| Android | Flavor `dev` | Flavor `prod` |
| iOS | Build configuration `Debug` | Build configuration `Release` |
| Backend | `FASTTOWIN_ENV=dev` | `FASTTOWIN_ENV=prod` |

Development dùng `ws://` để chạy trong mạng local. Production tắt cleartext trên Android và bắt buộc cấu hình endpoint `wss://`.

## Chạy local

Yêu cầu JDK 11 trở lên. Từ thư mục gốc project:

```powershell
$env:FASTTOWIN_ENV="dev"
.\gradlew.bat :server:run
```

Kiểm tra server:

```text
http://localhost:8080/health
```

Kết quả mong đợi là `OK`.

## Địa chỉ client

- Android flavor `dev`: `ws://10.0.2.2:8080/game`
- iOS Debug: `ws://127.0.0.1:8080/game`

Android truyền URL từ `BuildConfig` vào shared module. iOS đọc URL từ `Info.plist` rồi truyền vào shared module.

Để thử Android trên điện thoại thật, truyền địa chỉ IPv4 LAN của máy chạy server:

```powershell
.\gradlew.bat :app:assembleDevDebug -PFASTTOWIN_DEV_WS_URL=ws://192.168.1.10:8080/game
```

Máy tính và điện thoại phải cùng mạng, đồng thời firewall phải cho phép cổng 8080.

## Cấu hình production

Android production:

```powershell
.\gradlew.bat :app:assembleProdRelease -PFASTTOWIN_PROD_WS_URL=wss://api.ten-mien-cua-ban.com/game
```

iOS production: thay `GAME_SERVER_URL` trong build configuration `Release` của Xcode bằng endpoint `wss://` thật.

Backend production:

```powershell
$env:FASTTOWIN_ENV="prod"
$env:PORT="8080"
.\gradlew.bat :server:run
```

Giá trị `configure-production-server.invalid` chỉ là placeholder an toàn và không thể kết nối. Cần thay URL trước khi phát hành.

## Test

```powershell
.\gradlew.bat :server:test
.\gradlew.bat :app:compileDevDebugKotlin
```

Test backend bao gồm:

- Khôi phục đúng guest session bằng resume token.
- Từ chối mật khẩu phòng sai.
- Hai người chọn cùng một target đồng thời nhưng server chỉ chấp nhận một lượt.

## Giới hạn của MVP

- Phòng và session bị mất khi server restart.
- Resume token mới chỉ được giữ trong bộ nhớ của tiến trình app.
- Chưa có PostgreSQL, tài khoản, JWT và lịch sử trận.
- Cấu hình local dùng `ws://`; môi trường production phải dùng `wss://`.
