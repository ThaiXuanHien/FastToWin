# Lộ trình hoàn thiện Fast To Win

Cập nhật: 12/09/2026. Đây là danh sách theo dõi thống nhất từ các hạng mục chủ dự án đã duyệt. Có code không đồng nghĩa đã kiểm thử hoặc sẵn sàng production.

## Quy tắc thực hiện

- Ưu tiên hoàn thiện Web → iOS → cửa hàng/giải đấu mở rộng → production và mở rộng hạ tầng.
- Kiểm thử đi cùng từng bước, không chờ tới cuối mới kiểm thử.
- Hoàn thành một bước thì báo kết quả, phần chưa xác minh và đề xuất bước tiếp theo để chủ dự án duyệt.
- Không tự kích hoạt thanh toán thật, gửi email hàng loạt, mua dịch vụ hoặc triển khai production.
- Google/Apple login và danh sách người vừa thi đấu cùng đã được chủ dự án bỏ; không tính là thiếu.

## Phạm vi yêu cầu cập nhật đã chốt

Đây là danh sách nguồn để tránh làm sót yêu cầu khi chia thành từng giai đoạn. Một
mục chỉ được đánh dấu hoàn tất khi code, dữ liệu/migration cần thiết và kiểm thử
tương ứng đều đã có. Các phần chi tiết ở dưới phải bám theo danh sách này.

### Kinh tế, cửa hàng và phần thưởng

- [x] Bỏ Mặt số/Bàn số khỏi danh mục bán; cửa hàng chỉ còn mua Gem và đổi Gem lấy Vàng. Dữ liệu vật phẩm cũ vẫn được giữ để tương thích tài khoản hiện hữu.
- [x] Giao dịch Vàng/Gem và nhận thưởng do server quyết định, nguyên tử và chống xử lý lặp.
- [x] Cân bằng lại phần thưởng trận đấu, điểm danh, nhiệm vụ và thành tích; chủ động rời sau khi trận bắt đầu không nhận thưởng.
- [x] Bổ sung nhiệm vụ ngày, nhiệm vụ tuần và tiến trình dài hạn.
- [x] Bổ sung thành tích, phần thưởng mở khóa, 16 Khung và 12 Danh hiệu.
- [ ] Thanh toán Gem production qua Google Play Billing/StoreKit và xác thực hóa đơn backend.

### Bang hội và bảng xếp hạng

- [x] Thành viên quyên góp Vàng hoặc Gem; tài sản bị trừ khỏi ví và giao dịch không thể hoàn tác.
- [x] XP bang dùng tỷ lệ `100 Vàng = 10 XP`, `1 Gem = 10 XP`; cấp bang được tính từ tổng XP quyên góp.
- [x] Xếp hạng bang theo cấp, **tổng Vàng đã quyên góp** và **tổng Gem đã quyên góp**, không dùng số dư ví hiện tại của thành viên.
- [x] Xếp hạng người chơi theo Elo, Vàng và Gem; bảng Vàng/Gem không dùng bộ lọc mùa giải.
- [x] Bổ sung nhiệm vụ và thành tích liên quan đến quyên góp bằng dữ liệu chính thức từ server.

### Hạn mức chơi và quảng cáo thưởng

- [x] Mỗi tài khoản có 10 lượt online mỗi ngày; đấu thường và đấu hạng dùng chung hạn mức.
- [x] Chỉ trừ lượt khi trận thật sự bắt đầu; chờ/rời phòng trước khi bắt đầu không mất lượt.
- [x] Chủ động rời sau khi bắt đầu bị xử thua, không hoàn lượt và không nhận thưởng.
- [x] Mỗi quảng cáo hợp lệ cộng 2 lượt; không giới hạn số lần xem và không nhận trùng cùng biên nhận.
- [x] Server quyết định ngày hạn mức và thời điểm reset theo `Asia/Bangkok`.
- [ ] Tích hợp SDK quảng cáo và xác minh biên nhận thật cho production; development hiện dùng adapter mô phỏng.

### Gameplay, kết nối và tương thích màn hình

- [x] Bỏ cơ chế phát lại.
- [x] Thay cơ chế kết nối lại bằng state machine, transport mới cho từng lần thử và nút thử lại thực sự mở kết nối mới.
- [ ] Xác minh thủ công kết nối lại trên hai thiết bị và các trường hợp chuyển mạng, đưa app nền/khôi phục app.
- [ ] Lỗi phòng từ liên kết đã hết hạn hoặc đủ người phải được xóa khi người chơi chuyển sang Trang chủ.
- [ ] Trên thiết bị màn hình nhỏ, ô số không bị bẹp và bàn số vẫn không cần cuộn.

