# Rà soát backlog ngày 11/10/2026

Đối chiếu GitHub Issues #1–#27, source tại `2bf521f`, tài liệu và kết quả kiểm thử.
Checkbox hoặc issue OPEN không tự chứng minh một tính năng còn thiếu.

| Issue | Kết quả đối chiếu | Việc còn lại |
| --- | --- | --- |
| #1 | Cửa hàng hiện chỉ bán Gem/đổi Vàng; mua cosmetic đã bỏ. Có kiểm thử billing, ví, trang bị legacy và Android UI | Đối chiếu lại phạm vi, bổ sung E2E Web nếu thiếu; không làm lại cửa hàng cosmetic |
| #2 | CI build iOS simulator; không có máy Mac/iPhone/iPad ở workspace Windows | Chạy simulator và thiết bị thật, ghi cấu hình/bằng chứng; tài khoản Apple không phải điều kiện của build simulator không ký |
| #3 | Đã bổ sung 3 baseline đăng nhập; tổng 133 ảnh validate đạt, ảnh cũ không đổi | CI cần chạy revision mới; tài liệu đã cập nhật số ảnh |
| #4 | Đã đo Kover: server 86,67%, shared 52,29%; floor 86%/52%; gate chuẩn đạt và ngưỡng 100% bị từ chối | HTML/XML/artifact/gate đã thêm CI; cần kết quả CI revision mới |
| #5 | Harness HTTP/WebSocket cách ly đạt cả memory và PostgreSQL: 4 trận, 200 lượt bấm, reconnect, 0 lỗi; có số đo tài nguyên | Có hướng dẫn/JSON baseline và CI 2 cặp; chưa phải saturation test/khả năng chịu tải production |
| #6 | Production alpha đã có HTTPS/WSS; không đồng nghĩa staging riêng | Cần staging domain/runtime độc lập để diễn tập bảo trì/restore/rollback |
| #7 | Có Playwright nhiều trình duyệt | Thiết bị thật/Safari iPhone/iPad và bàn phím thật chưa được nghiệm thu |
| #9 | Public discovery/join/leave đã có; admission dùng chung, lưu snapshot và kiểm thử race/rollback | Đặc tả cũ yêu cầu lịch/hạn đăng ký, phần đó thuộc #13 và chưa có |
| #10 | Chưa chọn nhà cung cấp thanh toán Web | Cần quyết định quốc gia, nhà cung cấp, giá, phí và webhook; Google Play không thay cổng Web |
| #11 | Avatar còn lưu qua backend/PostgreSQL; chưa có adapter object storage/CDN | Phụ thuộc baseline #5 và cấu hình dịch vụ/chi phí |
| #12 | Realtime và matchmaking vẫn ở một process | Thiết kế/kiểm thử ownership, presence/rate limit nhiều instance; phụ thuộc #5 |
| #13 | Giải công khai vẫn do host bắt đầu; chủ dự án chốt tự bắt đầu và check-in trước 15 phút | Loại người vắng trước chia nhánh; hủy/hoàn phí khi không đủ người; triển khai phục hồi restart |
| #14 | StoreKit/APNs production chưa đủ điều kiện | Chủ dự án chưa có Apple Developer Program; giữ `ready-for-human` |
| #15 | Railway backend một replica; chưa có định tuyến nhiều instance | Phụ thuộc #12; không tăng replica trước khi có ownership/recovery |
| #16 | Chưa có luồng khán giả giải theo nhánh đấu | Phụ thuộc #9/#13; tách quyền chỉ đọc và giới hạn khán giả |
| #17–#24 | Admin, quà, sự kiện, đền bù, thông báo, bảo trì, analytics đã có code/test và đã phát hành | Cập nhật trạng thái release; còn nghiệm thu thiết bị/provider và nguồn ACTIVITY chưa có, không bật chiến dịch thật để thử |
| #25 | Dark-only đã merge/deploy; CI và ảnh baseline có bằng chứng; đã đóng issue | Không cần triển khai lại |
| #26–#27 | Đã đóng, deploy/merge `2bf521f` | Master CI backend/Android/iOS/screenshot đạt; Web E2E có lỗi helper cuộn đang sửa; matcher vẫn cần đối thủ trong giới hạn Elo |
| #28 | Đã tạo checklist Google Play internal testing, nhãn `ready-for-human` | Chủ tài khoản tạo app/sản phẩm/bản ký/license tester/API; chưa bật provider production |

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

## Thứ tự thực hiện

1. Coverage #4 và baseline cách ly #5.
2. Bù kiểm thử còn thiếu #1/#3 và đồng bộ issue/tài liệu với release thực tế.
3. Chuẩn bị phát hành Google Play; xác minh provider khi Console/credential sẵn sàng.
4. Lịch giải #13, sau đó khán giả #16.
5. Hạ tầng #11/#12/#15 dựa trên số đo #5 và lựa chọn dịch vụ; không coi danh sách chi phí là hạ tầng đã triển khai.

Tiến độ của từng mục được cập nhật trong issue tương ứng; chưa đánh dấu các việc
chỉ được mô tả trong tài liệu là hoàn tất.
