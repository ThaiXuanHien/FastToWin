# Quản lý nhiệm vụ cá nhân và nhiệm vụ bang trên admin

Ngày: 24/10/2026, Asia/Bangkok.

Trạng thái: bản thiết kế để chủ dự án duyệt trước kế hoạch triển khai.

## Mục tiêu và phạm vi

Chuyển catalog, mục tiêu, lịch và phần thưởng của nhiệm vụ cá nhân/bang từ giá trị cố định trong code sang dữ liệu server do admin quản lý. App gọi API để hiển thị nhiệm vụ, tiến độ, điều kiện và phần thưởng; người chơi vẫn thực hiện và nhận thưởng trong app.

Đây là phần triển khai đầu tiên của đợt cập nhật nhiệm vụ/bang. Đua top tuần, cấu hình thưởng top, thành viên online/hồ sơ, icon mở theo cấp, giá tạo bang, lịch sử tài sản, thông báo và bot có phần triển khai riêng. Nhiệm vụ bang chuẩn bị metadata điểm cống hiến cho đua top, nhưng phần này không tự mở cuộc đua hoặc phát thưởng top.

Các quyết định toàn đợt được ghi tại `.artifacts/clan-missions-20261024/scope-decisions.md`. Không mở lại các công việc release/UGC/Billing đang tạm hoãn, không làm lại #34/#43.

## Hành vi sản phẩm

### Admin

- Thêm mục Nhiệm vụ, hai nhóm Cá nhân/Bang hội. Danh sách có mã, loại hành động, kỳ, mục tiêu, thưởng, trạng thái và ngày hiệu lực; có tìm kiếm, lọc và phân trang theo chuẩn admin hiện có.
- Tạo nhiệm vụ bằng các loại hành động game đã hỗ trợ: chơi/thắng trận theo loại, chọn đúng số, độ chính xác, thắng hoàn hảo, chuỗi thắng, điểm danh và quyên góp. Không có biểu thức điều kiện hoặc script do admin nhập.
- Điều kiện chỉ xuất hiện cho scope mà backend có thể ghi nhận chính xác. Ví dụ không cộng chuỗi thắng của nhiều người thành một chuỗi; nhiệm vụ bang dạng chuỗi tính số thành viên đạt mốc cá nhân, có khóa chống ghi lặp cho từng thành viên và kỳ.
- Mỗi nhiệm vụ cấu hình mục tiêu, độ khó, ngày/tuần, khoảng ngày hiệu lực, thưởng XP/Vàng/Gem. Nhiệm vụ bang có thêm điểm cống hiến; đây là điểm chuẩn bị cho module đua top, tách khỏi XP cá nhân và XP bang hiện hành.
- Tên/diễn giải mặc định sinh từ mẫu bản địa hóa và tham số mục tiêu. Admin không phải nhập thủ công 12 bản dịch để đổi mục tiêu. TextKey mới phải có đủ các ngôn ngữ đang hỗ trợ.
- Có tạo, nhân bản, sửa bản nháp, xem trước, phát hành, ngừng kỳ tiếp theo và xóa bản nháp chưa dùng. Nút xóa trên nhiệm vụ đã phát hành dẫn đến ngừng/lưu trữ và giải thích lịch sử vẫn được giữ.
- Xem trước phải cho biết cấu hình áp dụng kỳ nào, thưởng một lần và ảnh hưởng tới danh sách nhiệm vụ người chơi. Mọi thay đổi có lý do, request ID và nhật ký trước/sau.
- Owner/Operator quản lý bản nháp; Owner phát hành hoặc thay đổi cấu hình kinh tế. Support không chỉnh nhiệm vụ/thưởng. Tái sử dụng phiên admin, Origin, CSRF và kiểm tra role hiện hành ở thời điểm mutation.

### App

