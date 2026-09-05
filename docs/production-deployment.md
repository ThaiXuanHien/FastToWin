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

- Một VPS Linux có IP public, tối thiểu 2 GB RAM cho giai đoạn thử nghiệm.
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
printf '%s' 'SMTP_APP_PASSWORD_THAT_BELONGS_TO_THIS_SERVER' > deploy/secrets/smtp_password.txt
cp /duong-dan-an-toan/firebase-service-account.json deploy/secrets/firebase-service-account.json
chmod 600 deploy/secrets/database_password.txt deploy/secrets/smtp_password.txt deploy/secrets/firebase-service-account.json
```

Ba file thật đều bị Git bỏ qua. Docker mount chúng vào `/run/secrets`; backend hỗ
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

Chỉ build và test, không khởi động container:

```bash
./deploy-production.sh --build-only
```

```powershell
.\deploy-production.cmd -BuildOnly
```

## 5. Kiểm tra sau triển khai

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

Trước mỗi lần cập nhật, sao lưu database và ghi lại commit đang chạy. Sau khi kéo
code đã review, chạy lại `deploy-production.sh`; volume PostgreSQL và dữ liệu chứng
chỉ Caddy được giữ nguyên.

Bật màn bảo trì bằng cách đặt trong `deploy/.env.production`:

```dotenv
FASTTOWIN_MAINTENANCE=true
FASTTOWIN_MAINTENANCE_MESSAGE=Máy chủ đang nâng cấp. Vui lòng quay lại sau.
```

Áp dụng cấu hình:

```bash
docker compose --env-file deploy/.env.production -f compose.production.yaml up -d --force-recreate server
```

Rollback ứng dụng bằng commit/image đã xác nhận tốt. Migration Flyway là tiến về
phía trước; nếu phiên bản cũ không tương thích schema mới, khôi phục từ bản backup
đã tạo trước khi nâng cấp thay vì tự sửa bảng production.

Dừng dịch vụ nhưng giữ dữ liệu:

```bash
docker compose --env-file deploy/.env.production -f compose.production.yaml down
```

Không dùng `down -v` trên production vì tùy chọn đó xóa volume database.
