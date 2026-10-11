# Rà soát backlog ngày 11/10/2026

Đối chiếu GitHub Issues #1–#27, source tại `2bf521f`, tài liệu và kết quả kiểm thử.
Checkbox hoặc issue OPEN không tự chứng minh một tính năng còn thiếu.

| Issue | Kết quả đối chiếu | Việc còn lại |
| --- | --- | --- |
| #1 | Cửa hàng hiện chỉ bán Gem/đổi Vàng; mua cosmetic đã bỏ. Có kiểm thử billing, ví, trang bị legacy, Android UI và E2E Web PostgreSQL local đạt | Cần merge/CI nhóm mới và nghiệm thu provider thật; không làm lại cửa hàng cosmetic |
| #2 | CI build iOS simulator; không có máy Mac/iPhone/iPad ở workspace Windows | Chạy simulator và thiết bị thật, ghi cấu hình/bằng chứng; tài khoản Apple không phải điều kiện của build simulator không ký |
| #3 | Đã bổ sung 3 baseline đăng nhập và 4 baseline lịch giải; tổng 137 ảnh validate local đạt, ảnh cũ không đổi | CI revision đã push đạt 133 ảnh; cần CI revision lịch giải mới |
| #4 | Revision lịch giải đo Kover: server 86,82%, shared 52,89%; floor 86%/52%; gate chuẩn đạt và ngưỡng 100% bị từ chối | HTML/XML/artifact/gate đã thêm CI và đạt ở revision đã push; cần CI revision lịch giải mới |
| #5 | Harness HTTP/WebSocket cách ly đạt cả memory và PostgreSQL: 4 trận, 200 lượt bấm, reconnect, 0 lỗi; có số đo tài nguyên | Có hướng dẫn/JSON baseline và CI 2 cặp; chưa phải saturation test/khả năng chịu tải production |
| #6 | Production alpha đã có HTTPS/WSS; không đồng nghĩa staging riêng | Cần staging domain/runtime độc lập để diễn tập bảo trì/restore/rollback |
| #7 | Có Playwright nhiều trình duyệt | Thiết bị thật/Safari iPhone/iPad và bàn phím thật chưa được nghiệm thu |
| #9 | Public discovery/join/leave đã có; admission dùng chung, lưu snapshot và kiểm thử race/rollback | Lịch/hạn đăng ký thuộc #13 đã có source và kiểm thử backend; còn gate Web/CI/integration |
| #10 | Chưa chọn nhà cung cấp thanh toán Web | Cần quyết định quốc gia, nhà cung cấp, giá, phí và webhook; Google Play không thay cổng Web |
| #11 | Avatar còn lưu qua backend/PostgreSQL; chưa có adapter object storage/CDN | Phụ thuộc baseline #5 và cấu hình dịch vụ/chi phí |
| #12 | Realtime và matchmaking vẫn ở một process | Thiết kế/kiểm thử ownership, presence/rate limit nhiều instance; phụ thuộc #5 |
| #13 | Đã triển khai lịch tự bắt đầu, check-in trước 15 phút, deadline, hủy/hoàn phí, phục hồi restart và modal tạo lịch | Chưa hoàn tất: WebKit PWA bị mất nội dung canvas khi mô phỏng thay đổi viewport bàn phím; cần sửa, kiểm thử và CI/integration |
| #14 | StoreKit/APNs production chưa đủ điều kiện | Chủ dự án chưa có Apple Developer Program; giữ `ready-for-human` |
| #15 | Railway backend một replica; chưa có định tuyến nhiều instance | Phụ thuộc #12; không tăng replica trước khi có ownership/recovery |
| #16 | Chưa có luồng khán giả giải theo nhánh đấu | Phụ thuộc #9/#13; tách quyền chỉ đọc và giới hạn khán giả |
| #17–#24 | Admin, quà, sự kiện, đền bù, thông báo, bảo trì, analytics đã có code/test và đã phát hành | Cập nhật trạng thái release; còn nghiệm thu thiết bị/provider và nguồn ACTIVITY chưa có, không bật chiến dịch thật để thử |
| #25 | Dark-only đã merge/deploy; CI và ảnh baseline có bằng chứng; đã đóng issue | Không cần triển khai lại |
| #26–#27 | Đã đóng, deploy/merge `2bf521f` | Master CI backend/Android/iOS/screenshot đạt; Web E2E có lỗi helper cuộn đang sửa; matcher vẫn cần đối thủ trong giới hạn Elo |
| #28 | Đã tạo checklist Google Play internal testing, nhãn `ready-for-human` | Chủ tài khoản tạo app/sản phẩm/bản ký/license tester/API; chưa bật provider production |
| #30 | Đã tái hiện canvas mất nội dung sau resize bàn phím trong WebKit Windows mô phỏng iOS PWA | Chẩn đoán renderer/compositor/fixture; kiểm tra ảnh thực, không chỉ DOM/geometry; gate phát hành #13 còn mở |

## Điều kiện Store mới được xác nhận

