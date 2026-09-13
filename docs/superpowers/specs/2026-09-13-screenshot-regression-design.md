# Thiết kế screenshot regression cho giao diện 2D Arcade

**Ngày:** 13/09/2026

**Trạng thái:** Đã thống nhất hướng tiếp cận, chờ duyệt bản đặc tả trước khi lập kế hoạch triển khai

## 1. Mục tiêu

Thiết lập một hàng rào kiểm thử hình ảnh cho giao diện 2D Arcade của Fast To
Win. Mỗi màn hình cốt lõi được render từ dữ liệu cố định ở ba kích thước thiết
bị, chụp thành PNG và so sánh với ảnh chuẩn đã được chủ dự án duyệt. Khi giao
diện thay đổi ngoài ý muốn, CI phải thất bại và cung cấp ảnh chuẩn, ảnh thực tế,
ảnh chênh lệch cùng báo cáo HTML để xác định lỗi trực quan.

Hệ thống này bổ sung cho Compose UI test và Web E2E hiện tại. Screenshot test
chịu trách nhiệm phát hiện thay đổi về bố cục, màu sắc, icon, ảnh nền, khoảng
cách và kiểu chữ; các bộ test hiện có vẫn chịu trách nhiệm kiểm tra hành vi,
callback, điều hướng, responsive của trình duyệt và luồng nhiều người chơi.

## 2. Quyết định đã chốt

- Dùng **Compose Preview Screenshot Testing** chính thức của Android trong
  module Android `app`.
- Chạy screenshot test ở biến thể `devDebug`; không thêm screenshot test trực
  tiếp vào module KMP `shared` vì plugin chưa hỗ trợ KMP.
- Bao phủ 10 màn cốt lõi: Trang chủ, Phòng chơi, Ván chơi, Kết quả, Hồ sơ, Cửa
  hàng, Xếp hạng, Bang hội, Đấu giải và Thông báo.
- Mỗi màn có ba ảnh chuẩn: điện thoại nhỏ `320 x 568 dp`, điện thoại lớn
  `430 x 932 dp` và tablet `840 x 1180 dp`. Đây là ba kích thước đã được dùng
  trong bộ responsive test của project.
- Lần đầu có đúng 30 ảnh chuẩn, dùng giao diện tối 2D Arcade, locale tiếng Việt,
  cỡ chữ chuẩn và dữ liệu có ý nghĩa thay vì màn rỗng.
- CI chỉ chạy xác thực. CI không được tạo commit hoặc tự cập nhật ảnh chuẩn khi
  có khác biệt.
- Việc cập nhật ảnh chuẩn là thao tác thủ công có chủ ý, phải xem ảnh và Git
  diff trước khi commit.
- Web E2E tiếp tục kiểm tra Chromium, Firefox, WebKit, kích thước trình duyệt và
  hành vi responsive. Không dùng ảnh Android Preview làm chuẩn pixel cho Web.

## 3. Phạm vi

### 3.1. Trong phạm vi

- Cấu hình plugin screenshot, dependency và source set riêng trong `app`.
- Fixture thuần, cố định cho 10 trạng thái màn hình.
- Multi-preview ba kích thước cho mỗi màn.
- Tạo và lưu ảnh PNG chuẩn trong Git.
- Gradle task để cập nhật có chủ ý và task để kiểm tra ảnh.
- Job CI độc lập chạy host-side, không cần emulator.
- Artifact khi thất bại gồm báo cáo HTML và toàn bộ đầu ra ảnh so sánh.
- Tài liệu cho contributor về cách xem lỗi và cập nhật baseline.
- Cập nhật roadmap sau khi bộ ảnh đã được duyệt và CI hoạt động.

### 3.2. Ngoài phạm vi của đợt đầu

- Screenshot iOS, Web hoặc ảnh có phần giao diện hệ điều hành.
- Chụp mọi dialog, mọi tab, mọi trạng thái rỗng/lỗi/loading của từng màn.
- Ma trận 12 ngôn ngữ, chữ trợ năng lớn, landscape hoặc theme sáng.
- Golden test cho animation theo từng frame.
- Thay thế Android Compose UI test hoặc Playwright Web E2E.
- Tự động duyệt thay đổi hình ảnh hoặc tự ghi đè baseline trong CI.

Các trạng thái trên có thể được bổ sung theo rủi ro sau khi bộ 30 ảnh đầu tiên
ổn định. Đợt đầu ưu tiên tín hiệu CI rõ ràng, thời gian chạy hợp lý và quy trình
duyệt dễ kiểm soát.

## 4. Kiến trúc kiểm thử

### 4.1. Vị trí tích hợp

Plugin `com.android.compose.screenshot` được khai báo qua version catalog và áp
dụng vào module `app`. Project bật đồng thời:

