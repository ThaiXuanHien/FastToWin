# Yêu cầu hỗ trợ AdMob — ĐÃ GỬI

**Kết quả cuối ngày01/11/2026:** sau khi Owner xác nhận rõ toàn bộ payload bên dưới, đã Submit một lần thành công. Google hiển thị **Your email has been sent** và xác nhận Sellside Support Team đã nhận, chuyên viên sẽ liên hệ lại. Không hiện mã case trên trang biên nhận; chưa đọc email hoặc có trả lời kỹ thuật. Bằng chứng: `.artifacts/admob-support-question-20261101/sent-receipt.png`, `sent-receipt.ax.txt`, `result.json`. Không cần gửi lại hoặc tự tạo theo dõi định kỳ.

Ngày01/11/2026. Owner đã đồng ý gửi [câu hỏi198/210](admob-partner-reconciliation-20261101.md). Đã chuẩn bị form chính thức tại [Google Mobile Ads SDK Support](https://support.google.com/admob/contact/contact_us_gma_sdk?hl=en), được dẫn từ [trang hỗ trợ Google Developers](https://developers.google.com/admob/support). Luồng hỗ trợ sản phẩm GDPR/CCPA chỉ hiện Community; không đăng cộng đồng.

**Lịch sử duyệt:** Submit đầu tiên bị automatic approval review chặn trước khi gửi vì chưa có xác nhận rõ cho payload mở rộng của form. Owner sau đó chọn “Cho phép gửi form với dữ liệu đã liệt kê”, bao gồm mã app/ad unit, email và metadata. Chỉ sau xác nhận đó mới đối chiếu lại và Submit thành công; không đổi kênh để vượt chặn.

## Dữ liệu đã gửi Google theo phạm vi được duyệt

| Trường | Giá trị |
| --- | --- |
| Người nhận | Google AdMob / Mobile Ads SDK Support, form chính thức tiếng Anh ở trên; không phải bài đăng cộng đồng |
| Tài khoản liên hệ | Tài khoản Google đang đăng nhập `xtit6789@gmail.com` |
| Platform | Android |
| Publisher ID | `pub-1914037185148012`, Google tự điền |
| SDK | `GMA Next-Gen 1.5.0; UMP 4.0.0` |
| Device model | `Not device-specific: AdMob Console web editor; no device reproduction` |
| OS version | `Console on Windows 10; Android device form not reproduced` |
| Play App ID | `com.hienthai.fastowin` |
| Ad Unit ID | `ca-app-pub-1914037185148012/7821587525` |
| Subject | `European regulations draft: 198 selected partners versus 210 in preview` |
| Description | Câu hỏi4đoạn đã duyệt trong tài liệu đối chiếu, chỉ chỉnh khoảng trắng để dễ đọc; bản điền tại `.artifacts/admob-support-question-20261101/question-body.txt` |
| Steps to reproduce |5bước: mở Settings thấy198; mở nháp thấy210; xem vendor/precise-location opt-in; đóng không Save/Publish; ghi rõ chưa tái hiện thiết bị |
| SDK sample / test ad unit reproduction | Cả hai: chưa thử tái hiện, không khai đã test |
| Priority | Mặc định `Technical troubleshooting & product questions` |
| CC / files | Trống; không tải ảnh, log, source hoặc tệp lên |

Đã mở mục Additional info của form để kiểm tra metadata tự gửi: email tài khoản ở trên, ngôn ngữEN, quốc giaVN, tên/id Help Center và form, URL form, trạng thái đăng nhập, Chrome156/WindowsNT10, mã thử nghiệm và mã phiên truy cập hỗ trợ do Google tạo. Không thấy token/mật khẩu/DB/request game trong mục này. Việc gửi form sẽ kèm metadata này theo thông báo của Google; không xuất hoặc gửi thêm cookie/header/tệp bí mật.

## Nội dung mô tả và bước tái hiện

Description giữ nguyên ý và các câu hỏi đã duyệt: vì sao198/210; cách lấy danh sách effectiveTCF/AdditionalConsent; cách xác nhận danh sách kết hợp nếu có; khai báo vị trí từ AdMaximGVL9/IndexGVL10; preview trên thiết bị khi chưa Publish. Không yêu cầu thay tài khoản, cấu hình hay kích hoạt.

Các bước đã điền:

1. In AdMob, open Privacy & messaging > European regulations > Settings.
2. Review ad partners: automatic inclusion of commonly used partners is enabled; Settings and the selector show198selected. Cancel the selector without saving changes.
3. Open the existing unpublished Android European regulations draft. The Consent(1/3)preview says210partners.
4. In Ad partners(3/3), observe AdMaxim Limited and Index Exchange Inc. listing precise-location data. In Manage options(2/3), observe Use precise geolocation data with consent initially off.
5. Close without saving or publishing. This is a Console preview observation; no on-device reproduction or SDK sample/test-ad reproduction has been performed for this question. Ads remain disabled.

Bằng chứng local: `.artifacts/admob-support-question-20261101/prepared-form.ax.txt`, `submission-blocked.ax.txt`, `prepared-question-review.png`, `question-body.txt`. Các file này là local, không được đính kèm form. Không thay cấu hình AdMob, hai nháp, PrivacyHTML, runtime, Play, DB, deployment, CI hay thiết bị.
