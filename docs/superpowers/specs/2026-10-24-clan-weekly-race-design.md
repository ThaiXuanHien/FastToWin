# Đua top bang hội theo tuần

Ngày24/10/2026,Asia/Bangkok. Trạng thái: chủ dự án đã duyệt thiết kế ngày24/10/2026 trong cuộc trao đổi; triển khai theo kế hoạch cùng ngày. Theo dõi trong tracker#61. Nhiệm vụ admin#60 đã merge local ở8e4918e. Không sửa quy tắc nhiệm vụ mới chỉ có hiệu lực từ kỳ tiếp theo; chủ dự án vừa yêu cầu giữ nguyên.

## Mục tiêu và ranh giới

Tạo cuộc đua tuần cho mọi bang, tổng điểm từ nhiệm vụ bang và quyên góp Vàng/Gem của toàn bộ thành viên. Top3 nhận thưởng theo hạng; thành viên có đóng góp và còn thuộc bang tại thời điểm chốt được nhận. App có Đua top tuần và Quà tuần này, xem các bang cùng đua, vị trí bang mình và đóng góp từng người.

Phần này có cấu hình admin, ghi điểm, lịch sử gia nhập, bảng xếp hạng, chốt tuần và thưởng qua hộp thư. Không trộn các mục còn lại: presence/hồ sơ/kết bạn, bộ icon mở theo cấp, giá tạo bang, lỗi thông báo/lịch sử tài sản toàn app, bot và release/provider. Dùng icon bang hiện có cho bảng đua; không đổi lịch mở khóa hay giá tạo bang trong phần này. Không deploy/migrate production, bật provider hoặc chạy emulator.

## Quyết định người dùng đã xác nhận

- Top3 theo tổng cống hiến toàn bang, gồm nhiệm vụ và quyên góp; không lấy riêng20thành viên.
- Thưởng mỗi hạng do admin cấu hình, không dùng bảng thưởng từng gợi ý làm mặc định.
- Không có ngưỡng số nhiệm vụ, số ngày hoặc điểm tối thiểu khác ngoài có đóng góp.
- Rời/kick trước chốt không nhận quà của bang đó. Điểm đã ghi vẫn thuộc bang cũ.
- Rời/kick rồi vào lại cùng bang: phải có đóng góp mới sau lần gia nhập mới; điểm cũ không tự phục hồi quyền nhận.
- Admin đặt tỷ lệ Vàng/Gem→điểm theo tuần; áp dụng tuần tiếp theo. Chưa có cấu hình phát hành thì chưa mở đua.
- Điểm từ nhiệm vụ cộng khi người có đóng góp bấm nhận quà, không tự cộng khi nhiệm vụ hoàn thành. Người không góp tiến độ không có điểm đua từ việc nhận quà nhiệm vụ chung.

## Phương án và lựa chọn

Chọn module đua tuần riêng với cấu hình bất biến, sổ sự kiện điểm, lịch sử từng lần gia nhập và kết quả chốt bền vững. Hook vào giao dịch nhiệm vụ/quyên góp; tái sử dụng hộp thư để nhận thưởng.

Không chọn tính lại từ bảng thành viên/cống hiến hiện tại: các dòng bị xóa khi rời/kick và tổng cống hiến cả đời không xác định đúng tuần. Không chọn quét lại toàn bộ lịch sử trận và phát thưởng thủ công: tốn truy vấn, tăng phạm vi và khó giữ chống lặp. Kết quả đã chốt phải xem lại được sau đổi tên/icon/rời hoặc giải tán bang.

## Kỳ tuần, cấu hình và bằng điểm (đề xuất để duyệt)

- Tuần bắt đầu00:00thứHai, kết thúc00:00thứHai kế tiếp,Asia/Bangkok; khoảng đầu bao gồm/cuối không bao gồm, thời gian server.
- Owner phát hành cấu hình tỷ lệ và thưởng cho tuần tương lai; Operator soạn nháp/xem trước, không thay giá trị kinh tế hay phát hành; Support không sửa cấu hình. Tái sử dụng Origin/CSRF/current-role/version/requestID/reason/audit.
- Cấu hình chỉ tạo cuộc đua khi đã phát hành đầy đủ. Tuần không cấu hình hiển thị Chưa mở đua, không ghi điểm hoặc hứa quà. Không tự sao chép cấu hình cũ sang tuần mới; admin có thể nhân bản rõ ràng rồi phát hành.
- Cấu hình tuần đã phát hành và đang chạy không sửa tại chỗ; thay đổi cho tuần sau. Không mở bù tuần đang dở hoặc tính ngược điểm trước lúc bật.
- Bảng hạng gồm bang tồn tại tại thời điểm đang xem/chốt, tổng điểm dương. Khi bằng điểm: bang đạt tổng điểm cuối đó sớm hơn đứng trước; nếu vẫn bằng dùng IDbang ổn định làm khóa cuối. Tối đa3hạng, không phát đồng hạng ngoài top3; bang0điểm không chiếm hạng thưởng.
- Snapshot kết quả giữ tên/icon/cấp bang và điểm tại chốt. Bang giải tán trước chốt không nhận hạng thưởng; giải tán sau chốt không xóa kết quả/quyền đã chốt.

