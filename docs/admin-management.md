# Quản trị FastToWin — phase A

Console tiếng Việt tại `/admin/` dùng tài khoản quản trị riêng, tách khỏi đăng nhập người chơi. Mặc định cả console và `/admin/api` đều bị tắt. Không có mật khẩu mặc định hay endpoint công khai để tạo Owner.

## Bật và cấp Owner

1. Chạy backend với PostgreSQL được cấu hình qua `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` như hướng dẫn server. Dùng cơ sở dữ liệu development riêng khi thử nghiệm.
2. Đặt `FASTTOWIN_ADMIN_ENABLED=true` và `FASTTOWIN_ADMIN_ORIGIN` bằng origin chính xác, không có đường dẫn, dấu `/` cuối, query hoặc fragment. Ví dụ development: `http://127.0.0.1:8080`; production: `https://admin.example.com`. `FASTTOWIN_ENV=prod` yêu cầu HTTPS và PostgreSQL; development chỉ cho HTTP loopback. Bỏ switch hoặc đặt `false` để tắt toàn bộ route quản trị.
3. Chạy công cụ offline `./gradlew :server:bootstrapAdminOwner` (Windows: `./gradlew.bat :server:bootstrapAdminOwner`) trong cùng môi trường database và admin. Cung cấp email qua `FASTTOWIN_ADMIN_BOOTSTRAP_EMAIL` hoặc prompt console. Mật khẩu được đọc bằng console ẩn ký tự, tối thiểu 12 và tối đa 128 ký tự. Nếu môi trường không có console, dùng `FASTTOWIN_ADMIN_BOOTSTRAP_PASSWORD_FILE` trỏ tới file bí mật chứa đúng mật khẩu, không thêm newline; hạn chế quyền đọc file và xóa khi không còn cần. Không đưa mật khẩu vào lệnh, lịch sử terminal, repository hoặc log.
4. Bootstrap cùng email của Owner đang hoạt động là idempotent, giữ nguyên mật khẩu hiện có. Công cụ từ chối thay thế tài khoản quản trị không phải Owner và không sửa tài khoản người chơi có cùng email. Production provisioning phải thực hiện trong quy trình vận hành được phê duyệt; tài liệu này không tự bật hay cấp bất kỳ tài khoản production nào.
5. Truy cập đúng origin đã cấu hình và `/admin/`. Có thể đặt reverse proxy cùng origin chuyển toàn bộ `/admin/` (gồm assets và API) tới Ktor. Giữ nguyên `Origin`, `Set-Cookie` và các security header; TLS termination phải cung cấp HTTPS cho trình duyệt. Không cache trang hoặc API; không đưa API quản trị tới origin khác. Danh sách CORS của game không cấp quyền đọc quản trị.

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

Danh sách và lịch sử phân trang theo 25 mục. Hồ sơ chỉ dùng dữ liệu thực: cấp độ, XP, Vàng, Gem, Elo, bang, hoạt động gần nhất. Hoạt động null hiển thị “Chưa có dữ liệu”. Lịch sử trận, ví và nguồn quà có phân trang riêng; nguồn quà chỉ gồm các nguồn `ADMIN_GIFT`/`COMPENSATION` đã tồn tại. Console phase A không chỉnh sửa người chơi hoặc tạo quà.

Owner phải nhập lý do cho mỗi thao tác cấp tài khoản/đổi quyền. Console sinh UUID request ID, vô hiệu hóa nút đang xử lý, giữ ID khi thử lại cùng payload bị lỗi; sửa form hoặc hoàn tất thành công kết thúc lần thử đó. Nếu đã rời trang mà kết quả mutation chưa rõ, kiểm tra danh sách và nhật ký trước khi gửi thao tác mới. Không có reset mật khẩu quản trị trong phase A.

Giao diện dùng Material Design nền trắng. Trang tài khoản chỉ hiển thị danh sách; nút “Cấp tài khoản” và “Sửa quyền” mở modal. Có thể đóng bằng Hủy, nút đóng, Escape hoặc bấm ngoài khi chưa lưu; mật khẩu nháp được xóa khi đóng. Trong lúc lưu, các trường và thao tác đóng bị khóa đến khi máy chủ trả lời. Lỗi giữ modal và dữ liệu để thử lại; thành công đóng modal, cập nhật danh sách và báo kết quả. Nếu phiên hết hạn hoặc quyền thay đổi, modal bị gỡ cùng dữ liệu riêng tư.

Nhật ký bền vững ghi truy cập, tìm kiếm, đọc hồ sơ/lịch sử, bootstrap và thay đổi quyền. Các bản ghi gồm hành động, actor/target ID, request ID, lý do và tóm tắt quyền trước/sau khi phù hợp. Không ghi mật khẩu, token hay truy vấn email. Giữ quyền truy cập và backup PostgreSQL theo quy trình nội bộ; quyết định retention/cleanup còn thuộc vận hành.

Phase B còn mở trong Issue #21: phát quà, cấu hình chiến dịch/sự kiện, preview/publish và các mutation dữ liệu người chơi. Console không hiển thị điều khiển giả cho các chức năng này.

## Kiểm tra tại máy local

`cd e2e` rồi `pnpm test:admin` chạy Chromium với HTTP fixture loopback, assets thật và DTO đúng hợp đồng. Fixture chỉ nằm trong `e2e/support`, không được đóng gói với server. Default suite không cần credential thật; flow backend được bỏ qua khi không có settings.

Flow backend/DB tùy chọn chỉ hỗ trợ database disposable `jdbc:postgresql://127.0.0.1:15432/admin_ui` và origin `http://127.0.0.1:18082`; biến `ADMIN_LIVE_SETTINGS` trỏ tới JSON local có `url`, `databaseUrl`, `email`, `passwordFile`. Người chạy tự chuẩn bị Owner và player `player@admin-test.invalid` trong database test. Bài test đọc password file riêng, không in credential; tắt trace cho flow dùng credential thật. Không dùng production settings cho suite này.

Backend: `./gradlew :server:test`. Để chạy đầy đủ PostgreSQL tests, cấp `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD` bằng database test riêng; các test tạo/drop schema riêng. Không dùng database production.
