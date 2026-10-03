# Dashboard thống kê người dùng và chi tiêu

Ngày: 03/10/2026, Asia/Bangkok. Mở rộng riêng của Issue #21 và #17.
Issue: https://github.com/ThaiXuanHien/FastToWin/issues/24.
GitHub Issue là đặc tả chính; tài liệu này là bản đối chiếu trong repository.
Chủ dự án chọn thống kê cả tiền thật và Vàng/Gem, đồng ý hướng thiết kế trong
chat. Chi tiết dưới đây cần duyệt trước khi lập kế hoạch/triển khai.

## Kết quả và phạm vi bản đầu

Dashboard tiếng Việt cho Owner/Operator: đăng ký mới, giao dịch mua thành công,
chi tiêu Vàng/Gem và vật phẩm bán nhiều nhất theo ngày/tuần/tháng. Owner xem
tiền thật/hoàn tiền; Operator chỉ xem đăng ký và kinh tế game. Support không
đọc dashboard tổng hợp. Server kiểm tra quyền cho từng metric, không chỉ ẩn UI.

Giữ admin trắng Material tối giản theo design-system/fast-to-win/pages/admin.md
và ui-ux-pro-max. Dùng vài thẻ tổng quan, biểu đồ theo thời gian và thanh ngang
top vật phẩm; không đưa mọi chỉ số vào một biểu đồ hoặc lặp nhiều hộp trang trí.

## Định nghĩa số liệu

| Nhóm | Định nghĩa và đơn vị |
| --- | --- |
| Đăng ký mới | Một lần chuyển thành REGISTERED thành công, gồm đăng ký trực tiếp và nâng cấp guest; không đếm đăng nhập, phiên hoặc guest mới |
| Tiền thanh toán | Tổng số tiền khách đã thanh toán của giao dịch được nguồn store xác minh; nhóm riêng theo currency/store |
| Hoàn tiền | Số tiền hoàn được nguồn xác minh xác nhận, ghi theo thời điểm hoàn; hỗ trợ hoàn từng phần, không vượt khoản gốc |
| Sau hoàn tiền | Tiền thanh toán trong kỳ trừ hoàn tiền trong kỳ, chưa trừ phí store hoặc điều chỉnh thuế; có thể âm, không gọi là tiền thực nhận |
| Giao dịch mua | Đếm transaction mua đã xác minh một lần; retry/khôi phục không tăng số lần |
| Người mua | Số tài khoản phân biệt có giao dịch hợp lệ trong phạm vi và khoảng thời gian; không cộng distinct các ngày thành distinct tháng |
| Vàng/Gem chi mua vật phẩm | Debit COSMETIC_PURCHASE đã commit, cộng giá trị chi dương và tách GOLD/GEMS; loại đổi tiền, quà, thưởng, nạp store, mua thất bại |
| Top vật phẩm | Đếm lần mua thành công và tổng GOLD/GEMS đã chi theo mã vật phẩm ổn định; quyền sở hữu do quà/mặc định không tính là mua |

Không cộng Vàng với Gem hoặc các currency tiền thật. Không lấy giá niêm yết
hiện tại để suy ra số tiền cũ, không lấy số Gem được cấp làm doanh thu. Mua Gem
bằng tiền thật và dùng Gem mua vật phẩm là hai sự kiện khác nhau; mỗi chart
ghi rõ ý nghĩa, không cộng hai giá trị vào một tổng doanh thu.

## Nguồn có thẩm quyền và dữ liệu lịch sử

- Hiện `store_purchases` lưu store/product/transaction/gems/verified_at nhưng
  chưa có amount/currency/refund. Bổ sung nguồn giao dịch tiền thật được xác minh
  từ store/báo cáo giao dịch phù hợp; browser không gửi số tiền làm thẩm quyền.
- Hợp đồng nguồn gồm store, môi trường LIVE/SANDBOX, mã transaction ổn định,
  mã product, thời điểm mua, amount theo đơn vị tiền nhỏ nhất, currency và
  sự kiện refund có ID/thời điểm/số tiền riêng. Tiền dùng số nguyên/decimal
  chính xác; HTTP truyền tổng lớn bằng chuỗi decimal, tránh sai số JS Number.
