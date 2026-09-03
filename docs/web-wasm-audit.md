# Audit phiên bản web Kotlin/Wasm và Kotlin/JS

## Phạm vi hiện tại

Module `webApp` là điểm khởi chạy trình duyệt, còn `shared` tiếp tục là nguồn UI và logic chung cho Android, iOS và web. `protocol`, `shared` và `webApp` có cả target `wasmJs` và `js`; gói compatibility ưu tiên Wasm và tự dùng JavaScript fallback khi trình duyệt không đáp ứng.

## Đã sẵn sàng

| Hạng mục | Trạng thái | Ghi chú |
| --- | --- | --- |
| Compose UI và theme 2D Arcade | Dùng chung | Render bằng `ComposeViewport`, viewport co giãn theo cửa sổ |
| HTTP API | Dùng chung | Ktor client Wasm dùng Fetch; backend đã bật CORS trong dev |
| WebSocket game | Dùng chung | Giữ nguyên `/game` và serialization hiện tại |
| Đăng nhập và resume token | Có bản web | Lưu trong `localStorage` để không mất khi tải lại trang |
| Cài đặt giao diện | Có bản web | Lưu trong `localStorage` |
| Âm thanh trận đấu | Có bản web | Dùng Web Audio sau tương tác đầu tiên để tuân thủ autoplay policy |
| Tải avatar từ máy | Có bản web | Dùng File API, resize và nén ảnh trước khi upload |
| Chia sẻ kết quả dạng ảnh | Có bản web | Render thẻ PNG bằng Canvas, dùng Web Share API hoặc tự tải ảnh xuống |
| Back/Forward trình duyệt | Có bản web | Đồng bộ các màn chính với History API và giữ nguyên nút Back trong app |
| URL và deep link | Có bản web | Hỗ trợ URL phòng `/room/{id}` và thử thách `/challenge/{code}` |
| JavaScript fallback | Có cấu hình | `composeCompatibilityBrowserDistribution` đóng gói cả Wasm và JS |
| Pull to refresh | Theo loại thiết bị | Chỉ bật gesture trong Web khi trình duyệt báo có màn hình cảm ứng |
| Cài đặt PWA | Có | Manifest, icon 192/512, icon maskable, trạng thái standalone và nút cài trong Cài đặt; có hướng dẫn thủ công cho Safari |
| App shell | Có | Service worker cache giao diện và tài nguyên tĩnh; không cache API hoặc WebSocket |
| Cập nhật PWA | Có | Worker mới chờ xác nhận; dialog chỉ hiện ngoài phòng, ghép trận và ván chơi |
| Trạng thái Offline | Dùng chung | Health check lỗi hiển thị màn riêng có thử lại và Luyện tập offline; không bị hiểu nhầm là bảo trì |
| Web Push | Có cấu hình | FCM dùng chung backend; người chơi chủ động bật và chọn riêng lời mời phòng, giải đấu, nhiệm vụ, điểm danh |
| Trạng thái bảo trì | Dùng chung | Vẫn đọc `GET /status`, mất mạng không bị hiểu là bảo trì |
| Chia sẻ text/link | Có bản web | Dùng Web Share API, fallback sang Clipboard API |
| Avatar từ server | Dùng chung | ByteArray được giải mã bằng API ảnh multiplatform |
| CORS production | Có cấu hình | Khai báo danh sách origin bằng `FASTTOWIN_WEB_ORIGINS` |

## Giới hạn cần xử lý theo từng giai đoạn

| Hạng mục | Hiện tại | Hướng triển khai |
| --- | --- | --- |
| Mua Gem | Nút bị vô hiệu hóa và có thông báo | Chọn cổng thanh toán web rồi thêm verifier riêng ở backend |
| Đăng nhập production | Token đang ở `localStorage` | Trước production nên chuyển refresh token sang cookie `HttpOnly`, `Secure`, `SameSite` |

## Kiểm thử bắt buộc trước khi phát hành

- Chrome, Edge, Firefox và Safari; kiểm tra cả nhánh WasmGC và JavaScript fallback.
- Kích thước 320 px, 375 px, tablet, desktop và cửa sổ ngang thấp.
- Chuột, touchpad, màn hình cảm ứng và bàn phím.
- Refresh giữa phiên, reconnect WebSocket và mở hai tab cùng tài khoản.
- Cấu hình web server trả `index.html` cho các đường dẫn SPA như `/room/*` và `/challenge/*`.
- Cài PWA, đóng/mở lại từ biểu tượng, kiểm tra icon/maskable và thử luồng cập nhật service worker.
- Bật/tắt Web Push và từng nhóm thông báo, kiểm tra tùy chọn đồng bộ, token bị xóa khi đăng xuất và link mở đúng màn.
- Tăng `SHELL_CACHE` khi phát hành client mới; xác nhận dialog cập nhật không xuất hiện giữa trận.
- HTTPS/WSS, CORS production, Content Security Policy và kiểm tra không lộ token.
- Chữ lớn, reduced motion, độ tương phản và thứ tự focus.
