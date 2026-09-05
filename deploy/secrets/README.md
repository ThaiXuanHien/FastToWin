# Production secrets

Tạo ba file sau trên máy chủ production. Chỉ ghi giá trị bí mật, không thêm dấu nháy:

- `database_password.txt`: mật khẩu PostgreSQL ngẫu nhiên, tối thiểu 12 ký tự.
- `smtp_password.txt`: mật khẩu ứng dụng/API của nhà cung cấp SMTP.
- `firebase-service-account.json`: service-account JSON tải từ Firebase Console.

Các file thật trong thư mục này được `.gitignore` loại trừ và không được commit. Giới hạn quyền đọc cho tài khoản triển khai; trên Linux có thể dùng `chmod 600 deploy/secrets/*`.
