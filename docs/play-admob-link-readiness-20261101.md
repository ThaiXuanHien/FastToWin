# Play / AdMob — kiểm tra khả năng liên kết ngày01/11/2026

Phạm vi Owner duyệt: chỉ đọc trạng thái xét duyệt/phát hành Play và kiểm tra app có thể tìm để liên kết AdMob chưa. Không phát hành thêm, sửa tester, cài app, Save store link, yêu cầu review AdMob, Publish consent hay bật quảng cáo.

## Kết quả

**Closed testing đã xuất bản; AdMob chưa tìm thấy app để liên kết.**

| Nguồn đọc trực tiếp | Trạng thái |
| --- | --- |
| Play → Hoạt động gửi → đợt1 |16thay đổi; trạng thái **Đã xuất bản**, ngày01/11/2026 |
| Chi tiết đợt1 | **1.0.9(10) – Closed testing**, bắt đầu phát hành đầy đủ trong kênh **Thử nghiệm khép kín – Alpha**; StoreVI/EN, nội dung ứng dụng, phân phối/cài đặt nằm trong cùng đợt |
| Phạm vi tester | Bản ghi đợt gửi dùng danh sách email FastToWin Internal. Không đổi danh sách, chưa kiểm tra lại số người opt-in hoặc thời gian tham gia. Đây không phải bằng chứng đã đủ điều kiện production. |
| Trang Store | [Fast To Win](https://play.google.com/store/apps/details?id=com.hienthai.fastowin), nhà phát triển **Franky-TX**, đúng package; mở được trong browser đang đăng nhập và có nút cài trên thiết bị khác. Không bấm cài. |
| AdMob app | `ca-app-pub-1914037185148012~8507808229`, Requires review, chưa có store/package link, phân phát bị hạn chế |
| AdMob → Add store → tìm URL Store chính xác | Tìm một lần, không có app khớp; Console hiện “Bạn không tìm thấy ứng dụng của mình? Hãy kiểm tra kỹ trang chi tiết của bạn trong cửa hàng ứng dụng, rồi thử lại.” Đã huỷ wizard, không Add/Continue/Save. |

Chi tiết Play hiển thị giờ gửi2:26PM và xuất bản2:34PM ngày01/11. Chưa xác minh timezone của Console nên không tự chuyển các giờ này thành giờ Việt Nam. Chỉ kết luận ngày/trạng thái được quan sát.

Trang Store được đọc trong tài khoản Google hiện có, vì vậy chưa xác minh người dùng ẩn danh hoặc người ngoài danh sách tester tải được app. Công cụ Web công khai không truy cập được URL này; đó là giới hạn công cụ, không phải chứng cứ trang bị404. “Phát hành đầy đủ” ở chi tiết Closed không có nghĩa phát hành production cho mọi người.

## Diễn giải theo tài liệu Google

[Hướng dẫn liên kết Store](https://support.google.com/admob/answer/10037806?hl=en) lưu ý app mới lên Store có thể mất24–48giờ, đôi khi tới một tuần, mới liên kết được. Đây là khả năng giải thích cần chờ đồng bộ, **chưa xác nhận là nguyên nhân duy nhất** của kết quả tìm kiếm hôm nay.

[Điều kiện app readiness](https://support.google.com/admob/answer/10564477?hl=en) yêu cầu app có trên Store được hỗ trợ, có thể tải và được liên kết đúng; tài liệu còn yêu cầu Android app khả dụng công khai. Liên kết có thể bắt đầu quy trình review. Không suy ra Closed được duyệt đồng nghĩa AdMobReady hoặc đủ điều kiện liên kết; không thử khai store khác/tên gói khác để vượt kết quả tìm kiếm.

## Việc tiếp theo

1. Đã chuẩn bị [bộ hướng dẫn và link Closed testers](closed-testing-tester-kit-20261101.md), đọc lại Alpha đang hoạt động với danh sách được chọn2email. Chưa thêm người, gửi lời mời hoặc xác nhận opt-in/ngày bắt đầu. Tiếp theo Owner cần tuyển nhóm thực tế.
2. Khi Owner yêu cầu kiểm tra lại AdMob sau thời gian đồng bộ, tìm đúng URL một lần. Nếu thấy app, đối chiếu tên/package/nhà phát triển và khả năng tải công khai trước khi xin phạm vi liên kết cụ thể.
3. Giữ adsOFF, hai consentDRAFT và Privacy hiện tại. Chờ câu trả lời về198/210 từ [yêu cầu hỗ trợ đã gửi](admob-support-request-20261101.md). Không tự polling Play/AdMob/email/CI hoặc tạo theo dõi định kỳ.

Bằng chứng local: `.artifacts/play-admob-link-readiness-20261101/` gồm submission-activity, published-submission-details, trang Store đã đăng nhập, trạng thái app AdMob, kết quả tìm Store và `result.json`. Không sửa GitHub Issues/Console/DB/runtime trong bước này.
