# Thiết kế đa ngôn ngữ Fast To Win

**Ngày:** 05/09/2026
**Trạng thái:** Đã được chủ dự án duyệt về phạm vi và hướng kiến trúc

## 1. Mục tiêu

Fast To Win hỗ trợ đổi ngôn ngữ ngay trong ứng dụng trên Android, iOS và Web mà
không khởi động lại, không đổi màn hình và không làm mất vị trí cuộn. Lần chạy đầu
ứng dụng dùng ngôn ngữ hệ thống; nếu ngôn ngữ đó không được hỗ trợ thì dùng tiếng
Anh.

Toàn bộ nội dung do hệ thống tạo phải được bản địa hóa, gồm UI, lỗi, trạng thái,
nhiệm vụ, cửa hàng, thông báo trong ứng dụng, push notification và email tài khoản.
Nội dung do người chơi nhập như biệt danh, tên phòng, mật khẩu phòng, tên bang và
mô tả bang không được dịch.

Thuật ngữ hiển thị `Mặt bài` được đổi thành `Mặt số`. Các ID kỹ thuật hiện có như
`card_back_gold`, `card_back_diamond` và enum `CARD_BACK` được giữ nguyên để bảo
toàn dữ liệu và tương thích client/server.

## 2. Ngôn ngữ hỗ trợ

| Mã trong ứng dụng | Language tag | Tên hiển thị |
| --- | --- | --- |
| `system` | lấy từ thiết bị | Theo hệ thống |
| `vi` | `vi` | Tiếng Việt |
| `en` | `en` | English |
| `zh-Hans` | `zh-Hans` | 中文简体 |
| `ja` | `ja` | 日本語 |
| `ko` | `ko` | 한국어 |
| `es` | `es` | Español |
| `pt-BR` | `pt-BR` | Português (Brasil) |
| `fr` | `fr` | Français |
| `de` | `de` | Deutsch |
| `id` | `id` | Bahasa Indonesia |
| `th` | `th` | ไทย |
| `ru` | `ru` | Русский |

`system` là lựa chọn, không phải catalog thứ mười ba. Resolver so khớp language
tag đầy đủ trước, sau đó chỉ so khớp phần ngôn ngữ với các catalog không giới hạn
vùng. `pt-BR` là catalog giới hạn vùng nên `pt-PT` dùng tiếng Anh. `zh-CN` và
`zh-SG` được ánh xạ rõ sang `zh-Hans`; các biến thể chữ Phồn thể dùng tiếng Anh vì
chưa nằm trong phạm vi.

## 3. Quyết định kiến trúc

### 3.1 Catalog Kotlin dùng chung

Ứng dụng dùng catalog Kotlin trong `shared/commonMain`, không dựa vào locale ngầm
định của `stringResource()`. Lý do là lựa chọn ngôn ngữ trong ứng dụng phải đổi
ngay và đồng nhất trên Web/iOS; API override locale của Compose Multiplatform chưa
đảm bảo hành vi này trên mọi target.

Các thành phần chính:

- `AppLanguage`: danh sách 12 ngôn ngữ cùng `languageTag`, tên bản địa và hàm ánh
  xạ từ mã đã lưu hoặc locale hệ thống.
- `TextKey`: tập khóa ổn định cho nội dung tĩnh và template có tham số.
- `QuantityKey`: tập khóa số lượng cần quy tắc số ít/số nhiều.
- `LocalizationCatalog`: cung cấp `text(TextKey, arguments)` và
  `quantity(QuantityKey, count, arguments)` theo ngôn ngữ đã resolve.
- `LocalLocalization`: `CompositionLocal` đặt tại gốc `FastToWinApp`, để tất cả
  composable đọc cùng catalog và tự recompose khi lựa chọn thay đổi.
- `LocalizationService`: API không phụ thuộc Compose cho controller, router,
  mapper thông báo và các luồng chạy ngoài composition.

Catalog tiếng Anh là fallback bắt buộc. Mỗi catalog khác chỉ được build khi đủ
100% khóa. Không được hiển thị tên khóa hoặc chuỗi rỗng cho người chơi.

Template dùng placeholder có tên, ví dụ `{player}` và `{count}`, thay vì nối chuỗi
trong UI. Validator kiểm tra mọi ngôn ngữ có đúng tập placeholder như catalog tiếng
Anh. Quy tắc số lượng hỗ trợ `one`, `few`, `many`, `other`; tiếng Nga dùng đầy đủ
các nhóm cần thiết, còn các ngôn ngữ không biến đổi theo số lượng có thể dùng
`other`.

### 3.2 Lựa chọn và lưu ngôn ngữ

`AppPreferences` thêm trường chuỗi `languageCode` với mặc định `system`. Dùng
chuỗi thay vì serialize enum trực tiếp để một mã lạ từ bản mới hơn không làm hỏng
toàn bộ preferences khi rollback. Giá trị không hợp lệ được resolve về `system`
mà không xóa các cài đặt âm thanh, rung, theme hoặc font hiện có.

Mỗi target cung cấp locale hệ thống qua một interface expect/actual nhỏ:

- Android: locale ưu tiên trong `LocaleList`.
- iOS: ngôn ngữ ưu tiên đầu tiên của `NSLocale`/`NSLocale.preferredLanguages`.
- Web Wasm/JS: `navigator.languages`, sau đó `navigator.language`.

Khi người chơi chọn ngôn ngữ, store hiện tại lưu preferences rồi cập nhật state ở
gốc ứng dụng. Composition được dựng lại bằng catalog mới nhưng navigation state,
controller, form state và lazy-list state vẫn giữ nguyên. Web đồng thời cập nhật
thuộc tính `lang` của thẻ `<html>` để trình đọc màn hình đọc đúng ngôn ngữ.

Khôi phục cài đặt mặc định đưa `languageCode` về `system` cùng các preference khác.

### 3.3 UI chọn ngôn ngữ

Màn Cài đặt thêm section `Ngôn ngữ` trong nhóm Giao diện. Row hiển thị lựa chọn
hiện tại; chạm vào mở `ArcadeDialog` gần sát chiều rộng màn hình, có danh sách cuộn
12 ngôn ngữ và `Theo hệ thống`. Mỗi item hiển thị tên bản địa, tên tiếng Anh ngắn
và trạng thái đang chọn. Dialog hỗ trợ màn hình nhỏ, chữ lớn, bàn phím/focus Web,
TalkBack và VoiceOver.

Chọn item áp dụng ngay và đóng dialog. Không có nút Lưu riêng. Với lựa chọn
`Theo hệ thống`, subtitle hiển thị ngôn ngữ đang được resolve để tránh mơ hồ.

### 3.4 Nội dung từ protocol và backend

Các mã nghiệp vụ là nguồn chuẩn; chuỗi tiếng Việt từ server chỉ còn là fallback
tương thích:

- `ServerMessage.Error.code` được ánh xạ sang `TextKey`; `message` tiếp tục tồn tại
  cho client cũ và lỗi chưa biết.
- Các response HTTP tài khoản tiếp tục dùng `code`; client hiển thị bản dịch theo
  code thay vì `message` khi nhận diện được.
- Những server message hiện chỉ có `message` hoặc `reason` được bổ sung trường tùy
  chọn `messageKey` và `messageArgs`. Client mới ưu tiên key, client cũ vẫn đọc
  chuỗi fallback vì `ProtocolJson.ignoreUnknownKeys = true`.
- `NotificationSnapshot` bổ sung `titleKey`, `titleArgs`, `messageKey` và
  `messageArgs`, đồng thời giữ `title`/`message` fallback. Migration PostgreSQL
  thêm cột key và JSONB arguments. Thông báo mới đổi ngôn ngữ ngay khi UI đổi;
  bản ghi cũ không có key tiếp tục hiển thị chuỗi đã lưu.
- Tên vật phẩm hệ thống được render từ ID ổn định. Trường tên hiện tại trong
  protocol được giữ làm fallback cho client cũ.

Mọi key nhận từ mạng phải nằm trong allowlist `TextKey`; key lạ không được dùng để
tra file hoặc thực thi format tùy ý.

### 3.5 Push notification và email

Backend cần biết ngôn ngữ đã resolve của thiết bị đang hoạt động:

- `ClientMessage.UpdateFcmToken` thêm `languageTag` tùy chọn và có giá trị mặc định
  tương thích client cũ.
- Khi đổi ngôn ngữ, client gửi lại token cùng language tag đã resolve. PostgreSQL
  lưu locale cạnh token; locale không hợp lệ được chuẩn hóa về `en`.
- Push template nằm trong catalog server có cùng key và placeholder với client.
  Firebase Web Push đặt trường `language` đúng tag thay vì cố định `vi`.
- Request gửi mã xác minh email và khôi phục mật khẩu có thêm `languageTag` tùy
  chọn. Email được render tại thời điểm gửi; client cũ mặc định dùng `en`.

Không đưa access token, refresh token, email, mật khẩu hoặc secret vào arguments
của thông báo hay log localization.

## 4. Luồng dữ liệu

### Khởi động

1. Platform preferences store tải `languageCode`.
2. Resolver đọc locale thiết bị nếu lựa chọn là `system`.
3. `FastToWinApp` tạo catalog cho ngôn ngữ đã resolve.
4. Root provider cung cấp catalog cho toàn bộ UI trước khi render màn bảo trì,
   offline hoặc đăng nhập.

### Đổi ngôn ngữ

1. Người chơi mở Cài đặt và chọn một item.
2. `AppPreferencesStore` lưu mã mới.
3. Root state cập nhật catalog và toàn bộ text recompose tại chỗ.
4. Web cập nhật `<html lang>`.
5. Nếu đã kết nối và có push token, client đồng bộ language tag với backend.

### Hiển thị nội dung server

1. Client nhận code/key, arguments và chuỗi fallback.
2. Mapper kiểm tra key trong allowlist và placeholder hợp lệ.
3. Catalog hiện tại render nội dung.
4. Nếu key không biết hoặc payload cũ không có key, client dùng fallback của
   server; nếu fallback rỗng thì dùng lỗi chung đã dịch.

