# Quản trị FastToWin

Console tiếng Việt tại `/admin/` dùng tài khoản quản trị riêng, tách khỏi đăng nhập người chơi. Mặc định cả console và `/admin/api` đều bị tắt. Không có mật khẩu mặc định hay endpoint công khai để tạo Owner.

## Bật và cấp Owner

1. Chạy backend với PostgreSQL được cấu hình qua `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` như hướng dẫn server. Dùng cơ sở dữ liệu development riêng khi thử nghiệm.
2. Đặt `FASTTOWIN_ADMIN_ENABLED=true` và `FASTTOWIN_ADMIN_ORIGIN` bằng origin chính xác, không có đường dẫn, dấu `/` cuối, query hoặc fragment. Ví dụ development: `http://127.0.0.1:8080`; production: `https://admin.example.com`. `FASTTOWIN_ENV=prod` yêu cầu HTTPS và PostgreSQL; development chỉ cho HTTP loopback. Bỏ switch hoặc đặt `false` để tắt toàn bộ route quản trị.
3. Chạy công cụ offline `./gradlew :server:bootstrapAdminOwner` (Windows: `./gradlew.bat :server:bootstrapAdminOwner`) trong cùng môi trường database và admin. Cung cấp email qua `FASTTOWIN_ADMIN_BOOTSTRAP_EMAIL` hoặc prompt console. Mật khẩu được đọc bằng console ẩn ký tự, tối thiểu 12 và tối đa 128 ký tự. Nếu môi trường không có console, dùng `FASTTOWIN_ADMIN_BOOTSTRAP_PASSWORD_FILE` trỏ tới file bí mật chứa đúng mật khẩu, không thêm newline; hạn chế quyền đọc file và xóa khi không còn cần. Không đưa mật khẩu vào lệnh, lịch sử terminal, repository hoặc log.
4. Bootstrap cùng email của Owner đang hoạt động là idempotent, giữ nguyên mật khẩu hiện có. Công cụ từ chối thay thế tài khoản quản trị không phải Owner và không sửa tài khoản người chơi có cùng email. Production provisioning phải thực hiện trong quy trình vận hành được phê duyệt; tài liệu này không tự bật hay cấp bất kỳ tài khoản production nào.
5. Truy cập đúng origin đã cấu hình và `/admin/`. Có thể đặt reverse proxy cùng origin chuyển toàn bộ `/admin/` (gồm assets và API) tới Ktor. Giữ nguyên `Origin`, `Set-Cookie` và các security header; TLS termination phải cung cấp HTTPS cho trình duyệt. Không cache trang hoặc API; không đưa API quản trị tới origin khác. Danh sách CORS của game không cấp quyền đọc quản trị.

## Xác thực hai lớp (TOTP)

Production bắt buộc mọi vai trò (Owner, Operator, Support) dùng ứng dụng xác thực theo thời gian (Google Authenticator, Microsoft Authenticator…).

1. **Trước khi deploy bản có MFA**, tạo khóa 32 byte và lưu làm secret của service backend. Nếu bật admin trên production mà thiếu khóa, server sẽ dừng ngay khi khởi động:

   ```bash
   openssl rand -base64 32
   ```

   Đặt giá trị vào `FASTTOWIN_ADMIN_MFA_KEY`, hoặc vào một file bí mật rồi trỏ `FASTTOWIN_ADMIN_MFA_KEY_FILE` tới file đó. Khóa này mã hóa secret TOTP trong database (AES-256-GCM). Nếu mất khóa, mọi admin phải thiết lập lại MFA bằng công cụ khôi phục bên dưới. Không đổi khóa khi chưa có kế hoạch cho việc đó.
