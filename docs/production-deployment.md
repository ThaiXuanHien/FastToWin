# Triển khai Fast To Win production

Kiến trúc production mặc định dùng một máy chủ Docker:

```text
Internet :80/:443
        |
      Caddy  -- HTTPS tự động, Web/PWA, reverse proxy WebSocket
        |
     server:8080  -- chỉ nằm trong mạng Docker
        |
     PostgreSQL   -- không mở cổng ra Internet
```

Web, HTTP API và WebSocket dùng chung một domain. Ví dụ với
`play.fasttowin.vn`, client kết nối `wss://play.fasttowin.vn/game`; cách này giảm cấu
hình CORS và không cần công khai cổng backend.

## 1. Điều kiện trước khi chạy

- Một VPS Linux có IP public; tối thiểu 4 GB RAM khi bật đầy đủ monitoring cục bộ.
- Docker Engine và Docker Compose plugin.
- Domain đã có bản ghi `A` trỏ tới IPv4 của VPS; chỉ tạo `AAAA` nếu VPS thực sự có IPv6 hoạt động.
- Firewall/security group cho phép TCP `80`, TCP `443` và UDP `443`.
- SMTP account để gửi mã xác minh/khôi phục mật khẩu.
- Firebase service-account JSON để gửi push notification.

Caddy tự xin và gia hạn chứng chỉ TLS khi domain đã trỏ đúng và cổng 80/443 có thể
truy cập từ Internet. Không mở cổng `8080` hoặc `5432` trên VPS.

## 2. Tạo cấu hình không bí mật

Tại thư mục project:

```bash
cp deploy/.env.production.example deploy/.env.production
```

Sửa `deploy/.env.production`:

```dotenv
FASTTOWIN_DOMAIN=play.fasttowin.vn
ACME_EMAIL=admin@fasttowin.vn
FASTTOWIN_SMTP_HOST=smtp.provider.vn
FASTTOWIN_SMTP_USERNAME=no-reply@fasttowin.vn
FASTTOWIN_SMTP_FROM_EMAIL=no-reply@fasttowin.vn
```

Không thêm mật khẩu hoặc private key vào file này.

## 3. Tạo secrets

```bash
mkdir -p deploy/secrets
openssl rand -base64 32 > deploy/secrets/database_password.txt
openssl rand -base64 32 > deploy/secrets/grafana_admin_password.txt
printf '%s' 'SMTP_APP_PASSWORD_THAT_BELONGS_TO_THIS_SERVER' > deploy/secrets/smtp_password.txt
cp /duong-dan-an-toan/firebase-service-account.json deploy/secrets/firebase-service-account.json
chmod 600 deploy/secrets/database_password.txt deploy/secrets/grafana_admin_password.txt deploy/secrets/smtp_password.txt deploy/secrets/firebase-service-account.json
```

Bốn file thật đều bị Git bỏ qua. Docker mount chúng vào `/run/secrets`; backend hỗ
trợ biến `*_FILE`, không cần đưa mật khẩu vào command line, image hoặc environment.

## 4. Build và chạy

Linux/macOS:

```bash
./deploy-production.sh
```

Windows PowerShell/CMD:

```powershell
.\deploy-production.cmd
```

Script thực hiện:

1. Chạy test server.
2. Đóng gói server JVM và Web compatibility Wasm + JavaScript fallback.
3. Xác thực Docker Compose và các file secret.
4. Build image runtime rồi chờ database/backend healthy.
5. Gắn image server/Web bằng mã commit hiện tại và ghi release đang hoạt động vào
   `deploy/state/`. Image của release trước được giữ lại để rollback ứng dụng.

Chỉ build và test, không khởi động container:

```bash
./deploy-production.sh --build-only
```

```powershell
.\deploy-production.cmd -BuildOnly
```

## 5. Kiểm tra sau triển khai

Lệnh tổng hợp được khuyến nghị:

```bash
./production-ops.sh status
./production-ops.sh health
```

```powershell
.\production-ops.cmd status
.\production-ops.cmd health
```

`health` chỉ thành công khi HTTPS `/health` trả chính xác `OK`; đồng thời nó đọc
`/status` để hiển thị trạng thái bảo trì. Có thể kiểm tra thủ công bằng:

```bash
curl --fail https://play.fasttowin.vn/health
curl --fail https://play.fasttowin.vn/status
docker compose --env-file deploy/.env.production -f compose.production.yaml ps
docker compose --env-file deploy/.env.production -f compose.production.yaml logs --tail=200 server web
```

Sau đó kiểm tra bằng trình duyệt:

- Đăng ký, nhận email xác minh và đăng nhập.
- Tạo hai tài khoản, mở hai thiết bị/trình duyệt và chơi đủ một trận.
- Upload avatar, Back/Forward, deep link phòng và cài PWA.
- Bật Web Push rồi xác nhận thông báo mở đúng màn hình.
- Kiểm tra CORS từ một origin không nằm trong allowlist bị từ chối.

Backend production dừng ngay khi domain/CORS/database/SMTP/secrets thiếu, dùng HTTP
hoặc còn giá trị placeholder. `FASTTOWIN_TRUST_PROXY_HEADERS=true` chỉ an toàn vì
service `server` không publish port; không đưa cổng 8080 ra Internet.

## 6. Build Android và iOS cùng domain

Android production:

```powershell
.\gradlew.bat :app:assembleProdRelease -PFASTTOWIN_PROD_WS_URL=wss://play.fasttowin.vn/game
```

iOS Release: đặt `GAME_SERVER_URL` thành `wss://play.fasttowin.vn/game` trong Xcode.
Không phát hành nếu vẫn còn domain `configure-production-server.invalid`.

## 7. Cập nhật, bảo trì và rollback

Mỗi image server/Web được gắn tag bằng 12 ký tự đầu của commit Git. Có thể chỉ định
tag dễ nhớ nhưng không được dùng lại tag cho code khác:

```bash
./deploy-production.sh --release-tag release-2026-09-05
```

```powershell
.\deploy-production.cmd -ReleaseTag release-2026-09-05
```

Trước mỗi lần cập nhật, chạy trọn chu trình backup, xác minh checksum/định dạng
PostgreSQL và dọn các bản quá hạn:

```bash
./production-ops.sh backup-cycle
```

```powershell
.\production-ops.cmd backup-cycle
```

Backup định dạng PostgreSQL custom nằm trong `deploy/backups/`, còn tag đang chạy
và tag trước đó nằm trong `deploy/state/`. Hai thư mục này không được Git theo dõi;
phải sao chép backup sang máy hoặc object storage khác, không chỉ giữ trên cùng VPS.
Thời hạn lưu cục bộ mặc định là 14 ngày; thay đổi bằng
`FASTTOWIN_BACKUP_RETENTION_DAYS` trong `deploy/.env.production`.

Có thể kiểm tra bản gần nhất hoặc một file cụ thể mà không restore vào database thật:

```bash
./production-ops.sh verify-backup
./production-ops.sh verify-backup deploy/backups/fasttowin-YYYYMMDD-HHMMSS.dump
```

Diễn tập khôi phục tạo một PostgreSQL 17 tạm bằng `tmpfs`, không publish cổng, kiểm
tra các bảng lõi rồi tự xóa container:

```bash
./production-ops.sh restore-drill
```

```powershell
.\production-ops.cmd restore-drill
```

Trên VPS Linux, cài lịch chạy bằng tài khoản có quyền dùng Docker:

```bash
chmod +x production-ops.sh scripts/install-production-backup-cron.sh
./scripts/install-production-backup-cron.sh install
```

Lịch mặc định chạy `backup-cycle` mỗi ngày lúc 02:17 và `restore-drill` mỗi Chủ
nhật lúc 03:43 theo múi giờ của VPS. Hai tác vụ dùng `flock` để không chạy trùng;
log nằm trong `deploy/state/`. Gỡ lịch bằng:

```bash
./scripts/install-production-backup-cron.sh remove
```

Bật/tắt màn bảo trì mà không sửa file cấu hình:

```bash
./production-ops.sh maintenance-on "Máy chủ đang nâng cấp. Vui lòng quay lại sau."
./production-ops.sh maintenance-off
```

```powershell
.\production-ops.cmd maintenance-on "Máy chủ đang nâng cấp. Vui lòng quay lại sau."
.\production-ops.cmd maintenance-off
```

