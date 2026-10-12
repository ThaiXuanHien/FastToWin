# Rà soát backlog — 12/10/2026

Nguồn quyết định: GitHub Issues và yêu cầu chủ dự án ngày 12/10/2026. Rà soát code
release `0a88a75`, bản sửa #31 tại `f8ef3a9`, đặc tả/test và hồ sơ release. Các
ghi chú “chưa merge/deploy” trong báo cáo local cũ là lịch sử; không dùng làm
trạng thái hiện tại. Không sửa hàng loạt checkbox hoặc đóng issue chỉ vì có code.

## Trạng thái đã đối chiếu

| Issue | Phần đã triển khai và bằng chứng | Phần còn lại / quyết định |
| --- | --- | --- |
| #31 | Marker profile nền độc lập với thứ tự message; ví vẫn cập nhật, lỗi giải B và pending profile không bị xác nhận nhầm. RED đúng lỗi rồi GREEN 647 server / 249 shared / 106 protocol, 0 failure/error/skip; scoped review không còn Critical/Important | Commit local f8ef3a9, chưa phát hành; client cũ cần cập nhật để dùng reducer mới |
| #9 | Public discovery/filter/số chỗ, đăng ký/rời, chống trùng/vượt chỗ, paid admission bền vững/rollback; schedule/deadline/check-in đã release cùng #13. GameEngineTest public cases, PostgresTournamentAdmissionTest, ScheduledTournamentTest, GameStateTest và TournamentScreenTest; Web pwa-ios/tournament-schedule | Không xây lại public tournament; physical-device acceptance theo #2/#7 |
| #17 | Nền vận hành và năm issue con có triển khai/release. Bộ server647 local mới chạy lại với PostgreSQL; CI master38188720799 đủ năm job SUCCESS | Tổng kết theo từng issue con; bổ sung nguồn hoạt động mới, giữ riêng provider/device acceptance |
| #18 | Lịch mẫu tạo draft, UTC/[start,end), x2 XP/Vàng theo server, snapshot phần thưởng, max factor từng tài sản, retry/restart và không nhân quà/đền bù. RewardEventService/Routes, PostgresRewardEventRepository/Settlement, RewardEventGameEngine; admin-events và native UI evidence | Không tự tạo lịch lễ hoặc bật chiến dịch thật để thử; bản dịch ngoài VI/EN và thiết bị thuộc nghiệm thu chung |
| #19 | Hộp thư/claim một lần, freeze người nhận, rollback/replay/concurrency/restart, privacy và quà không tăng earned leaderboard. GiftRoutes/PostgresGiftRepository/InMemoryGiftRepository/GiftMaintenance và admin-gifts; precision follow-up giữ totals dạng chuỗi | Bằng chứng local dùng PG thật, không đồng nghĩa hai điện thoại thật đã nghiệm thu; không phát quà live để test |
| #20 | Incident/round/supplemental reason, preview và đóng băng tập nhận, claim engine chung, audit/roles, nhận một lần, nội dung không lộ evidence. Bảy suite compensation và admin-compensation/counters | MVP từng báo ACTIVITY unavailable đúng sự thật; nay bổ sung nguồn kết nối 90 ngày theo lựa chọn mới, không suy diễn từ last_seen |
| #21 | Admin riêng/Owner–Operator–Support, CSRF/origin, thu hồi phiên/current-role, tìm mã người chơi/mail, phân trang, mutation audit/version/replay, các màn vận hành. AdminService/Routes/PostgresAdminRepository/AdminPlayerReference/AdminAssets; admin.spec/worker và màn con | Nghiệm thu thiết bị thật vẫn riêng; không thêm chỉnh số dư/Elo trực tiếp hoặc mở API công khai |
| #22 | Thông báo trong app/banner, lịch/hủy, receipt đồng bộ, snapshot/replay/restart, VI/EN, push tùy chọn/tôn trọng cài đặt/unknown. Announcement suites, protocol compatibility, admin-announcements và native evidence | Provider push và thiết bị thật cần cấu hình/nghiệm thu; không lấy fake provider hay 0 sends làm bằng chứng push LIVE |
| #23 | Planned/drain/emergency, Owner/version/replay/audit, durable policy/runtime observation, môi trường override, settle/receipt một lần, maintenance recovery và admin/status | Mười suite maintenance gồm HTTP/Netty/PG/concurrency; staging rehearsal #6 là công việc hạ tầng riêng, không thay bằng local test |
| #24 | Facts đăng ký/direct/guest-upgrade và item debit nguyên tử, erasure/anonymous historical totals, API snapshot/UTC+7/tuần-tháng/distinct/exact money, chart+bảng và role fences. AnalyticsFacts/Repository/Routes/Query, GoogleOrdersFinancialSource/PostgresFinancialSync, admin-analytics | Google known-order adapter đã có nhưng OFF; phải cấu hình Console để nhận dữ liệu thật, chỉ PARTIAL. Apple UNAVAILABLE. Không dựng doanh thu từ số Gem/catalog; ACTIVE_PLAYERS bổ sung bằng nguồn mới |