2. Ở lần đăng nhập đầu tiên sau khi deploy, console hiển thị khóa thiết lập. Admin thêm khóa vào ứng dụng xác thực, nhập mã 6 số, rồi nhận **10 mã khôi phục dùng một lần**. Các mã này chỉ hiển thị đúng một lần và cần được lưu ở trình quản lý mật khẩu.
3. Mỗi lần đăng nhập sau đó cần mật khẩu và một mã mới. Một mã đã dùng không dùng lại được. Sai 5 lần hoặc quá 5 phút thì phải nhập lại mật khẩu.
4. Khi một admin mất thiết bị: Owner khác vào **Tài khoản quản trị**, bấm biểu tượng khóa ở tài khoản đó và nhập lý do. Thao tác này xóa MFA của tài khoản và đăng xuất mọi phiên của họ. Owner không thể tự đặt lại MFA cho chính mình qua console.
5. Development không bắt buộc MFA. Muốn thử, đặt `FASTTOWIN_ADMIN_MFA_REQUIRED=true`. Không có khóa thì development dùng một khóa cố định công khai, nên khóa này chỉ an toàn cho môi trường local.

## Cảnh báo hoạt động admin (ADM-11)

Đặt `FASTTOWIN_ADMIN_ALERT_EMAILS` là danh sách email nhận cảnh báo, phân cách bằng dấu phẩy. Tính năng này cần `FASTTOWIN_EMAIL_PROVIDER=brevo`; nếu thiếu, server ghi cảnh báo vào log và không gửi thư. Hệ thống gửi email khi:

- Có admin đăng nhập thành công (kèm cách xác thực và IP).
- Một tài khoản đăng nhập sai mật khẩu hoặc mã 2 lớp 5 lần trong 15 phút.
- Owner cấp tài khoản quản trị mới, đổi quyền hoặc đặt lại 2 lớp.
- Có yêu cầu duyệt, hoặc vừa duyệt, một lần công bố quà, mã quà hoặc bồi thường vượt trần.

Thư không chứa mật khẩu, token hay mã xác thực. Cảnh báo được gửi sau khi thao tác đã lưu xong. Nếu gửi lỗi, thao tác admin không bị ảnh hưởng; lỗi chỉ được ghi vào log. Thư cảnh báo không trừ vào trần email hằng ngày của người chơi, nhưng vẫn tính vào quota của Brevo.

## Trần giá trị quà và duyệt bởi Owner thứ hai (ADM-12)

Khi công bố **quà admin**, **đợt bồi thường** hoặc **mã quà** vượt trần, Owner thứ nhất bấm Công bố thì hệ thống ghi lại yêu cầu và báo `Cần một Owner khác`. Một **Owner khác** phải mở đúng phiên bản đó và bấm Công bố thì nội dung mới được phát hành. Với mã quà, Owner thứ hai phải nhập lại đúng mã, phần thưởng, hạn dùng và giới hạn lượt nhận. Nếu bản nháp bị sửa, yêu cầu duyệt cũ hết hiệu lực.

| Trần | Mặc định | Biến môi trường |
| --- | --- | --- |
| Vàng mỗi người nhận / mỗi lượt mã | 50.000 | `FASTTOWIN_GIFT_CAP_RECIPIENT_GOLD` |
| Gem mỗi người nhận / mỗi lượt mã | 100 | `FASTTOWIN_GIFT_CAP_RECIPIENT_GEMS` |
| XP mỗi người nhận / mỗi lượt mã | 5.000 | `FASTTOWIN_GIFT_CAP_RECIPIENT_XP` |
| Lượt chơi mỗi người nhận / mỗi lượt mã | 20 | `FASTTOWIN_GIFT_CAP_RECIPIENT_PLAYS` |
| Tổng Vàng của một lần công bố | 50.000.000 | `FASTTOWIN_GIFT_CAP_TOTAL_GOLD` |
| Tổng Gem của một lần công bố | 50.000 | `FASTTOWIN_GIFT_CAP_TOTAL_GEMS` |

Tổng XP và tổng lượt chơi mặc định không giới hạn; có thể đặt bằng `FASTTOWIN_GIFT_CAP_TOTAL_XP` và `FASTTOWIN_GIFT_CAP_TOTAL_PLAYS`. Mã quà **không giới hạn lượt nhận** mà có Vàng hoặc Gem luôn cần Owner thứ hai duyệt, vì không tính được tổng. Hệ thống cần ít nhất 2 Owner đang hoạt động để công bố nội dung vượt trần.

## Khôi phục khẩn cấp (ADM-10)