- Adapter phải xác minh nguồn, chống replay/duplicate/import trùng và phân
  biệt LIVE/SANDBOX. Chỉ LIVE vào tổng mặc định. Không ghi token/receipt thô
  vào dữ liệu dashboard, browser hoặc audit. Nguồn một store chưa sẵn sàng
  phải hiển thị chưa có dữ liệu store đó; không biến unavailable thành 0.
- Chi tiết adapter/API store phải được kiểm tra với tài liệu chính thức khi
  lập kế hoạch. Không cần chọn cổng thanh toán web #10 hoặc hoàn thiện StoreKit
  #14 để có metric Vàng/Gem/đăng ký; không giả lập doanh thu các nền tảng đó.
- `users.created_at` là ngày tạo identity; nâng cấp guest hiện giữ ngày này.
  Cần lưu sự kiện đăng ký thành công theo thời điểm server trong cùng transaction
  đăng ký/nâng cấp, unique một lần cho mỗi identity. Không suy diễn ngày nâng cấp
  từ updated_at. Không đếm lại identity khi đăng nhập hoặc retry.
- Facts mua vật phẩm ghi item/currency/amount/time từ transaction thật; wallet
  là nguồn đối chiếu debit. Với lịch sử thiếu item, được tổng hợp chi tiêu từ
  debit đáng tin cậy nhưng không tự gán vật phẩm hoặc dựng top giả.
- Facts lịch sử không cascade mất theo việc xóa người dùng; không cần email/tên
  trong tổng hợp. Khóa phân biệt ẩn danh phục vụ distinct cần tuân thủ quy trình
  xóa dữ liệu hiện có; dashboard không cung cấp tra ngược identity đã xóa.
- Không tự backfill ngày đăng ký hoặc giá tiền không chứng minh được. Mỗi metric
  có coverageStart, freshness, nguồn và trạng thái COMPLETE/PARTIAL/UNAVAILABLE.
  Với dữ liệu cũ chỉ biết tổng REGISTERED hiện tại, có thể hiện tổng hiện tại
  riêng, không trình bày thành lịch sử đăng ký chính xác.
- Số 0 chỉ hợp lệ khi nguồn được xác nhận đủ và không có sự kiện. PARTIAL phải
  có nhãn phạm vi/thiếu dữ liệu; UNAVAILABLE hiện “Chưa có dữ liệu”.

## Thời gian, lọc và so sánh

- Lưu UTC, hiển thị/phân nhóm Bangkok UTC+7; ngày theo 00:00 địa phương, tuần
  bắt đầu thứ Hai, tháng theo lịch. Các range dùng [từ, đến), không trùng biên.
- Mặc định 30 ngày; preset 7/30/90 ngày và tùy chọn ngày, tối đa 24 tháng/query.
  Chọn bucket DAY/WEEK/MONTH và filter store/currency/product khi phù hợp.
  Query tổng hợp server, không tải toàn bộ người dùng/giao dịch vào browser.
- Tiền nhóm theo thời điểm mua/refund của nguồn, không theo giờ import; đăng ký
  và chi tiêu game theo thời điểm commit server. Import đến muộn có thể chỉnh
  lại bucket lịch sử; hiển thị thời điểm cập nhật.
- So sánh cùng độ dài kỳ ngay trước; ghi rõ ngày thực tế hai kỳ. Không so sánh
  phần kỳ chưa trôi qua với trọn kỳ trước. Nếu thiếu coverage, không hiện % giả;
  kỳ trước 0 thì hiện thay đổi tuyệt đối, không chia cho 0.
- Đối soát theo currency/source, giữ độ chính xác tiền và tiền game. Refund
  ở kỳ khác ghi kỳ phát sinh refund và liên kết transaction gốc để chống trùng.

## API, truy cập và giao diện