Tên suite chỉ vị trí bằng chứng, không phải số test cộng thêm vào tổng. Các lượt
historical và focused có giao nhau, không cộng thành số “test mới”. Triển khai
nghiệp vụ mua vật phẩm bằng GEMS hiện không tồn tại trong cửa hàng Gem/Vàng;
mô hình hỗ trợ GEMS không được xem là đã có writer bán vật phẩm bằng Gem.
Không khôi phục cửa hàng cosmetic đã được chủ dự án bỏ.

## Bằng chứng release và giới hạn

- PR29 đã merge; master0a88a75 có CI38188720799 SUCCESS cho build/test, Android
  screenshot, Android UI, iOS simulator build và Web E2E.
- Hồ sơ release12/10 ghi backend8f5f5ff3-e126-4fd1-a609-4cec9fe10f65 và
  web4a40588b-e880-4c8b-812b-e80bbeabcff5 SUCCESS; maintenance trở lại ACTIVE.
  Đây là bằng chứng release đó, không phải deploy bản activity/#31 mới.
- Bản backup production trước release có SHA-256 và đọc được danh mục dump;
  chưa có diễn tập restore thực tế trên một staging Railway riêng.
- Bộ local647/249/106 của #31 không gọi Google/Apple production, không thay
  nghiệm thu thiết bị thật hoặc saturation test. File IDE của chủ dự án giữ nguyên.

## Quyết định backlog mới

- #16 CLOSED/NOT_PLANNED + wontfix: bỏ khán giả giải đấu.
- #10 CLOSED/NOT_PLANNED + wontfix: bỏ thanh toán Gem tiền thật trên Web; mua
  qua app. Ví và đổi Gem/Vàng vẫn giữ nguyên.
- #28/#1 Google Play internal testing/mua Gem thật để sau. Chủ dự án mới có tài
  khoản Console; xem [checklist](google-play-internal-testing.md).
- #2/#7 kiểm thử thiết bị và #14 StoreKit/APNs chờ phản hồi có Mac/thiết bị/tài
  khoản Apple; CI simulator không thay thế các bước này.
- #6/#11/#12→#15: [lựa chọn hạ tầng](infrastructure-options-2026-10-12.md), chưa
  provision/buy/tăng replica. Staging trước, object storage, Redis rồi mới nhiều backend.

## Nguồn hoạt động mới — triển khai local (#32)

Chủ dự án chọn tài khoản đăng nhập và kết nối game, lưu 90 ngày. Hợp đồng tại
[thiết kế](superpowers/specs/2026-10-12-connected-activity-design.md), hướng dẫn
[nguồn hoạt động](admin-activity.md). Ghi nhận kết nối `/game` được chấp nhận,
kể cả lobby/idle; không tính guest, HTTP-only login hoặc khoảng không quan sát.
Không backfill hoặc suy diễn từ last_seen.

- Collector `8d71243`: checkpoint 30 giây, gap trên 60 giây/failure/restart tách
  coverage; retention/cutoff 90 ngày, cùng privacy link và quy tắc xóa tài khoản.
  Preview recipients/coverage chung snapshot một câu SQL tránh cleanup race.
- Backend `607ed58`: `ACTIVE_PLAYERS` distinct toàn kỳ và ngày/tuần/tháng;
  partial/unknown giữ đúng ý nghĩa, tiền thật không trộn Vàng/Gem. `ACTIVITY`
  compensation dùng khoảng sự cố trên cùng gift transaction; persist nguồn,
  ràng buộc preview, đóng băng tập nhận, giữ replay/current-role/claim một lần.
  Coverage thiếu trả lỗi an toàn, không fallback toàn bộ người dùng.
- Admin `b4ee1bf`: selector mới, source đã lưu và legacy fallback; coverage,
  freshness, 90 ngày, khoảng sự cố, bảng số liệu và các lỗi có nhãn rõ. Giữ white
  Material, UI UX Pro Max và guard phiên/modal. Ảnh 360/1440 đã được xem trực tiếp.
- Review từng task đều đạt spec và quality. Test-only `44bdb72` sửa đường dẫn
  ảnh Windows có dấu cách và locator logout phía sau modal; không đổi nghiệp vụ.
- Gate tích hợp: 678 server / 249 shared / 106 protocol, không failure/error/skip,
  PostgreSQL thật. Localization và coverage thresholds đạt (server86.95%,
  shared53.45%). Focused browser29, fix12 và assets2 qua; all-admin gate cuối 255 qua, 2 ca tùy chọn backend local chưa cấu hình bị bỏ qua.

Đây là bản local trong branch `codex/activity-history`; chưa merge/deploy.
Nguồn chỉ bảo đảm coverage một backend. Không kích hoạt provider, phát đền bù
thật, mua hạ tầng hoặc thay nghiệm thu thiết bị/staging. Revenue Google vẫn OFF,
Apple UNAVAILABLE theo phạm vi đã hoãn. Minor còn theo dõi: đồng bộ finalizer
trong test socket replacement bằng delay20ms; final review sẽ phân loại trước PR.