Luôn duy trì **ít nhất 2 Owner đang hoạt động**, có MFA và mã khôi phục được lưu riêng. Khi Owner duy nhất còn lại mất mật khẩu hoặc mất cả thiết bị lẫn mã khôi phục, hoặc khi nghi tài khoản bị chiếm:

1. Người có quyền vận hành database chạy công cụ offline trong đúng môi trường production (cùng biến database, admin và khóa MFA):

   ```bash
   ./gradlew :server:recoverAdminAccount
   ```

   Công cụ hỏi email, lý do và mật khẩu mới qua console ẩn ký tự. Nếu không có console, dùng `FASTTOWIN_ADMIN_RECOVERY_EMAIL`, `FASTTOWIN_ADMIN_RECOVERY_REASON` và `FASTTOWIN_ADMIN_RECOVERY_PASSWORD_FILE`.
2. Công cụ đặt mật khẩu mới, xóa MFA, thu hồi mọi phiên của tài khoản đó và ghi `ADMIN_OFFLINE_RECOVERY` vào nhật ký kiểm toán. Công cụ không tạo tài khoản và không đổi vai trò.
3. Đăng nhập lại để thiết lập MFA mới. Kiểm tra **Nhật ký kiểm toán** để tìm thao tác lạ trong thời gian nghi bị chiếm quyền. Thu hồi hoặc đặt lại các tài khoản liên quan.
4. Nếu nghi bị chiếm quyền, thu hồi phiên của các Owner khác bằng **Tài khoản quản trị** và đổi khóa `FASTTOWIN_ADMIN_MFA_KEY` theo kế hoạch: mọi admin cùng chạy công cụ khôi phục rồi thiết lập lại MFA.

## Quyền và bảo mật phiên

### Cùng origin với game và cập nhật trình duyệt

Khi game và console dùng chung origin, phải phát hành `service-worker.js` của game cùng bản console này. Worker bỏ qua toàn bộ `/admin` và `/admin/…` (trang, assets, API), đồng thời xóa các mục quản trị đã lưu trong CacheStorage của origin. Không đổi đường dẫn worker `/service-worker.js` hoặc chặn việc kiểm tra cập nhật worker ở proxy. Các cache/offline của game vẫn hoạt động; console cần kết nối mạng.

Trước mỗi request quản trị, console xác nhận worker đang điều khiển trang đã áp dụng chính sách này và hoàn tất dọn cache. Nếu còn worker cũ, console yêu cầu cập nhật và kích hoạt bản đang chờ; chưa xác nhận được thì giữ màn hình lỗi với nút “Thử lại”, không gửi request quản trị. Trang không có worker vẫn dùng bình thường và dọn các mục quản trị còn sót trong CacheStorage. Nếu lỗi tiếp diễn, kiểm tra mạng, đóng các thẻ cùng origin rồi mở lại. Việc kích hoạt bản mới để truy cập quản trị có thể thay worker của thẻ game đang mở; cơ chế cập nhật thủ công và offline của game không bị thay đổi.

Khi triển khai lần đầu bản sửa này, yêu cầu người vận hành đóng hoặc tải lại mọi tab quản trị đã mở bằng bản console cũ. JavaScript cũ đang chạy không tự nhận được ranh giới mới; bản HTML/JS cũ cũng có thể còn trong cache trước khi worker được nâng cấp. Nếu cần chặn truy cập trong khoảng chuyển tiếp, tạm tắt route quản trị ở backend, phát hành worker và console, yêu cầu đóng các tab cũ rồi mới bật lại. Sau nâng cấp, xác minh đăng xuất Owner → đăng nhập Support chỉ thấy email che và CacheStorage không còn mục `/admin`. Không coi việc đóng tab/hủy request là hoàn tác mutation đã được server nhận; nếu kết quả chưa xác nhận, kiểm tra dữ liệu và nhật ký trước khi gửi lại.

| Vai trò | Khả năng phase A |
| --- | --- |
| Owner | Tra cứu đầy đủ, xem hồ sơ/lịch sử; cấp tài khoản quản trị; đổi vai trò, bật/thu hồi quyền; xem kiểm toán |
| Operator | Tra cứu đầy đủ và xem hồ sơ/lịch sử người chơi |
| Support | Tra cứu ID/tên; email do server che; không tìm bằng email |