- `android.experimental.enableScreenshotTest=true` trong `gradle.properties`;
- experimental property tương ứng trong block `android` của `app`;
- `screenshotTestImplementation` cho `screenshot-validation-api` và Compose
  `ui-tooling`.

Phiên bản plugin được pin là `0.0.1-alpha15` trong version catalog, tương thích
với yêu cầu hiện tại của AGP `9.1.0`, Kotlin `2.4.10` và JDK 17. Không dùng
dynamic version; mọi lần nâng plugin sau này phải là một thay đổi riêng có chạy
lại đủ 30 ảnh.

Các preview test đặt tại:

```text
app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/
```

Ảnh chuẩn của biến thể `devDebug` đặt tại:

```text
app/src/screenshotTestDevDebug/reference/
```

Module `shared` tiếp tục chứa UI production. Source set screenshot của `app`
gọi các composable công khai từ `shared` và truyền fixture trực tiếp, không mở
WebSocket, database, Firebase, quảng cáo hoặc API bên ngoài.

### 4.2. Cấu trúc file

Để tránh một file fixture hoặc preview quá lớn, bộ kiểm thử được chia theo vai
trò:

```text
app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/
├── ArcadeScreenshotDevices.kt
├── ArcadeScreenshotFixtures.kt
├── ArcadeCoreScreenshotTest.kt
└── ArcadeFeatureScreenshotTest.kt
```

- `ArcadeScreenshotDevices.kt` định nghĩa multi-preview dùng chung cho ba kích
  thước đã chốt.
- `ArcadeScreenshotFixtures.kt` tạo profile, phòng, trận, mùa, bang, giải đấu,
  thông báo và callback no-op có tính quyết định.
- `ArcadeCoreScreenshotTest.kt` chứa Trang chủ, Phòng chơi, Ván chơi, Kết quả và
  Hồ sơ.
- `ArcadeFeatureScreenshotTest.kt` chứa Cửa hàng, Xếp hạng, Bang hội, Đấu giải
  và Thông báo.

Mỗi hàm ảnh có một tên ổn định, gắn `@PreviewTest` và multi-preview. Không đổi
tên hàm tùy tiện vì tên hàm cùng tham số preview được dùng để liên kết ảnh
chuẩn.

### 4.3. Khung render thống nhất

Mọi preview đi qua một wrapper duy nhất:

1. Cố định locale là tiếng Việt.
2. Dùng `FastToWinTheme` và nền tối 2D Arcade.
3. Bao nội dung bằng surface toàn kích thước preview.
4. Không hiển thị status bar/navigation bar của hệ điều hành.
5. Không đọc kích thước cửa sổ thật, thời gian hệ thống, mạng hoặc dữ liệu máy.

Nếu một màn hình hiện phụ thuộc thời gian, random hoặc animation vô hạn, chỉ bổ
sung seam nhỏ với giá trị production mặc định, ví dụ clock/state truyền vào.
Screenshot fixture truyền giá trị cố định. Không thay đổi hình ảnh production
chỉ để làm test pass.

## 5. Ma trận ảnh chuẩn

Ba cấu hình áp dụng giống nhau cho cả 10 màn:

| Tên cấu hình | Kích thước | Font scale | Locale | Theme |
| --- | ---: | ---: | --- | --- |
| Điện thoại nhỏ | 320 x 568 dp | 1.0 | `vi` | 2D Arcade tối |
| Điện thoại lớn | 430 x 932 dp | 1.0 | `vi` | 2D Arcade tối |
| Tablet | 840 x 1180 dp | 1.0 | `vi` | 2D Arcade tối |

Landscape và font scale lớn đã có Compose UI test riêng. Chúng chỉ được thêm
vào screenshot matrix sau khi có lỗi thực tế đáng bảo vệ, tránh tăng baseline
và thời gian review thiếu kiểm soát.

## 6. Trạng thái chuẩn của từng màn