Cấu hình mặc định lâu dài vẫn có thể đặt trong `deploy/.env.production`:

```dotenv
FASTTOWIN_MAINTENANCE=true
FASTTOWIN_MAINTENANCE_MESSAGE=Máy chủ đang nâng cấp. Vui lòng quay lại sau.
```

Áp dụng cấu hình:

```bash
docker compose --env-file deploy/.env.production -f compose.production.yaml up -d --force-recreate server
```

Rollback không tự chạy khi health check lỗi vì migration Flyway là tiến về phía
trước. Trước tiên phải đọc migration của release mới và xác nhận server cũ tương
thích schema hiện tại. Sau khi đã kiểm tra, rollback về tag chỉ định hoặc tag trước
đó được ghi trong `deploy/state/previous-release.txt`:

```bash
./production-ops.sh rollback release-2026-09-04 --confirm-schema-compatible
```

```powershell
.\production-ops.cmd rollback release-2026-09-04 -ConfirmSchemaCompatible
```

Rollback kiểm tra đủ hai image, tạo thêm backup, bật bảo trì, chạy image cũ mà
không build lại, chờ container healthy rồi kiểm tra HTTPS. Nếu schema không tương
thích, dừng quy trình và phục hồi database trên môi trường riêng từ file `.dump`
trước khi quyết định khôi phục production; không tự sửa bảng trực tiếp.

Quy trình phát hành chuẩn:

1. Chốt commit đã review và CI xanh.
2. Tạo backup, sao chép backup ra ngoài VPS và thử đọc bằng `pg_restore --list`.
3. Bật bảo trì nếu thay đổi schema hoặc có khả năng ngắt phiên đang chơi.
4. Chạy deploy với tag mới, sau đó chạy `status` và `health`.
5. Smoke test đăng nhập, tạo phòng, một trận hai người và email.
6. Tắt bảo trì, theo dõi log ít nhất 15 phút và ghi lại tag phát hành.
7. Nếu lỗi, đánh giá tương thích schema rồi mới dùng rollback; không xóa volume.

Dừng dịch vụ nhưng giữ dữ liệu:

```bash
docker compose --env-file deploy/.env.production -f compose.production.yaml down
```

Không dùng `down -v` trên production vì tùy chọn đó xóa volume database.

## 8. Monitoring, log và cảnh báo

Stack production chạy thêm:

- Prometheus lưu metrics 15 ngày và đọc `/internal/metrics` trực tiếp trong mạng Docker.
- Alertmanager nhóm/cô lập cảnh báo backend down, heap JVM cao, rate limit và message lỗi tăng mạnh.
- Loki giữ log tập trung 7 ngày; Alloy chỉ đọc file Docker log, không mount Docker socket.
- Grafana tự nạp Prometheus, Loki, Alertmanager và dashboard **Fast To Win — Production**.
- Docker `json-file` xoay tối đa 5 file × 10 MB cho mỗi container để tránh đầy ổ đĩa.

`/internal/metrics` không được Caddy public ra Internet. Grafana, Prometheus, Loki
và Alertmanager chỉ bind vào `127.0.0.1` của VPS. Truy cập Grafana qua SSH tunnel:

```bash
ssh -L 3000:127.0.0.1:3000 user@dia-chi-vps
```

Sau đó mở `http://localhost:3000`, đăng nhập bằng `GRAFANA_ADMIN_USER` và mật khẩu
trong `grafana_admin_password.txt`. Có thể tạo tunnel tương tự cho Prometheus
`:9090`, Alertmanager `:9093` hoặc Loki `:3100` khi chẩn đoán.

Alertmanager hiện lưu và hiển thị cảnh báo nội bộ, chưa tự gửi ra email/chat để
tránh gửi nhầm khi chưa chọn người nhận. Trước khi mở production, cấu hình một
kênh nhận thật rồi kích hoạt thử một cảnh báo có kiểm soát. Không public trực tiếp
các cổng monitoring và không đưa mật khẩu Grafana vào `.env.production`.

Metrics ứng dụng chỉ có counter/gauge tổng hợp, không gắn email, player ID, tên
phòng hoặc token làm label. Log vẫn phải tránh ghi secrets; trace/test artifact có
thông tin tài khoản thử nghiệm không được đưa vào Loki production.
