# Fast To Win backend MVP

Backend hiện tại là Ktor WebSocket server chạy JVM. Server là nơi duy nhất tạo phòng, xác minh mật khẩu, sinh bàn 50 số, kiểm tra lượt bấm và tính điểm. Khi bật PostgreSQL, guest identity, resume session và trạng thái phòng/trận đang diễn ra đều được lưu để khôi phục sau khi server restart.

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

Flyway tự tạo các bảng tài khoản, hồ sơ, phiên đăng nhập, kết quả trận, thống kê, xã hội, thành tích, nhiệm vụ, mùa giải và `active_room_snapshots`. Resume token chỉ được lưu dưới dạng SHA-256 hash, không lưu token gốc. Migration `V13` bổ sung XP/cấp độ, vật phẩm trang trí, nhiệm vụ và Elo theo mùa; `V14` bổ sung thông báo tài khoản và lời mời phòng bền vững; `V15` làm rõ điều kiện thành tích; `V16` bổ sung điểm danh hằng ngày và chuỗi điểm danh; `V17` bổ sung các mốc thưởng điểm danh; `V19` bổ sung phần thưởng nhiệm vụ; `V20` bổ sung loại trận thường/xếp hạng, năm trận phân hạng và Elo cao nhất mùa.

Để tạo lại tài khoản kiểm thử có đầy đủ dữ liệu trên PostgreSQL development, chạy
`.\seed-dev-full-account.cmd` trên Windows hoặc `./seed-dev-full-account.sh` trên macOS.
Seeder bị khóa ở môi trường `dev`, có tính idempotent và không được dùng cho production.

Khi trận kết thúc, backend lưu kết quả đúng một lần theo `roomId`, gồm điểm từng người, thắng/thua/hòa, tổng số trận, điểm cao nhất và chuỗi thắng. Mỗi thay đổi của một phòng được xử lý trong bộ nhớ rồi UPSERT đúng snapshot của phòng đó vào PostgreSQL; server không ghi lại toàn bộ danh sách phòng sau mỗi lượt bấm.

Từ màn hình **Hồ sơ**, người chơi có thể xem mã người chơi, tổng trận, thắng/thua/hòa, điểm cao nhất, chuỗi thắng, biểu đồ phong độ và tối đa 20 trận gần nhất. Mỗi trận có màn chi tiết cùng bản phát lại dựa trên event do server lưu. Dữ liệu được lấy qua WebSocket của phiên hiện tại nên client không thể yêu cầu hồ sơ riêng tư của player ID khác.

Phòng có thể đặt công khai hoặc riêng tư bằng mật khẩu. Sau khi tham gia, hai người phải xác nhận sẵn sàng; chủ phòng có thể mời người chơi còn lại ra khỏi phòng. Danh sách phòng hỗ trợ tìm kiếm, lọc chế độ chơi, tự làm mới và hiển thị chất lượng kết nối.

Ghép trận trực tuyến dành cho tài khoản đã đăng nhập và tách riêng hàng chờ thường/xếp hạng. Trận thường không đổi Elo. Hàng chờ xếp hạng ưu tiên đối thủ cùng chế độ và Elo gần nhất, bắt đầu ở khoảng ±100 Elo rồi mở rộng thêm 50 sau mỗi 10 giây, tối đa ±300; cặp người chơi đã chặn nhau không được ghép. Trận được tạo và bắt đầu hoàn toàn từ server.

Với chế độ **Đua 60 giây**, đồng hồ kết thúc do backend quyết định. Server kiểm tra timer mỗi 250 ms, phát `game_finished` cho cả hai người chơi và lưu kết quả đúng một lần; client chỉ hiển thị đồng hồ, không tự quyết định kết quả trận.

Backend giữ audit log tối đa 2.000 request cho mỗi trận và ghi hàng loạt vào `match_events` khi trận hoàn thành. Mỗi event gồm người bấm, số đã bấm, target tại thời điểm đó, đúng/sai, request ID, thứ tự và thời gian server nhận. Request ID trùng của cùng người chơi trả lại kết quả cũ và không tạo event hay cộng điểm lần hai.

## Rate limiting

Backend dùng token bucket trong bộ nhớ và khóa định danh SHA-256, không giữ email/IP dạng gốc trong bucket. Giới hạn mặc định:

| Thao tác | Giới hạn |
|---|---|
| Đăng nhập theo IP | 20 lần/phút |
| Đăng nhập theo email chuẩn hóa | 8 lần/5 phút |
| Khởi tạo WebSocket theo IP | 60 lần/phút |
| Mọi message WebSocket theo IP | 300 lần/giây |
| Mọi message WebSocket theo người chơi | 120 lần/giây |
| Tạo phòng | 5 lần/phút/người chơi và 20 lần/phút/IP |
| Tham gia phòng | 12 lần/phút/người chơi, 60 lần/phút/IP và 20 lần/phút cho mỗi cặp IP-phòng |
| Chọn số | 20 lần/giây/người chơi và 80 lần/giây/IP |

