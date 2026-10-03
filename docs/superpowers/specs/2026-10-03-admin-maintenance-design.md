# Điều khiển bảo trì từ web quản trị

Ngày: 03/10/2026, Asia/Bangkok. Thuộc mở rộng vận hành của Issue #21 và #17.
Issue: https://github.com/ThaiXuanHien/FastToWin/issues/23.
GitHub Issue là đặc tả chính; tài liệu này là bản đối chiếu trong repository.
Chủ dự án đã đồng ý hướng thiết kế hai chế độ trong chat. Chi tiết dưới đây
cần được duyệt trước khi lập kế hoạch và triển khai; chưa có thay đổi runtime.

## Kết quả mong muốn và hiện trạng

Owner bật/tắt bảo trì trong admin để cập nhật phiên bản, xử lý sự cố,
bị tấn công hoặc quá tải. Người chơi nhận trạng thái và thông báo nhất quán;
trận đấu và giao dịch không bị coi là hoàn tất chỉ vì một kết nối bị đóng.

Hiện server có `/status`, công tắc `FASTTOWIN_MAINTENANCE` và thông báo qua
biến môi trường; client có MaintenanceScreen. WebSocket kiểm tra trạng thái
khi kết nối. Cơ chế mới cần kiểm tra cả hành động trên kết nối đang tồn tại.
Không chỉ thêm công tắc giao diện hoặc dựa vào polling của client.

## Quyền, giao diện và ý định thao tác

- Owner được bật, đổi thông báo, chuyển sang khẩn cấp và tắt. Operator/Support
  được đọc trạng thái, không mutation, kể cả gọi trực tiếp API.
- Mục **Bảo trì** hiển thị trạng thái hiệu lực, nguồn cấu hình, nguyên nhân,
  thông báo Việt–Anh, thời điểm bắt đầu, dự kiến kết thúc, số trận còn chạy,
  người thay đổi và thời điểm thay đổi.
- Nút bật mở modal chọn THEO_KẾ_HOẠCH/KHẨN_CẤP, nguyên nhân
  UPDATE/INCIDENT/SECURITY/OVERLOAD/OTHER, lý do nội bộ và thông báo Việt–Anh.
  Thời điểm dự kiến kết thúc có thể để trống; không tự mở lại khi đến giờ này.
- Theo kế hoạch: cảnh báo mặc định 5 phút, có thể chọn 0–60 phút; sau cảnh báo,
  chờ trận đang chạy tối đa mặc định 15 phút, chọn 1–60 phút.
- Khẩn cấp: modal xác nhận rõ chặn người chơi và ngắt kết nối game ngay khi
  chính sách hiệu lực. Không yêu cầu chờ hết thời gian của chế độ kế hoạch.
- Tắt cần lý do và xác nhận. UI hiển thị kiểm tra sức khỏe phục vụ quyết định;
  không tự suy diễn server an toàn hoặc tự mở lại sau restart.
- Nền trắng Material, modal, icon có nhãn truy cập, nút xác nhận có chữ;
  loading/error/pending rõ ràng, khóa đóng modal khi mutation chưa xác nhận.
  Dùng design-system/fast-to-win/pages/admin.md và ui-ux-pro-max.

## Máy trạng thái và ảnh hưởng người chơi

Trạng thái bền vững: ACTIVE, ANNOUNCING, DRAINING, MAINTENANCE.
Mode PLANNED/EMERGENCY được lưu riêng; thông báo dự kiến không đồng nghĩa lịch.

| Trạng thái | Chính sách |
| --- | --- |
| ACTIVE | Hoạt động bình thường |
| ANNOUNCING | Banner/đếm ngược; chưa chặn hoạt động; hết giờ sang DRAINING |
| DRAINING | Chặn trận mới, tạo/tham gia phòng mới và các hành động kinh tế mới; cho trận đã bắt đầu kết thúc |
| MAINTENANCE | Chặn mọi hành động game của người chơi; hiển thị màn bảo trì và đóng kết nối game |

- PLANNED đi ACTIVE → ANNOUNCING → DRAINING → MAINTENANCE. Có thể bỏ bước
  ANNOUNCING khi cảnh báo 0 phút. Không có trận đang chạy thì hoàn tất drain ngay.
- EMERGENCY chuyển từ bất kỳ trạng thái phục vụ người chơi sang MAINTENANCE.
  Owner có thể tắt ở ANNOUNCING/DRAINING/MAINTENANCE; thao tác không tự phục hồi
  trận đã bị kết thúc hoặc khôi phục phiên đã đóng.
- DRAINING cho phép resume vào đúng trận đã bắt đầu, hành động thi đấu,
  spectator hiện tại, kết quả và settlement; không cho dùng resume để tạo trận.
  Client không thay màn trận bằng MaintenanceScreen trong DRAINING.
- Khi hết hạn drain hoặc khẩn cấp: kết thúc trận chưa hoàn tất với lý do
  bảo trì, không ghi thua/phạt Elo vì bị ngắt. Giữ kết quả/thưởng đã commit;
  không phát phần thưởng cho trận chưa hoàn tất. Quy tắc này áp dụng cả giải đấu.
- Điều kiện bắt đầu trận và chuyển trạng thái phải được đồng bộ ở server:
  một trận hoặc bắt đầu trước ranh giới và được drain, hoặc bị từ chối.
