# Checklist App Store Connect cho mua Gem trên iOS

Code StoreKit 2 và phần xác thực trên server đã có (xem [store-billing.md](store-billing.md#ios-storekit-2)). Các bước dưới đây chủ app tự làm trên trình duyệt, không cần máy Mac. Server không cần khóa hay secret nào của Apple để xác thực giao dịch.

## 1. Hợp đồng, thuế, ngân hàng (bắt buộc trước khi bán)

- [ ] App Store Connect → **Business** (Agreements, Tax, and Banking): ký **Paid Apps Agreement**.
- [ ] Điền **Bank Account** nhận tiền.
- [ ] Điền **Tax Forms** (cá nhân ngoài Mỹ thường là W-8BEN).
- [ ] Đợi trạng thái hợp đồng thành **Active**. Khi chưa Active, StoreKit không trả sản phẩm, app sẽ báo thiếu sản phẩm.

## 2. Tạo ba sản phẩm Consumable

App Store Connect → app Fast To Win (ID 6835730095) → **Monetization → In-App Purchases** → **+** → **Consumable**. Product ID phải giống hệt Android:

| Reference Name | Product ID | Gem |
|---|---|---:|
| Gói Tân binh | `fasttowin_gems_80` | 80 |
| Gói Bứt tốc | `fasttowin_gems_250` | 250 |
| Gói Cao thủ | `fasttowin_gems_650` | 650 |

Với từng sản phẩm:

- [ ] Chọn giá (Price Schedule). Gợi ý cùng mức Android: 19.000 / 49.000 / 99.000 VNĐ, Apple tự quy đổi các nước khác.
- [ ] Availability: các quốc gia muốn bán.
- [ ] Localization: thêm ít nhất tiếng Việt và tiếng Anh (Display Name, Description, ví dụ "80 Gem").
- [ ] **Review Information**: tải một ảnh chụp màn hình Cửa hàng Gem trên iPhone và ghi chú ngắn (ví dụ "Consumable gems used in the in-game shop").
- [ ] Trạng thái phải thành **Ready to Submit**.

Lưu ý: In-App Purchase đầu tiên phải được gửi duyệt **cùng một bản build**. Khi tạo phiên bản app để gửi duyệt, ở mục **In-App Purchases and Subscriptions** hãy chọn cả ba gói.

## 3. Tài khoản Sandbox để thử

- [ ] **Users and Access → Sandbox → Test Accounts** → tạo một tài khoản sandbox (email chưa từng dùng Apple ID).
- [ ] Trên iPhone: **Cài đặt → App Store → Sandbox Account**, đăng nhập tài khoản sandbox (không cần đăng xuất Apple ID thật).
- [ ] Cài bản TestFlight mới (sau khi PR này được merge và chạy workflow TestFlight), mở Cửa hàng → Gem: giá phải là giá thật của App Store, không còn chữ "Sandbox".
- [ ] Mua gói 80 Gem bằng tài khoản sandbox → Gem tăng 80, **Hồ sơ → Hoạt động → Lịch sử tài sản** có dòng mua Gem.
- [ ] Thử thêm: hủy giữa chừng (không cộng Gem), tắt mạng ngay sau khi trả tiền rồi mở lại app (Gem được cộng khi có mạng, không cộng hai lần).
- [ ] Thử Ask to Buy nếu muốn: trong cài đặt tài khoản sandbox có tùy chọn giả lập.

## 4. Cấu hình server production

Không bắt buộc đổi gì: mặc định server đã nhận giao dịch App Store của bundle `com.hienthai.fastowin`, cả Production lẫn Sandbox. Apple duyệt app bằng tài khoản Sandbox, nên **giữ `APP_STORE_ALLOW_SANDBOX` không đặt (hoặc `true`) khi gửi duyệt**.

Tùy chọn sau khi app đã lên App Store: đặt `APP_STORE_ALLOW_SANDBOX=false` và `APP_STORE_TEST_PLAYER_IDS=<UUID nội bộ tài khoản QA>` trên Railway để tester TestFlight khác không nhận Gem miễn phí. Phải bật lại `true` trước mỗi lần gửi duyệt bản mới.

## 5. Chưa cần làm lúc này

- **App Store Server Notifications URL**: chưa có endpoint (Android cũng chưa thu hồi Gem khi hoàn tiền). Để trống.
- **In-App Purchase Key / App Store Server API key**: không cần, server xác thực JWS offline bằng chứng chỉ gốc của Apple.
- **Shared Secret**: chỉ dùng cho receipt kiểu cũ và subscription, không cần.

## 6. Khi gửi duyệt

- [ ] Ghi trong App Review Notes: tài khoản game để reviewer đăng nhập (Cửa hàng Gem yêu cầu đăng nhập) và đường dẫn tới Cửa hàng → Gem.
- [ ] Gắn ba In-App Purchase vào phiên bản gửi duyệt (mục 2).