| Màn hình | Trạng thái được chụp | Thành phần phải nhìn thấy |
| --- | --- | --- |
| Trang chủ | Tài khoản đã đăng nhập, đã có tiến trình mùa và lượt online | Header avatar/ví/cấp, CTA chơi, điểm danh, lối tắt tính năng và bottom bar |
| Phòng chơi | Tab công khai có nhiều phòng hợp lệ | Header, tab công khai/riêng tư, item phòng, icon trạng thái, nút tạo/tham gia |
| Ván chơi | Trận 1 vs 1 đang diễn ra với một phần số đã chọn | Hai người chơi, điểm, thời gian, số tiếp theo, bàn 50 số và hành động trận |
| Kết quả | Người hiện tại thắng một trận đấu hạng | Kết quả thắng, tỷ số, Elo/phần thưởng và các nút hành động |
| Hồ sơ | Tab tổng quan của tài khoản cấp cao nhưng chưa đạt trần XP giả | Avatar/khung/danh hiệu, cấp và XP lũy tiến, thống kê, các mục hồ sơ |
| Cửa hàng | Tab Vàng với ba gói đổi, số dư đủ mua | Hai tab Gem/Vàng, ví, ba gói đổi và CTA rõ ràng |
| Xếp hạng | Bảng cá nhân theo Elo có top 3 và người hiện tại | Tab cá nhân/bang, tab chỉ số, huy hiệu thứ tự, tên/khung và giá trị hạng |
| Bang hội | Người chơi đã ở trong bang và là thành viên thường | Logo, cấp/XP bang, tổng quyên góp, đóng góp cá nhân, nhiệm vụ và thành viên |
| Đấu giải | Giải 8 người đang ở giai đoạn nhánh đấu | Header, tab, thông tin giải và nhánh đấu có dữ liệu |
| Thông báo | Danh sách trộn thông báo chưa đọc và đã đọc | Header actions, trạng thái đọc, nhiều loại thông báo và thời gian cố định |

Fixture dùng asset local đã commit. Avatar không tải qua URL. Chuỗi thời gian
hiển thị được cố định để ảnh không thay đổi theo ngày chạy test. Danh sách đủ
dài để thể hiện cấu trúc, nhưng ảnh chỉ chụp viewport đầu; load-more vẫn do UI
test hành vi kiểm tra.

## 7. Tính quyết định và độ ổn định

Screenshot test phải cho cùng một ảnh khi chạy lặp lại trên cùng revision:

- Không gọi backend và không dùng tài khoản dev trong database.
- Không tải ảnh từ internet hoặc filesystem ngoài repository.
- Không dùng `Clock.System.now()`, UUID ngẫu nhiên hoặc thứ tự map không ổn
  định trong nội dung render.
- Tắt con trỏ nhập liệu, snackbar tự hết hạn và trạng thái loading luân phiên.
- Trạng thái số đã chọn, timer, điểm, XP và thời gian thông báo là hằng số fixture.
- Callback đều là no-op; test ảnh không mô phỏng thao tác.
- Danh sách fixture có thứ tự khai báo rõ ràng.

Không đặt ngưỡng pixel riêng trong đợt đầu. Dùng comparator mặc định của plugin
để mọi khác biệt hình ảnh được báo cáo. Nếu CI chứng minh có nhiễu render liên
nền tảng, chỉ điều chỉnh comparator sau khi lưu mẫu lỗi và ghi rõ lý do trong
tài liệu; không tăng tolerance để che lỗi bố cục.

## 8. Quy trình ảnh chuẩn

### 8.1. Tạo hoặc cập nhật có chủ ý

