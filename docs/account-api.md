# Đặc tả API tài khoản

Tài liệu này mô tả JSON API tài khoản đang được client Android, iOS và Web sử dụng.
Nguồn chuẩn của kiểu dữ liệu là
[`AuthProtocol.kt`](../protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/AuthProtocol.kt),
còn route và mã HTTP được định nghĩa trong
[`Application.kt`](../server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt).

## Quy ước chung

- Development: base URL mặc định là `http://127.0.0.1:8080` hoặc
  `http://localhost:8080`.
- Production: dùng origin HTTPS công khai, ví dụ `https://play.example.com`.
- Request và response dùng `Content-Type: application/json`, trừ `POST /auth/logout`
  trả `204 No Content` khi thành công.
- `accessToken` hết hạn sau 15 phút; `refreshToken` hết hạn sau 30 ngày và được xoay
  vòng sau mỗi lần refresh.
- Mật khẩu dài từ 8 đến 128 ký tự. Biệt danh dài từ 1 đến 32 ký tự.
- Đăng nhập thành công trên thiết bị mới thu hồi mọi phiên cũ của cùng tài khoản.
  Thiết bị cũ bị đóng WebSocket với lý do `Account signed in elsewhere` và trở về
  màn đăng nhập.
- Các trường có dấu `?` là tùy chọn hoặc có thể là `null`.

Response phiên đăng nhập dùng chung:

```json
{
  "userId": "11111111-2222-3333-4444-555555555555",
  "displayName": "Người chơi",
  "accessToken": "access-token",
  "refreshToken": "refresh-token",
  "accessExpiresAtEpochMillis": 1788566400000,
  "refreshExpiresAtEpochMillis": 1791158400000,
  "emailVerified": false
}
```

Response thao tác tài khoản dùng chung:

```json
{
  "message": "Mô tả kết quả",
  "devResetToken": null,
  "devEmailVerificationCode": null,
  "emailVerified": null
}
```

`devResetToken` và `devEmailVerificationCode` chỉ có thể xuất hiện trong môi trường
`dev`. Production luôn ẩn hai giá trị này.

Response lỗi dùng chung:

```json
{
  "code": "INVALID_REQUEST",
  "message": "Dữ liệu gửi lên không hợp lệ."
}
```

## Danh sách endpoint

### 1. Đăng ký

- Method và URL: `POST /auth/register`
- Request:

```json
{
  "email": "player@example.com",
  "password": "mat-khau-toi-thieu-8-ky-tu",
  "displayName": "Người chơi",
  "devicePlatform": "android",
  "gender": "MALE"
}
```

`devicePlatform` có thể là `android`, `ios` hoặc `web`. `gender` nhận `MALE` hoặc
`FEMALE`, mặc định là `MALE` nếu client cũ không gửi.

- Thành công: `201 Created` với response phiên đăng nhập; `emailVerified` là
  `false` cho tài khoản mới.
- Lỗi chính: `400 INVALID_EMAIL`, `400 INVALID_PASSWORD`,
  `400 INVALID_DISPLAY_NAME`, `409 EMAIL_ALREADY_EXISTS`.

### 2. Đăng nhập

- Method và URL: `POST /auth/login`
- Request:

```json
{
  "email": "player@example.com",
  "password": "mat-khau-cua-nguoi-choi",
  "devicePlatform": "android"
}
```

- Thành công: `200 OK` với response phiên đăng nhập. Mọi phiên cũ của tài khoản bị
  thu hồi.
- Lỗi chính: `401 INVALID_CREDENTIALS`, `429 RATE_LIMITED`.

### 3. Nâng cấp tài khoản khách

- Method và URL: `POST /auth/upgrade-guest`
- Request:

```json
{
  "resumeToken": "guest-resume-token",
  "email": "player@example.com",
  "password": "mat-khau-toi-thieu-8-ky-tu",
  "devicePlatform": "ios"
}
```

- Thành công: `200 OK` với response phiên đăng nhập. `userId` và dữ liệu chơi của
  guest được giữ nguyên.
