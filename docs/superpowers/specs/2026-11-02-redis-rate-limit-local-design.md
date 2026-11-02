# Redis rate limit — giai đoạn local đầu tiên

Issue: [#12](https://github.com/ThaiXuanHien/FastToWin/issues/12). Ngày: 02/11/2026.

Trạng thái: **Owner đã duyệt “Duyệt, triển khai local” ngày 02/11/2026**. Owner đã chọn “Rate limit trước”; presence và nhiều backend production nằm ngoài giai đoạn này.

## Mục tiêu và hiện trạng

Hai backend local phải cùng tiêu thụ một hạn mức ở các điểm vào được chọn. Giữ nguyên ngưỡng và thuật toán token bucket hiện có. Mặc định vẫn dùng bộ nhớ; không cần Redis nếu chưa bật cấu hình riêng.

Source hiện có:

- `RateLimiter.kt`: interface `consume(bucket, key, policy)`, token bucket trong RAM, key đã băm và thời hạn dọn bucket 15 phút.
- `Application.kt`: dùng cùng limiter cho đăng nhập, xác minh email, phòng và WebSocket. Các giới hạn message chung hiện có thể đóng socket khi bị vượt mức.
- `AdminService.kt`: tự tạo limiter riêng nếu không được truyền vào; cần nối đúng limiter khi chọn Redis.
- `Main.kt`: chưa có cấu hình/client Redis hoặc vòng đời giải phóng tài nguyên Redis.

Do đó không thay toàn bộ limiter bằng Redis trong một lần: lỗi Redis ở lớp kiểm tra từng frame có thể làm gián đoạn trận đang chạy.

## Các cách cân nhắc

| Cách | Đánh đổi | Quyết định đề xuất |
| --- | --- | --- |
| Redis cho các điểm vào, RAM cho giới hạn trong trận | Phạm vi rõ, giảm ảnh hưởng của lỗi Redis; mới hoàn tất một phần #12 | Chọn cho giai đoạn này |
| Redis cho mọi lời gọi `consume` | Dùng chung toàn bộ hạn mức, nhưng đưa Redis vào đường xử lý từng frame/chọn số và nhiều luồng mua/nhận thưởng | Để giai đoạn sau, cần thiết kế availability riêng |
| Chỉ giới hạn ở proxy | Chặn theo IP thuận tiện, không thay được giới hạn theo tài khoản/người chơi/thao tác trong WebSocket | Không thay thế yêu cầu #12 |

## 1. Phạm vi dùng chung

Thêm implementation Redis và bộ chọn theo **danh sách bucket cố định** sau. Không nhận tên bucket từ client.

| Nhóm | Bucket dùng Redis khi bật |
| --- | --- |
| Đăng nhập người chơi | `login-ip`, `login-account` |
| Yêu cầu/nhập mã khôi phục mật khẩu | `password-reset-ip`, `password-reset-account`, `password-reset-confirm-ip`, `password-reset-confirm-account` |
| Yêu cầu/nhập mã xác minh email | `email-verification-request-ip`, `email-verification-request-account`, `email-verification-confirm-ip`, `email-verification-confirm-account` |
| Khởi tạo phiên WebSocket | `websocket-connect-ip` |
| Tạo phòng | `create-room-player`, `create-room-ip` |
| Tham gia phòng | `join-room-player`, `join-room-ip`, `join-room-ip-room` |
| Đăng nhập admin | `admin-login-ip`, `admin-login-account` |

Tổng cộng 18 bucket. Thứ tự kiểm tra nhiều bucket và việc dừng ở lần từ chối đầu tiên giữ nguyên. Không coi việc tiêu thụ nhiều bucket là một transaction mới; bucket trước có thể đã bị trừ khi bucket sau từ chối, như hành vi hiện tại.

`websocket-message-ip`, `websocket-message-player`, chọn số, avatar, báo cáo, Billing và AdMob tiếp tục dùng limiter RAM hiện có trong giai đoạn này. Đây là phân vùng đã thiết kế, không phải tự chuyển sang RAM khi Redis hỏng. Không tuyên bố mọi rate limit đã dùng chung hoặc #12 hoàn tất.

## 2. Cấu hình và vòng đời

- `FASTTOWIN_RATE_LIMIT_STORE`: `memory` hoặc `redis`; không đặt thì `memory`. Giá trị lạ/rỗng được đặt tường minh phải bị từ chối, không tự sửa thành mặc định.
- `FASTTOWIN_RATE_LIMIT_REDIS_URL` hoặc biến `_FILE` tương ứng: bắt buộc khi chọn Redis, dùng quy ước đọc secret của repo. Không log URI, username/password, nội dung tệp hoặc exception có thể chứa URI.
- `FASTTOWIN_RATE_LIMIT_NAMESPACE`: bắt buộc với Redis; 1–48 ký tự ASCII thường/số/`_`/`-`, bắt đầu bằng chữ hoặc số. Hai backend cùng môi trường phải dùng cùng namespace; mỗi lần test có namespace riêng.
- Timeout lệnh 200 ms; timeout kết nối/startup tối đa 2 giây; tối đa 128 thao tác Redis đang chờ cho mỗi backend. Đầy hàng chờ trả unavailable ngay, không chờ vô hạn.
- Chỉ tạo client Redis khi chọn `redis`; startup phải kết nối/PING thành công. Sai cấu hình hoặc không kết nối được thì startup thất bại rõ ràng, không chạy bằng RAM thay thế.
- Một client/kết nối chia sẻ cho mỗi backend; dùng async API và chờ coroutine có thể hủy. Đóng kết nối/client khi dừng hoặc startup thất bại, không để thread hay kết nối rò rỉ.
- Hỗ trợ `redis://` và `rediss://`; nghiệm thu ban đầu chỉ dùng loopback local. TLS giữ xác minh chứng chỉ mặc định. Chưa chọn transport/provider/budget production.

Client đề xuất: `io.lettuce:lettuce-core:7.9.0.RELEASE`, bản release chính thức đã đối chiếu, hỗ trợ Java 8 trở lên và Redis 8.x. Trước khi giữ dependency phải kiểm tra resolved dependencies/Netty với Ktor hiện tại; không ép phiên bản Netty hoặc nâng Ktor để chữa xung đột ngoài phạm vi. Nếu không tương thích, dừng và đề xuất lại dependency. [Release Lettuce](https://github.com/redis/lettuce/releases/tag/7.9.0.RELEASE).

Lettuce có thể giữ/replay lệnh qua reconnect theo mặc định. Cấu hình từ chối lệnh khi disconnect, giới hạn hàng chờ và không replay các lệnh đã dispatch. Cho phép nối lại kết nối, nhưng không tự thử lại `consume` hoặc nghiệp vụ đăng nhập/tạo phòng. [Tài liệu client options](https://redis.github.io/lettuce/advanced-usage/client-options/).

## 3. Dữ liệu và tính nguyên tử

Key dạng `ftw:<namespace>:rl:v1:<bucket>:<digest>`. `digest` là SHA-256 của key nội bộ caller truyền vào; không ghi identifier gốc vào Redis. Việc băm lại key đã băm là cố định và giống nhau giữa các instance, không thay đổi kết quả phân vùng hạn mức.

Mỗi key là một Redis hash chứa token còn lại, thời điểm refill, capacity và refill window. Một Lua script ngắn thực hiện đọc → refill → quyết định → ghi → đặt TTL trong **một lệnh EVAL**. Dùng Redis TIME cho đồng hồ chung; khi thời gian đi lùi, giữ mốc refill gần nhất để không tạo thêm token từ lần điều chỉnh lùi đó. Redis bảo đảm script chạy nguyên tử; không dùng chuỗi GET/SET tách rời. [Redis scripting](https://redis.io/docs/latest/develop/programmability/eval-intro/).

- Capacity ban đầu bằng ngưỡng hiện có; refill liên tục như `InMemoryRateLimiter`, không đổi sang fixed window.
- Retry tính từ số token thiếu, làm tròn lên millisecond. HTTP vẫn làm tròn lên giây như hiện tại.
- Mỗi lần consume làm mới TTL bằng `max(15 phút, refillWindowMillis)`; không cần job SCAN/dọn key nền.
- Redis adapter chỉ chấp nhận capacity 1–1.000.000 và refill window 1 ms–24 giờ; các policy hiện có đều nằm trong miền này. Kiểm tra trước khi gửi lệnh.
- Nếu key đã có cấu hình capacity/window khác với caller thì từ chối bằng lỗi unavailable/policy mismatch; không reset key để cấp thêm token. Thay đổi policy trong rollout sau cần kế hoạch chuyển đổi riêng.
- Trạng thái Redis sai kiểu/malformed hoặc phản hồi script sai định dạng là unavailable; không ghi đè bằng bucket mới.
- Script kiểm tra kiểu dữ liệu/policy trước khi ghi. Tính nguyên tử của Lua không phải cam kết rollback nếu script lỗi giữa chừng; lỗi hoặc mất phản hồi được coi là kết quả ghi chưa xác định, không tự thử lại hoặc hoàn token. Phải kiểm tra key được tạo có TTL trong bài test thực.
- Không tự xóa key hoặc FLUSHDB khi ứng dụng chạy. Fixture test chỉ dùng Redis cô lập do bài test tạo.

## 4. Redis lỗi và tương thích client

Phân biệt hai kết quả:

1. **Vượt hạn mức thật:** giữ `RATE_LIMITED`, HTTP 429/Retry-After và hành vi WebSocket hiện tại.
2. **Không xác định được hạn mức do Redis:** trả lỗi tạm thời, không gán thành “thao tác quá nhanh” và không cho nghiệp vụ đi tiếp.

Đường HTTP trả 503 với code `RATE_LIMIT_UNAVAILABLE`, dùng `TextKey.ServerUnavailable` hiện có, không tiết lộ chi tiết Redis. Đăng nhập admin chuyển lỗi này qua `AdminException` 503/response hiện có, không biến thành 500 hoặc 401.

Đường WebSocket gửi `ServerMessage.Error` cùng code và `ServerUnavailable`. Redis lỗi khi ConnectGuest/ConnectAccount: không tạo phiên chơi mới và đóng socket chưa xác thực bằng lỗi tạm thời; không dùng close reason vi phạm chính sách. Redis lỗi khi CreateRoom/JoinRoom: từ chối thao tác trước khi gọi engine, giữ socket đã đăng nhập hoạt động. Không tự đăng xuất, thu hồi token, đóng phòng hoặc thay đổi ví/Elo/kết quả trận.

Hủy coroutine phải được truyền tiếp; không nuốt CancellationException thành lỗi Redis. Lệnh timeout có thể đã trừ token nhưng mất phản hồi: không chạy nghiệp vụ và không thử lại lệnh đó. Chấp nhận tiêu thụ hạn mức bảo thủ; không hứa bảo đảm exactly-once cho token bucket.

Trận đang chạy trên một backend vẫn dùng các limiter RAM hiện tại cho frame/chọn số. Kết quả/ví vẫn theo cơ chế nguyên tử hiện có. Không sửa protocol version, payload, UI hoặc bản dịch; lỗi mới dùng cấu trúc/error key đã có.

## 5. Quan sát và giới hạn tài nguyên

Thêm vào metrics nội bộ hiện có:

- `fasttowin_rate_limit_shared_decisions_total{outcome="allowed|limited|unavailable"}`.
- `fasttowin_rate_limit_shared_failures_total{reason="timeout|disconnected|policy_mismatch|invalid_response|overloaded|other"}`.

Label hữu hạn, không chứa IP, email, user ID, key Redis, namespace hoặc credential. Không mở `/internal/metrics` ra Internet. Không đổi `/health` thành lỗi chỉ vì Redis runtime bị gián đoạn: liveness không được tự restart backend và làm mất trận đang chơi. Chưa triển khai bộ monitoring mới hay gửi thông báo ra ngoài.

Fixture Redis local chạy riêng, cổng chỉ bind `127.0.0.1`, `maxmemory` 64 MB và `noeviction`. Thiếu bộ nhớ phải được báo unavailable, không đẩy bớt bucket còn hiệu lực để cấp lại hạn mức. Không đụng Redis/DB đang có trên máy hoặc dữ liệu production.

Redis fixture đề xuất bản 8.10.3 theo release chính thức; xác minh và pin image chính thức/tag hoặc digest trước khi khởi tạo fixture. Không dùng `latest`. [Redis release](https://github.com/redis/redis/releases/tag/8.10.3).

Nếu Redis restart mà không giữ dữ liệu, hạn mức được khởi tạo lại. Bài test phải ghi rõ hành vi này; giai đoạn local không chứng minh persistence/HA. Chính sách AOF/backup/noeviction/quota production cần duyệt cùng provider trước rollout thực tế.

## 6. Nghiệm thu local

Kiểm thử qua interface và Redis thật, không chỉ assert source/script text:

1. Mặc định RAM không mở Redis; cấu hình sai/thiếu URL/namespace bị từ chối và không in secret.
2. Hai limiter, hai kết nối Redis riêng, cùng namespace/key: tổng số lần allowed không vượt capacity khi gửi đồng thời; policy/key/namespace khác được tách đúng.
3. Refill liên tục, retry và TTL có hành vi đúng; không cấp lại burst trước hạn. Kiểm tra policy mismatch và dữ liệu hỏng không biến thành allowed.
4. Hai Ktor application local dùng chung Redis: login và tạo/tham gia phòng nhận cùng hạn mức. Đây chỉ là bằng chứng rate limit, không chứng minh phòng/trận đã chạy phân tán.
5. HTTP trả 429 khi hết token, 503 khi Redis unavailable; không tạo session hoặc thực thi nghiệp vụ bị chặn.
6. WebSocket phân biệt lỗi tạm thời và vượt hạn mức. Create/Join gặp lỗi Redis không đóng socket đang xác thực; trận đang chạy vẫn nhận thao tác hợp lệ qua limiter local.
7. Ngắt Redis, mất phản hồi sau khi ghi, timeout, hủy coroutine và quá số thao tác chờ: không fallback RAM/replay request; hồi phục có kiểm soát và giải phóng tài nguyên.
8. Admin login dùng đúng bộ chọn limiter, trả 503 rõ ràng khi Redis lỗi; bảo toàn kiểm thử phân quyền/session hiện có.
9. Metrics hữu hạn và không chứa identifier/secret; regressions cho memory limiter và các luồng HTTP/WebSocket liên quan đều đạt.

Redis thật là điều kiện để kết luận integration test đạt. Nếu Docker/local runtime chưa sẵn sàng thì báo thiếu bằng chứng, không biến skip hoặc mock thành nghiệm thu. Không tự chạy Samsung/emulator, bật staging, kiểm tra/chạy CI hoặc deploy.

## Sau giai đoạn này

Giữ #12 mở cho presence, các rate limit còn local, nghiệm thu môi trường thật và quyết định provider/budget. #15 vẫn chưa sẵn sàng rollout: phòng/trận, ownership, reconnect và settlement vẫn chưa được nghiệm thu nhiều instance. Production tiếp tục một backend; AdMob/UGC/Billing flags giữ nguyên.
