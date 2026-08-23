# Cửa hàng Gem và Store Billing

Fast To Win dùng máy chủ làm nguồn quyết định số Gem. Ứng dụng chỉ lấy giá đã bản địa hóa từ Store, mở giao diện thanh toán và gửi token giao dịch lên server. Gem chỉ được cộng sau khi server xác thực thành công.

## Gói sản phẩm

Tạo đúng ba one-time product dạng consumable trên Store:

| Product ID | Tên trong game | Gem |
|---|---|---:|
| `fasttowin_gems_80` | Gói Tân binh | 80 |
| `fasttowin_gems_250` | Gói Bứt tốc | 250 |
| `fasttowin_gems_650` | Gói Cao thủ | 650 |

Không gửi số Gem từ client để tránh sửa request. Product ID và số Gem luôn được đối chiếu với catalog cố định trên server.

## Test nhanh ở môi trường dev

1. Chạy PostgreSQL và server bằng `start-dev-server-with-db.cmd`.
2. Đăng nhập bằng tài khoản, mở **Cửa hàng → Gem**.
3. Nếu app không được cài từ Google Play test track hoặc sản phẩm chưa tồn tại, bản dev tự chuyển sang giá `Sandbox`.
4. Nhấn gói Gem. Server dev xác thực token `dev:` và cộng Gem một lần.
5. Mở **Hồ sơ → Hoạt động → Lịch sử tài sản** để kiểm tra dòng `Mua Gem từ cửa hàng`.

Sandbox chỉ hoạt động khi `FASTTOWIN_ENV=dev`. Production không chấp nhận token thử nghiệm.

## Android production

Android đang dùng Google Play Billing Library 9.1.0 với:

- `ProductDetails` cho one-time products;
- tự kết nối lại Billing Service;
- hỗ trợ giao dịch pending;
- khôi phục giao dịch đã mua nhưng chưa xử lý;
- gửi `purchaseToken` lên backend trước khi cấp Gem;
- consume giao dịch sau khi server trả về `GRANTED` hoặc `ALREADY_GRANTED`.

Cấu hình Google Play Console:

1. Tạo app có package `com.hienthai.fastowin`.
2. Tạo và kích hoạt ba one-time product trong bảng trên.
3. Tạo license tester và phát hành app lên internal testing track.
4. Tạo service account có quyền xem đơn hàng/giao dịch của app.
5. Tải JSON credential vào máy chủ, không đưa file này vào Git.
6. Khai báo biến môi trường production:

```text
FASTTOWIN_ENV=prod
GOOGLE_PLAY_PACKAGE_NAME=com.hienthai.fastowin
GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/google-play-service-account.json
```

Server gọi Google Play Developer API để kiểm tra trạng thái `PURCHASED`, product ID và tài khoản đã làm mờ trước khi cộng Gem.

## iOS

Trên Windows, iOS hiện có gateway sandbox để kiểm tra đầy đủ UI → server → ví. Khi `FASTTOWIN_ENV=prod`, giao dịch App Store bị từ chối an toàn.

Trước khi phát hành iOS cần làm trên macOS/Xcode:

1. Tạo ba consumable In-App Purchase với cùng product ID trong App Store Connect.
2. Tích hợp StoreKit 2 và lấy signed transaction JWS.
3. Xác thực JWS/App Store Server API ở backend.
4. Chỉ gọi `finish()` sau khi server đã cấp Gem.
5. Test StoreKit Configuration, sandbox account và TestFlight.

## Chống gian lận và nhận trùng

- Token chỉ được xử lý sau xác thực Store.
- Server lưu SHA-256 của token, không lưu token gốc.
- Unique key `(store, transaction_id)` ngăn một giao dịch cấp Gem hai lần.
- Giao dịch Store và lịch sử ví được ghi trong cùng database transaction.
- Token đã thuộc người chơi khác sẽ bị từ chối.
- Xác thực giao dịch có rate limit riêng theo người chơi và IP.

Tài liệu Google: [tích hợp Play Billing](https://developer.android.com/google/play/billing/integrate), [vòng đời one-time purchase](https://developer.android.com/google/play/billing/lifecycle/one-time), [release notes PBL](https://developer.android.com/google/play/billing/release-notes).