- Lỗi chính: `401 INVALID_GUEST_SESSION`, `409 EMAIL_ALREADY_EXISTS`,
  `503 DATABASE_REQUIRED`.

### 4. Làm mới phiên

- Method và URL: `POST /auth/refresh`
- Request trên Android/iOS:

```json
{
  "refreshToken": "refresh-token"
}
```

- Thành công: `200 OK` với response phiên đăng nhập mới; refresh token cũ mất hiệu
  lực ngay.
- Lỗi chính: `401 INVALID_REFRESH_TOKEN`.

### 5. Đăng xuất

- Method và URL: `POST /auth/logout`
- Request trên Android/iOS:

```json
{
  "refreshToken": "refresh-token"
}
```

- Thành công: `204 No Content`. Phiên hiện tại bị thu hồi.

### 6. Liệt kê thiết bị đăng nhập

- Method và URL: `POST /auth/sessions`
- Request:

```json
{
  "accessToken": "access-token"
}
```

- Thành công: `200 OK`:

```json
{
  "sessions": [
    {
      "sessionId": "11111111-2222-3333-4444-555555555555",
      "devicePlatform": "android",
      "createdAtEpochMillis": 1788566400000,
      "lastSeenAtEpochMillis": 1788566700000,
      "expiresAtEpochMillis": 1791158400000,
      "isCurrent": true
    }
  ]
}
```

- Lỗi chính: `401 INVALID_ACCESS_TOKEN`.

Với chính sách một phiên hiện tại, danh sách thường chỉ chứa thiết bị đang đăng
nhập. Endpoint vẫn xử lý an toàn dữ liệu phiên cũ hoặc phiên đã tồn tại trước khi
chính sách này được áp dụng.

### 7. Thu hồi một phiên

- Method và URL: `POST /auth/sessions/revoke`
- Request:

```json
{
  "accessToken": "access-token",
  "sessionId": "11111111-2222-3333-4444-555555555555"
}
```

- Thành công: `200 OK` với response thao tác tài khoản.
- Lỗi chính: `400 INVALID_SESSION_ID`, `400 SESSION_NOT_FOUND`,
  `401 INVALID_ACCESS_TOKEN`.

### 8. Đăng xuất tất cả thiết bị

- Method và URL: `POST /auth/sessions/revoke-all`
- Request:

```json
{
  "accessToken": "access-token"
}
```

- Thành công: `200 OK` với response thao tác tài khoản. Phiên hiện tại cũng bị thu
  hồi nên client phải trở về màn đăng nhập.
- Lỗi chính: `401 INVALID_ACCESS_TOKEN`.

### 9. Đổi mật khẩu

- Method và URL: `POST /auth/change-password`
- Request:

```json
{
  "accessToken": "access-token",
  "currentPassword": "mat-khau-hien-tai",
  "newPassword": "mat-khau-moi"
}
```

- Thành công: `200 OK` với response thao tác tài khoản. Tất cả phiên bị thu hồi.
- Lỗi chính: `400 INVALID_CURRENT_PASSWORD`, `400 INVALID_PASSWORD`,
  `400 PASSWORD_UNCHANGED`, `401 INVALID_ACCESS_TOKEN`.

### 10. Yêu cầu khôi phục mật khẩu

- Method và URL: `POST /auth/password-reset/request`
- Request:

```json
{
  "email": "player@example.com"
}
```

- Thành công: `200 OK` với thông báo chung, không tiết lộ email có tồn tại hay
  không. Development có thể trả `devResetToken`; production gửi mã qua SMTP và
  không trả token.
- Lỗi chính: `400 INVALID_EMAIL`, `429 RATE_LIMITED`,
  `503 PASSWORD_RESET_DELIVERY_UNAVAILABLE` khi production chưa cấu hình SMTP.

### 11. Xác nhận khôi phục mật khẩu

- Method và URL: `POST /auth/password-reset/confirm`
- Request:

```json
{
  "email": "player@example.com",
  "resetToken": "reset-token",
  "newPassword": "mat-khau-moi"
}
```

