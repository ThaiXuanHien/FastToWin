# Audit phiên bản web Kotlin/Wasm

## Phạm vi hiện tại

Module `webApp` là điểm khởi chạy trình duyệt, còn `shared` tiếp tục là nguồn UI và logic chung cho Android, iOS và web. `protocol` cũng đã có target `wasmJs` nên web dùng đúng message model của backend, không tạo protocol riêng.

## Đã sẵn sàng

| Hạng mục | Trạng thái | Ghi chú |
| --- | --- | --- |
| Compose UI và theme 2D Arcade | Dùng chung | Render bằng `ComposeViewport`, viewport co giãn theo cửa sổ |
| HTTP API | Dùng chung | Ktor client Wasm dùng Fetch; backend đã bật CORS trong dev |
| WebSocket game | Dùng chung | Giữ nguyên `/game` và serialization hiện tại |
| Đăng nhập và resume token | Có bản web | Lưu trong `localStorage` để không mất khi tải lại trang |
| Cài đặt giao diện | Có bản web | Lưu trong `localStorage` |
| Trạng thái bảo trì | Dùng chung | Vẫn đọc `GET /status`, mất mạng không bị hiểu là bảo trì |
| Chia sẻ text/link | Có bản web | Dùng Web Share API, fallback sang Clipboard API |
| Avatar từ server | Dùng chung | ByteArray được giải mã bằng API ảnh multiplatform |
| CORS production | Có cấu hình | Khai báo danh sách origin bằng `FASTTOWIN_WEB_ORIGINS` |

## Giới hạn cần xử lý theo từng giai đoạn

| Hạng mục | Hiện tại | Hướng triển khai |
| --- | --- | --- |
| Mua Gem | Nút bị vô hiệu hóa và có thông báo | Chọn cổng thanh toán web rồi thêm verifier riêng ở backend |
| Tải avatar từ máy | Chưa mở file picker | Tích hợp browser File API, resize và giới hạn dung lượng trước upload |
| Âm thanh trận đấu | Chưa phát trên web | Thêm Web Audio và chỉ khởi tạo sau thao tác đầu tiên của người dùng |
| Chia sẻ kết quả dạng ảnh | Mới chia sẻ caption | Render ảnh trên canvas rồi gọi Web Share/Download |
| Back/Forward trình duyệt | Dùng nút Back trong app | Liên kết navigation stack với browser history và URL |
| Deep link | Chưa có URL web chuẩn | Chuyển phòng/thử thách thành `/room/{id}` và `/challenge/{code}` |
| Đăng nhập production | Token đang ở `localStorage` | Trước production nên chuyển refresh token sang cookie `HttpOnly`, `Secure`, `SameSite` |
| Trình duyệt cũ | Chỉ có Wasm | Bổ sung compatibility build JS nếu số liệu người dùng yêu cầu |

## Kiểm thử bắt buộc trước khi phát hành

- Chrome, Edge, Firefox và Safari phiên bản có WasmGC.
- Kích thước 320 px, 375 px, tablet, desktop và cửa sổ ngang thấp.
- Chuột, touchpad, màn hình cảm ứng và bàn phím.
- Refresh giữa phiên, reconnect WebSocket và mở hai tab cùng tài khoản.
- HTTPS/WSS, CORS production, Content Security Policy và kiểm tra không lộ token.
- Chữ lớn, reduced motion, độ tương phản và thứ tự focus.