HTTP trả `429 Too Many Requests`, mã `RATE_LIMITED` và header `Retry-After`. Create/join/select bị giới hạn sẽ nhận WebSocket error `RATE_LIMITED` nhưng vẫn giữ kết nối; burst vượt giới hạn tổng message sẽ nhận lỗi rồi bị đóng socket. Token được hồi liên tục nên người chơi có thể thử lại sau thời gian được thông báo.

Limiter hiện phù hợp một tiến trình backend. Khi chạy nhiều instance phải thay implementation bằng Redis và bổ sung rate limit tại reverse proxy/API gateway. Backend hiện lấy IP trực tiếp từ socket; khi đặt sau proxy cần giữ backend trong mạng riêng và cấu hình xử lý forwarded header đáng tin cậy, không tin `X-Forwarded-For` trực tiếp từ Internet.

Từ audit log, server cộng dồn tổng lượt đúng/sai và thời gian phản ứng cho từng người chơi. Thời gian phản ứng của một lượt đúng được tính từ lúc target đó xuất hiện trên server đến lúc server nhận lượt chọn đúng. Hồ sơ hiển thị tỷ lệ chính xác, tổng đúng/sai và thời gian phản ứng trung bình; số liệu client tự khai báo không được sử dụng.

Màn hình **Bảng xếp hạng** có hai tab mùa hiện tại và toàn thời gian, hiển thị tối đa 100 người chơi có trận hoàn thành. Thứ tự ưu tiên Elo, số trận thắng, tỷ lệ thắng, điểm cao nhất rồi thời điểm cập nhật; người chơi hiện tại vẫn nhận được thứ hạng cá nhân kể cả khi nằm ngoài top 100.

Mỗi người chơi bắt đầu với **1000 Elo**. Sau mỗi trận hai người, server dùng công thức Elo với K=32 để cộng/trừ dựa trên kết quả và chênh lệch rating; hòa cũng có thể tăng hoặc giảm nếu rating hai bên khác nhau. Rating tối thiểu là 100. Mọi thay đổi được lưu trong `rating_history`, hiển thị ở lịch sử trận và bảng xếp hạng ưu tiên Elo trước các tiêu chí phụ. Hồ sơ progression còn tổng hợp tối đa 50 mùa đã kết thúc từ `season_ratings`, `season_leaderboard_archive` và `season_reward_claims`, không cần nhân bản thêm dữ liệu lịch sử.

Server tự xét và lưu thành tích, không dựa vào dữ liệu client. Bộ thành tích đầu tiên gồm: chiến thắng đầu tiên, 10 chiến thắng, chuỗi thắng 5, có lượt đúng và không bấm sai trong cả trận, và tự chọn đủ 50 số trong tối đa 30 giây. Khóa chính `(user_id, achievement_code)` bảo đảm mỗi thành tích chỉ được mở một lần.

Mỗi trận cấp XP và vàng theo kết quả. Hồ sơ hiển thị cấp độ, tiến trình XP, nhiệm vụ ngày/tuần và vật phẩm đã mở khóa. Người chơi có thể trang bị khung avatar hoặc danh hiệu; backend kiểm tra vật phẩm đã mở trước khi lưu. Mùa khởi đầu được tạo bởi migration `V13`; từ migration `V33`, backend tự chốt bảng xếp hạng, tạo mùa mới ba tháng một lần và đưa Elo mùa về mốc 1.000 mà không thay đổi Elo toàn thời gian. Migration `V35` bổ sung ngoại trang riêng theo từng mùa: bậc Đồng/Bạc nhận danh hiệu, từ Vàng trở lên nhận khung theo bậc; việc cấp tiền và ngoại trang dùng cùng transaction và không thể nhận trùng. Migration `V36` lưu thời điểm người chơi đã xem tổng kết mùa, nhờ đó dialog chỉ xuất hiện một lần và đồng bộ trạng thái giữa các thiết bị.

Âm thanh, rung, chủ đề, màu bàn số, cỡ chữ, hướng dẫn lần đầu và chế độ luyện tập offline được lưu cục bộ trên thiết bị. Luyện tập không gửi kết quả lên backend và không ảnh hưởng Elo.

