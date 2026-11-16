# Runbook nâng cấp hạ tầng (checklist 30/10/2026)

Tài liệu này hướng dẫn các việc trong `system-upgrade-checklist.xlsx` và `admin-upgrade-checklist.xlsx` cần thao tác trên dashboard (mua dịch vụ, DNS, Railway, Cloudflare, Brevo) và không thể tự động hóa bằng code. Những việc làm được bằng code đã có trong các PR riêng; phần **Đã có trong code** ở mỗi mục chỉ tới PR tương ứng.

> Mọi bước dưới đây đều thay đổi hệ thống thật hoặc phát sinh chi phí. Hãy thử trên staging trước, ghi lại thời điểm thực hiện và cập nhật cột *Trạng thái* trong file checklist.

## Thứ tự đề xuất

| Bước | Việc | Phụ thuộc | Cần phát hành app? |
| --- | --- | --- | --- |
| 1 | INF-03 Backup tự động Postgres | – | Không |
| 2 | SEC-01 Xác minh IP thật trên staging | PR1 đã deploy | Không |
| 3 | SEC-11 Cảnh báo chi phí mềm | – | Không |
| 4 | INF-01 Mua domain + DNS trên Cloudflare | – | – |
| 5 | EML-02 SPF / DKIM / DMARC | INF-01 | Không |
| 6 | SEC-05 Bật proxy Cloudflare | INF-01 | Không |
| 7 | SEC-06, SEC-10 Rule rate limit và ngoại lệ callback | SEC-05 | Không |
| 8 | APP-01 Đổi `FASTTOWIN_PUBLIC_HOST`, phát hành app | INF-01 | **Có** |
| 9 | SEC-08 Chặn đi vòng qua Cloudflare | Phần lớn người dùng đã cập nhật app | Không |
| 10 | ADM-03 Console admin ở domain con + Cloudflare Access | INF-01, SEC-05 | Không |
| 11 | INF-02 Chuyển region sang Singapore | INF-03 | Không |
| 12 | INF-04 Giám sát và cảnh báo | – | Không |
| 13 | EML-01 Nâng Brevo trước khi mở đăng ký công khai | – | Không |

---

## INF-03 – Backup tự động Postgres

1. Railway → service Postgres → **Backups** → bật lịch backup hằng ngày (nếu gói hiện tại hỗ trợ). Ghi lại số ngày được giữ.
2. Backup phải được lưu thêm **ngoài Railway**. Chạy `pg_dump` hằng ngày bằng một job (Railway cron service hoặc GitHub Actions có secret `DATABASE_URL` chỉ đọc), mã hóa và đẩy lên R2:
   ```bash
   pg_dump --format=custom --no-owner "$DATABASE_URL" > fasttowin-$(date +%F).dump
   sha256sum fasttowin-$(date +%F).dump > fasttowin-$(date +%F).dump.sha256
   ```
   Đặt lifecycle rule trên bucket để giữ khoảng 30 bản gần nhất.
3. Mỗi tháng diễn tập khôi phục một lần: `pg_restore` vào một database staging trống, khởi động server staging, đăng nhập bằng một tài khoản thử và kiểm tra ví. Ghi kết quả vào `docs/staging-rehearsal.md`.

## SEC-01 – Xác minh backend nhận đúng IP người chơi

**Đã có trong code (PR1):** Caddy chỉ tin header IP từ dải mạng riêng (Railway edge) và chuyển đúng `{client_ip}` cho backend.

1. Deploy PR1 lên staging.
2. Gửi 21 lần đăng nhập sai liên tiếp từ máy A (ví dụ dùng script `curl`). Lần thứ 21 phải trả `429`.
3. **Ngay sau đó**, đăng nhập đúng từ máy B ở một mạng khác (ví dụ 4G). Nếu máy B đăng nhập được thì backend đang phân biệt IP đúng. Nếu máy B cũng bị `429`, Railway edge không nằm trong dải được tin: xem log Caddy để lấy IP của edge, rồi đặt biến `FASTTOWIN_TRUSTED_PROXIES` của service web theo dải đó (ví dụ `private_ranges 100.64.0.0/10 <dải-edge>`).
4. Có thể dùng thêm cảnh báo đăng nhập admin (PR3): email cảnh báo phải ghi IP thật của bạn, không phải IP nội bộ.