## 5. Phạm vi di chuyển chuỗi

Việc chuyển đổi bao phủ theo thứ tự để mỗi đợt vẫn build được:

1. Nền tảng localization, preferences và selector trong Cài đặt.
2. App shell: bảo trì, offline, cập nhật, header, bottom bar và dialog dùng chung.
3. Đăng nhập, đăng ký, xác minh email, quên mật khẩu và tài khoản.
4. Home, phòng chơi, matchmaking, màn chơi, kết quả và luyện tập.
5. Hồ sơ, hoạt động, điểm danh, nhiệm vụ, bộ sưu tập và lịch sử.
6. Bạn bè, bang hội, bảng xếp hạng, giải đấu và cửa hàng.
7. Protocol messages, notification history, push và email backend.
8. Tài liệu SRS/API/roadmap và chuỗi test liên quan.

Trong suốt migration, CI có allowlist tạm thời cho chuỗi không phải nội dung người
dùng như test tag, ID, URL, log nội bộ và tên enum. Khi bước 8 hoàn tất, scanner
không được tìm thấy chuỗi UI tiếng Việt hard-code ngoài catalog.

## 6. Kiểm thử

### Common unit test

- Đủ đúng 12 catalog và toàn bộ khóa.
- Không có bản dịch rỗng; placeholder khớp catalog tiếng Anh.
- Resolve đúng exact tag, language-only tag hợp lệ, `pt-BR`/`pt-PT`,
  `zh-CN`/`zh-SG`, unsupported locale và mã preference hỏng.
- Quy tắc số lượng tiếng Anh, Nga và các ngôn ngữ không biến đổi.
- Preferences cũ thiếu `languageCode` vẫn đọc được và giữ các trường khác.
- Thuật ngữ `Mặt bài` không còn trong UI/catalog/tài liệu; ID `card_back_*` không
  thay đổi.

### Backend test

- Persist và load notification key/arguments mà không mất fallback cũ.
- Push/email dùng locale của thiết bị/request và fallback `en` cho locale lạ.
- Client cũ không gửi `languageTag` vẫn kết nối và nhận response hợp lệ.
- Không cho placeholder hoặc key ngoài allowlist đi vào renderer.

### UI và Web test

- Chọn ngôn ngữ đổi ngay title, navigation và dialog mà không đổi route.
- Reload/restart giữ lựa chọn; `system` phản ánh locale thiết bị.
- Web Back/Forward, deep link và `<html lang>` không bị hỏng.
- Kiểm tra 320 px, điện thoại lớn, tablet, ngang, font lớn và viewport thấp với
  English, German, Russian, Thai, Japanese và Simplified Chinese.
- Selector ngôn ngữ thao tác được bằng touch, chuột, bàn phím và accessibility.

Việc chạy trên iPhone/iPad thật vẫn phụ thuộc môi trường macOS/Apple hiện có; build
và unit test target iOS không được bỏ qua trong CI hỗ trợ macOS.

## 7. Tương thích và rollout

- Các trường protocol mới đều tùy chọn/có default; không đổi ID, enum hoặc dữ liệu
  sở hữu hiện tại.
- Migration database chỉ thêm cột nullable/default, không viết lại thông báo cũ.
- Backend được deploy trước hoặc đồng thời với client mới. Client mới vẫn hoạt
  động với fallback raw message khi nối vào backend cũ; client cũ bỏ qua field mới.
- Protocol version chỉ tăng khi thay đổi wire format được đưa vào code, và script
  đóng gói hiện tại phải build lại cả `protocol` lẫn `server` để tránh lệch class.
- Catalog được đóng gói trong app; không tải bản dịch động và không phụ thuộc mạng.

## 8. Ngoài phạm vi

- Dịch nội dung do người chơi nhập.
- Dịch trang Google Play, App Store hoặc nội dung marketing ngoài repository.
- Dịch runtime bằng dịch vụ AI hoặc API bên thứ ba.
- Tiếng Trung Phồn thể và các ngôn ngữ RTL trong đợt này.
- Thay đổi ID `card_back_*`, enum `CARD_BACK` hoặc dữ liệu vật phẩm đã sở hữu.

## 9. Tiêu chí hoàn thành

- Có `Theo hệ thống` và đủ 12 ngôn ngữ trong Cài đặt.
- Đổi ngôn ngữ tức thì trên Android, iOS và Web, giữ nguyên màn/vị trí cuộn.
- Fallback English hoạt động với locale, key và preferences không hợp lệ.
- Toàn bộ nội dung hệ thống trong app được dịch; user-generated content giữ nguyên.
- Thông báo mới có key đổi theo lựa chọn; thông báo cũ dùng raw fallback;
  push/email mới gửi đúng locale.
- Không còn thuật ngữ hiển thị `Mặt bài`; `Mặt số` và bản dịch tương ứng được dùng.
- Không làm mất tài khoản, phiên, vật phẩm, lịch sử hoặc cài đặt hiện có.
- Test catalog, protocol/backend, Compose UI và Web đạt; các hạn chế kiểm thử iOS
  thật được ghi rõ nếu chưa có thiết bị.