Trung tâm **Thông báo** của tài khoản được lưu trong PostgreSQL và đồng bộ trạng thái đã đọc/xóa qua WebSocket giữa các thiết bị. Server tạo thông báo lời mời kết bạn/phòng; client so sánh hai lần tải hồ sơ liên tiếp để phát hiện nhiệm vụ, thành tích hoặc vật phẩm vừa mở rồi đồng bộ idempotent lên server. Lần tải hồ sơ đầu tiên chỉ tạo mốc nên không báo lại toàn bộ phần thưởng cũ. Guest vẫn dùng thông báo theo phiên ứng dụng. Đây chưa phải push notification của hệ điều hành.

## API tài khoản email

Backend hỗ trợ tài khoản email/mật khẩu qua JSON API:

| Phương thức | Endpoint | Công dụng |
|---|---|---|
| `POST` | `/auth/register` | Tạo tài khoản và phiên đăng nhập |
| `POST` | `/auth/login` | Đăng nhập trên một thiết bị |
| `POST` | `/auth/upgrade-guest` | Chuyển guest hiện tại thành tài khoản email |
| `POST` | `/auth/refresh` | Xoay vòng refresh token và cấp access token mới |
| `POST` | `/auth/logout` | Thu hồi phiên của thiết bị hiện tại |
| `POST` | `/auth/sessions` | Liệt kê các phiên đăng nhập còn hoạt động |
| `POST` | `/auth/sessions/revoke` | Thu hồi một phiên thuộc tài khoản hiện tại |
| `POST` | `/auth/sessions/revoke-all` | Thu hồi toàn bộ phiên của tài khoản |

Ví dụ đăng ký:

```json
{
  "email": "player@example.com",
  "password": "mat-khau-toi-thieu-8-ky-tu",
  "displayName": "Người chơi",
  "devicePlatform": "android"
}
```

Access token có hiệu lực 15 phút, refresh token có hiệu lực 30 ngày. Token gốc không được lưu trong database; server chỉ lưu SHA-256 hash. Refresh token được thay mới sau mỗi lần sử dụng và token cũ lập tức mất hiệu lực. Mật khẩu được dẫn xuất bằng PBKDF2-HMAC-SHA256 với salt ngẫu nhiên riêng cho từng tài khoản.

Compose Multiplatform có màn hình đăng nhập, đăng ký và lựa chọn chơi khách. Android mã hóa phiên bằng AES-GCM với khóa trong Android Keystore; iOS lưu phiên trong Keychain. Ứng dụng tự refresh access token trước khi hết hạn và tài khoản dùng `connect_account` để xác thực WebSocket. Server tự lấy player ID và biệt danh từ phiên đăng nhập, không nhận các giá trị này từ client. Chế độ khách vẫn dùng `connect_guest` và resume token cũ.

Trong màn **Hồ sơ**, tài khoản có thể đổi biệt danh/avatar và mở phần **Bảo mật** để đổi mật khẩu. Đổi mật khẩu thành công thu hồi mọi phiên hiện có và đưa ứng dụng về màn đăng nhập. Luồng **Quên mật khẩu** khóa email sau khi gửi yêu cầu, nhận mã có hiệu lực 15 phút và đặt mật khẩu mới; ở môi trường dev mã được hiển thị và tự điền, còn production vẫn chờ tích hợp dịch vụ email theo giới hạn bên dưới. Client và backend cùng áp dụng giới hạn mật khẩu từ 8 đến 128 ký tự.

Một tài khoản có thể giữ phiên đăng nhập HTTP trên nhiều thiết bị, nhưng chỉ có một kết nối game WebSocket hoạt động tại một thời điểm. Khi thiết bị mới kết nối cùng tài khoản, server đóng WebSocket cũ với lý do `Session resumed elsewhere`; kết nối mới giữ nguyên player ID và snapshot trận hiện tại. Thiết bị cũ nhận thông báo `SESSION_REPLACED` và dừng tự reconnect để hai máy không liên tục giành kết nối của nhau. Việc thay thế socket không đánh dấu người chơi offline và không làm mất phòng.

Màn **Thiết bị** trong Hồ sơ liệt kê nền tảng, thời gian hoạt động, hạn phiên và đánh dấu thiết bị hiện tại. Người chơi có thể đăng xuất riêng một thiết bị hoặc tất cả thiết bị. Backend luôn kiểm tra session ID thuộc đúng user từ access token; không thể dùng ID đã biết để thu hồi phiên của tài khoản khác. Thu hồi thiết bị hiện tại hoặc toàn bộ phiên đưa app về màn đăng nhập; WebSocket dùng token đã bị thu hồi sẽ bị đóng ở thao tác kế tiếp.

Protocol WebSocket hiện tại là phiên bản 21. Sau khi cập nhật ứng dụng, cần khởi động lại backend để client và server dùng cùng phiên bản.

