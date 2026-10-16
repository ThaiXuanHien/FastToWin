# Avatar R2 — nghiệm thu Android thật ngày 14/10/2026

Liên quan [#11](https://github.com/ThaiXuanHien/FastToWin/issues/11).
Phạm vi là luồng avatar Android với R2 private trên staging, không phải nghiệm thu toàn bộ game hay production.

## Môi trường

- Samsung SM-A366B, Android16/API36, màn1080×2340.
- Bản Dev `com.hienthai.fastowin.dev` cài riêng; base master `57b9910bf1c1f09bf36409c6d85ae9740a722447`, sau đó thử bản sửa PNG alpha trong nhánh `codex/avatar-android-alpha`.
- Dev dùng WSS/HTTPS `staging-web-staging-b446.up.railway.app`, bucket private `fasttowin-avatars-staging`. Backend staging dùng artifact nguồn `066f5c0`, runtime code giống master trên.
- Tài khoản và ba ảnh JPEG/PNG tổng hợp riêng. Email fixture được xác minh trực tiếp trong staging DB; vòng này **không chứng minh SMTP đăng ký hoạt động**. Không dùng ảnh hay tài khoản cá nhân trên điện thoại.

## Kết quả quan sát

| Bài thử | Kết quả | Bằng chứng |
| --- | --- | --- |
| Đăng nhập Dev, mở Android photo picker, chọn JPEG và lưu | Đạt | Profile báo đã lưu; revision1, JPEG512×384/8626bytes; SHA HTTP khớp metadata |
| PNG trong suốt trên bản master | Lỗi được tái hiện | Revision2 có corner RGB(0,0,0); ảnh đã được Android nén JPEG trước khi backend nhận |
| PNG sau bản sửa compositing nền trắng | Đạt | Picker thật → lưu → revision3, JPEG512×512/12808bytes; corner RGB(255,255,255), SHA khớp |
| Chọn ảnh cam làm draft, mở rồi hủy picker | Đạt | Draft cam còn trong editor; avatar đã lưu ở đầu trang vẫn xanh |
| Tắt Wi-Fi và dữ liệu; thử reconnect khi offline | Đạt về bảo toàn ảnh đã lưu | Màn offline chặn editor; không gửi thao tác Lưu. Avatar trên backend vẫn revision3 và SHA không đổi |
| Bật lại mạng, tự reconnect | Đạt về ảnh đã lưu; **lỗi draft** | Profile ảnh xanh đúng; editor đóng, draft cam chưa lưu bị mất. Theo dõi [#39](https://github.com/ThaiXuanHien/FastToWin/issues/39) |
| Chọn lại ảnh cam, lưu sau reconnect | Đạt | Revision4, JPEG512×384/8598bytes; ảnh HTTP khớp SHA metadata |
| Force-stop riêng Dev rồi mở lại | Đạt | Không cần đăng nhập lại; profile tải ảnh cam mới, không giữ ảnh xanh cũ |
| Process exit của Dev sau vòng cuối | Không thấy crash/ANR trong bản ghi thu thập | `dumpsys activity exit-info` chỉ có USER REQUESTED/FORCE STOP do bài thử; không suy rộng thành kiểm thử ổn định dài hạn |

SHA revision3: `6ab12b75f9af179017649610557c6214d9ee909d9c8370103ebe3186754dcf36`.
SHA revision4: `5057da42dd0741a2db732063754c084fb024862fce4a0e624d12ec4cebb4abd0`.

## Bản sửa PNG và kiểm thử

Android dùng Canvas ghép bitmap lên nền trắng trước JPEG, giống contract chuẩn hóa của backend. Giữ giới hạn kích thước/byte và giải phóng bitmap; không đổi bố cục/theme.

- Red: Android test URI/Bitmap pipeline thực trên Samsung thất bại đúng pixel nền đen; test JPEG opaque pass.
- Green:2/2 Android instrumentation pass trên cùng thiết bị, gồm trong suốt, alpha50%, JPEG signature/bytes và màu/kích thước ảnh opaque.
- Shared host:287/287 pass.
- Rà soát mã độc lập: không có finding cần sửa. Luồng hệ thống picker được kiểm tra thật riêng; instrumentation xử lý ảnh dùng reflection để gọi hàm private, không thêm API production chỉ dành cho test.

## Bằng chứng và dọn dẹp

Bằng chứng local ignored giữ trong `.artifacts/r2-20261012/device-qa/`: ảnh chụp JPEG/PNG trước-sau, draft/hủy, offline, reconnect, mở lại; layout sau từng thao tác; JPEG tải lại và metadata/hash; `alpha-red.log`, `alpha-green.log`, process exit và cleanup records. Không commit credential hoặc object key.

Xóa tài khoản thử và các object R2 của đúng UUID thử; chỉ đưa thời điểm dọn các key synthetic đã nghỉ về hiện tại để worker xử lý, không coi là đã chờ24giờ thực. Thu hồi SSH tạm, tắt bốn dịch vụ staging và đối chiếu deployment IDs/health production. Dọn ba ảnh QA và Dev app do vòng này cài; Wi-Fi/dữ liệu trả về trạng thái gốc. App production version1.0/code1 vẫn có thời gian cập nhật22/09/2026 07:31:45. Các file admin UI/IDE của người dùng giữ nguyên hash.

## Giới hạn còn lại

- Chưa đạt bảo toàn draft qua offline/reconnect (#39); không đánh dấu toàn bộ luồng avatar đạt hoàn toàn.
- iOS thật chưa có thiết bị; vòng này không nghiệm thu lại Web/PWA. CDN/custom domain và kích hoạt R2 production còn riêng, không tự deploy production.
- Master CI38575876457 có177/178 Android UI pass, lỗi test lịch giải đấu được ghi ở [#38](https://github.com/ThaiXuanHien/FastToWin/issues/38#issuecomment-6171446072). Không coi toàn bộ CI master xanh; bản sửa alpha không xử lý lỗi này.