- Giữ vị trí màn nhiệm vụ cá nhân và nhiệm vụ bang, thay nguồn danh sách bằng API. Không xóa các màn hoặc mục mở nhiệm vụ của người chơi.
- Hiển thị tên/mục tiêu, tiến độ, phần thưởng, kỳ hiệu lực và trạng thái chưa hoàn thành/có thể nhận/đã nhận do server trả về.
- Mở lại app, reconnect hoặc đổi thiết bị phải đọc lại cùng instance và trạng thái đã nhận. Không tự dựng lại catalog hay tính thưởng trong client.
- Tải lần đầu có trạng thái đang tải; danh sách rỗng sau response có thông báo phù hợp. Lỗi có thử lại; không dùng danh sách cố định làm fallback cho lỗi mạng.
- Request nhận thưởng chỉ truyền instance ID và khóa chống lặp. Client không gửi tài sản, số điểm, target hoặc tiến độ cần áp dụng.

## Vòng đời catalog và kỳ nhiệm vụ

- Tách mã định danh nhiệm vụ khỏi loại hành động. Nhiều nhiệm vụ có thể dùng một action type với target khác nhau mà không phải sửa một nhánh `when (code)`.
- Bản nháp thay đổi được; revision đã phát hành bất biến. Sửa tạo revision mới, chỉ có hiệu lực từ kỳ tiếp theo phù hợp ngày bắt đầu.
- Kỳ ngày bắt đầu 00:00 mỗi ngày; kỳ tuần bắt đầu 00:00 thứ Hai, Asia/Bangkok. So sánh và lưu timestamp bằng thời gian server.
- Mỗi kỳ lưu một kế hoạch đã đóng băng gồm revision, target, reward, scope và cách chọn. Chỉnh admin sau khi kỳ bắt đầu không đổi phần thưởng hoặc tiến độ của instance đã được xác lập.
- Khi chuyển đổi lần đầu, giữ luật xoay vòng nhiệm vụ cá nhân hiện có: ngày 4 slot (1 dễ, 2 thường, 1 khó/tinh anh); tuần 4 slot (1 thường, 2 khó, 1 tinh anh). Catalog ban đầu được lưu trong database, không còn là nguồn production cố định trong client/runtime.
- Admin có thể bổ sung/xóa nhiệm vụ trong pool cho các kỳ tương lai. Nếu pool không đủ một nhóm slot, kỳ mới chọn số nhiệm vụ khả dụng không trùng và admin nhận cảnh báo trước phát hành; không crash hoặc lặp cùng nhiệm vụ để lấp slot.
- Nhiệm vụ bang dùng cùng catalog/revision/period engine với tiến độ tổng hợp theo bang và tiến độ từng người, thay cho một bộ số quest cố định trong record bang. Quy tắc nhận thưởng nhiệm vụ bang và thưởng top tuần là hai quy tắc riêng, không áp điều kiện thưởng top vào nhiệm vụ legacy.
- Ngừng phát hành không tạo instance mới từ kỳ bị ngừng, nhưng không xóa instance đã có, quyền nhận thưởng hoặc lịch sử. Không thay đổi retroactive số tài sản đã cấp.

## Ranh giới module và API

- Tạo package nhiệm vụ riêng trên server cho catalog, instance, progress và claim. Repository che giấu SQL/locking; service thực hiện authorization, validation và điều phối sự kiện.
- Loại sự kiện nhiệm vụ dựa trên dữ kiện game đã commit. Các điểm gọi từ kết quả trận, điểm danh và quyên góp dùng chung bộ tính tiến độ theo action type; không copy catalog vào mỗi repository.
- Protocol nhiệm vụ riêng chứa DTO đọc catalog/instance/claim và lỗi ổn định. Tránh mở rộng thêm một vùng lớn trong GameProtocol.kt hoặc GameEngine.kt.
- Admin API dưới `/admin/api/missions`: danh sách/chi tiết, tạo và cập nhật nháp, preview, publish, archive và xóa nháp. Dùng phân trang, payload giới hạn, version check và mã lỗi theo convention đang có.
- Player API có xác thực dưới `/api/missions`: đọc nhiệm vụ cá nhân, đọc nhiệm vụ bang mà tài khoản đang có quyền xem và nhận thưởng theo instance. Quyền thành viên được đọc lại từ server cho thao tác bang; client không được chỉ định tài khoản nhận thưởng.
- App dùng HTTP client/controller nhỏ, gắn request vào identity/account epoch để response tài khoản trước không cập nhật UI hoặc cấp quyền claim cho tài khoản sau. Reconnect/refresh lấy lại snapshot server; WebSocket hiện có có thể gửi invalidation, không là nguồn cấp thưởng thứ hai.
- Query và claim phải trả `no-store`; không đưa email, token, nội dung riêng của tài khoản khác hoặc dữ liệu admin vào DTO người chơi.
- Client cũ vẫn nhận snapshot legacy tương thích trong rollout. Cả HTTP mới và command nhận nhiệm vụ cũ phải đi qua một claim service/ledger, không tạo hai đường cấp cùng thưởng. Protocol hiện có không đổi ý nghĩa field để giấu nhiều nhiệm vụ vào một quest cũ.

