# Đua top bang hội theo tuần

Đặc tả đã duyệt: [thiết kế](superpowers/specs/2026-10-24-clan-weekly-race-design.md), GitHub issue #61. Module này gồm cấu hình admin, điểm từ nguồn thật, lịch sử thành viên, chốt tuần và thưởng qua hộp thư.

## Cấu hình và lịch

Tuần bắt đầu thứ Hai 00:00, múi giờ `Asia/Bangkok`; đầu kỳ được tính, cuối kỳ không được tính. Owner tạo cấu hình cho **tuần tương lai**, xem trước rồi phát hành. Cấu hình gồm điểm trên 1 Vàng/1 Gem và XP/Vàng/Gem **cho mỗi thành viên đủ điều kiện** của top 1/2/3. Không có mức thưởng mặc định production. Preview hiển thị quy đổi 100 Vàng/1 Gem và ngân sách tối đa 50 người mỗi hạng, 150 người tổng cộng.

Operator được xem trước/nhân bản nguyên cấu hình và lưu nháp không thay đổi kinh tế; phát hành, sửa kinh tế và ngừng tuần tương lai yêu cầu Owner. Support không sửa cấu hình. API giữ kiểm tra current-role, Origin, CSRF, version, request ID và lý do/audit. Published revision bất biến. Tuần hiện tại không sửa tại chỗ hoặc mở bù; tuần không cấu hình hiển thị **Chưa mở đua**, không ghi điểm và không tự sao chép tuần trước. Phát hành cấu hình tuần tiếp theo không thay Quà tuần này.

## Điểm và thành viên

Một điểm bằng 1.000.000 đơn vị nguyên. Tỷ lệ dương có tối đa sáu chữ số thập phân; tính chính xác bằng số nguyên, DTO điểm lớn là chuỗi. Không dùng float, không làm tròn mỗi giao dịch xuống điểm nguyên. Quyên góp Vàng vẫn theo quy tắc sẵn có (bội số 100). Ví dụ tỷ lệ `0.000001` thì 100 Vàng được `0.0001` điểm, vẫn đủ điều kiện đóng góp. Overflow hủy cả giao dịch nguồn, không cấp/trừ một phần.

Quyên góp commit ghi đúng một event cùng transaction ví, lịch sử và tiến độ nhiệm vụ. Replay request không cộng lại. Mission ghi điểm khi **nhận quà thành công lần đầu**, dùng contributionPoints đã đóng băng và tuần server lúc claim. Người nhận cần có tiến độ dương hợp lệ trong lần gia nhập hiện tại; người chỉ nhận quà chung vẫn nhận quà nhiệm vụ theo luật cũ nhưng không có điểm đua. Claim đầu tiên lúc inactive/không góp tiến độ không được backfill khi replay. Quyên góp và claim nhiệm vụ là hai nguồn độc lập.

Mỗi lần gia nhập có tenure riêng. Rời/kick không xóa điểm bang cũ. Vào lại phải có điểm mới trong tenure mới; điểm cũ không khôi phục quyền nhận. Tại cutoff, thành viên phải thuộc bang ngay trước mốc kết thúc và có điểm dương trong tenure đó, của tuần đó. Kick trước cutoff mất quyền; kick sau cutoff không mất quyền dù worker chạy muộn. Lịch sử bang/tenure giữ lại khi các dòng hiện tại bị xóa; không hồi dựng điểm trước cutover.

Bảng hạng gồm mọi bang tồn tại tại thời điểm xem/chốt có điểm dương, cộng tất cả thành viên. Thứ tự: điểm giảm dần, thời điểm đạt điểm cuối sớm hơn, UUID bang ổn định. Chỉ tối đa ba hạng nhận quà; bang không có người đủ điều kiện vẫn giữ hạng, không đôn bang thứ tư. API phân trang vẫn trả ownClan ngoài trang hiện tại.

## Chốt tuần, restart và hộp thư

Khi database được cấu hình, production worker chạy settlement rồi delivery ngay khi khởi động và lặp mỗi 5 giây. Settlement xử lý các tuần đã hết hạn, đóng băng bảng hạng, danh tính bang, reward và entitlement trong một transaction. Khóa chung theo thứ tự admin → mission/race → row/wallet. Nguồn điểm và roster lấy thời gian sau khi đợi khóa; không nhận clock/điểm/người hưởng từ client.

Entitlement duy nhất theo week+clan+user, campaign/source ID ổn định. Delivery có outbox/checkpoint; retry, restart và nhiều worker không phát hai lần. Kết quả `SETTLED` không có nghĩa mọi thư đã giao hoặc đã claim. `CLOSED` là tuần hết hạn chưa chốt; checkpoint lỗi hiển thị trì hoãn, giữ quyền retry. Tài khoản đã xóa chuyển `ACCOUNT_UNAVAILABLE`, không tạo lại. Guest giữ pending `REGISTRATION_REQUIRED`; nâng cấp cùng identity mới giao thư và bắt đầu hạn nhận.