### Profile, avatar và tiến trình

- [x] Chọn/tải avatar chỉ tạo preview; chỉ lưu và tải lên khi bấm `Lưu`, bấm `Hủy` phải giữ avatar hiện tại.
- [ ] Xác minh avatar đồng bộ ở Home, Profile, phòng, ván chơi và các màn còn lại trên Mobile/Web.
- [ ] Cấp 100 phải hiển thị XP phù hợp với cấp tối đa, không hiển thị sai `0/100 XP`.
- [x] Tài khoản development đầy đủ có tài sản và vật phẩm tiến trình cần thiết để kiểm thử.

### UI Web, xếp hạng và tài nguyên Khung

- [x] Nền ngoài vùng nội dung Web dùng màu tối thay vì màu trắng.
- [ ] Chuyển nút `Thử lại` cạnh header trên Web thành icon hành động trong header và không làm tràn bố cục.
- [ ] Tăng khoảng cách phía trên phần `Đấu sĩ • Mùa khởi đầu`.
- [ ] Đổi nhãn `Xem thưởng các bậc` thành `Xem thưởng` và giữ trên một dòng.
- [ ] Thiết kế lại Khung theo cấp/độ hiếm; mọi Khung mới phải có asset PNG đồng nhất với định dạng Khung cũ.
- [x] Popup/dialog và nội dung cửa hàng đã có ma trận responsive tự động; vẫn cần smoke test trên thiết bị Web thật.

### Đa ngôn ngữ

- [x] Catalog dùng chung hỗ trợ 12 ngôn ngữ và có kiểm thử khóa cho các luồng cốt lõi, kinh tế, bang hội và hạn mức chơi.
- [ ] Audit toàn bộ chuỗi còn hard-code/fallback ở mọi màn, dialog, lỗi backend và thông báo động.
- [ ] Nhờ người bản ngữ kiểm duyệt 10 ngôn ngữ ngoài tiếng Việt/Anh trước production.

### Production và quy mô tải

- [ ] Viết tài liệu chi phí và kiến trúc triển khai riêng cho các mốc 10.000, 50.000 và 100.000 người dùng/kết nối đồng thời; nêu rõ giả định tải thay vì đồng nhất người dùng với request/giây.
- [ ] Chốt danh sách dịch vụ cần thuê: compute/container, PostgreSQL, Redis, load balancer/WebSocket gateway, object storage/CDN, email, monitoring/log/cảnh báo, backup, Firebase/APNs, quảng cáo và billing store.
- [ ] Load test trong môi trường riêng để hiệu chỉnh con số chi phí trước khi ký hợp đồng dịch vụ.

### Danh mục tên đã duyệt

Khung: `Tia Chớp`, `Liệt Hỏa`, `Chiến Binh`, `Bách Chiến`, `Kim Cương`,
`Thách Đấu`, `Vinh Quang`, `Bất Khuất`, `Huyền Thoại`, `Đế Vương`,
`Tốc Ảnh`, `Quán Quân`, `Bất Diệt`, `Long Uy`, `Chí Tôn`, `Vô Song`.

Danh hiệu: `Khai Chiến`, `Thần Tốc`, `Bất Bại`, `Bách Chiến`, `Mắt Thần`,
`Trụ Cột`, `Nhất Kích`, `Phản Xạ Vàng`, `Cao Thủ`, `Kẻ Chinh Phục`,
`Chiến Thần`, `Vua Tốc Độ`.

## 1. Hoàn thiện Web — ưu tiên hiện tại

Các mục sau trong danh sách cũ **đã có triển khai**; tiếp tục hồi quy chứ không làm lại:

