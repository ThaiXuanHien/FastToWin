# Fast To Win backend MVP

Backend hiện tại là Ktor WebSocket server chạy JVM. Server là nơi duy nhất tạo phòng, xác minh mật khẩu, sinh bàn 50 số, kiểm tra lượt bấm và tính điểm. Guest identity và resume session có thể được lưu trong PostgreSQL; phòng và trạng thái trận đấu hiện vẫn nằm trong bộ nhớ.

## Môi trường

| Nền tảng | Development | Production |
|---|---|---|
| Android | Flavor `dev` | Flavor `prod` |
| iOS | Build configuration `Debug` | Build configuration `Release` |
| Backend | `FASTTOWIN_ENV=dev` | `FASTTOWIN_ENV=prod` |

Development dùng `ws://` để chạy trong mạng local. Production tắt cleartext trên Android và bắt buộc cấu hình endpoint `wss://`.

## Chạy local

Yêu cầu JDK 17 trở lên. Từ thư mục gốc project:

```powershell
$env:FASTTOWIN_ENV="dev"
.\gradlew.bat :server:run
```

Nếu dùng CMD, có thể chạy file hỗ trợ. File này tự thiết lập `JAVA_HOME`, cấu hình `adb reverse` cho tất cả emulator/thiết bị đang kết nối rồi khởi động server:

```bat
start-dev-server.cmd
```

Phải mở emulator trước khi chạy file trên. Nếu emulator được khởi động lại trong lúc server vẫn đang chạy, mở một cửa sổ CMD khác và chạy:

```bat
connect-dev-device.cmd
```

`adb reverse` không được giữ lại sau khi emulator/thiết bị restart.

## Chạy cùng PostgreSQL

Yêu cầu Docker Desktop. Lệnh sau khởi động PostgreSQL, tự chạy Flyway migration, cấu hình `adb reverse` cho mọi emulator và chạy backend:

```bat
start-dev-server-with-db.cmd
```

Database development dùng các giá trị trong `compose.yaml`:

```text
database: fasttowin
user: fasttowin
password: fasttowin
port: 5432
```

Nếu chưa cài Docker, tiếp tục dùng `start-dev-server.cmd`; backend sẽ dùng bộ nhớ và game vẫn hoạt động bình thường.

Để chạy thủ công bằng PowerShell:

```powershell
docker compose up -d --wait database
$env:FASTTOWIN_ENV="dev"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/fasttowin"
$env:DATABASE_USER="fasttowin"
$env:DATABASE_PASSWORD="fasttowin"
.\gradlew.bat :server:run
```

Flyway tự tạo các bảng `users`, `profiles`, `sessions`, `matches`, `match_players` và `player_stats`. Resume token chỉ được lưu dưới dạng SHA-256 hash, không lưu token gốc.

Khi trận kết thúc, backend lưu kết quả đúng một lần theo `roomId`, gồm điểm từng người, thắng/thua/hòa, tổng số trận, điểm cao nhất và chuỗi thắng. Việc bấm số trong trận vẫn được xử lý trong bộ nhớ để giữ độ trễ thấp.

Từ màn hình danh sách phòng, người chơi có thể mở **Hồ sơ** để xem mã người chơi, tổng trận, thắng/thua/hòa, điểm cao nhất, chuỗi thắng và tối đa 20 trận gần nhất. Dữ liệu được lấy qua WebSocket của phiên hiện tại nên client không thể yêu cầu hồ sơ riêng tư của player ID khác.

Với chế độ **Đua 60 giây**, đồng hồ kết thúc do backend quyết định. Server kiểm tra timer mỗi 250 ms, phát `game_finished` cho cả hai người chơi và lưu kết quả đúng một lần; client chỉ hiển thị đồng hồ, không tự quyết định kết quả trận.

Backend giữ audit log tối đa 2.000 request cho mỗi trận và ghi hàng loạt vào `match_events` khi trận hoàn thành. Mỗi event gồm người bấm, số đã bấm, target tại thời điểm đó, đúng/sai, request ID, thứ tự và thời gian server nhận. Request ID trùng của cùng người chơi trả lại kết quả cũ và không tạo event hay cộng điểm lần hai.

