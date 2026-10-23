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

Token giả lập `dev:` chỉ hoạt động khi `FASTTOWIN_ENV=dev`. Production luôn từ chối token này. Giao dịch test thật từ Google Play có cổng QA riêng bên dưới.

## Android production

Ngày 23/10/2026, app và ba sản phẩm đã được tạo trong Play Console. Sản phẩm Active tại 174 thị trường mua hàng được Console hỗ trợ; giá Việt Nam lần lượt là 19.000 / 49.000 / 99.000 VNĐ. Giá địa phương do Play cung cấp, không lấy từ bảng giá giả lập.
Xem [checklist internal testing](google-play-internal-testing.md) để theo dõi phần credential và nghiệm thu mua thật qua license tester còn lại.

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
4. Bật Google Play Android Developer API; tạo service account Billing riêng, chỉ cấp quyền đọc thông tin app và xem dữ liệu tài chính/API giao dịch mua của Fast To Win. Không cấp quyền hoàn tiền, quản trị hoặc phát hành.
5. Tải JSON credential vào máy chủ, không đưa file này vào Git.
6. Khai báo biến môi trường production:

```text
FASTTOWIN_ENV=prod
GOOGLE_PLAY_PACKAGE_NAME=com.hienthai.fastowin
GOOGLE_PLAY_CREDENTIALS_FILE=/run/secrets/google-play-billing.json
```

Có thể dùng `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` chứa toàn bộ JSON thay cho file, phù hợp với secret Railway. Thứ tự ưu tiên: JSON riêng → file Billing riêng → `GOOGLE_APPLICATION_CREDENTIALS` (tương thích cấu hình cũ). Credential riêng sai sẽ làm startup thất bại với lỗi an toàn, không tự dùng credential Firebase. Không thay biến/file Firebase đang hoạt động.

Server gọi Google Play Developer API để kiểm tra trạng thái `PURCHASED`, product ID và tài khoản đã làm mờ trước khi cộng Gem.

### Giao dịch Google Play test trên môi trường prod

Mặc định server prod từ chối mọi giao dịch có `testPurchaseContext`. Để nghiệm thu có kiểm soát, đặt `GOOGLE_PLAY_TEST_PLAYER_IDS` thành UUID nội bộ của đúng tài khoản QA, phân cách bằng dấu phẩy nếu cần. Không dùng mã người chơi công khai, email hoặc `*`. Giá trị trống giữ cổng test đóng; ID sai làm startup thất bại. Danh sách này độc lập với danh sách License testing của Google.

Giao dịch test phải được Google API xác minh, đúng sản phẩm/trạng thái, có `obfuscatedExternalAccountId` trùng SHA-256 UUID của QA và có order ID hợp lệ. Mất binding/order hoặc tài khoản ngoài danh sách đều bị từ chối. Token `dev:` vẫn bị chặn kể cả QA. Order được ghi `SANDBOX` cùng transaction cấp Gem; gửi lại không cấp thêm. Đơn thật vẫn là `LIVE` kể cả người mua là QA. Không suy ra doanh thu từ Gem hoặc giá hiển thị.

Gỡ UUID QA khỏi cấu hình sau nghiệm thu và áp dụng cấu hình bằng lần deploy được duyệt. Luồng collector tiền thật vẫn opt-in riêng; xem [financial-analytics-sources.md](financial-analytics-sources.md). Thay đổi hiện tại chưa bật verifier hoặc collector trên production.

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