Trên Windows, contributor chạy:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:updateDevDebugScreenshotTest --no-daemon
```

Sau đó phải:

1. Mở montage hoặc các PNG vừa tạo và kiểm tra đủ 30 ảnh.
2. Xem `git diff --stat` và danh sách ảnh thay đổi.
3. Chạy validate để xác nhận baseline vừa tạo có thể tái lập.
4. Chỉ commit ảnh khi thay đổi UI là chủ ý và đã được chủ dự án duyệt.

Không cung cấp script vừa chạy validate vừa tự update. Không chạy update task
trong CI, pre-commit hook hoặc khi test thất bại.

### 8.2. Xác thực cục bộ

```powershell
.\gradlew.bat :app:validateDevDebugScreenshotTest --no-daemon
```

Khi khác biệt, báo cáo HTML nằm dưới:

```text
app/build/reports/screenshotTest/preview/devDebug/index.html
```

Tên và vị trí ảnh actual/diff do plugin tạo dưới `app/build`; tài liệu contributor
sẽ chỉ rõ đường dẫn thực tế sau lần chạy đầu tiên thay vì sao chép thủ công sang
thư mục reference.

## 9. Tích hợp CI

Thêm job `android-screenshot-test` vào workflow hiện tại. Job chạy trên Ubuntu,
dùng JDK 17 và Android SDK như job build Android, rồi chạy:

```bash
./gradlew :app:validateDevDebugScreenshotTest --no-daemon
```

Job không cần emulator, PostgreSQL hoặc dev server. Job có quyền đọc repository
và không có quyền ghi nội dung GitHub.

Artifact được upload với `if: always()` để còn xem được khi Gradle thất bại:

```text
app/build/reports/screenshotTest/**
app/build/outputs/screenshotTest-results/**
```

Artifact giữ trong 7 ngày giống Web E2E hiện tại. Tên artifact chứa run number
và commit SHA để không nhầm giữa các lần chạy.

CI xử lý các trường hợp như sau:

- Thiếu ảnh chuẩn: job thất bại, yêu cầu tạo baseline cục bộ và review.
- Ảnh khác: job thất bại, upload reference/actual/diff và HTML report.
- Preview không biên dịch: job thất bại như lỗi source thông thường.
- Artifact không tồn tại vì lỗi xảy ra trước bước render: bước upload không làm
  che mất lỗi Gradle gốc.

Job screenshot có thể chạy song song với Web E2E và Android emulator sau khi
checkout/setup hoàn tất. Nó không thay điều kiện xanh của các job hiện có; merge
chỉ hợp lệ khi toàn bộ job bắt buộc cùng đạt.

## 10. Kiểm tra chính hệ thống screenshot

Trước khi coi Phase 6 hoàn tất, thực hiện phép thử có kiểm soát:

1. Generate đủ 30 reference và chạy validate: phải pass.
2. Tạm thay đổi một thuộc tính dễ nhận biết trong worktree, ví dụ khoảng cách
   của một component fixture: validate phải fail.
3. Kiểm tra report có reference, actual và diff đúng màn/kích thước.
4. Hoàn tác thay đổi thử nghiệm bằng patch đảo đúng phần vừa sửa.
5. Chạy validate lại: phải pass.
6. Chạy regression hiện tại của protocol, server, shared, Android compile/APK và
   Web Wasm để đảm bảo hạ tầng ảnh không ảnh hưởng các nền tảng khác.

Không commit thay đổi dùng để cố tình làm test thất bại.

## 11. Tài liệu và vận hành

`docs/testing.md` được bổ sung:

- mục đích và giới hạn của screenshot test;
- lệnh update/validate trên Windows và Linux;
- vị trí reference và report;
- cách đọc reference/actual/diff;
- quy tắc duyệt baseline;
- lưu ý plugin đang ở giai đoạn alpha.

`docs/roadmap.md` chỉ đánh dấu Phase 6 hoàn tất sau khi:

- 30 ảnh đã được tạo và chủ dự án duyệt;
- validate pass cục bộ;
- CI của đúng commit pass;
- artifact lỗi đã được thử bằng một thay đổi có kiểm soát.

Nếu plugin alpha gây lỗi tương thích sau khi nâng AGP/Kotlin, ưu tiên pin phiên
bản đang hoạt động và mở issue nâng cấp riêng. Không xóa baseline hoặc vô hiệu
hóa job âm thầm để làm CI xanh.

## 12. Rủi ro và cách giảm thiểu

| Rủi ro | Cách xử lý |
| --- | --- |
| Plugin còn alpha | Pin phiên bản, cô lập trong `app`, ghi tài liệu nâng cấp |
| Plugin không hỗ trợ KMP | Chạy source set screenshot trong Android `app`, gọi UI công khai từ `shared` |
| Fixture lệch dữ liệu thật | Dùng chính model protocol/state production và cập nhật fixture khi compiler báo thay đổi |
| Ảnh thay đổi do thời gian/mạng | Cố định clock, asset, locale và dữ liệu; không I/O |
| Baseline quá lớn | Giới hạn ban đầu 10 màn x 3 kích thước và PNG do plugin quản lý |
| Contributor ghi đè lỗi thật | Tách rõ update/validate; CI chỉ validate |
| Báo cáo CI khó tìm | Upload toàn bộ report/output bằng `if: always()` |
| Test trùng trách nhiệm | Screenshot kiểm tra pixel; Compose UI test/Web E2E kiểm tra hành vi |

## 13. Tiêu chí nghiệm thu

- Có đúng 10 màn cốt lõi và ba kích thước cho mỗi màn, tổng cộng 30 ảnh chuẩn.
- Ảnh dùng locale tiếng Việt, theme 2D Arcade tối và fixture cố định.
- Ảnh chuẩn đã được chủ dự án xem và chấp thuận trước khi merge.
- `:app:validateDevDebugScreenshotTest` pass khi UI không đổi.
- Một thay đổi hình ảnh có kiểm soát làm validate fail và sinh báo cáo diff hữu
  ích; sau khi hoàn tác, validate pass lại.
- CI có job screenshot riêng, không cần emulator và không tự sửa repository.
- Khi job lỗi, artifact chứa báo cáo HTML và đầu ra ảnh so sánh.
- Toàn bộ regression cũ vẫn pass, gồm protocol, server, shared, Android test
  compile/APK và Web Wasm.
- `docs/testing.md` mô tả đầy đủ quy trình; roadmap phản ánh đúng trạng thái.

## 14. Điều kiện bắt đầu triển khai

Sau khi chủ dự án duyệt tài liệu này, bước kế tiếp là viết implementation plan
theo từng checkpoint: cấu hình plugin, dựng fixture/preview, tạo và review ảnh,
tích hợp CI, kiểm chứng failure artifact, cập nhật tài liệu và chạy hồi quy cuối.