Máy chủ kiểm tra vai trò, trạng thái hoạt động, phiên và thời hạn trên mỗi request. Ẩn nút ở console chỉ hỗ trợ giao diện. Thay đổi quyền thu hồi mọi phiên của tài khoản đích, kể cả khi chọn lại cùng vai trò/trạng thái. Tự thay đổi quyền sẽ quay về đăng nhập. Server bảo vệ Owner đang hoạt động cuối cùng; console hiển thị lỗi xung đột nếu thao tác không hợp lệ.

Cookie riêng `fasttowin_admin` có `HttpOnly`, `SameSite=Strict`, `Path=/admin`, thời hạn 8 giờ; `Secure` ngoài development. Bearer token hoặc cookie người chơi không cấp quyền quản trị. Mutation yêu cầu Origin chính xác và header `X-Admin-CSRF` gắn với phiên; CSRF chỉ nằm trong bộ nhớ trang, được lấy lại từ `/session` khi reload. Console không lưu mật khẩu, CSRF hay snapshot người chơi vào localStorage/sessionStorage/IndexedDB. CSP chỉ cho script, stylesheet và kết nối cùng origin, không inline script, CDN hay HTML từ dữ liệu người chơi.

Đăng xuất ẩn ngay dữ liệu và gửi thu hồi phiên tới server. Nếu mạng lỗi, giao diện báo chưa xác nhận đăng xuất; đóng tab không chứng minh phiên server đã bị thu hồi. Khi nhận 401, console xóa toàn bộ thông tin đang hiển thị và yêu cầu đăng nhập lại. Refresh thất bại xóa snapshot cũ, hiển thị lỗi rõ ràng. Request cũ sau chuyển màn, đăng xuất hoặc hết hạn không được cập nhật màn hình.

## Công việc hỗ trợ

Danh sách và lịch sử phân trang theo 25 mục. Hồ sơ chỉ dùng dữ liệu thực: cấp độ, XP, Vàng, Gem, Elo, bang, hoạt động gần nhất. Hoạt động null hiển thị “Chưa có dữ liệu”. Lịch sử trận, ví và nguồn quà có phân trang riêng; nguồn quà chỉ gồm các nguồn `ADMIN_GIFT`/`COMPENSATION` đã tồn tại. Console không chỉnh sửa dữ liệu người chơi; chiến dịch quà được quản lý riêng theo hướng dẫn bên dưới.

Owner phải nhập lý do cho mỗi thao tác cấp tài khoản/đổi quyền. Console sinh UUID request ID, vô hiệu hóa nút đang xử lý, giữ ID khi thử lại cùng payload bị lỗi; sửa form hoặc hoàn tất thành công kết thúc lần thử đó. Nếu đã rời trang mà kết quả mutation chưa rõ, kiểm tra danh sách và nhật ký trước khi gửi thao tác mới. Không có reset mật khẩu quản trị trong phase A.

Giao diện dùng Material Design nền trắng. Trang tài khoản chỉ hiển thị danh sách; nút “Cấp tài khoản” và “Sửa quyền” mở modal. Có thể đóng bằng Hủy, nút đóng, Escape hoặc bấm ngoài khi chưa lưu; mật khẩu nháp được xóa khi đóng. Trong lúc lưu, các trường và thao tác đóng bị khóa đến khi máy chủ trả lời. Lỗi giữ modal và dữ liệu để thử lại; thành công đóng modal, cập nhật danh sách và báo kết quả. Nếu phiên hết hạn hoặc quyền thay đổi, modal bị gỡ cùng dữ liệu riêng tư.

Nhật ký bền vững ghi truy cập, tìm kiếm, đọc hồ sơ/lịch sử, bootstrap và thay đổi quyền. Các bản ghi gồm hành động, actor/target ID, request ID, lý do và tóm tắt quyền trước/sau khi phù hợp. Không ghi mật khẩu, token hay truy vấn email. Giữ quyền truy cập và backup PostgreSQL theo quy trình nội bộ; quyết định retention/cleanup còn thuộc vận hành.