| Hạng mục | Trạng thái hiện tại |
| --- | --- |
| Âm thanh đúng/sai | Web Audio; phát sau tương tác đầu tiên |
| Chia sẻ ảnh kết quả | Canvas PNG, Web Share hoặc tải ảnh xuống |
| Back/Forward trình duyệt | History API và đồng bộ màn trong app |
| URL phòng/thử thách | `/room/{id}`, `/challenge/{code}` |
| Trình duyệt không hỗ trợ WasmGC | Có target JS và smoke test bundle compatibility trong CI |
| Avatar và responsive | Có chọn/nén/tải ảnh Web, chiều rộng nội dung và dialog theo viewport; đã có ma trận responsive tự động |
| Pull-to-refresh | Gesture chỉ dành cho thiết bị cảm ứng |
| PWA, offline, update và Web Push | Đã có; Web Push đã được người dùng xác nhận nhận đủ 4 nhóm thông báo |

Phần đang làm và còn lại:

- [x] Hoàn thiện và xác minh bộ Web E2E trong `e2e/`: đăng nhập/F5, Back/Forward, phòng hai người, trận đủ 50 số, mất kết nối, đấu lại, về sảnh độc lập. CI #67 đã đạt.
- [x] Mở rộng kiểm thử responsive tự động: điện thoại nhỏ/lớn, tablet, ngang, chữ lớn, nội dung dài và viewport thấp mô phỏng bàn phím ảo.
- [x] Smoke test tự động Chromium/Firefox/WebKit và bundle Kotlin/JS fallback trong CI.
- [ ] Smoke test Chrome/Edge/Safari và bàn phím ảo thật trên thiết bị. Playwright WebKit không thay thế Safari trên thiết bị Apple.
- [ ] Mua Gem trên Web: cần thống nhất cổng thanh toán, phí và xác thực giao dịch backend trước khi làm.

Chi tiết: [audit Web](web-wasm-audit.md), [hướng dẫn Web E2E](web-e2e.md).

## 2. Hoàn thiện iOS

- [x] Chọn, nén và tải ảnh đại diện bằng picker iOS; ảnh được thu về tối đa 512 px và nén trước khi gửi qua WebSocket.
- [ ] Hoàn thiện StoreKit 2 production và xác thực giao dịch App Store; hiện có phần chuẩn bị/sandbox, chưa coi là thanh toán thật.
- [x] Đã tích hợp đăng ký thiết bị, quyền thông báo, APNs/FCM và mở đúng màn từ thông báo.
- [ ] Cấu hình APNs Authentication Key và smoke test trên thiết bị thật: tạm hoãn đến khi có tài khoản Apple Developer Program.
- [ ] Build/chạy trên macOS/Xcode, iPhone và iPad simulator.
- [ ] Hai người chơi thật, safe area, bàn phím, xoay màn hình, nền/khôi phục ứng dụng trên thiết bị iOS thật.

Mua hàng và APNs cần cấu hình Apple tương ứng; kiểm thử thiết bị cần máy Mac/thiết bị. Không tự đăng ký dịch vụ trả phí.

## 3. Cửa hàng

Danh mục bán hiện tại chỉ có hai tab **Gem** và **Vàng**. Vàng được đổi từ Gem
theo ba gói do server quản lý; request đổi có mã chống xử lý lặp và giao dịch cập
nhật ví nguyên tử. Mặt số/Bàn số cũ không còn được bán nhưng dữ liệu sở hữu, trang
bị và lịch sử mua của tài khoản hiện hữu vẫn được giữ để tương thích.

- [x] Cửa hàng hai tab Gem/Vàng, ba gói đổi 10/45/80 Gem thành 1.000/5.000/10.000 Vàng.
- [x] Kiểm thử đổi trùng, thiếu Gem, cập nhật hồ sơ và lịch sử giao dịch.
- [ ] Thanh toán Gem production vẫn phụ thuộc Google Play Billing/StoreKit và xác thực hóa đơn backend.

## 4. Giải đấu mở rộng

Đã có giải riêng 4, 8 hoặc 16 người, nhánh đấu loại trực tiếp, nhà vô địch và lịch sử.

- [x] Giải riêng 8 người với tứ kết, bán kết và chung kết.
- [x] Giải riêng 16 người với vòng 1/8, tứ kết, bán kết và chung kết.
- [ ] Giải công khai/toàn hệ thống.
- [ ] Lịch thi đấu, thời hạn đăng ký và xử lý vắng mặt.
- [ ] Khán giả xem trận đấu giải (khác với xem phòng thông thường).
- [x] Hồi quy tạo nhánh đấu 4/8/16 người, reconnect/restart, xử thua khi rời hoặc quá hạn và phần thưởng không trùng.

## 5. Tài khoản và cấu hình production

