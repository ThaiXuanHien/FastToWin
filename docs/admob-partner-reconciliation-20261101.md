# EU consent: đối chiếu đối tác và vị trí — 01/11/2026

Phạm vi rà soát: đọc lại nháp `FTW_Android_EU_EN_Draft_20261101` và cấu hình đối tác trên AdMob. Không đổi lựa chọn, không Save/Publish hoặc chạy thiết bị/CI. Bước tiếp theo được Owner duyệt riêng: đã gửi câu hỏi kỹ thuật tới Google, xem biên nhận bên dưới. Liên quan [#51](https://github.com/ThaiXuanHien/FastToWin/issues/51), [rà soát nền](admob-consent-policy-sources-20261101.md), [Privacy VI/EN local](privacy-admob-vi-en-draft-20261101.md).

## Đã làm rõ phần vị trí trong preview

| Bằng chứng đọc trực tiếp | Kết luận có thể rút ra |
| --- | --- |
| Settings → danh sách đối tác: tìm `Index` thấy Index Exchange Inc., GVL10, checkbox đã chọn; tìm `AdMaxim` thấy AdMaxim Limited, GVL9, đã chọn. Radio tự động bao gồm đối tác thường dùng vẫn ON, tổng198. | Hai đối tác này thực sự nằm trong cấu hình đang chọn, không chỉ là tên tình cờ nhìn thấy trong tài liệu. Không thay checkbox/radio khi tìm. |
| Preview → Ad partners(3/3): AdMaxim Limited và Index Exchange Inc. đều có `Precise location data` trong nhóm dữ liệu công bố. | Danh sách preview có khai báo vị trí chính xác từ các đối tác được chọn. Không thể gọi câu vị trí ở màn đầu là một câu thừa chỉ dựa vào việc source game không đọc GPS. |
| Preview → Manage options(2/3): có Special features → `Use precise geolocation data`, checkbox Consent=0. | Preview có lựa chọn đồng ý riêng cho vị trí chính xác và mặc định chưa chọn. Đây là bằng chứng editor; chưa xác nhận form UMP giao tới thiết bị thật hoặc tín hiệu lựa chọn thực tế. |
| Settings: Special Feature2 vẫn OFF. | Đây là tính năng chủ động quét thiết bị để nhận dạng, khác với Special Feature1 về vị trí chính xác. Tắt Feature2 không loại bỏ khai báo Feature1. |

[Hướng dẫn TCF của Google](https://support.google.com/admob/answer/9461778?hl=en) phân biệt hai special features và yêu cầu disclosure/opt-in phù hợp cho từng feature. Cần giữ lựa chọn/disclosure đúng với cấu hình, không tự chỉnh hoặc che câu vị trí để làm preview khớp một suy đoán.

Những bằng chứng này **không chứng minh FastToWin đang truy cập GPS, gửi tọa độ hoặc đã xin quyền vị trí trên thiết bị**. Source app/shared được rà soát trước không có quyền fine/coarse location hoặc lệnh gửi tọa độ rõ ràng; chưa kiểm tra merged manifest/traffic của một candidate có quảng cáo. Không đổi permission, SDK, chế độ quảng cáo hay cơ sở pháp lý trong bước này.

## Chênh lệch198/210: vẫn chưa có nguyên nhân được xác nhận

- Selector và trang Settings ghi198đối tác; màn đầu preview ghi210partners. Cả hai cùng xuất hiện ở cấu hình chưa Publish hiện có.
- Trang Ad partners preview có các nhóm TCF/vendor và thông tin GVL. Số1812items trong AX là số mục accessibility/virtualized container, **không phải1812nhà cung cấp**. Danh sách hiện render theo phần; không có phép đếm đầy đủ210tên hoặc bản diff12tên trong bằng chứng này.
- [Google về danh sách đối tác](https://support.google.com/admob/answer/10113004?hl=en) nói danh sách đã chọn đi vào thông báo. [Tài liệu dùng chung AdMob/Ad Manager](https://support.google.com/admob/answer/14188809?hl=en) mô tả trường hợp danh sách kết hợp được dùng ở preview và form. Chưa có bằng chứng tài khoản này dùng một danh sách Ad Manager kết hợp; không khẳng định đó là nguyên nhân.
- Chưa chứng minh đây là lỗi, dữ liệu mẫu cố định, khác cách đếm ATP/GVL, cache hoặc12đối tác bổ sung. Không tự xóa12đối tác hoặc đổi cài đặt cấp tài khoản để ép khớp số.

Bằng chứng local: `.artifacts/admob-partner-reconciliation-20261101/preview-vendors.ax.txt`, `preview-purposes.ax.txt`, `selected-index.ax.txt`, `selected-admaxim.ax.txt`. Hai nháp vẫn chưa Publish; đóng editor và huỷ selector không lưu. Settings cuối phiên có Save/Cancel changes disabled.

## Bước tiếp theo cụ thể

1. Giữ nháp và quảng cáo OFF; cập nhật ghi chú duyệt Privacy rằng phần vị trí đã có vendor/opt-in trong editor, chưa phải nghiệm thu thiết bị.
2. Đã gửi câu hỏi nhờ Google giải thích198/210 và cách lấy danh sách effective sau khi Owner duyệt riêng payload form. Chờ câu trả lời trước khi khẳng định danh sách bên nhận đã đối chiếu đủ. Không kèm ảnh/log/source; form có các mã công khai và metadata đã duyệt, xem bản kê bên dưới.
3. Khi đã chốt cấu hình và nội dung, mới đề xuất phạm vi Publish/TEST riêng. Không tự tiếp tục Samsung vì Owner đã yêu cầu để kiểm thử thiết bị sang khi cần.

### Câu hỏi kỹ thuật gửi Google AdMob Support

Owner đã duyệt câu hỏi và sau đó duyệt rõ payload bổ sung của form khi Submit đầu bị automatic approval review chặn. Form kỹ thuật chính thức đã gửi thành công: **Your email has been sent**, Sellside Support Team xác nhận đã nhận. Xem [bản kê dữ liệu và biên nhận](admob-support-request-20261101.md). Không hiện mã case; không đăng cộng đồng, chưa có trả lời kỹ thuật.

Subject: European regulations draft:198selected partners versus210in preview

We are reviewing an unpublished Android European regulations consent message. AdMob Settings and the ad-partner selector both show198selected partners with automatic inclusion of commonly used partners enabled. The same draft's main consent preview says210partners. We have not published the message or enabled ads.

What exactly is counted by each number? Is there a supported export or UI that lists the complete effective partner identities used by this draft, including TCF and Additional Consent entries, so we can reconcile the numbers without changing selections? If a combined Ad Manager/AdMob list applies, how can we verify the contributing list through the supported UI?

The selected list includes AdMaxim Limited(GVL9) and Index Exchange Inc.(GVL10). Both are shown with precise-location data in the partner preview; Manage options separately shows Use precise geolocation data with consent off initially. We are not assuming that this establishes device GPS access. Please confirm how the effective vendor list drives that disclosure and opt-in in the delivered UMP form, and whether there is a supported unpublished device-preview method.

We want to retain Consent, Do not consent, Manage options and close-as-refusal. We are asking for the effective-list/counting and preview behavior, not for account changes or activation.