Chiến dịch quà Vàng/XP của Issue #19 đã có trong console. Cấu hình sự kiện và các mutation dữ liệu người chơi khác của Issue #21 vẫn còn mở; console không hiển thị điều khiển giả.

## Chiến dịch Quà admin

Owner và Operator có mục **Quà admin**. Nút dấu cộng mở bản nháp với tiêu đề/nội dung Việt–Anh, Vàng/XP nguyên không âm (ít nhất một khoản dương), lý do và lịch phát. Chỉ Owner được công bố/hủy. Support vẫn xem lịch sử nguồn quà trong hồ sơ; API cho Support đọc danh sách/chi tiết chiến dịch, không mutation.

Chọn tất cả tài khoản đã đăng ký, danh sách UUID (tối đa 10.000) hoặc đăng ký trong khoảng `[từ, đến)`. Guest không nhận quà. Lọc theo hoạt động chưa hỗ trợ vì không có nguồn hoạt động chính thức. Mọi mốc nhập/hiển thị dùng Bangkok (UTC+7), API lưu epoch UTC; hạn mặc định 30 ngày tính từ giờ phát, tự cập nhật khi đổi giờ phát trước khi tùy chỉnh hạn.

**Lưu bản nháp → Xem trước → Tiếp tục công bố → Công bố chiến dịch**. Xem trước hiển thị hai ngôn ngữ, số người nhận, ID trùng/không hợp lệ và tổng Vàng/XP. ID không hợp lệ hoặc không có người nhận chặn bước xác nhận. Nếu phiên bản/người nhận thay đổi, lỗi 409 bỏ xem trước cũ và yêu cầu xem trước lại. Sau công bố, nội dung/người nhận/lịch phát cố định; sửa sai bằng chiến dịch mới. Hủy cần lý do, chặn quà chưa nhận, giữ quà đã nhận.

Danh sách phân trang thật từ máy chủ (25 mục), có trạng thái bản nháp/đã công bố/đã hủy; chưa có lọc trạng thái vì API chưa hỗ trợ. Báo cáo thể hiện đủ điều kiện, chờ phát, đã phát, đã nhận, hết hạn, hủy chưa nhận, bỏ qua và tổng Vàng/XP thực nhận. Các chỉ số có phần giao nhau; không cộng chúng thành tổng người nhận. Nháp có chỉ số 0, dùng xem trước để ước tính.

Trong khi request đang xử lý, không đóng modal bằng Hủy/đóng/Escape/bấm ngoài và không chỉnh trường. Thử lại cùng nội dung trong modal giữ request ID. Lỗi chưa xác nhận không được coi là thành công; nếu đã đóng modal/rời trang, kiểm tra báo cáo/nhật ký trước khi tạo ý định mới. Hết phiên hoặc thu hồi quyền xóa modal và dữ liệu chiến dịch. Nội dung quà chỉ được hiển thị dạng text, giữ xuống dòng.

Hợp đồng chi tiết: [admin-gifts.md](admin-gifts.md). Không phát push tự động. Việc chạy suite/local preview không cho phép công bố production.

## Kiểm tra tại máy local

`cd e2e` rồi `pnpm test:admin` chạy Chromium với HTTP fixture loopback, assets thật và DTO đúng hợp đồng. Fixture chỉ nằm trong `e2e/support`, không được đóng gói với server. Default suite không cần credential thật; flow backend được bỏ qua khi không có settings.

Flow backend/DB tùy chọn chỉ hỗ trợ database disposable `jdbc:postgresql://127.0.0.1:15432/admin_ui` và origin `http://127.0.0.1:18082`; biến `ADMIN_LIVE_SETTINGS` trỏ tới JSON local có `url`, `databaseUrl`, `email`, `passwordFile`. Người chạy tự chuẩn bị Owner và player `player@admin-test.invalid` trong database test. Bài test đọc password file riêng, không in credential; tắt trace cho flow dùng credential thật. Không dùng production settings cho suite này.

Backend: `./gradlew :server:test`. Để chạy đầy đủ PostgreSQL tests, cấp `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD` bằng database test riêng; các test tạo/drop schema riêng. Không dùng database production.