Từ audit log, server cộng dồn tổng lượt đúng/sai và thời gian phản ứng cho từng người chơi. Thời gian phản ứng của một lượt đúng được tính từ lúc target đó xuất hiện trên server đến lúc server nhận lượt chọn đúng. Hồ sơ hiển thị tỷ lệ chính xác, tổng đúng/sai và thời gian phản ứng trung bình; số liệu client tự khai báo không được sử dụng.

Màn hình **Bảng xếp hạng** hiển thị tối đa 100 người chơi có trận hoàn thành. Thứ tự ưu tiên Elo, số trận thắng, tỷ lệ thắng, điểm cao nhất rồi thời điểm cập nhật; người chơi hiện tại vẫn nhận được thứ hạng cá nhân kể cả khi nằm ngoài top 100.

Mỗi người chơi bắt đầu với **1000 Elo**. Sau mỗi trận hai người, server dùng công thức Elo với K=32 để cộng/trừ dựa trên kết quả và chênh lệch rating; hòa cũng có thể tăng hoặc giảm nếu rating hai bên khác nhau. Rating tối thiểu là 100. Mọi thay đổi được lưu trong `rating_history`, hiển thị ở lịch sử trận và bảng xếp hạng ưu tiên Elo trước các tiêu chí phụ.

Server tự xét và lưu thành tích, không dựa vào dữ liệu client. Bộ thành tích đầu tiên gồm: chiến thắng đầu tiên, 10 chiến thắng, chuỗi thắng 5, có lượt đúng và không bấm sai trong cả trận, và tự chọn đủ 50 số trong tối đa 30 giây. Khóa chính `(user_id, achievement_code)` bảo đảm mỗi thành tích chỉ được mở một lần.

Kiểm tra server:

```text
http://localhost:8080/health
```

Kết quả mong đợi là `OK`.

## Địa chỉ client

- Android flavor `dev`: `ws://127.0.0.1:8080/game` qua `adb reverse`
- iOS Debug: `ws://127.0.0.1:8080/game`

Android truyền URL từ `BuildConfig` vào shared module. iOS đọc URL từ `Info.plist` rồi truyền vào shared module.

Khi không dùng `start-dev-server.cmd`, cần tự cấu hình từng emulator/thiết bị:

```bat
adb -s emulator-5554 reverse tcp:8080 tcp:8080
adb -s emulator-5556 reverse tcp:8080 tcp:8080
```

Để thử Android qua Wi-Fi mà không dùng cáp/ADB, truyền địa chỉ IPv4 LAN của máy chạy server:

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
$env:DATABASE_URL="jdbc:postgresql://database-host:5432/fasttowin"
$env:DATABASE_USER="fasttowin_app"
$env:DATABASE_PASSWORD="mat-khau-bi-mat"
.\gradlew.bat :server:run
```

Giá trị `configure-production-server.invalid` chỉ là placeholder an toàn và không thể kết nối. Cần thay URL trước khi phát hành.

## Test

```powershell
.\gradlew.bat :server:test
.\gradlew.bat :app:compileDevDebugKotlin
```

Để chạy thêm integration test với PostgreSQL development:

```powershell
$env:TEST_DATABASE_URL="jdbc:postgresql://localhost:5432/fasttowin"
$env:TEST_DATABASE_USER="fasttowin"
$env:TEST_DATABASE_PASSWORD="fasttowin"
.\gradlew.bat :server:test --rerun-tasks
```

Test backend bao gồm:

- Khôi phục đúng guest session bằng resume token.
- Từ chối mật khẩu phòng sai.
- Hai người chọn cùng một target đồng thời nhưng server chỉ chấp nhận một lượt.

## Giới hạn của MVP

- Phòng và trạng thái trận đấu vẫn bị mất khi server restart.
- Guest identity và session tồn tại qua restart khi bật PostgreSQL.
- Chưa có tài khoản email/Google/Apple, JWT và lịch sử trận.
- Cấu hình local dùng `ws://`; môi trường production phải dùng `wss://`.

## Reconnect hiện tại

- Resume token được lưu bằng SharedPreferences trên Android và NSUserDefaults trên iOS.
- Client tự kết nối lại với exponential backoff tối đa 15 giây giữa các lần thử.
- Server giữ phòng trong 30 giây sau khi người chơi mất kết nối.
- Reconnect trong thời gian này nhận lại cùng player ID và snapshot trận đấu.
- Quá 30 giây, server đóng phòng và thông báo cho người chơi còn lại.
- Guest session không ở trong phòng được dọn sau 5 phút offline.
