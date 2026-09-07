# Localization

Fast To Win dùng catalog Kotlin chung cho Android, iOS và Web. Trạng thái hiện
tại có đúng 12 catalog; `system` là lựa chọn theo locale thiết bị, không phải
catalog thứ 13.

| Mã | Ngôn ngữ |
| --- | --- |
| `vi` | Tiếng Việt |
| `en` | English |
| `zh-Hans` | 中文简体 |
| `ja` | 日本語 |
| `ko` | 한국어 |
| `es` | Español |
| `pt-BR` | Português (Brasil) |
| `fr` | Français |
| `de` | Deutsch |
| `id` | Bahasa Indonesia |
| `th` | ไทย |
| `ru` | Русский |

## Quy tắc runtime

- Lựa chọn ngôn ngữ có hiệu lực ngay. Không khởi tạo lại controller, route, form,
  phiên, phòng hoặc trận; chỉ thông báo tạm thời có thể được xóa để tránh giữ câu
  ở ngôn ngữ cũ.
- Lựa chọn được lưu trên thiết bị. Web đồng thời cập nhật thuộc tính `lang` của
  phần tử `html`.
- Locale/tag không được hỗ trợ và khóa bị thiếu hoặc rỗng đều fallback về catalog
  tiếng Anh. `system` chỉ phân giải một trong 12 ngôn ngữ; nếu thiết bị không
  khớp thì dùng tiếng Anh.
- Tên người chơi, tên phòng, tên clan và mọi nội dung do người dùng nhập không
  được dịch hay chuẩn hóa qua catalog. ID, route, error code và test tag giữ nguyên.
- Thuật ngữ hiển thị tiếng Việt cho card-back/number skin là **Mặt số**.

## Khóa, placeholder và số nhiều

Khai báo khóa tĩnh trong `TextKey` bằng tên tiếng Anh theo ý nghĩa giao diện,
không theo nguyên văn của một ngôn ngữ; ví dụ `SettingsTitle`, không dùng tên kiểu
`CaiDat`. Khóa đã phát hành là định danh ổn định: sửa bản dịch, không đổi tên khóa
chỉ để chỉnh câu chữ.

Placeholder phải có tên rõ nghĩa dạng `{player}`, `{count}`, `{rewards}` và dùng
cùng một tập tên ở mọi catalog. Không nối thứ tự từ bằng nhiều mảnh đã dịch. Giá
trị được render một lần nên dấu ngoặc hoặc ký tự `$` trong nội dung người dùng
được giữ nguyên. Không bao giờ truyền mật khẩu, token, email nhạy cảm, secret hay
session ID làm đối số localization hoặc ghi chúng vào log.

Nội dung phụ thuộc số lượng dùng `QuantityKey` và các dạng `ONE`, `FEW`, `MANY`,
`OTHER`; không tự viết `if (count == 1)` ở UI. Luôn có `OTHER`. Bộ render tự thêm
placeholder `{count}` và áp dụng quy tắc số nhiều của ngôn ngữ đang chọn trước khi
fallback sang tiếng Anh.

## Thêm hoặc sửa ngôn ngữ

1. Với câu mới, thêm một `TextKey` hoặc `QuantityKey`, rồi bổ sung English và
   Vietnamese trước; các catalog khác có thể dùng English fallback cho tới khi
   bản dịch được kiểm duyệt.
2. Điền khóa vào các map catalog dùng chung. Giữ đúng placeholder và không dịch
   ID, mã lỗi, route, test tag hoặc nội dung người dùng.
3. Muốn thêm ngôn ngữ thứ 13 phải có quyết định sản phẩm: thêm `AppLanguage`, một
   catalog gốc, quy tắc tag/plural, lựa chọn UI và cập nhật test đếm catalog trong
   cùng thay đổi. Không dùng `system` làm catalog.
4. Chạy toàn bộ lệnh kiểm tra bên dưới và thử đổi ngôn ngữ giữa một luồng đang có
   dữ liệu để xác nhận route/form/controller không bị reset.

## Thông điệp từ server

Protocol gửi `messageKey`/`messageArgs` (hoặc `titleKey`/`titleArgs`) dưới dạng
trường optional với map mặc định rỗng. Client mới render khóa bằng catalog hiện
tại. Raw `message`/`title` chỉ dành cho client cũ và mọi literal tiếng Việt loại
này phải đi qua seam có tên `legacyFallback(...)`; không dùng seam cho nội dung UI
mới. Nội dung bảo trì do operator nhập vẫn là dữ liệu runtime và không được đưa
vào source. `languageTag` trong request email là optional; thiếu hoặc không hỗ trợ
thì server gửi tiếng Anh.

PostgreSQL migration V41 lưu khóa và đối số thông báo. V42 thêm
`users.notification_language VARCHAR(16) NOT NULL DEFAULT 'en'`; client cập nhật
locale này cùng token push để thông báo phát sinh sau đó dùng đúng ngôn ngữ.

## Kiểm tra

Chạy đúng các lệnh sau từ thư mục gốc repository:

```powershell
./gradlew.bat :protocol:jvmTest :shared:checkLocalizedUiText :shared:testAndroidHostTest :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon
Set-Location e2e
pnpm test --grep "language"
Set-Location ..
git diff --check
```

`checkLocalizedUiText` quét source UI/state/network/platform dùng chung và các
producer backend. Mọi ngoại lệ tương lai phải là pattern file/literal hẹp, có lý
do cụ thể; không được allowlist cả file hoặc thư mục để che literal hiển thị.
