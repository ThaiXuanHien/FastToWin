# Đưa FastToWin lên Google Play internal testing

Trạng thái ngày 23/10/2026: app `com.hienthai.fastowin` đã có internal track; chủ dự án xác nhận hồ sơ thanh toán đã được duyệt. Ba sản phẩm Gem đã Active tại 174 thị trường được hỗ trợ; app Alpha đang chọn 178 quốc gia/khu vực, gồm phần còn lại của thế giới. Đây chưa phải phát hành production toàn cầu.

License testing có danh sách Billing QA riêng với một email đã được chủ dự án chọn. Service account Billing riêng đã có quyền đọc trên duy nhất Fast To Win; Google Play Developer API đã bật. Khóa thay thế được tạo trên máy, đăng ký public certificate với Cloud và kiểm tra OAuth thành công; khóa tải xuống không tìm được đã thu hồi. Private JSON giữ ngoài Git, chưa cài Railway. Khóa hiện tại hết hạn **21/01/2027**; cần thay trước hạn và kiểm tra lại trước khi dùng.

Backend hỗ trợ cổng QA cho giao dịch test do Google xác minh, nhưng chưa deploy trong bước này. Chưa có bằng chứng mua Gem Google Play trên Samsung; kiểm thử cục bộ không thay nghiệm thu provider.

## 1. Tạo app và chuẩn bị bản ký

