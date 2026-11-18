# Kế hoạch sau khi phát hành công khai

Lập ngày 18/11/2026. Dùng sau khi Android lên Production trên Google Play và iOS được Apple duyệt.

## Mốc thời gian hiện tại

| Mốc | Ngày dự kiến | Ghi chú |
|---|---|---|
| Android đủ 14 ngày closed testing | khoảng 01/12/2026 | Cần ít nhất 12 người thử liên tục, nên mời dư người. Sau đó vào Play Console bấm **Apply for production access**. |
| iOS 1.0 gửi Apple duyệt | sau khi build TestFlight 1.0 (5) xong | Còn: chọn build, gắn 3 gói Gem, tài khoản cho reviewer. Xem [app-store-iap-checklist.md](app-store-iap-checklist.md). |
| Xóa database cũ ở sfo | 24/11/2026 | Đã có lịch; Owner đã đồng ý, kiểm tra an toàn trước khi xóa. |
| Diễn tập khôi phục backup | ngày 1 hằng tháng | Theo [infrastructure-runbook.md](infrastructure-runbook.md). |
| Đổi token R2 ảnh đại diện | trước 15/01/2027 | Có lịch nhắc ngày 05/01/2027; Owner tạo token mới. |

## 1. Ngay khi app public (trong ngày)

- [ ] Tải app từ store bằng tài khoản thường (không phải tester): đăng nhập, chơi 1 trận, mở Cửa hàng → Gem xem giá thật.
- [ ] Xóa `GOOGLE_PLAY_TEST_PLAYER_IDS` trên server production; rà lại các tài khoản QA và tài khoản "Play Reviewer".
- [ ] Theo dõi CPU/RAM, Postgres, Redis (Railway, Grafana) trong 1–2 ngày đầu. Production hiện chạy **1 server**, nên lượng người tăng đột ngột là rủi ro lớn nhất.

## 2. Tuần đầu

- [ ] **Báo lỗi crash** (ưu tiên cao): app chưa có công cụ thu thập crash. Thêm Firebase Crashlytics cho Android và iOS.
- [ ] **Bật quảng cáo có thưởng (#51) cho Android và iOS cùng lúc** (ưu tiên cao):
  - Liên kết app AdMob với trang store để AdMob chuyển sang trạng thái **Ready**.
  - Đặt file `app-ads.txt` trên web.
  - Cập nhật Play Data safety (quảng cáo, Advertising ID) và App Privacy của Apple.
  - Cấu hình consent cho người dùng EU.
  - Làm phần iOS: `RewardedAds.ios.kt` hiện là bản rỗng.
  - Thử bằng quảng cáo test, sau cùng mới đặt `FASTTOWIN_ADMOB_ENABLED=true`.
  - Hiện nút xem quảng cáo tự ẩn khi quảng cáo tắt, nên chưa bật vẫn an toàn.
- [ ] **Doanh thu thật trên dashboard admin (#24)**: bật `FINANCIAL_GOOGLE_ENABLED` cùng mốc `FINANCIAL_COLLECTION_START`. App Store chưa có nguồn số liệu. Xem [financial-analytics-sources.md](financial-analytics-sources.md).
- [ ] Đọc đánh giá trên Play và App Store, cùng Android vitals; trả lời sớm các đánh giá xấu.

## 3. Khi người chơi tăng dần

- [ ] **Thu hồi Gem khi khách được hoàn tiền**, làm cho cả hai nền tảng: Google Play Real-time Developer Notifications hoặc Voided Purchases API, và App Store Server Notifications V2. Nên có trước khi doanh thu lớn.
- [ ] **Load test (SCL-01)** để biết 1 server chịu được bao nhiêu người.
- [ ] Sau đó làm **nhiều server và cân bằng tải (#15)**, tức nhóm C/D của checklist nâng cấp. Branch `codex/room-ownership-fix` đang làm dở: lần triển khai 14/11 đã rollback, phải merge master trước khi làm tiếp.
- [ ] **Cache ảnh đại diện qua CDN (#11)**: ảnh đã lưu ở R2, nhưng vẫn đi qua server rồi mới tới người chơi. Làm cùng đợt chạy nhiều server.
- [ ] **Bật Cloudflare Turnstile** nếu thấy đăng ký bất thường. Code đã có nhưng đang tắt; Owner đăng nhập Cloudflare để lấy khóa.

## Việc nhỏ đã ghi lại

- Khi người chơi tắt công tắc push chung trên máy, server vẫn giữ mã nhận push, nên báo cáo thông báo admin tính là "thất bại" thay vì "không có địa chỉ push". Người chơi không bị ảnh hưởng. Chi tiết trong #22.
