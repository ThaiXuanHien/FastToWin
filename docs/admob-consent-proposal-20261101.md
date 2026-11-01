# Đề xuất cấu hình consent AdMob — 01/11/2026

Trạng thái: **đề xuất local để Owner duyệt, chưa tạo/lưu/xuất bản trên AdMob**.
Tham chiếu: [#51](https://github.com/ThaiXuanHien/FastToWin/issues/51), [kết quả rà soát](admob-readiness-20261101.md), [runbook AdMob](admob-android.md).

## Phạm vi và lựa chọn đề xuất

Chuẩn bị hai thông báo chuẩn Google cho đúng app Android `Fast To Win`, App ID `ca-app-pub-1914037185148012~8507808229`, package dự kiến liên kết `com.hienthai.fastowin`.

Đề xuất dùng **English (US)** cho form Google; giữ Privacy và phần Cài đặt hiện có của app bằng VI/EN. Danh sách [ngôn ngữ AdMob hỗ trợ](https://support.google.com/admob/answer/10107561?hl=en) không có tiếng Việt cho GDPR hoặc US states. Tiếng Việt được liệt kê ở nhóm IDFA riêng, không phải form Android đang làm. Việc Google chọn ngôn ngữ theo thiết bị không đồng nghĩa theo nút đổi ngôn ngữ trong game.

Đây là cấu hình trên luồng UMP hiện có, không thêm CMP, popup tự viết, màn hình hay SDK mới. Giữ nguyên phân phối178khu vực/PC, các giới hạn tuổi đã duyệt và chế độ quảng cáo tắt.

Các hướng đã cân nhắc:

| Hướng | Đánh giá |
| --- | --- |
| Hai thông báo có tên rõ, gắn đúng app, form EN chuẩn Google | **Đề xuất**: có cấu hình để preview/nghiệm thu; phù hợp ngôn ngữ Google hỗ trợ |
| Chỉ giữ thông báo tự động/dự phòng hiện tại | Ít thao tác nhưng chưa có bản thông báo do mình quản lý để duyệt nội dung/phạm vi |
| Yêu cầu form GDPR/US tiếng Việt từ một giải pháp khác | Cần đánh giá CMP/tích hợp mới, vượt phạm vi bản nháp hiện tại |

## Cấu hình chung

| Trường | Giá trị đề xuất |
| --- | --- |
| App được chọn | Chỉ Fast To Win Android, đúng App ID ở trên |
| Trạng thái lưu sau khi được duyệt | **DRAFT** cho cả hai thông báo |
| Ngôn ngữ mặc định | English (US) |
| Ngôn ngữ bổ sung | Không chọn ngôn ngữ ngoài những ngôn ngữ app hỗ trợ; không gán nhầm tiếng Việt ở nhóm IDFA |
| Tên app trong form | Fast To Win |
| Nội dung giải thích mục đích/nhà cung cấp | Giữ mẫu Google sinh từ cấu hình thực tế; không thay bằng một đoạn dịch pháp lý tự viết |
| Giao diện | Giữ mẫu chuẩn Google trong bản nháp; chưa thêm logo/style tuỳ chỉnh |
| Privacy URL trong cấu hình AdMob của app | `https://fasttowin.up.railway.app/privacy/en/` |
| Privacy tiếng Việt | Có sẵn tại `https://fasttowin.up.railway.app/privacy/`, truy cập từ liên kết đổi ngôn ngữ |
| Callback, store link, client/backend flags | Không đổi trong bước tạo nháp |

Google mô tả [Privacy URL](https://support.google.com/admob/answer/10113106) là giá trị của app được dùng cho các thông báo hiện tại/tương lai, không chỉ một ô riêng của bản nháp. Vì vậy, nếu Owner cho tạo nháp tiếp theo, quyền lưu URL này phải được nêu rõ cho đúng app. Không đổi Privacy URL trên Play Console trong phạm vi đó.

Hai URL đã được kiểm tra public ngày01/11: HTTP200, có liên kết sang ngôn ngữ còn lại, không có thẻ script. Bằng chứng local: `.artifacts/admob-consent-proposal-20261101/privacy-url-check.json`. Đây không phải nghiệm thu nội dung chính sách cho bản quảng cáo LIVE: phần quảng cáo hiện vẫn ghi phiên bản hiện tại chưa bật AdMob.

## Nháp1: European regulations

| Trường | Giá trị đề xuất |
| --- | --- |
| Tên nội bộ | `FTW_Android_EU_EN_Draft_20261101` |
| App | Fast To Win Android duy nhất |
| Targeting | EEA, Vương quốc Anh và Thuỵ Sĩ theo lựa chọn chuẩn Google; không chọn Everywhere |
| Default language | English (US) |
| Additional languages | Không thêm |
| Do not consent | **ON**, hiển thị trong toàn bộ vùng nhắm mục tiêu này |
| Close (do not consent) | **ON**; đóng form mang ý nghĩa từ chối theo lựa chọn chuẩn Google |
| Consent / Manage options | Giữ hành vi và nhãn chuẩn Google |
| Lưu | Save draft; chưa Publish |

Luồng mong đợi: người dùng có thể đồng ý, từ chối ngay ở màn đầu, hoặc quản lý mục đích/đối tác rồi xác nhận lựa chọn. Không coi đóng/từ chối là đồng ý. Các khả năng và lựa chọn vùng ở trên được đối chiếu với [hướng dẫn tạo European message cho app](https://support.google.com/admob/answer/10113207?hl=en).

## Nháp2: US state regulations

| Trường | Giá trị đề xuất |
| --- | --- |
| Tên nội bộ | `FTW_Android_US_EN_Draft_20261101` |
| App | Fast To Win Android duy nhất |
| Default language | English (US) |
| Targeting | All current and future supported US states = **ON** |
| Nội dung/quyền chọn | Mẫu opt-out US states của Google; không áp tên nút GDPR sang mẫu US |
| Lưu | Save draft; chưa Publish |

Đây là phạm vi **thông báo** theo các bang Google hỗ trợ, không thay danh sách quốc gia phân phối app hoặc bật thêm xử lý dữ liệu. [Hướng dẫn US states](https://support.google.com/admob/answer/10860309?hl=en) có lựa chọn này và các ngôn ngữ EN/Spanish; bản đề xuất chọn EN phù hợp app.

## Cài đặt cấp tài khoản: giữ nguyên trong bước nháp

Lần rà soát trước thấy danh sách đối tác thường dùng GDPR198, US345; chế độ tối đa hoá phạm vi thông báo/dự phòng đang bật, tự thêm nguồn quảng cáo đang tắt. Đây là các quan sát, không phải kết luận mọi đối tác trong danh sách đều thực sự cần cho Fast To Win.

- Không đổi danh sách/vendor, mục đích, legitimate-interest defaults, consent mode, special feature2, consent sync hay chế độ dự phòng cấp tài khoản khi tạo hai nháp.
- Không thêm mục đích riêng của nhà phát hành hoặc nguồn dàn xếp mới.
- Trước Publish cần rà soát danh sách/mục đích thực sự dùng và duyệt mọi thay đổi cấp tài khoản riêng. Bản nháp kế thừa các giá trị hiện có không phải nghiệm thu danh sách đó.
- Nếu wizard buộc phải sửa/lưu cài đặt cấp tài khoản để tiếp tục, dừng ở điểm đó và báo chính xác trường đang yêu cầu; không tự chọn giá trị mới ngoài bảng này.

## VI/EN trong app: giữ luồng hiện có

| Điểm vào/nhóm tuổi | Hành vi hiện có cần giữ và kiểm chứng khi được phép test |
| --- | --- |
| Cài đặt → quyền riêng tư quảng cáo | Nhãn được dịch VI/EN của app; mở form Google khi UMP yêu cầu entry point |
| Ngôn ngữ thiết bị không có trong form | Dùng English (US) mặc định; Privacy có liên kết tiếng Việt |
| Chưa chọn tuổi hoặc dưới13 | Không cho yêu cầu quảng cáo ở driver |
| 13–15 | Under-age-of-consent bật, treatmentCHILD, không cá nhân hoá |
| 16–17 | TreatmentTEEN, không cá nhân hoá |
| Người lớn | Chỉ yêu cầu sau khi UMP cho phép; lựa chọn thực tế của người dùng/SDK quyết định xử lý quảng cáo |

Không thêm lời nhắc mới vào app trong bước tài liệu/nháp này. Từ chối cá nhân hoá không mặc nhiên bằng `canRequestAds()==false`; việc được phép request, quảng cáo hạn chế hay không request phải được quan sát theo UMP. Không tự cấp2lượt từ việc bấm nút đồng ý hoặc callback client.

## Điều kiện trước khi xuất bản/thử nghiệm/LIVE

1. Owner duyệt hai bộ giá trị trên và quyền cập nhật Privacy URL của đúng app nếu tạo DRAFT trên AdMob.
2. Đọc lại hai nháp sau lưu: đúng app, tên, ngôn ngữ, vùng, lựa chọn từ chối/đóng, trạng tháiDRAFT; không tạo bản trùng nếu phản hồi lưu chưa rõ.
3. Rà soát preview Google và danh sách đối tác/mục đích; hoàn thiện chính sách Privacy theo thực tế bản có quảng cáo trước Publish/phát hành có ads. Chưa có sửa hoặc deploy Privacy được duyệt.
4. Xin phạm vi Publish/thử TEST riêng sau khi cấu hình đã được duyệt. Kiểm tra form theo vùng, đồng ý/từ chối, quyền thay đổi lựa chọn, tuổi, no-fill/đóng sớm và SSV2lượt trên bản/test-target đã chốt. Không tự chạy Samsung hoặc emulator.
5. Trước LIVE vẫn cần store link/readiness, app-ads.txt, callback production, binary có ads và khai báo Play tương ứng. Không dùng việc lưu hai nháp làm bằng chứng các cổng đó đã đạt.

## Kiểm tra bản đề xuất

- Đã đối chiếu giới hạn ngôn ngữ, mặc định khi không khớp ngôn ngữ thiết bị, lựa chọn form/vùng và Privacy URL với tài liệu Google.
- Đã đối chiếu App ID, tuổi và luồng UMP với source/kết quả rà soát hiện có; không đổi hành vi hoặc giao diện app.
- Chỉ có tài liệu local và kiểm tra hai URL công khai; không mở wizard tạo thông báo, lưu cài đặt Console, bật quảng cáo, đọc DB/credential, chạy test/CI hoặc publish gì.