- API admin tổng hợp read-only trên Ktor/PostgreSQL hiện có; response có đơn vị,
  khoảng ngày, timezone, bucket, coverage và freshness. Summary và series cùng
  snapshot/filter; distinct theo toàn khoảng được tính riêng tại server.
- Dùng phiên admin, current-role và chính sách no-store/worker hiện có. Không
  lưu số liệu riêng tư vào localStorage/CacheStorage. Thu hồi quyền/hết phiên
  xóa biểu đồ và bảng; response đến muộn không khôi phục dữ liệu tài khoản cũ.
- Chỉ Owner có metric tiền thật/refund, kể cả request metric thủ công. Audit
  truy cập tổng hợp chứa actor/range/metric an toàn, không chứa receipt/token.
- Chart đường/cột cho xu hướng; thanh ngang cho top, tối đa 10 vật phẩm.
  Có legend, đơn vị, tooltip và bảng số liệu xem bằng bàn phím/screen reader;
  không phân biệt series chỉ bằng màu hoặc bắt buộc hover trên điện thoại.
- Loading/empty/error/partial rõ ràng; khi tải lại lỗi không trình bày snapshot
  cũ như dữ liệu vừa cập nhật. Nút refresh dùng icon có nhãn truy cập; filter
  có nhãn, touch target 48px; chart/table co giãn ở phone và desktop.
- Dùng asset chart cùng origin và tương thích CSP hiện tại; lựa chọn thư viện
  ở bước lập kế hoạch, không mở CDN/inline script chỉ để vẽ chart.

## Giai đoạn và ngoài phạm vi

1. Đăng ký/facts chính xác, Vàng/Gem, top vật phẩm và khung dashboard.
2. Tiền thật/refund cho nguồn store đã xác minh đủ amount/currency/coverage;
   có nguồn nào hiển thị nguồn đó, hoàn thiện đủ nguồn trước nghiệm thu metric.
3. Mở rộng riêng: DAU/WAU/MAU, retention D1/D7/D30, trận hoàn thành/bỏ trận,
   lỗi/mất kết nối, quá tải, hiệu quả sự kiện và tỷ lệ nhận quà.

Nhóm 3 cần hợp đồng nguồn hoạt động/sự cố riêng; không gọi last_seen rời rạc
là dữ liệu hoạt động chính thức. Quà có thể lấy báo cáo #19 nhưng không cộng
counter có phần giao nhau thành một tổng. Không thêm thu tiền/hoàn tiền bằng
admin, quy đổi tỷ giá, tính phí/thuế, xuất toàn bộ users, tự bật bảo trì hay
biểu đồ dự báo trong issue này.

## Nghiệm thu

- [ ] REGISTERED trực tiếp/guest upgrade/retry có ngày đúng và không đếm trùng.
- [ ] Biên UTC+7, tuần thứ Hai/tháng, [từ, đến), kỳ chưa kết thúc được kiểm thử.
- [ ] Bỏ identity theo quy trình xóa không tự làm thay đổi tổng lịch sử đã công bố.
- [ ] Mua vật phẩm trừ đúng GOLD/GEMS; quà/đổi/thất bại/ownership mặc định bị loại.
- [ ] Top item và distinct buyers chính xác, không cộng distinct các bucket.
- [ ] Nguồn mua/refund được xác minh; duplicate/replay/import trùng không tăng tổng.
- [ ] Sandbox bị loại, nhiều currency tách riêng, refund kỳ khác và một phần đúng.
- [ ] Tổng lớn trên 2^53 vẫn chính xác từ DB/JSON đến hiển thị; không float tiền.
- [ ] Dữ liệu thiếu không thành 0; lịch sử không chứng minh được có nhãn coverage.
- [ ] Summary/series/snapshot/filter khớp; late import có freshness và đúng kỳ.
- [ ] Phân quyền Owner/Operator/Support, no-store, expiry/role-change/late-response đúng.
- [ ] Phone/desktop và ảnh chụp, keyboard/chart-table, tooltip cảm ứng, empty/error được kiểm tra.
