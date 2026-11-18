# Checklist App Store Connect cho mua Gem trên iOS

Code StoreKit 2 và phần xác thực trên server đã có (xem [store-billing.md](store-billing.md#ios-storekit-2)). Các bước dưới đây chủ app tự làm trên trình duyệt, không cần máy Mac. Server không cần khóa hay secret nào của Apple để xác thực giao dịch.

Cập nhật lần cuối: 18/11/2026.

## Tóm tắt tiến độ

| Hạng mục | Trạng thái |
|---|---|
| Code StoreKit 2 + xác thực JWS trên server | ✅ Merge PR #93, deploy production 18/11/2026 |
| Bản TestFlight có StoreKit | ✅ Build 1.0 (4) |
| Hợp đồng, ngân hàng, thuế | ✅ Active (18/11/2026) |
| Digital Services Act (EU) | ⏳ Đã khai trader, Apple đang duyệt |
| Ba sản phẩm Consumable | ✅ Đã tạo đủ thông tin, chờ gửi duyệt cùng bản app |
| Mua thử trên TestFlight | ⏳ Apple chưa trả sản phẩm (chờ đồng bộ sau khi hợp đồng Active) |
| Gửi duyệt bản 1.0 | ⬜ Chưa làm |

## 1. Hợp đồng, thuế, ngân hàng (bắt buộc trước khi bán)

- [x] App Store Connect → **Business**: cập nhật Legal Entity và ký **Paid Apps Agreement**.
- [x] Điền **Bank Account** nhận tiền. Tài khoản VND tại Vietcombank; trong danh sách ngân hàng của Apple, Vietcombank có tên "Joint Stock Commercial Bank for Foreign Trade of Vietnam", tìm theo từ khóa "Foreign Trade". Mã CITAD là mã 8 số của chi nhánh. Ô Date of Birth dùng định dạng `mm/dd/yy`.
- [x] Điền **Tax Forms**: U.S. Certificate of Foreign Status of Beneficial Owner và W-8BEN, cả hai đều Active. Không khai Part II (treaty), vì hiệp định thuế Việt Nam – Mỹ chưa có hiệu lực. Hệ quả: Apple khấu trừ 30% trên doanh thu bán cho người mua ở Mỹ.
- [x] Trạng thái hợp đồng: **Active**.
- [ ] **Digital Services Act**: đã khai **trader**, chọn ngôn ngữ giấy tờ English (Asia) và nộp giấy tờ. Trạng thái **In Review**. Khi được duyệt, app được bán ở 27 nước EU; địa chỉ, số điện thoại và email đã khai sẽ hiển thị công khai trên App Store EU.

## 2. Tạo ba sản phẩm Consumable

App Store Connect → app Fast To Win (ID 6835730095) → **Monetization → In-App Purchases**. Product ID giống hệt Android:

| Reference Name | Product ID | Gem | Giá cơ sở (VN) | Apple ID |
|---|---|---:|---:|---|
| Gói Tân binh | `fasttowin_gems_80` | 80 | 19.000 ₫ | 6836117189 |
| Gói Bứt tốc | `fasttowin_gems_250` | 250 | 49.000 ₫ | 6836117947 |
| Gói Cao thủ | `fasttowin_gems_650` | 650 | 99.000 ₫ | 6836119837 |

Với từng sản phẩm:

- [x] Price Schedule: giá cơ sở theo Việt Nam (VND); Apple tự quy đổi cho 174 nước khác (gói 80 Gem khoảng 0,99 USD).
- [x] Availability: tất cả 175 quốc gia và khu vực.
- [x] Localization tiếng Việt và tiếng Anh, ví dụ "80 Gem" / "80 Gems", "Nhận 80 Gem dùng trong cửa hàng game" / "80 gems for the in-game shop".
- [x] **Review Information**:
  - Ảnh chụp màn hình tab Cửa hàng → Gem, kích thước 1179×2556, dựng từ đúng giao diện app bằng công cụ screenshot test. Ảnh gốc lưu ngoài Git: `gem-shop-review-1179x2556.png` ở thư mục gốc dự án.
  - Review Notes: "Consumable gems used in the in-game shop. Sign in to a game account, then open Shop > Gems."
- [x] Thông tin đã đủ: nút **Add for Review** đã bật. Với gói In-App Purchase đầu tiên, trạng thái vẫn là "Prepare for Submission" cho tới khi gửi duyệt cùng bản app.

Lưu ý: In-App Purchase đầu tiên phải được gửi duyệt **cùng một bản build**. Khi tạo phiên bản app để gửi duyệt, ở mục **In-App Purchases and Subscriptions** hãy chọn cả ba gói.

## 3. Mua thử

Bản cài qua **TestFlight** tự động dùng môi trường Sandbox với chính Apple ID thật của tester và không trừ tiền, nên **không cần tài khoản Sandbox**. Mục "Sandbox Account" trong Cài đặt chỉ hiện trên máy đã chạy bản build trực tiếp từ Xcode; tài khoản Sandbox chỉ cần khi build bằng Xcode trên Mac.

- [x] Cài bản TestFlight **1.0 (4)**, bản có StoreKit 2. Các build 2 và 3 không có StoreKit.
- [ ] Mở Cửa hàng → Gem: giá phải là 19.000 / 49.000 / 99.000 ₫.
  - Ngày 18/11/2026 app báo không có gói Gem, tức là StoreKit trả về danh sách rỗng.
  - Nguyên nhân: hợp đồng vừa Active và Apple chưa đồng bộ, thường mất vài giờ đến 24 giờ.
  - Nếu sau 24 giờ vẫn rỗng, kiểm tra lại trạng thái sản phẩm và cấu hình In-App Purchase của App ID.
- [ ] Mua gói 80 Gem (bảng thanh toán ghi Sandbox): Gem tăng 80, và **Hồ sơ → Hoạt động → Lịch sử tài sản** có đúng một dòng mua Gem.
- [ ] Thử thêm:
  - Hủy giữa chừng: không được cộng Gem.
  - Tắt mạng ngay sau khi trả tiền rồi mở lại app: Gem được cộng khi có mạng, và không cộng hai lần.

## 4. Cấu hình server production

Không bắt buộc đổi gì: mặc định server đã nhận giao dịch App Store của bundle `com.hienthai.fastowin`, cả Production lẫn Sandbox. Apple duyệt app bằng giao dịch Sandbox, nên **giữ `APP_STORE_ALLOW_SANDBOX` không đặt (hoặc `true`) khi gửi duyệt**.

Tùy chọn sau khi app đã lên App Store: đặt `APP_STORE_ALLOW_SANDBOX=false` và `APP_STORE_TEST_PLAYER_IDS=<UUID nội bộ tài khoản QA>` trên Railway, để tester TestFlight khác không nhận Gem miễn phí. Phải bật lại `true` trước mỗi lần gửi duyệt bản mới.

## 5. Chưa cần làm lúc này

- **App Store Server Notifications URL**: chưa có endpoint (Android cũng chưa thu hồi Gem khi hoàn tiền). Để trống.
- **In-App Purchase Key / App Store Server API key**: không cần, server xác thực JWS offline bằng chứng chỉ gốc của Apple.
- **Shared Secret**: chỉ dùng cho receipt kiểu cũ và subscription, không cần.

## 6. Khi gửi duyệt

- [ ] Ghi trong App Review Notes: tài khoản game để reviewer đăng nhập (Cửa hàng Gem yêu cầu đăng nhập) và đường dẫn tới Cửa hàng → Gem.
- [ ] Gắn ba In-App Purchase vào phiên bản gửi duyệt (mục 2).
- [ ] Giữ `APP_STORE_ALLOW_SANDBOX` là `true` trên server production (mục 4).