## Điểm quyên góp

- Admin nhập số điểm cho1Vàng và cho1Gem, số dương tối đa6chữ số thập phân. UI xem trước thêm ví dụ100Vàng/1Gem, hiển thị rõ đơn vị; không mặc định giá trị production.
- Lưu/tính bằng đơn vị phần triệu điểm và số nguyên chính xác, không dùng float hoặc sốJS vượt giới hạn chính xác. Điểm từ nhiệm vụ nguyên được quy đổi cùng đơn vị. DTO tổng/đơn vị lớn truyền chuỗi thập phân; phép tính có chốt giới hạn/overflow và lỗi rõ, không wrap/saturate hoặc cấp phần thưởng một phần.
- Mỗi quyên góp commit thành công trong tuần có cấu hình được ghi đúng một nguồn điểm, cùng transaction với trừ ví/quyên góp/tiến độ nhiệm vụ. Request replay không cộng lần hai.
- Không làm tròn từng giao dịch xuống điểm nguyên: chia100Vàng thành nhiều lần vẫn có cùng tổng với quyên góp100Vàng một lần. Khoản nhỏ vẫn có điểm dương chính xác và được tính là có đóng góp; giao diện không làm tròn về0rồi báo không đủ quyền.
- Tuần nhận điểm là tuần của thời điểm giao dịch server; không chấp nhận timestamp hay số điểm từ client. Không nhập lại quyên góp/claim trước cutover làm điểm đua.

## Điểm từ nhiệm vụ bang

- Dùng contributionPoints đã đóng băng của instance nhiệm vụ, không dùng bản nháp mới hoặc tự cộng từ XPbang. Nhiệm vụ cá nhân không sinh điểm bang.
- Chỉ lần nhận quà nhiệm vụ thành công đầu tiên mới có thể phát điểm đua. Người nhận phải còn trong bang và có sự kiện tăng tiến độ hợp lệ của chính người đó thuộc lần gia nhập hiện tại, trước lần claim này. Chỉ claim chung không có đóng góp không phát điểm đua; quyền XP/Vàng/Gem nhiệm vụ hiện có không bị thu hẹp bởi luật top tuần.
- Điểm được ghi vào tuần đang chạy tại thời điểm claim, theo lựa chọn của chủ dự án; nhiệm vụ hoàn thành tuần trước nhưng nhận ở tuần sau sẽ ghi vào tuần sau nếu tuần đó đang mở. Mỗi user/instance chỉ một nguồn điểm, replay quaHTTP/WebSocket không phát thêm.
- Nếu claim lần đầu lúc cuộc đua chưa mở hoặc không có đóng góp trong lần gia nhập hiện tại, không cấp điểm đua bù khi thử nhận lại sau đó. Không đảo lại quà nhiệm vụ đã cấp.
- Một quyên góp có thể tạo điểm trực tiếp và làm hoàn thành nhiệm vụ. Điểm khi claim nhiệm vụ là nguồn riêng đã được admin đặt, không phải replay của giao dịch quyên góp; UI tách hai nguồn.
- Điểm nhiệm vụ của cùng một instance có thể được các thành viên thực sự góp tiến độ nhận riêng một lần/người. Admin thấy điểm là mỗi người đủ điều kiện nhận, không phải tổng ngân sách chia giữa thành viên.

## Thành viên, rời/vào lại và thời điểm chốt