Chủ dự án chỉ mới có tài khoản Google Play Console; chưa tạo app hoặc ba sản phẩm
Gem. Android Billing và xác thực Google đã có triển khai; việc có tài khoản chưa
chứng minh mua thật hoạt động. Cần tạo app `com.hienthai.fastowin`, sản phẩm
`fasttowin_gems_80`, `fasttowin_gems_250`, `fasttowin_gems_650`, bản ký/internal
testing và service account có quyền của app trước khi nghiệm thu provider.

Chưa có tài khoản Apple: không bật StoreKit/APNs production. Các kiểm thử/build
không cần tài khoản vẫn tiếp tục; thiết bị/macOS là phụ thuộc riêng.

Không đưa credentials, purchase token hoặc dữ liệu cá nhân vào báo cáo/backlog.

Hướng dẫn Console: [Google Play internal testing](google-play-internal-testing.md),
issue [#28](https://github.com/ThaiXuanHien/FastToWin/issues/28).

## Bằng chứng bổ sung trong đợt rà soát

- Server 620 và shared 234 test: 0 failure/error/skip; coverage báo cáo/gate đã đo với PostgreSQL local.
- Screenshot 133 đạt; đã xem 3 ảnh đăng nhập phone nhỏ/lớn/tablet, chỉ thêm 3 PNG.
- Load safeguard 3 test đạt. Cả memory/PostgreSQL 4 cặp/1 lượt đạt; PostgreSQL lưu đủ 4 trận, dừng JVM và dọn schema. Memory thêm 2 lượt đạt 8 trận.
- Review harness đã sửa xác nhận rời phòng, giới hạn/cancel setup, xác nhận listener thuộc JVM vừa tạo và dọn schema khi CREATE không rõ kết quả.
- CI master `38107748133` kết thúc với Web E2E 88 pass/1 fail/2 skip: ô tên phòng nằm phía trên nhưng helper chỉ cuộn xuống khi semantics bounds bằng 0. Đã có test tái hiện và sửa helper; không quy lỗi này thành gameplay/production mất kết nối.
- Bản sửa helper đạt 8 kiểm thử Chromium (1 skip có chủ đích cho viewport touch), sau đó 6 kiểm thử WebKit/Chromium gồm language/reload, Dark-only migration và compact dialog. Dùng cả phần tử có role để phát hiện vị trí cuộn WebKit; chỉ phần tử có id không đủ. Firefox local không khởi động được (`spawn UNKNOWN`); kết quả Firefox Linux trong CI vẫn là điều kiện xác minh riêng.
- Web wallet với schema PostgreSQL riêng: 1/1 đạt; đổi 10 Gem lấy 1.000 Vàng, replay không cộng trùng, F5 giữ 70 Gem/1.000 Vàng và lịch sử đúng nguồn/request/số tiền. Runner mới đạt thêm 6/6 hồi quy memory WebKit/Chromium; dọn schema đã xác nhận. Provider thật vẫn chưa được nghiệm thu.
- Revision lịch giải local: server 646, shared 242, protocol 104 test, không failure/error/skip; 137 ảnh validate đạt. Android test đã biên dịch, chưa chạy instrumentation cho revision này.
- CI `38110905003`/`38110947311`: build/test/coverage/load, 133 ảnh, iOS simulator và Android UI đạt; Web thất bại ở helper cuộn Settings. Bản sửa helper mới đạt 7 ca Settings local (1 skip có chủ đích); chưa có CI revision mới.
- Modal tạo giải: 4 ca native input Chromium/WebKit và audit shared input đạt; 6 ca PWA bàn phím chưa đạt. Ảnh cho thấy canvas trống dù kích thước CSS phục hồi; giữ assertion điều hướng Home và không nghiệm thu dựa riêng vào geometry.
- Đã sửa thiếu dữ liệu múi giờ trên Web bằng module IANA và lockfile cho Wasm/JS. Luồng tạo lịch/check-in/F5/hủy giải đạt 2/2 trên phone `Asia/Bangkok` và desktop `UTC`.
- Thử nghiệm nâng Compose/Skiko chỉ qua cấu hình local không sửa được pixel canvas trống; lần mở rộng đạt 4 native / thất bại 7 PWA/audit. Đã khôi phục build chuẩn, không đưa override alpha vào source phát hành. [Issue #30](https://github.com/ThaiXuanHien/FastToWin/issues/30) theo dõi lỗi riêng; chưa khẳng định iOS thật bị cùng lỗi.

## Thứ tự thực hiện

1. Coverage #4 và baseline cách ly #5.
2. Bù kiểm thử còn thiếu #1/#3 và đồng bộ issue/tài liệu với release thực tế.
3. Chuẩn bị phát hành Google Play; xác minh provider khi Console/credential sẵn sàng.
4. Lịch giải #13, sau đó khán giả #16.
5. Hạ tầng #11/#12/#15 dựa trên số đo #5 và lựa chọn dịch vụ; không coi danh sách chi phí là hạ tầng đã triển khai.

Tiến độ của từng mục được cập nhật trong issue tương ứng; chưa đánh dấu các việc
chỉ được mô tả trong tài liệu là hoàn tất.