## SEC-11 – Cảnh báo chi phí mềm

- Railway → **Usage** → đặt *usage alert* ở mức 50%, 80% và 100% ngân sách tháng. **Không** bật *hard limit* cho production, vì khi chạm hard limit Railway sẽ dừng toàn bộ service.
- Cloudflare (sau INF-01) → **Notifications** → bật cảnh báo *DDoS attack* và *Usage-based billing*.
- Ghi ngân sách đã chốt vào mục 6 của `docs/infrastructure-options-2026-10-12.md`.

## INF-01 – Domain riêng và DNS

1. Mua domain (Cloudflare Registrar bán đúng giá gốc, khoảng 10–30 USD/năm) rồi thêm site vào Cloudflare (gói Free).
2. Railway → service **web** → **Settings → Domains** → *Custom domain* → nhập `play.<domain>` (hoặc domain gốc). Railway hiển thị bản ghi CNAME cần tạo.
3. Trên Cloudflare DNS, tạo đúng bản ghi CNAME đó. Lúc đầu để **DNS only (mây xám)** cho tới khi Railway báo đã cấp chứng chỉ, sau đó mới làm SEC-05.
4. Trên service **server**, cập nhật `FASTTOWIN_PUBLIC_URL` và `FASTTOWIN_WEB_ORIGINS` để thêm origin mới. **Giữ cả origin cũ** cho tới khi xong SEC-08.
5. Hạ TTL xuống 300 giây trước mọi lần chuyển hạ tầng.

## EML-02 – SPF, DKIM, DMARC

1. Brevo → **Senders, Domains & Dedicated IPs → Domains** → thêm domain, rồi tạo trên Cloudflare đúng các bản ghi TXT/CNAME (DKIM, mã xác minh) mà Brevo cung cấp. Đặt các bản ghi này ở chế độ **DNS only**.
2. SPF: `v=spf1 include:spf.brevo.com ~all` (gộp với bản ghi SPF sẵn có nếu đã có).
3. DMARC: bắt đầu bằng `v=DMARC1; p=none; rua=mailto:dmarc@<domain>`. Sau 2–4 tuần xem báo cáo, rồi nâng lên `p=quarantine`.
4. Đổi `FASTTOWIN_EMAIL_FROM_EMAIL` sang địa chỉ thuộc domain mới (ví dụ `no-reply@<domain>`).

## SEC-05 – Bật proxy Cloudflare

1. Đổi bản ghi CNAME của domain game sang **Proxied (mây cam)**.
2. **SSL/TLS** → chọn *Full (strict)*. **Network** → bật *WebSockets*.
3. Đặt biến của service **web** trên Railway: `FASTTOWIN_CLIENT_IP_HEADERS=CF-Connecting-IP`. Chỉ đặt biến này cùng lúc với SEC-08, vì trước đó kẻ tấn công có thể gọi thẳng domain Railway và tự ghi giả header này.
4. Kiểm tra lại các bước của SEC-01 qua domain mới.

## SEC-06 và SEC-10 – Rule rate limit và ngoại lệ callback

- **Security → WAF → Rate limiting rules**: tạo rule cho `starts_with(http.request.uri.path, "/auth/")`, giới hạn khoảng 30 request mỗi 10 giây cho mỗi IP, hành động *Block* trong 60 giây. Gói Free chỉ có 1 rule; nếu cần thêm rule thì cân nhắc gói Pro.
- **SEC-10**: tạo *WAF custom rule* với hành động *Skip* (bỏ qua các rule còn lại, gồm rate limit và *Bot Fight Mode*) cho callback server-to-server của AdMob:
  `http.request.uri.path eq "/api/rewarded-ads/ssv"`.
  Backend vẫn tự xác thực chữ ký SSV. Các API mà app gọi (`/api/store-purchases/verify`, `/game`, `/auth/*`) không thể vượt qua thử thách JavaScript. Vì vậy **không** bật *Under Attack Mode* hay *Managed Challenge* cho domain game; chỉ dùng rate limit và Turnstile của chính ứng dụng.