- Mỗi lần gia nhập có tenureID và khoảng thời gian server; tham gia/rời/kick/giải tán ghi lịch sử trong cùng transaction. Tiến độ/điểm mang đúng tenure tại lúc phát sinh. Không suy từ joined_at hiện tại để phục hồi lịch sử đã mất.
- Tại chốt: user phải còn thuộc bang ngay trước mốc kết thúc tuần và có điểm đua dương phát sinh trong tenure đó của chính tuần đó. Không thêm ngưỡng đóng góp khác.
- Điểm cũ vẫn thuộc tổng bang sau khi user rời; quyền nhận của tenure cũ không chuyển sang tenure mới. Có điểm mới sau khi vào lại sẽ có quyền, không cần lặp số ngày hay nhiệm vụ.
- Chốt theo lịch sử tại cutoff, không theo membership ở lúc worker thực sự chạy. Nếu server nghỉ qua mốc hoặc kick sau cutoff trước khi worker tỉnh lại, người đủ quyền tại cutoff vẫn giữ quà. Nếu kick commit trước cutoff thì không có quyền. Khóa/thời gian phân định thống nhất với ghi điểm.
- Không hồi dựng tuần trước cutover. Tuần đầu mở từ đầu tuần tương lai đã phát hành; membership hiện có được đánh dấu cutover rồi ghi đầy đủ sự kiện tiếp theo.

## Thưởng, hộp thư và phát lại (đề xuất để duyệt)

- Thưởng top1/2/3 là XP/Vàng/Gem **cho mỗi thành viên đủ điều kiện**, không phải quỹ chia đều. Tổng mỗi tài sản không âm; mỗi hạng có quà; toptrước không thấp hơn topsau theo từng tài sản và có ít nhất một tài sản giảm giữa các hạng. Admin tự đặt mức.
- Trước phát hành, preview hiển thị thưởng từng hạng, tỷ lệ quy đổi, kỳ áp dụng và ngân sách tối đa50người/hạng(150người/3hạng), giúp nhận biết số tiền có thể phát.
- Settlement đóng băng thứ hạng/danh sách người nhận/reward và tạo entitlement bền vững có khóa duy nhất week+clan+user; worker chạy lại/restart/nhiềuinstance không phát hai lần. Có checkpoint/lỗi rõ, không bỏ qua âm thầm người nhận thất bại.
- Gửi vào hộp thư hiện có với ngữ cảnh đua tuần riêng: tuần, hạng, bang, lý do và thưởng. Claim/claim-all dùng cùng transaction ví/XP/ledger/dấu đã nhận. Source WALLET rõ CLAN_WEEKLY_RACE, không gắn nhãnADMIN_GIFT hoặcCOMPENSATION; quà đã chốt không bị thao tác admincampaign thông thường hủy.
- Thời hạn nhận đề xuất7ngày từ lúc quà thực sự sẵn sàng, để downtime trước phát không ăn mất hạn; hạn cụ thể đóng băng và hiển thị. Nhận rồi không thu hồi vì rời/kick sau chốt. User đã xóa không được tái tạo account để phát; trạng thái xử lý được audit, không mở lại quyền cấp khi restart.
- Kết quả tuần cũ và nguồn điểm vẫn xem/audit được; xóa bang/tài khoản không cascade mất lịch sử hạng/thưởng đã chốt. Không backfill hoặc phát quà vào người chơi thật trong nghiệm thu.

## Module, API và lưu trữ

- Module clan race riêng: cấu hình/lịch, eventledger/tenure, querybảnghạng, settlement/entitlement, mailboxbridge. Không mở rộng thêm một vùng lớn trongGameEngine hoặcGameProtocol; DTO riêngxếp hạng/cấu hình/thưởng.
- Admin API có list/detail/create/clone/editdraft/preview/publish/archivefuture, phân trang và no-store. Không giả succeeds khi database hỏng; dùng stable400/401/403/404/409/503 theo convention.
- Player API: tuần hiện tại/cấu hình thưởng/bảng xếp hạng phân trang+vị trí bang mình, đóng góp của bang mình, lịch sử kết quả; không cho client truyền người nhận hoặc điểm để ghi. Người xem không có bang vẫn xem top, không có quyền đọc chi tiết riêng của bang khác.
- Hook write dùng cùng JDBCtransaction với nguồn thật; áp dụng một thứ tự khóa chung cho claim/quyên góp/roster/settlement/admin trước ví. Kiểm thử cạnh tranh thực tế thay vì chỉ serviceđơn lẻ.
- Lịch sử roster/clan có thể đọc as-ofcutoff sau downtime; event/snapshot/entitlement giữ identity bền vững qua xóa dòng live. Migrations mới sau versioncao nhất tại head; không sửaV1–V66 hoặc seed dữ liệu lịch sử cũ thành điểm mới.
- Các gateway/hộp thư cũ giữ tương thích DTO mặc định; không nới quyền củaquàadmin/đền bù để accommodate đuatop. Khách đăng nhập/đăng ký theo luồng hiện có; không làm hệthốngauthmới.

