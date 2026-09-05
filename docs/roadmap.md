# Lộ trình hoàn thiện Fast To Win

Cập nhật: 05/09/2026. Đây là danh sách theo dõi thống nhất từ các hạng mục chủ dự án đã duyệt. Có code không đồng nghĩa đã kiểm thử hoặc sẵn sàng production.

## Quy tắc thực hiện

- Ưu tiên hoàn thiện Web → iOS → cửa hàng/giải đấu mở rộng → production và mở rộng hạ tầng.
- Kiểm thử đi cùng từng bước, không chờ tới cuối mới kiểm thử.
- Hoàn thành một bước thì báo kết quả, phần chưa xác minh và đề xuất bước tiếp theo để chủ dự án duyệt.
- Không tự kích hoạt thanh toán thật, gửi email hàng loạt, mua dịch vụ hoặc triển khai production.
- Google/Apple login và danh sách người vừa thi đấu cùng đã được chủ dự án bỏ; không tính là thiếu.

## 1. Hoàn thiện Web — ưu tiên hiện tại

Các mục sau trong danh sách cũ **đã có triển khai**; tiếp tục hồi quy chứ không làm lại:

| Hạng mục | Trạng thái hiện tại |
| --- | --- |
| Âm thanh đúng/sai | Web Audio; phát sau tương tác đầu tiên |
| Chia sẻ ảnh kết quả | Canvas PNG, Web Share hoặc tải ảnh xuống |
| Back/Forward trình duyệt | History API và đồng bộ màn trong app |
| URL phòng/thử thách | `/room/{id}`, `/challenge/{code}` |
| Trình duyệt không hỗ trợ WasmGC | Có target JS và smoke test bundle compatibility trong CI |
| Avatar và responsive | Có chọn/nén/tải ảnh Web, chiều rộng nội dung và dialog theo viewport; đã có ma trận responsive tự động |
| Pull-to-refresh | Gesture chỉ dành cho thiết bị cảm ứng |
| PWA, offline, update và Web Push | Đã có; Web Push đã được người dùng xác nhận nhận đủ 4 nhóm thông báo |

Phần đang làm và còn lại:

- [x] Hoàn thiện và xác minh bộ Web E2E trong `e2e/`: đăng nhập/F5, Back/Forward, phòng hai người, trận đủ 50 số, mất kết nối, đấu lại, về sảnh độc lập. CI #67 đã đạt.
- [x] Mở rộng kiểm thử responsive tự động: điện thoại nhỏ/lớn, tablet, ngang, chữ lớn, nội dung dài và viewport thấp mô phỏng bàn phím ảo.
- [x] Smoke test tự động Chromium/Firefox/WebKit và bundle Kotlin/JS fallback trong CI.
- [ ] Smoke test Chrome/Edge/Safari và bàn phím ảo thật trên thiết bị. Playwright WebKit không thay thế Safari trên thiết bị Apple.
- [ ] Mua Gem trên Web: cần thống nhất cổng thanh toán, phí và xác thực giao dịch backend trước khi làm.

Chi tiết: [audit Web](web-wasm-audit.md), [hướng dẫn Web E2E](web-e2e.md).

## 2. Hoàn thiện iOS

- [x] Chọn, nén và tải ảnh đại diện bằng picker iOS; ảnh được thu về tối đa 512 px và nén trước khi gửi qua WebSocket.
- [ ] Hoàn thiện StoreKit 2 production và xác thực giao dịch App Store; hiện có phần chuẩn bị/sandbox, chưa coi là thanh toán thật.
- [x] Đã tích hợp đăng ký thiết bị, quyền thông báo, APNs/FCM và mở đúng màn từ thông báo.
- [ ] Cấu hình APNs Authentication Key và smoke test trên thiết bị thật: tạm hoãn đến khi có tài khoản Apple Developer Program.
- [ ] Build/chạy trên macOS/Xcode, iPhone và iPad simulator.
- [ ] Hai người chơi thật, safe area, bàn phím, xoay màn hình, nền/khôi phục ứng dụng trên thiết bị iOS thật.

Mua hàng và APNs cần cấu hình Apple tương ứng; kiểm thử thiết bị cần máy Mac/thiết bị. Không tự đăng ký dịch vụ trả phí.

## 3. Cửa hàng