Sau trận, snapshot từ server chứa thời lượng, số lượt đúng/sai và thời gian phản ứng trung bình của từng người chơi để cả tài khoản lẫn khách đều xem được tóm tắt ngay. Yêu cầu đấu lại có thời hạn 30 giây và hỗ trợ chấp nhận, từ chối hoặc hủy; server là bên quyết định hết hạn và đồng bộ trạng thái cho cả hai máy. Người dùng đã đăng nhập có thể kết bạn, chấp nhận lời mời đang chờ hoặc chặn đối thủ ngay trên màn kết quả; thao tác chặn đồng thời rời phòng hiện tại.

Khi nâng cấp guest, client gửi resume token hiện tại cùng email và mật khẩu. Backend khóa phiên guest và cập nhật chính user hiện tại trong một transaction, sau đó thu hồi toàn bộ resume token khách và tạo phiên tài khoản mới. `user_id` không đổi nên hồ sơ, player code, Elo, lịch sử, thống kê và thành tích được giữ nguyên. Chức năng này yêu cầu PostgreSQL; server chạy thuần bộ nhớ trả lỗi `DATABASE_REQUIRED`.

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
- Khôi phục phòng, bàn số, target, điểm và request ID qua hai lần khởi động Ktor application với hai WebSocket client thật.
- Ghi/đọc snapshot phòng qua PostgreSQL và Flyway migration.
- Rate limit đăng nhập theo IP/email; rate limit WebSocket tổng và riêng cho create/join/select.
- Từ chối mật khẩu phòng sai.
- Hai người chọn cùng một target đồng thời nhưng server chỉ chấp nhận một lượt.
- Hai người phải cùng sẵn sàng trước khi phòng thủ công bắt đầu và có thể đấu lại bằng đồng thuận.
- Ghép nhanh theo khoảng Elo, đồng thời loại cặp người chơi đã chặn nhau.
- Lưu XP, nhiệm vụ, rating mùa, chi tiết event và trang bị vật phẩm sau trận.
- Đối thủ vẫn có thể tiếp tục lượt trong thời gian một máy mất mạng; máy đó resume đúng target và bàn số mới nhất trong thời gian gia hạn.
- Hủy ghép nhanh hoặc mất kết nối sẽ xóa người chơi khỏi hàng chờ, không tạo trận với người chơi “ma”.
- Phiếu đấu lại và trạng thái trận được khôi phục đúng sau khi backend restart.
- Rời phòng chủ động đóng phòng cho cả hai phía và xóa snapshot đã lưu.
- Kết nối cùng tài khoản từ thiết bị mới thay thế socket cũ mà không làm phiên mới bị đánh dấu offline.
- Liệt kê/thu hồi session chỉ tác động đúng tài khoản, refresh token của thiết bị bị đăng xuất lập tức mất hiệu lực.

## Giới hạn của MVP

- Phòng, trận đấu, guest identity và session tồn tại qua restart khi bật PostgreSQL; chế độ chạy thuần bộ nhớ vẫn mất dữ liệu này.
- Lời mời bạn bè vào phòng của tài khoản được lưu với thời hạn trong PostgreSQL và khôi phục cùng phòng sau khi server restart; chế độ chạy thuần bộ nhớ vẫn mất dữ liệu này.
- Snapshot hiện dành cho một tiến trình backend; khi chạy nhiều instance cần chuyển trạng thái realtime sang Redis hoặc kho trạng thái phân tán.
- Rate limit hiện nằm trong bộ nhớ từng tiến trình và được làm mới khi server restart; nhiều instance cần dùng Redis.
- Chưa tích hợp dịch vụ email để gửi mã khôi phục và xác minh email; chưa có đăng xuất khỏi tất cả thiết bị.
- Theo dõi log tác vụ vòng đời mùa; backend kiểm tra mỗi phút, đóng băng bảng xếp hạng mùa cũ và tự tạo mùa kế tiếp.
- Cấu hình local dùng `ws://`; môi trường production phải dùng `wss://`.

## Reconnect hiện tại

- Resume token được lưu bằng SharedPreferences trên Android và NSUserDefaults trên iOS.
- Client tự kết nối lại với exponential backoff tối đa 15 giây giữa các lần thử.
- Server giữ phòng trong 30 giây sau khi người chơi mất kết nối.
- Sau khi server restart với PostgreSQL, người chơi trong phòng có 30 giây để kết nối lại kể từ lúc server khởi động xong.
- Reconnect trong thời gian này nhận lại cùng player ID và snapshot trận đấu.
- Quá 30 giây, server đóng phòng và thông báo cho người chơi còn lại.
- Guest session không ở trong phòng được dọn sau 5 phút offline.