## Lưu trữ và tính nhất quán

- Thêm migration Flyway phiên bản mới tại thời điểm triển khai; không sửa V1–V63 đã được áp dụng. Tên/version phải kiểm tra lại với head đang làm trước khi tạo migration.
- Catalog/revision có scope, action type/params, period, difficulty, lịch hiệu lực, thưởng, điểm bang và trạng thái; revision đã publish không được UPDATE tại chỗ.
- Kế hoạch theo kỳ đóng băng lựa chọn và revision. Instance cá nhân hoặc bang giữ snapshot mục tiêu/thưởng cùng tiến độ; progress/claim cá nhân trong nhiệm vụ bang có identity riêng.
- Sự kiện có ID và thời điểm server. Uniqueness theo nguồn sự kiện/instance/người đóng góp ngăn replay tăng tiến độ; cùng match nhiều lần không tạo nhiều completion. Hai sự kiện khác nhau vẫn có thể cộng tiến độ đồng thời mà không mất cập nhật.
- Claim và thay đổi ví/XP/ledger/dấu đã nhận phải commit trong một transaction. Request chạy đồng thời nhận cùng kết quả bền vững; lỗi database không trả success trước khi grant được lưu.
- Target, reward, điểm và phép nhân dùng validation cùng kiểm tra overflow. Bảo toàn luật kinh tế cũ: nhiệm vụ quyên góp không hoàn lại nhiều tài sản cùng loại hơn số đã quyên góp; không tự thay đổi các bộ đếm tài sản hợp lệ.
- Điểm cống hiến từ nhiệm vụ ghi thành một nguồn sự kiện riêng có khóa chống lặp. Module đua top sau này đọc điểm nhiệm vụ và quyên góp; claim lại không phát thêm sự kiện điểm.

## Chuyển dữ liệu cũ

- Seed catalog database bằng các nhiệm vụ đã có để không tự thay đổi kinh tế tại cutover. Không seed lại hoặc ghi đè chỉnh sửa admin sau mỗi startup.
- Ánh xạ mission code/period cũ sang instance/revision bền vững; giữ target, thưởng, progress, completed/claimed và khóa ledger cũ. Không xóa rồi sinh lại nhiệm vụ trong kỳ đang chạy.
- Quest bang đang tồn tại chuyển thành instance legacy tương ứng, giữ tiến độ tổng, cống hiến từng người và dấu đã nhận. Có kiểm tra một bang có nhiều người đã nhận và người chưa nhận trong cùng một kỳ legacy.
- Trước khi chuyển nguồn đọc, đối chiếu dữ liệu nhiệm vụ cũ và mới trên fixture PostgreSQL: số tài sản, tiến độ và quyền nhận phải tương đương. Chuyển đổi chạy lại không tạo thưởng hoặc nhiệm vụ trùng.
- Nếu storage chưa sẵn sàng, endpoint báo unavailable; không giả nhiệm vụ trống như thể người chơi không còn quyền nhận.
- Không chạy migration hoặc phát hành catalog mới lên production trong bước thiết kế/triển khai local. Rollout có kiểm tra tương thích riêng trước thao tác vận hành.