Danh mục bán hiện tại: Gem, Mặt bài Hoàng Kim, Mặt bài Kim Cương, Bàn số Bóng Đêm và Bàn số Rừng Xanh. Theo quyết định sản phẩm, Cửa hàng không hiển thị tab Khung và Biểu cảm; các phần thưởng này vẫn có thể mở khóa ở Bộ sưu tập/thành tích.

- [ ] Duyệt hình ảnh, giá Vàng/Gem và điều kiện mở khóa trước khi thêm danh mục.
- [ ] Kiểm thử mua trùng, thiếu tài sản, trang bị/đồng bộ và lịch sử giao dịch.

## 4. Giải đấu mở rộng

Đã có giải riêng 4, 8 hoặc 16 người, nhánh đấu loại trực tiếp, nhà vô địch và lịch sử.

- [x] Giải riêng 8 người với tứ kết, bán kết và chung kết.
- [x] Giải riêng 16 người với vòng 1/8, tứ kết, bán kết và chung kết.
- [ ] Giải công khai/toàn hệ thống.
- [ ] Lịch thi đấu, thời hạn đăng ký và xử lý vắng mặt.
- [ ] Khán giả xem trận đấu giải (khác với xem phòng thông thường).
- [x] Hồi quy tạo nhánh đấu 4/8/16 người, reconnect/restart, xử thua khi rời hoặc quá hạn và phần thưởng không trùng.

## 5. Tài khoản và cấu hình production

- [x] Dịch vụ SMTP production cho quên mật khẩu; token chỉ hiển thị ở dev.
- [x] Xác minh địa chỉ email bằng mã 6 số và giới hạn gửi lại/thử mã.
- [x] Mẫu triển khai domain, HTTPS/WSS, CORS allowlist và Docker secrets.
- [x] Bảo vệ phiên Web: refresh token dùng cookie host-only `HttpOnly`, `Secure`, `SameSite=Strict`; access token chỉ nằm trong bộ nhớ và request cookie được kiểm tra `Origin`/CSRF.
- [x] Chốt quy trình phát hành, rollback, bảo trì và kiểm tra sức khỏe dịch vụ; image được gắn release tag, backup có SHA-256 và rollback yêu cầu xác nhận tương thích schema.

## 6. Hạ tầng khi mở rộng

Backend hiện dùng trạng thái realtime trong một instance. Không coi các mục sau là điều kiện phải làm ngay khi chưa có nhu cầu tải:

- [ ] Redis cho presence/trạng thái liên instance và rate limiting dùng chung.
- [ ] Load balancer/sticky session hoặc WebSocket gateway; xác định rõ instance sở hữu trận và cách khôi phục.
- [x] Monitoring/log cho một instance: metrics nội bộ, Prometheus, Alertmanager, Grafana, Loki/Alloy, dashboard dựng sẵn và giới hạn dung lượng Docker log.
- [x] Backup PostgreSQL tự động, thời gian lưu cấu hình được, xác minh checksum/dump và diễn tập khôi phục cách ly bằng PostgreSQL tạm.
- [ ] CDN/object storage cho avatar và tài nguyên.
- [ ] Load test có giới hạn trên môi trường riêng trước khi mở rộng.

## 7. Kiểm thử và phát hành xuyên suốt

Đã có unit/integration test server/shared, Android Compose UI test và GitHub Actions. Chưa đánh đồng build xanh với toàn bộ trải nghiệm đa nền tảng đạt.

- [ ] Screenshot regression test với ảnh chuẩn đã review (ảnh chụp khi test lỗi chưa phải screenshot regression).
- [ ] Báo cáo độ bao phủ test và ngưỡng phù hợp.
- [x] Ma trận Playwright Chromium/Firefox/WebKit và JS fallback trong CI.
- [ ] Kiểm thử hai người trên iOS thật.
- [ ] Cập nhật SRS/API/hướng dẫn dev khi hành vi hoặc cấu hình thay đổi.

## Bước đang thực hiện

**Chuẩn bị production**. Bộ cấu hình Docker/Caddy, release tag, backup tự động có
xác minh/restore drill, bảo trì, health check, rollback và monitoring một instance
đã có. Bước tiếp theo là smoke test trên staging domain thật. StoreKit/APNs
production vẫn tạm hoãn vì phụ thuộc
tài khoản Apple.