## APP-01 – Phát hành app dùng domain mới

**Đã có trong code (PR4):** domain chỉ được cấu hình ở `FASTTOWIN_PUBLIC_HOST` trong `gradle.properties`. Bước kiểm tra `:shared:checkIosPublicHost` báo lỗi nếu Xcode project dùng giá trị khác. Phiên đăng nhập của người chơi được tự chuyển từ URL cũ sang URL mới.

1. Đổi `FASTTOWIN_PUBLIC_HOST` trong `gradle.properties` và `FASTTOWIN_PUBLIC_HOST` trong Release build setting của `iosApp`.
2. Thêm host cũ vào `previousHosts` trong `shared/.../platform/PublicEndpoints.kt` (nếu chưa có) để giữ phiên đăng nhập của người chơi.
3. Phát hành bản app mới. Cập nhật URL chính sách quyền riêng tư và URL xóa tài khoản trên Play Console và App Store Connect.

## SEC-08 – Chặn đi vòng qua Cloudflare

Chỉ làm khi phần lớn người dùng đã dùng bản app có domain mới. Xem tỉ lệ phiên bản trên Play Console.

1. Tạo một chuỗi bí mật ngẫu nhiên: `openssl rand -hex 32`.
2. Cloudflare → **Rules → Transform Rules → Modify Request Header** → *Set static* header `X-FTW-Edge` bằng chuỗi bí mật, áp dụng cho mọi request của domain game và domain admin.
3. Railway → service **web** → đặt `FASTTOWIN_EDGE_SECRET` bằng cùng chuỗi đó, rồi đặt `FASTTOWIN_CLIENT_IP_HEADERS=CF-Connecting-IP` (bước 3 của SEC-05). Caddy sẽ trả `403` cho mọi request thiếu header hoặc sai header (riêng `/health` vẫn được phép để Railway kiểm tra sức khỏe), và xóa header này trước khi chuyển request vào backend.
4. Kiểm tra: gọi trực tiếp `https://<service>.up.railway.app/api/content-terms` phải nhận `403`; gọi qua domain Cloudflare phải nhận `200`.
5. Khi không còn bản app cũ nào dùng domain Railway, có thể xóa hẳn domain `*.up.railway.app` khỏi service web. Xóa origin cũ khỏi `FASTTOWIN_WEB_ORIGINS`.

Cấu hình này đã được thử bằng Docker: request thiếu hoặc sai bí mật nhận `403`, còn `X-Forwarded-For` giả do client gửi không lọt vào backend.

## ADM-03 – Console admin ở domain con và Cloudflare Access

1. Tạo `admin.<domain>` trỏ tới service web, bật proxy.
2. Cloudflare **Zero Trust** (gói Free, tối đa 50 người) → **Access → Applications** → *Self-hosted* cho `admin.<domain>`. Policy *Allow* chỉ gồm email của nhóm vận hành, đăng nhập bằng mã email hoặc Google.
3. Đặt `FASTTOWIN_ADMIN_ORIGIN=https://admin.<domain>` cho service server.
4. Gỡ `/admin` khỏi domain game: trong `deploy/railway/web/Caddyfile`, chỉ chuyển `/admin*` tới backend khi `{host}` là domain admin. Xác minh rằng `https://play.<domain>/admin/` trả 404.

## INF-02 – Chuyển region sang Singapore

1. Thông báo bảo trì bằng tính năng **Bảo trì** trong console admin (chế độ drain), chờ các trận đang diễn ra kết thúc.
2. Backup (INF-03), tạo Postgres mới ở region `asia-southeast1`, restore bản backup, kiểm tra số dòng của các bảng chính (`users`, `wallet_transactions`, `match_results`).
3. Chuyển service server và web sang region Singapore và trỏ `DATABASE_URL` tới DB mới.
4. Mở lại dịch vụ. Đo ping từ Việt Nam (mục tiêu dưới 60 ms). Giữ DB cũ ở chế độ chỉ đọc 7 ngày rồi mới xóa.