## UI và khả năng truy cập

- App dùng Arcade hiện có; admin dùng giao diện Material sáng hiện có. Áp dụng `.agents/skills/ui-ux-pro-max/SKILL.md`, token màu/typography/spacing sẵn có và không thay design system toàn ứng dụng.
- Form có nhãn, đơn vị mục tiêu, số tài sản riêng và lỗi ngay trường; đổi loại hành động phải reset/xác thực params không còn phù hợp.
- Preview thể hiện thông tin người chơi sẽ thấy, kỳ áp dụng và số thưởng. Số âm, vượt giới hạn, kỳ đảo ngược, action không được hỗ trợ và revision stale phải có thông báo rõ.
- Trạng thái nhận thưởng có pending/đã nhận/không đủ điều kiện và phản hồi thành công/lỗi; không để bấm lặp làm cấp thưởng lặp.
- Kiểm tra ảnh phone 375px, desktop và landscape; large text, keyboard/focus, reduced motion, tương phản nền tối và target chạm. Không dùng riêng màu để phân biệt nhiệm vụ hoàn thành hoặc lỗi.

## Nghiệm thu

1. Admin tạo nhiệm vụ từ loại có sẵn, chỉnh mục tiêu/lịch/thưởng, preview và phát hành cho kỳ phù hợp; app hiển thị đúng dữ liệu API sau refresh.
2. Hai nhiệm vụ cùng action type nhưng mã/target khác nhau đều ghi nhận đúng mà không thêm code theo từng mã.
3. Sửa/xóa/ngừng không làm thay target/thưởng đang chạy, mất progress, mất quyền nhận hoặc mở lại một phần thưởng đã nhận.
4. PostgreSQL thật kiểm chứng claim đồng thời, replay sự kiện/request, crash/retry boundary và rollback: mỗi phần thưởng chỉ một ledger, ví/XP không đổi khi thất bại.
5. Đổi kỳ qua nửa đêm/thứ Hai, timezone và restart giữ đúng revision/instance; pool thiếu nhiệm vụ không crash hoặc chọn trùng.
6. Cá nhân và bang đều hoạt động; membership giả, tài khoản khác, role sai, CSRF sai, revision stale và payload sai bị từ chối.
7. Migration fixture có các trạng thái chưa làm/đang làm/hoàn thành/chưa nhận/đã nhận, bao gồm quest bang legacy; không mất quyền hoặc cấp hai lần giữa API mới và command cũ.
8. App load lần đầu, lỗi mạng, retry, reconnect và đổi tài khoản không hiển thị chéo dữ liệu. Admin và app có kiểm tra ảnh cùng accessibility phù hợp.
9. Runtime production không đọc catalog cố định hoặc dùng nó làm fallback khi API/storage lỗi. Các định nghĩa cũ chỉ còn trong migration/fixture kiểm thử cần thiết.
10. Kiểm thử có phạm vi phù hợp; Android emulator CI tiếp tục opt-in và không tự khởi chạy. Không coi host/Web hoặc ảnh render là nghiệm thu thiết bị thật.

## Ranh giới với các phần tiếp theo

- Đua top tuần: dùng điểm nhiệm vụ và quyên góp, top 3, reward do admin setting, có Quà tuần này. Chưa tự gán tỷ lệ quy đổi điểm hoặc thưởng production trong phần nhiệm vụ.
- Bang 50 thành viên, presence, xem hồ sơ và kết bạn cần sửa hợp đồng công khai/permission riêng; GetFriendProfile hiện giới hạn người đã kết bạn nên không được gọi nguyên luồng và giả định dùng cho mọi đồng bang.
- Giá tạo bang theo quyết định mới là 10.000 Vàng + 100 Gem, cấp tối thiểu 10; áp dụng trong phần cấu hình bang, không trộn với migration quyền nhận nhiệm vụ.
- Bot tự ghép sau 60 giây là phần matchmaking riêng, cần xác định reward/quota trước khi tái sử dụng settlement PvP. Không cho bot sinh điểm đua top qua giả sự kiện người thật.
