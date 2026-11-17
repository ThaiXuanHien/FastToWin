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

Gỡ UUID QA khỏi cấu hình sau nghiệm thu và áp dụng cấu hình bằng lần deploy được duyệt. Bộ đồng bộ doanh thu dùng chung thứ tự chọn credential ở trên, nhưng vẫn opt-in riêng bằng `FINANCIAL_GOOGLE_ENABLED=true` và cần mốc `FINANCIAL_COLLECTION_START` hợp lệ; cấu hình Billing không tự bật thu thập. Credential riêng sai khiến collector không khả dụng, không tự dùng credential khác. Xem [financial-analytics-sources.md](financial-analytics-sources.md). Thay đổi cấu hình nguồn này chưa được deploy hoặc bật collector trên production.

## iOS (StoreKit 2)

Luồng mua giống Android, chỉ khác Store:

1. Swift host `iosApp/iosApp/StoreKitCoordinator.swift` sở hữu StoreKit 2 và cài cầu nối `IosStoreKitBridge` khi app khởi động. Logic cửa hàng dùng chung nằm ở `StoreKitBillingGateway` (commonMain, có test).
2. App tải giá bằng `Product.products(for:)`; giá hiển thị là `displayPrice` địa phương do App Store trả về.
3. Khi mua, app gắn `appAccountToken` = UUID suy ra từ SHA-256 của ID tài khoản (`appStoreAccountToken`, server dùng đúng công thức). Apple không thấy ID nội bộ.
4. Giao dịch đã xác minh trên máy được gửi lên server dưới dạng JWS (`jwsRepresentation`) qua cùng `/api/store-purchases/verify`.
5. Chỉ gọi `transaction.finish()` sau khi server trả `GRANTED` hoặc `ALREADY_GRANTED`. Giao dịch chưa finish (tắt app, mất mạng, server lỗi) được đọc lại từ `Transaction.unfinished` mỗi lần mở Cửa hàng.
6. `Transaction.updates` nhận Ask to Buy được duyệt, giao dịch bị gián đoạn và hoàn tiền. Mua đang chờ (`.pending`) hiện thông báo chờ, không cộng Gem. Giao dịch đã bị thu hồi trước khi cấp thì chỉ finish, không gửi lên server.

Server (`AppStorePurchaseVerifier`) xác thực offline, không cần secret nào của Apple:

- JWS phải là ES256, chuỗi `x5c` gồm leaf → intermediate → Apple Root CA - G3 (đóng gói trong `server/src/main/resources/appstore`, kiểm tra SHA-256 khi khởi động), leaf/intermediate có OID đánh dấu của Apple, chuỗi hợp lệ tại thời điểm `signedDate`;
- `bundleId` = `com.hienthai.fastowin`, `productId` khớp gói, `type` = `Consumable`, `quantity` = 1, không có `revocationDate`, `appAccountToken` khớp tài khoản gửi lên;
- khóa chống nhận trùng là `transactionId` + môi trường (`LIVE`/`SANDBOX`), không phải chuỗi JWS, vì mỗi lần StoreKit trả về JWS được ký lại.

Cấu hình server (đều tùy chọn, mặc định đã bật):

```text
APP_STORE_BUNDLE_ID=com.hienthai.fastowin   # mặc định
APP_STORE_ALLOW_SANDBOX=true                # mặc định; App Review và TestFlight luôn mua bằng Sandbox
APP_STORE_TEST_PLAYER_IDS=<UUID QA>         # chỉ dùng khi APP_STORE_ALLOW_SANDBOX=false
APP_STORE_BILLING_ENABLED=false             # tắt hẳn mua iOS khi cần
```

Apple duyệt app trên bản production nhưng mua bằng tài khoản Sandbox, nên server production phải nhận giao dịch `Sandbox`, nếu không app sẽ bị từ chối. Hệ quả: tester TestFlight nhận Gem mà không mất tiền. Sau khi app đã lên App Store, có thể đặt `APP_STORE_ALLOW_SANDBOX=false` cùng `APP_STORE_TEST_PLAYER_IDS`, nhưng phải bật lại trước mỗi lần gửi duyệt. Môi trường `Xcode` (StoreKit Configuration cục bộ) chỉ được nhận khi `FASTTOWIN_ENV=dev`.

Chưa có: App Store Server Notifications V2 (thu hồi Gem khi hoàn tiền) và đồng bộ doanh thu App Store. Android hiện cũng chưa thu hồi Gem khi hoàn tiền, nên hai nền tảng đang giống nhau. Giao dịch App Store không ghi vào `store_financial_orders`.

Token `dev:APP_STORE:` vẫn dùng được với server dev khi StoreKit không trả sản phẩm (ví dụ simulator không có StoreKit Configuration).

Việc chủ app cần làm trong App Store Connect: xem [app-store-iap-checklist.md](app-store-iap-checklist.md).

## Chống gian lận và nhận trùng

- Token chỉ được xử lý sau xác thực Store.
- Server lưu SHA-256 của token, không lưu token gốc.
- Unique key `(store, transaction_id)` ngăn một giao dịch cấp Gem hai lần.
- Giao dịch Store và lịch sử ví được ghi trong cùng database transaction.
- Token đã thuộc người chơi khác sẽ bị từ chối.
- Xác thực giao dịch có rate limit riêng theo người chơi và IP.

Tài liệu Google: [tích hợp Play Billing](https://developer.android.com/google/play/billing/integrate), [vòng đời one-time purchase](https://developer.android.com/google/play/billing/lifecycle/one-time), [release notes PBL](https://developer.android.com/google/play/billing/release-notes).