- [x] Dịch vụ SMTP production cho quên mật khẩu; token chỉ hiển thị ở dev.
- [x] Xác minh địa chỉ email bằng mã 6 số và giới hạn gửi lại/thử mã.
- [x] Mẫu triển khai domain, HTTPS/WSS, CORS allowlist và Docker secrets.
- [x] Bảo vệ phiên Web: refresh token dùng cookie host-only `HttpOnly`, `Secure`, `SameSite=Strict`; access token chỉ nằm trong bộ nhớ và request cookie được kiểm tra `Origin`/CSRF.
- [x] Chốt quy trình phát hành, rollback, bảo trì và kiểm tra sức khỏe dịch vụ; image được gắn release tag, backup có SHA-256 và rollback yêu cầu xác nhận tương thích schema.

## 6. Hạ tầng khi mở rộng

Backend hiện dùng trạng thái realtime trong một instance. Không coi các mục sau là điều kiện phải làm ngay khi chưa có nhu cầu tải:

- [ ] Redis cho presence/trạng thái liên instance và rate limiting dùng chung.
- [ ] Load balancer/sticky session hoặc WebSocket gateway; xác định rõ instance sở hữu trận và cách khôi phục.
- [x] Monitoring/log cho một instance: metrics nội bộ, Prometheus, Alertmanager, Grafana, Loki/Alloy, dashboard dựng sẵn và giới hạn dung lượng Docker log.
- [x] Backup PostgreSQL tự động, thời gian lưu cấu hình được, xác minh checksum/dump và diễn tập khôi phục cách ly bằng PostgreSQL tạm.
- [ ] CDN/object storage cho avatar và tài nguyên.
- [ ] Load test có giới hạn trên môi trường riêng trước khi mở rộng.

## 7. Kiểm thử và phát hành xuyên suốt

Đã có unit/integration test server/shared, Android Compose UI test và GitHub Actions. Chưa đánh đồng build xanh với toàn bộ trải nghiệm đa nền tảng đạt.

- [x] Catalog giao diện dùng chung cho 12 ngôn ngữ; các màn cốt lõi, hồ sơ, xã hội, xếp hạng, giải đấu, thông báo và cửa hàng đã dùng khóa localization ổn định. Tiếng Việt/Anh đã phủ đầy đủ; 10 ngôn ngữ còn lại đã dịch các nội dung chính và dùng English fallback cho nội dung chưa qua kiểm duyệt ngôn ngữ ở bước audit cuối.
- [x] Chuẩn hóa thông báo động từ backend thành `messageKey/messageArgs`; trường protocol vẫn optional/default và raw fallback cũ được cô lập qua `legacyFallback(...)` để tương thích client cũ.
- [ ] Screenshot regression test với ảnh chuẩn đã review (ảnh chụp khi test lỗi chưa phải screenshot regression).
- [ ] Báo cáo độ bao phủ test và ngưỡng phù hợp.
- [x] Ma trận Playwright Chromium/Firefox/WebKit và JS fallback trong CI.
- [ ] Kiểm thử hai người trên iOS thật.
- [x] Cập nhật API, backend setup và hướng dẫn contributor cho hành vi localization, locale thông báo và quy trình kiểm tra tự động.

## Các giai đoạn kinh tế đã hoàn tất

**Giai đoạn 2 — kinh tế và tiến trình cá nhân đã hoàn tất phần triển khai**:

- [x] Server quyết định phần thưởng đấu thường/đấu hạng; chủ động rời sau khi trận bắt đầu không nhận thưởng.
- [x] Bốn nhiệm vụ ngày và bốn nhiệm vụ tuần được chọn ổn định theo chu kỳ, tiến trình và nhận thưởng chống xử lý lặp.
- [x] Có 20 thành tích, 16 Khung và 12 Danh hiệu; phần thưởng mở khóa được ghi cùng giao dịch và giữ tương thích vật phẩm cũ.
- [x] Tên, mô tả, điều kiện mở khóa và thông báo kinh tế dùng đủ 12 catalog ngôn ngữ.
- [x] Tài khoản dev đầy đủ có đủ Gem để thử mọi gói Vàng và sở hữu phần thưởng thành tích ngoài bang, không tự nhận Mặt số/Bàn số cũ.
- [x] Cửa hàng và Bộ sưu tập có kiểm tra responsive trên điện thoại nhỏ, điện thoại lớn và tablet.