Hạn nhận là **7 ngày từ khi thực sự sẵn sàng**, lấy thời gian sau khi đợi khóa người nhận; downtime trước giao không ăn mất hạn. Hộp thư hiển thị tuần, bang, hạng, reward mỗi người và deadline tuyệt đối. Claim/claim-all dùng giao dịch ví/XP/ledger cũ. Source là `CLAN_WEEKLY_RACE`, tách `ADMIN_GIFT` và `COMPENSATION`. Thư đua đã chốt không được hủy qua admin campaign thông thường; kick sau cutoff không thu hồi quà. Overflow giữ quyền retry. Lịch sử kết quả và entitlement không bị xóa theo live clan/user.

## API và giao diện

Player Bearer routes: `/api/clan-race`, `/standings`, `/contributions`, `/history`. Contributions chỉ trả bang hiện tại của người gọi. Mailbox giữ `/api/gifts/mailbox` và prefix cũ `/gifts/mailbox`; claim và claim-all không có đường cộng ví riêng. Admin root `/admin/api/clan-race` gồm list/detail/create/draft/clone/preview/publish/archive. Lỗi storage trả 503 `STORAGE_UNAVAILABLE`, không giả dữ liệu rỗng; responses no-store.

App: Bang hội → Đua top tuần (`/clan/race`), giữ năm tab. Quà tuần này dùng cấu hình server đang chạy, điểm hiển thị chuỗi chính xác, tách đóng góp mission/donation/tenure hiện tại và cũ. Tải lỗi có retry; đổi tài khoản/bang/token/view hoặc request mới fence kết quả cũ. Có loading/inactive/empty/closed/settled/history và thời gian cập nhật. TextKey có bản dịch Tiếng Việt và English; source chỉ đóng gói hai catalog này. Nhãn giờ hiển thị là Giờ Việt Nam (UTC+7), còn mã IANA và mốc chốt của server giữ nguyên.

## Nghiệm thu local có kiểm soát

Runner `e2e/support/clan-race-live.mjs` sử dụng admin assets và Kotlin/Wasm thật → Ktor thật → một UUID schema trong PostgreSQL test loopback `127.0.0.1:15432/admin_test`. Ports riêng **18088/18089**. Không dùng mocked MissionState hoặc fulfill API. `ClanRaceAcceptanceLauncher.kt` chỉ thuộc test source: clock file/stdin điều khiển economy, auth giữ wall time. Không sửa OS clock, không thêm production test endpoint.

Chuẩn bị JDK/Android SDK, PostgreSQL test settings đã được guard, Playwright/Chromium của `e2e`. Không đưa password vào command line, trace hay report. Build trước khi chạy:

```powershell
./gradlew.bat :server:clanRaceAcceptanceClasspath :webApp:wasmJsBrowserDevelopmentWebpack --max-workers=1 --no-parallel --console=plain
# E2E_POSTGRES_SETTINGS: đường dẫn settings local đã guard, đọc passwordFile trong bộ nhớ.
# E2E_PSQL: executable psql local; JAVA_HOME: JDK.
node --test e2e/support/clan-race-live-startup.test.mjs
node e2e/support/clan-race-live.mjs
```

Runner tự tạo fixture accounts, cấu hình admin thật, thực hiện source donation/mission claim, SQL sắp xếp roster/ví có transaction-local clock, chốt muộn/restart/concurrent workers và app claim/reload. Hai campaign ordinary/compensation cho claim-all được sắp xếp bằng GiftTransaction test source, không được tính là nghiệm thu publication của hai module đó. HTTP receipts chỉ gồm các route kinh tế được chọn; không lưu auth token/cookie/password hay trace. Evidence/images ở `.artifacts/clan-weekly-race/task-5-live/`, không commit.

Clock economy của fixture tiến vào tuần tương lai, còn client/auth giữ wall time. Vì vậy ảnh mailbox có thể ghi “Còn 16 ngày”; đây không phải bằng chứng UI còn đúng 7 ngày. Bằng chứng hạn 7 ngày là `expiresAt - availableAt = 604800000` từ receipt và deadline tuyệt đối; việc reload trong scenario là explicit reload, không chứng minh mọi thay đổi tự refresh trong nền.

Startup regression thực sự gọi Java executable không tồn tại và kiểm tra FAIL evidence, password/schema cleanup, ports trống. Runner chỉ dừng backend nó tạo và drop UUID schema nó sở hữu; không dừng shared PostgreSQL, không đụng schema/ports preview khác. Bất kỳ cleanup failure giữ trạng thái FAIL; nếu backend chưa dừng thì giữ schema để tránh chạy vào storage đã xóa.

## Rollout và rollback

V67 thêm config/point/tenure history; V68 thêm settlement/entitlement/outbox và source type. Không chỉnh migration cũ. Trước rollout thực tế cần backup và kiểm tra migration/quyền database, theo dõi FAILED/PENDING/outbox/availability và công bố rõ tuần tương lai đầu tiên. Không backfill tuần đang chạy, không phát quà người chơi thật để kiểm tra.

Có thể ngừng một cấu hình tương lai trước khi tuần bắt đầu; không sửa/revoke kết quả/reward đã chốt. Rollback binary về trước V68 cần đánh giá tương thích enum/JSON/source mới và worker, không xóa migration, ledger hoặc entitlement để “hoàn tác”. Dừng worker chỉ trì hoãn delivery, không xóa quyền đã chốt. Triển khai production, thao tác provider, publish/push/merge và emulator không nằm trong nghiệm thu này.