- Thành công: `200 OK` với response thao tác tài khoản. Mã chỉ dùng một lần và hết
  hạn sau 15 phút.
- Lỗi chính: `400 INVALID_RESET_TOKEN`, `400 INVALID_PASSWORD`,
  `429 RATE_LIMITED`.

### 12. Gửi mã xác minh email

- Method và URL: `POST /auth/email-verification/request`
- Request:

```json
{
  "accessToken": "access-token"
}
```

- Thành công: `200 OK` với response thao tác tài khoản. Development có thể trả
  `devEmailVerificationCode`; production gửi mã 6 số qua SMTP.
- Lỗi chính: `401 INVALID_ACCESS_TOKEN`, `429 RATE_LIMITED`,
  `503 EMAIL_DELIVERY_UNAVAILABLE`, `503 EMAIL_DELIVERY_FAILED`.

### 13. Xác nhận email

- Method và URL: `POST /auth/email-verification/confirm`
- Request:

```json
{
  "accessToken": "access-token",
  "verificationCode": "123456"
}
```

- Thành công: `200 OK`; response có `emailVerified: true`.
- Lỗi chính: `400 INVALID_VERIFICATION_CODE`, `401 INVALID_ACCESS_TOKEN`,
  `429 RATE_LIMITED`.

### 14. Xóa tài khoản

- Method và URL: `POST /auth/delete-account`
- Request:

```json
{
  "accessToken": "access-token",
  "password": "mat-khau-hien-tai"
}
```

- Thành công: `200 OK` với response thao tác tài khoản. Tài khoản và dữ liệu cá
  nhân liên quan bị xóa.
- Lỗi chính: `400 INVALID_CURRENT_PASSWORD`, `401 INVALID_ACCESS_TOKEN`.

## Quy tắc riêng cho Web

Web không lưu hoặc đọc refresh token bằng JavaScript. Với mọi endpoint tài khoản,
client gửi cookie bằng `credentials: include` và hai header:

```text
X-FastToWin-Web-Session: 1
X-FastToWin-CSRF: 1
```

`Origin` phải nằm trong `FASTTOWIN_WEB_ORIGINS`. Khi đăng ký, đăng nhập và nâng cấp
guest, request đặt `devicePlatform` là `web`; server trả `refreshToken` rỗng trong
JSON và đặt refresh token thật trong cookie host-only `HttpOnly`, `SameSite=Strict`.
Production dùng cookie `Secure` có tên bắt đầu bằng `__Host-`. Với refresh/logout,
server lấy refresh token từ cookie thay vì trường JSON.

Request Web sai origin/header nhận `403 INVALID_WEB_SESSION_REQUEST`.

## Rate limit và bảo mật

| Luồng | Giới hạn mặc định |
|---|---|
| Đăng nhập theo IP | 20 lần/phút |
| Đăng nhập theo email chuẩn hóa | 8 lần/5 phút |
| Gửi mã khôi phục theo IP | 10 lần/15 phút |
| Gửi mã khôi phục theo email | 3 lần/15 phút |
| Thử mã khôi phục theo IP | 30 lần/15 phút |
| Thử mã khôi phục theo email | 8 lần/15 phút |
| Gửi mã xác minh theo IP | 60 lần/15 phút |
| Gửi mã xác minh theo tài khoản | 3 lần/15 phút |
| Thử mã xác minh theo IP | 120 lần/15 phút |
| Thử mã xác minh theo tài khoản | 8 lần/15 phút |

Khi vượt giới hạn, server trả `429 Too Many Requests`, lỗi `RATE_LIMITED` và header
`Retry-After`. Rate limiter hiện nằm trong bộ nhớ của từng backend instance; cần
Redis hoặc kho dùng chung trước khi scale ngang.

Token gốc không được lưu trong PostgreSQL; backend chỉ lưu SHA-256 hash. Mật khẩu
được dẫn xuất bằng PBKDF2-HMAC-SHA256 với salt riêng. Không ghi email, mật khẩu,
token, session ID hoặc nội dung SMTP secret vào log production.