**Giai đoạn 3 — kinh tế và tiến trình bang đã hoàn tất phần triển khai**:

- [x] Thành viên có thể quyên góp Vàng hoặc Gem bằng giao dịch nguyên tử, chống xử lý lặp và có kiểm tra số dư phía server.
- [x] XP/cấp bang được tính từ tổng tài sản đã quyên góp; `100 Vàng = 10 XP`, `1 Gem = 10 XP`.
- [x] Hồ sơ bang hiển thị cấp, tiến trình XP, tổng Vàng/Gem quyên góp và đóng góp của từng thành viên.
- [x] Bảng xếp hạng người chơi hỗ trợ Elo, Vàng và Gem; bảng xếp hạng bang hỗ trợ cấp, tổng Vàng và tổng Gem quyên góp.
- [x] Nhiệm vụ và thành tích liên quan đến quyên góp dùng dữ liệu giao dịch chính thức, không dùng tiến trình giả từ client.
- [x] Migration PostgreSQL, kiểm thử repository, engine và Compose UI cho quyên góp/xếp hạng đã được bổ sung.

**Giai đoạn 4 — giới hạn lượt online và thưởng quảng cáo đã hoàn tất phần triển khai**:

- [x] Đấu thường và đấu hạng dùng chung 10 lượt mỗi ngày; luyện tập và đấu giải không tiêu thụ lượt.
- [x] Chỉ trừ lượt khi trận thực sự chuyển sang `PLAYING`; tạo/chờ/rời phòng trước khi bắt đầu không mất lượt.
- [x] Mỗi biên nhận quảng cáo hợp lệ cộng 2 lượt, không giới hạn số lần xem; mã giao dịch và mã trận chống xử lý lặp.
- [x] Ngày hạn mức và thời điểm reset do server tính theo `Asia/Bangkok`.
- [x] UI hiển thị lượt còn lại, dialog hết lượt và trạng thái quảng cáo thích ứng trên điện thoại nhỏ, lớn, ngang và tablet.
- [x] Nội dung quota/quảng cáo có bản dịch rõ ràng trong đủ 12 ngôn ngữ.
- [x] Regression tổng sau merge đạt 108 tác vụ Gradle; 361 unit/integration test được ghi nhận không có lỗi.

Adapter quảng cáo development hiện dùng biên nhận mô phỏng. Production vẫn từ chối
phát lượt cho đến khi tích hợp SDK quảng cáo mobile và xác minh biên nhận thật phía
server; Web production không tự giả lập quảng cáo.

## Bước tiếp theo

**Giai đoạn 5 — hoàn thiện các yêu cầu bổ sung và audit giao diện**:

- [x] Avatar preview trước khi lưu; Hủy không thay đổi dữ liệu đã lưu.
- [ ] Dọn lỗi link phòng khi quay về Trang chủ.
- [ ] Sửa tỷ lệ ô số trên màn hình nhỏ và XP cấp 100.
- [ ] Đưa hành động thử lại Web lên header, rút gọn `Xem thưởng` và chỉnh khoảng cách mùa giải.
- [ ] Hoàn thiện asset PNG theo cấp/độ hiếm cho đủ 16 Khung.
- [ ] Audit chuỗi localization còn sót và xác minh avatar giữa mọi màn.
- [ ] Viết tài liệu kiến trúc/dịch vụ/chi phí production cho ba mốc tải đã yêu cầu.

**Giai đoạn 6 — screenshot regression cho giao diện 2D Arcade**:

- [ ] Chốt bộ ảnh chuẩn đã được chủ dự án review cho các màn cốt lõi.
- [ ] Chụp và so sánh tự động trên điện thoại nhỏ, điện thoại lớn và tablet.
- [ ] Bao phủ ít nhất Home, phòng chơi, ván chơi, kết quả, Profile, Cửa hàng, Xếp hạng, Bang hội, Đấu giải và Thông báo.
- [ ] Lưu ảnh chênh lệch làm artifact CI để có thể xem trực tiếp khi giao diện thay đổi ngoài ý muốn.
- [ ] Tách cập nhật ảnh chuẩn thành thao tác có chủ ý, không tự ghi đè khi test thất bại.

StoreKit/APNs production tiếp tục tạm hoãn vì phụ thuộc tài khoản Apple. Các bản
dịch fallback tiếng Anh còn lại vẫn cần người bản ngữ kiểm duyệt.