## INF-04 – Giám sát và cảnh báo

Endpoint `/metrics` (định dạng Prometheus) đã có các chỉ số dưới đây. Kết nối Grafana Cloud (gói Free) hoặc stack trong `deploy/monitoring`, rồi tạo các cảnh báo:

| Cảnh báo | Điều kiện gợi ý | Chỉ số |
| --- | --- | --- |
| Server không phản hồi | `/health` lỗi 2 phút liền | uptime check |
| Email lỗi | `increase(fasttowin_auth_emails_total{outcome="failed"}[15m]) > 3` | EML-03 |
| Chạm trần email | `increase(fasttowin_auth_emails_total{outcome="budget_rejected"}[1h]) > 0` | SEC-03 |
| Bị chặn vì quá nhiều kết nối | `increase(fasttowin_websocket_connection_limit_rejected_total[5m]) > 50` | SEC-09 |
| Rate limit tăng đột biến | `rate(fasttowin_websocket_rate_limited_total[5m])` gấp 5 lần mức bình thường | SEC-01/02 |
| RAM | `fasttowin_jvm_memory_used_bytes / fasttowin_jvm_memory_max_bytes > 0.85` trong 10 phút | – |

## EML-01 – Nâng gói Brevo

Khi số thư mỗi ngày vượt khoảng 240 (theo dõi `fasttowin_auth_emails_total`), hoặc trước khi mở đăng ký công khai: nâng lên Brevo Starter, rồi đặt `FASTTOWIN_EMAIL_DAILY_LIMIT` bằng khoảng 80% hạn mức mới, hoặc `0` nếu gói không còn trần theo ngày.

## SCL-05 – Deploy không làm mọi người chơi kết nối lại cùng lúc

**Đã có trong code (PR4 + PR5):** khi server chuẩn bị dừng, `/ready` trả `503`, server từ chối socket mới và đóng dần socket cũ trong `FASTTOWIN_DRAIN_SECONDS` (mặc định 10 giây). Phía app tự cộng thêm độ trễ ngẫu nhiên 0–2 giây khi kết nối lại.

- Railway → service **server** → đặt `RAILWAY_DEPLOYMENT_DRAINING_SECONDS=20`, lớn hơn `FASTTOWIN_DRAIN_SECONDS`. Nếu không, Railway sẽ dừng hẳn tiến trình trước khi server kịp đóng dần các kết nối.
- Đặt *healthcheck path* của service server là `/ready` để Railway chỉ chuyển traffic sang bản deploy mới khi bản đó đã sẵn sàng.
- Lưu ý: trận đang chơi vẫn nằm trong RAM của instance cũ, nên trận chưa xong có thể bị ảnh hưởng khi deploy. Hãy deploy vào giờ ít người chơi hoặc dùng chế độ bảo trì drain, cho tới khi làm SCL-07.

## SCL-04 – Pool kết nối database

Theo dõi `fasttowin_db_pool_pending_threads` trên `/metrics`. Nếu giá trị này lớn hơn 0 kéo dài trong giờ cao điểm, tăng `DATABASE_POOL_SIZE` từng bước (10 → 15 → 20) và kiểm tra giới hạn kết nối của Postgres. Khi chạy từ 2 backend trở lên, thêm PgBouncer ở chế độ *transaction pooling*. Không tăng pool theo số WebSocket.

## SCL-03 – Chuyển avatar sang R2

Làm theo `docs/avatar-object-storage.md` (code đã sẵn sàng; chỉ cần cấu hình và chuyển ảnh cũ).

## Lưu ý khi đổi domain

- Phiên đăng nhập trên app được tự chuyển sang URL mới (APP-01). Trên Web, phiên gắn với origin cũ, nên người chơi Web sẽ phải đăng nhập lại một lần.
- Cookie phiên admin gắn với origin: sau ADM-03, mọi admin đăng nhập lại.