- Mutation ví/quà đã tiếp nhận phải commit hoặc rollback nguyên tử, retry
  không trả thưởng hai lần. Chặn ý định mua/đổi/nhận quà mới khi DRAINING hoặc
  MAINTENANCE; giữ khả năng đối chiếu kết quả đã commit.
- Thanh toán store đã phát sinh có thể đến sau khi bật bảo trì. Luồng xác minh
  và hoàn tất receipt đã thanh toán vẫn được xử lý một lần theo cơ chế hiện có;
  không bỏ receipt hoặc coi đóng phiên là hủy thanh toán. Không khởi tạo mua mới.
  Cần luồng hoàn tất receipt được xác thực độc lập với kết nối game bị đóng;
  không mở lại toàn bộ socket chỉ để xác minh thanh toán. Nguồn thông báo store
  chỉ được acknowledge sau khi lưu bền vững theo hợp đồng nguồn đó.
- `/status`, `/health`, admin và các luồng xác minh/hoàn tất thanh toán nói trên
  có chính sách riêng; không dùng một middleware chặn toàn bộ HTTP một cách mù.
  Đăng xuất vẫn thực hiện được. Đăng nhập/đăng ký người chơi bị chặn từ DRAINING.
- Người chơi không có bypass bảo trì. Client cũ/HTTP trực tiếp/socket mở từ
  trước đều chịu kiểm tra server. Mất mạng có trạng thái offline riêng.

## Lưu trữ, hợp đồng và vận hành

- PostgreSQL lưu snapshot/version, mode, nguyên nhân, nội dung, lịch chuyển,
  người thay đổi; restart tiếp tục trạng thái và tính thời hạn từ thời gian UTC.
- Mutation admin dùng phiên hiện có, current-role, CSRF, exact Origin,
  expectedVersion, request ID và audit trong cùng transaction. Replay cùng
  ý định trả kết quả cũ; payload khác/xung đột version trả 409.
- `/status` giữ các trường cũ; thêm trạng thái, mode, version, nội dung Việt–Anh
  và mốc thời gian cho client mới. Trường maintenance cũ chỉ true ở MAINTENANCE,
  để client cũ không ngắt trận trong DRAINING; server vẫn chặn hành động mới.
  Lý do nội bộ và actor không đưa ra API công khai.
- Snapshot mới áp dụng trên instance đang chạy trong tối đa 5 giây sau commit;
  UI chỉ báo đã lưu và hiển thị trạng thái thực tế, không báo mọi phiên đã đóng
  ngay khi mutation commit. Instance có thể đóng ngay khi nhận snapshot.
- Biến môi trường maintenance=true là khóa khẩn cấp vận hành có ưu tiên cao
  hơn snapshot DB. Admin không tắt được khóa này; UI chỉ rõ nguồn và yêu cầu
  tắt bằng công cụ vận hành hiện có. Snapshot DB không bị ghi đè bởi override.
- DB lỗi: mutation trả unavailable, không báo thành công; giữ chính sách chặn
  đã biết và tạm chặn ý định game/kinh tế mới khi không đọc được chính sách.
  Chưa tải được chính sách lúc startup thì không phục vụ game. Không
  công bố trạng thái ACTIVE giả khi chưa đọc được nguồn có thẩm quyền.
- MVP dành cho mô hình runtime hiện có; mọi instance cùng cấu hình phải đọc
  cùng snapshot. Điều phối trận realtime xuyên instance thuộc #12/#15.
- Nút bảo trì không triển khai bản mới, rollback hoặc thay thế việc xử lý
  nguyên nhân sự cố. Không tự động truy cập hệ thống production trong task này.

## Ngoài phạm vi bản đầu

Tự bật theo tải/lỗi, chế độ hạn chế chỉ chặn trận mới, lịch lặp, push tự động,
đền bù tự động, thao tác triển khai, tự tắt và bypass thử nghiệm cho người chơi.
Banner cảnh báo trong game là phần tối thiểu của trạng thái kế hoạch, không
phụ thuộc hoàn thành hệ thống thông báo/push #22. Đền bù vẫn theo #20.

## Nghiệm thu

- [ ] Owner mutation được; player/Operator/Support/thiếu CSRF/sai Origin bị từ chối.
- [ ] Version/replay/audit failure nhất quán, không đổi trạng thái khi rollback.
- [ ] Restart ở mỗi trạng thái giữ đúng thông báo, thời hạn và chính sách.
- [ ] Override môi trường không bị nút tắt admin vượt qua.
- [ ] Socket cũ và API trực tiếp không tạo trận/mutation mới sau ranh giới drain.
- [ ] Trận đang chạy hoàn tất và settle đúng một lần trong drain; resume đúng phạm vi.
- [ ] Hết hạn drain/khẩn cấp ngắt trận không tạo thua/phạt/trao thưởng chưa hoàn tất.
- [ ] Claim/đổi/mua đồng thời với bảo trì commit hoặc rollback, retry không trả trùng.
- [ ] Receipt đã thanh toán đến muộn vẫn được đối chiếu/hoàn tất một lần.
- [ ] Status tương thích client cũ; mất mạng và bảo trì hiển thị riêng.
- [ ] DB lỗi/startup/chuyển trạng thái đồng thời được kiểm thử, không ACTIVE giả.
- [ ] Admin vẫn truy cập khi bảo trì; phone/desktop, keyboard, pending và ảnh chụp được kiểm tra.
