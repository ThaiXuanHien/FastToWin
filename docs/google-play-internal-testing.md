# Đưa FastToWin lên Google Play internal testing

Trạng thái ngày 11/10/2026: chủ dự án mới có tài khoản Google Play Console.
Chưa xác nhận app, sản phẩm, upload key hoặc credential Developer API.
Android Billing và backend verifier đã có code; chưa nghiệm thu mua qua Google Play.

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

| Product ID | Tên | Gem server cấp |
| --- | --- | ---: |
| `fasttowin_gems_80` | Gói Tân binh | 80 |
| `fasttowin_gems_250` | Gói Bứt tốc | 250 |
| `fasttowin_gems_650` | Gói Cao thủ | 650 |

Hoàn thiện payments profile nếu Console yêu cầu. Thêm tài khoản mua thử vào
**License testing**; việc được mời vào internal track không tự biến tài khoản
thành license tester. Dùng phương thức thanh toán thử của Google để nghiệm thu.

## 3. Cho backend xác thực giao dịch

Tạo service account trong Google Cloud, bật **Google Play Android Developer API**,
mời email service account trong quyền truy cập Play Console và cấp quyền cần
thiết cho app/đơn hàng theo tài liệu Google. JSON credential là secret của backend;
không dán vào issue/chat, không đưa vào APK, bundle hoặc Git.

Sau khi app và quyền sẵn sàng, đặt file credential qua cơ chế secret của môi
trường Railway, rồi cấu hình:

```text
FASTTOWIN_ENV=prod
GOOGLE_PLAY_PACKAGE_NAME=com.hienthai.fastowin
GOOGLE_APPLICATION_CREDENTIALS=<đường dẫn file secret trên server>
```

`GOOGLE_APPLICATION_CREDENTIALS` cũng được Firebase Admin sử dụng trong project.
Không thay credential production đang dùng mà chưa đối chiếu quyền Firebase;
hoặc cấp quyền Play cần thiết cho đúng service account đã được chọn. Không bật
collector analytics tài chính chỉ vì verifier đã cấu hình: collector có cấu hình
và nghiệm thu riêng trong [financial-analytics-sources.md](financial-analytics-sources.md).

Nguồn: [Developer API và quyền service account](https://developers.google.com/android-publisher/getting_started).

## 4. Nghiệm thu bằng license tester

- Ba gói hiển thị giá từ Play; production không hiện giá Sandbox.
- Mua thành công: Gem chỉ tăng sau khi backend trả `GRANTED`; ví có đúng một dòng.
- Mua pending: chưa cộng Gem; xử lý tiếp khi Store chuyển sang purchased.
- Hủy thanh toán: ví không đổi; app cho phép thử lại.
- Mất mạng/đóng app sau khi thanh toán: mở lại khôi phục giao dịch và cấp một lần.
- Gửi lại cùng giao dịch: `ALREADY_GRANTED`, không tăng Gem lần thứ hai.
- Consume sau khi cấp Gem thành công; mua tiếp cùng gói được phép.
- Credential/API lỗi: không cộng Gem và không consume; người chơi có thể thử lại.

Chỉ lưu kết quả tổng hợp, phiên bản và ảnh đã che thông tin cá nhân. Không ghi
purchase token, credential hoặc mật khẩu vào bằng chứng. Đây là nghiệm thu Google
Play; sandbox dev và unit tests không thay thế các bước này.

iOS StoreKit/APNs vẫn theo [#14](https://github.com/ThaiXuanHien/FastToWin/issues/14)
khi có tài khoản Apple và máy Mac/thiết bị phù hợp.