## Giao diện

App giữ Arcade hiện có, admin Material sáng, áp dụngui-ux-pro-max và design-system hiện hữu.

- Bang có entry Đua top tuần. Headerghi tuần/thời gian còn lại/trạng thái. Quà tuần này trình bày3hạng và điều kiện nhận; nội dung lấy cấu hình đã đóng băng, không lấy bản nháp tuần sau.
- Bảnghiển thị hạng/icon/tên/cấp/điểm của các bang, đánh dấu bang mình và vị trí riêng nếu ngoài trangtop; không coi tải lỗi là0điểm. Chi tiết bang mình tách điểm nhiệm vụ/quyên góp và thành viên hiện tại, ghi rõ quyền nhận tuần này; lịch sử thành viên cũ giữ nguồn điểm nhưng không gán quyền cho tenuremới.
- Có loading/empty/inactive/error/retry/settling/settled, lastrefreshtime. Refresh/reconnect/đổi tài khoản/đổi bang có fencing để responsecũ không cập nhật UI hay quyền nhận. Không thêm bottomtabthứ6.
- Admin có nhóm Đua top bang, cấu hình nháp cho tuần tương lai, tỷ lệ với ví dụ và thưởng mỗi thành viên, preview, lịch sử và trạng thái chốt/phát quà. Nhãn và helper giải thích reward perperson/ratepercurrency, bảo toàn IDs retry/version và focus/error/a11y.
- TextKey mới đủ12ngôn ngữ; ảnh375px/desktop/landscape/largefont, focus/keyboard/reducedmotion và darkcontrast. Dùng icon hiện có, không tạo bộhình mới trong phần này.

## Nghiệm thu bắt buộc

1. Admin draft/preview/publish tuần tương lai; sai role/CSRF/version/overflow/rate/reward bị từ chối, cấu hình tuần đang chạy bất biến. Chưa cấu hình=>inactivekhông ghi điểm.
2. Quyên góp thật, missionclaimthật, replay/đồng thời=>mỗi nguồn một lần; tách/multipledonationkhông lệch do làmtròn. Điểmmissionchỉ cho actualcontributor/currenttenure, không cấp khi chỉclaimchung.
3. Rời/kicktrước/saucutoff, vào lại cùngbang vàđónggópmới/cũ, chuyểnbang, xóa/giảitán, workerchạymuộn/restart vẫn chốt đúngquyền/hạng/điểm.
4. Bằngđiểm deterministic, không nhận hạng ở0điểm, tốiđa3bang, điểm mọi thànhviêncộngđủ; DTOngoài top vẫn báo vịtrí ownclan.
5. Settlement/worker/claim/claim-all chạyđồngthời/retry=>mộtentitlement/mộtledgergrant, rollbackkhông mấtquà; víoverflow giữquyềnretry, khôngsuccessgiả.
6. Hộpthưhiểnthị đúngtuần/bang/hạng/reward/source/hạn; quàchốt không bịkicksaumốc tước. Tương thíchquàadmin/đềnbùcũ.
7. Appthật/adminthật→Ktor/PostgreSQLfixture với testclock riêng: cấuhình/chuyểntuần/đónggóp/chốt/nhậnquà/reload, assertsresponse+ví+ledger. KhôngđổiOSclock haythêmproductiontestendpoint.
8. Gaterelatedserver/protocol/shared/browser/localization/compile/coveragekhông nớingưỡng, ảnh đượcxem; emulatortiếptụcopt-in. Khôngdeploy/publish/provider thật.

## Các đề xuất bổ sung đã được duyệt cùng bản thiết kế

Chủ dự án đã chọn “Duyệt, triển khai theo thiết kế” cho các lựa chọn: thứHai00chốttuần; tie-breakđạtđiểmsớmhơn; thưởngmỗithànhviên; hạnnhận7ngày; chínhxáctỷlệ6chữsố; điểmclaimghi tuầnnhậnkểcảnhiệmvụcũ. Nhữngquyếtđịnhrõcủangườidùng ở mục trênđãchốt vàkhônghỏilại. Bản thiết kế đã được duyệt; tiếp tục viết kế hoạch và triển khai trong worktree riêng.