Trong [Play Console](https://play.google.com/console/), tạo app FastToWin,
chọn ngôn ngữ mặc định, loại Game và các khai báo đúng với chủ thể phát hành.
Package được xác lập từ bundle tải lên: **`com.hienthai.fastowin`**.
Không dùng package dev **`com.hienthai.fastowin.dev`**.

Trong Android Studio, chọn **Build → Generate Signed App Bundle / APK → Android
App Bundle**, module `app`, variant **prodRelease**. Project hiện chưa có signing
config tự động; dùng upload keystore riêng và bảo quản keystore, alias và mật khẩu
ngoài Git. Bản `prodDebug` đã có chỉ dùng kiểm tra kết nối, không phải bản upload.

Kiểm tra trước khi upload:

- Application ID `com.hienthai.fastowin`; `APP_ENV=prod`.
- WebSocket mặc định `wss://fasttowin.up.railway.app/game`.
- `versionCode` hiện là `1`; mỗi lần upload tiếp theo phải tăng mã này.
- Bundle được ký bằng upload key; không dùng debug keystore.
- Không đóng gói service account backend, mật khẩu Owner hoặc dữ liệu backup.

Tạo release ở **Internal testing**, cấu hình Play App Signing theo hướng dẫn
Console, thêm email tester và lưu link tham gia test. Tester phải tham gia track
và cài app từ Google Play bằng đúng tài khoản, thay vì cài APK dev đang có.

Nguồn: [ký ứng dụng và upload key](https://developer.android.com/studio/publish/app-signing),
[chuẩn bị Play Billing và bản upload](https://developer.android.com/google/play/billing/getting-ready).

## 2. Tạo sản phẩm Gem

Tạo ba one-time product consumable, cấu hình purchase option/giá/khu vực và kích
hoạt theo màn hình Console. Chủ dự án quyết định giá và các khai báo thương mại;
không lấy giá Sandbox trong app làm giá bán.

| Product ID | Tên | Gem server cấp | Giá Việt Nam đã duyệt |
| --- | --- | ---: | ---: |
| `fasttowin_gems_80` | Gói Tân binh | 80 | 19.000 VNĐ |
| `fasttowin_gems_250` | Gói Bứt tốc | 250 | 49.000 VNĐ |
| `fasttowin_gems_650` | Gói Cao thủ | 650 | 99.000 VNĐ |

Hoàn thiện payments profile nếu Console yêu cầu. Thêm tài khoản mua thử vào
**License testing**; việc được mời vào internal track không tự biến tài khoản
thành license tester. Dùng phương thức thanh toán thử của Google để nghiệm thu.

## 3. Cho backend xác thực giao dịch

Tạo service account trong Google Cloud, bật **Google Play Android Developer API**,
mời email service account trong quyền truy cập Play Console và cấp quyền cần
thiết: đọc thông tin ứng dụng và xem dữ liệu tài chính/API giao dịch mua của riêng Fast To Win. Không cấp quyền hoàn tiền, quản trị, chỉnh sửa sản phẩm hoặc phát hành. JSON credential là secret của backend;
không dán vào issue/chat, không đưa vào APK, bundle hoặc Git.

Sau khi app và quyền sẵn sàng, đặt file credential qua cơ chế secret của môi
trường Railway, rồi cấu hình:

```text
FASTTOWIN_ENV=prod
GOOGLE_PLAY_PACKAGE_NAME=com.hienthai.fastowin
GOOGLE_PLAY_CREDENTIALS_FILE=<đường dẫn file secret Billing trên server>
GOOGLE_PLAY_TEST_PLAYER_IDS=<UUID nội bộ của đúng tài khoản QA>
```

Có thể đặt JSON trong secret `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` thay cho file Billing. Không đưa giá trị JSON vào lệnh shell, log, issue hoặc app. `GOOGLE_APPLICATION_CREDENTIALS` cũng được Firebase Admin dùng; giữ nguyên credential Firebase. Xem thứ tự ưu tiên và cách fail closed trong [store-billing.md](store-billing.md).

Chỉ điền UUID QA sau khi xác định tài khoản thực tế trên Samsung và đối chiếu thông tin trong quản trị. UUID là danh tính game, khác email Google của License testing. Chỉ tiếp tục khi màn thanh toán ghi rõ giao dịch test và dùng phương thức thanh toán thử. Không bật
collector analytics tài chính chỉ vì verifier đã cấu hình: collector có cấu hình
và nghiệm thu riêng trong [financial-analytics-sources.md](financial-analytics-sources.md).

Nguồn: [Developer API và quyền service account](https://developers.google.com/android-publisher/getting_started).

## 4. Nghiệm thu bằng license tester

- Cài bản internal bằng đúng Google license tester; không dùng emulator theo yêu cầu chủ dự án.
- Đăng nhập đúng tài khoản game có UUID trong danh sách QA; không mở test cho mọi người.
- Ba gói hiển thị giá từ Play; production không hiện giá Sandbox.
- Màn Google xác nhận đang mua test, không dùng thẻ thật. Nếu không hiện test thì dừng.
- Test đã xác minh phải ghi `SANDBOX`; thống kê LIVE không tăng, không coi Gem/giá catalog là doanh thu thật.
- Mua thành công: Gem chỉ tăng sau khi backend trả `GRANTED`; ví có đúng một dòng.
- Mua pending: chưa cộng Gem; xử lý tiếp khi Store chuyển sang purchased.
- Hủy thanh toán: ví không đổi; app cho phép thử lại.
- Mất mạng/đóng app sau khi thanh toán: mở lại khôi phục giao dịch và cấp một lần.
- Gửi lại cùng giao dịch: `ALREADY_GRANTED`, không tăng Gem lần thứ hai.
- Consume sau khi cấp Gem thành công; mua tiếp cùng gói được phép.
- Credential/API lỗi: không cộng Gem và không consume; người chơi có thể thử lại.

- Sau nghiệm thu, gỡ UUID khỏi cấu hình test và áp dụng bằng lần deploy được duyệt. Theo dõi hạn khóa, thay khóa trước khi hết hạn; giữ bản cũ đến khi xác nhận bản mới hoạt động rồi mới thu hồi.

Chỉ lưu kết quả tổng hợp, phiên bản và ảnh đã che thông tin cá nhân. Không ghi
purchase token, credential hoặc mật khẩu vào bằng chứng. Đây là nghiệm thu Google
Play; sandbox dev và unit tests không thay thế các bước này.

iOS StoreKit/APNs vẫn theo [#14](https://github.com/ThaiXuanHien/FastToWin/issues/14)
khi có tài khoản Apple và máy Mac/thiết bị phù hợp.
