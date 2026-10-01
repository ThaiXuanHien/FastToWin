# Quản trị FastToWin — phase A

Console tiếng Việt tại `/admin/` dùng tài khoản quản trị riêng, tách khỏi đăng nhập người chơi. Mặc định cả console và `/admin/api` đều bị tắt. Không có mật khẩu mặc định hay endpoint công khai để tạo Owner.

## Bật và cấp Owner

1. Chạy backend với PostgreSQL được cấu hình qua `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` như hướng dẫn server. Dùng cơ sở dữ liệu development riêng khi thử nghiệm.
2. Đặt `FASTTOWIN_ADMIN_ENABLED=true` và `FASTTOWIN_ADMIN_ORIGIN` bằng origin chính xác, không có đường dẫn, dấu `/` cuối, query hoặc fragment. Ví dụ development: `http://127.0.0.1:8080`; production: `https://admin.example.com`. `FASTTOWIN_ENV=prod` yêu cầu HTTPS và PostgreSQL; development chỉ cho HTTP loopback. Bỏ switch hoặc đặt `false` để tắt toàn bộ route quản trị.
3. Chạy công cụ offline `./gradlew :server:bootstrapAdminOwner` (Windows: `./gradlew.bat :server:bootstrapAdminOwner`) trong cùng môi trường database và admin. Cung cấp email qua `FASTTOWIN_ADMIN_BOOTSTRAP_EMAIL` hoặc prompt console. Mật khẩu được đọc bằng console ẩn ký tự, tối thiểu 12 và tối đa 128 ký tự. Nếu môi trường không có console, dùng `FASTTOWIN_ADMIN_BOOTSTRAP_PASSWORD_FILE` trỏ tới file bí mật chứa đúng mật khẩu, không thêm newline; hạn chế quyền đọc file và xóa khi không còn cần. Không đưa mật khẩu vào lệnh, lịch sử terminal, repository hoặc log.
4. Bootstrap cùng email của Owner đang hoạt động là idempotent, giữ nguyên mật khẩu hiện có. Công cụ từ chối thay thế tài khoản quản trị không phải Owner và không sửa tài khoản người chơi có cùng email. Production provisioning phải thực hiện trong quy trình vận hành được phê duyệt; tài liệu này không tự bật hay cấp bất kỳ tài khoản production nào.
5. Truy cập đúng origin đã cấu hình và `/admin/`. Có thể đặt reverse proxy cùng origin chuyển toàn bộ `/admin/` (gồm assets và API) tới Ktor. Giữ nguyên `Origin`, `Set-Cookie` và các security header; TLS termination phải cung cấp HTTPS cho trình duyệt. Không cache trang hoặc API; không đưa API quản trị tới origin khác. Danh sách CORS của game không cấp quyền đọc quản trị.

## Quyền và bảo mật phiên

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

Nhật ký bền vững ghi truy cập, tìm kiếm, đọc hồ sơ/lịch sử, bootstrap và thay đổi quyền. Các bản ghi gồm hành động, actor/target ID, request ID, lý do và tóm tắt quyền trước/sau khi phù hợp. Không ghi mật khẩu, token hay truy vấn email. Giữ quyền truy cập và backup PostgreSQL theo quy trình nội bộ; quyết định retention/cleanup còn thuộc vận hành.

Phase B còn mở trong Issue #21: phát quà, cấu hình chiến dịch/sự kiện, preview/publish và các mutation dữ liệu người chơi. Console không hiển thị điều khiển giả cho các chức năng này.

## Kiểm tra tại máy local

`cd e2e` rồi `pnpm test:admin` chạy Chromium với HTTP fixture loopback, assets thật và DTO đúng hợp đồng. Fixture chỉ nằm trong `e2e/support`, không được đóng gói với server. Default suite không cần credential thật; flow backend được bỏ qua khi không có settings.

Flow backend/DB tùy chọn chỉ hỗ trợ database disposable `jdbc:postgresql://127.0.0.1:15432/admin_ui` và origin `http://127.0.0.1:18082`; biến `ADMIN_LIVE_SETTINGS` trỏ tới JSON local có `url`, `databaseUrl`, `email`, `passwordFile`. Người chạy tự chuẩn bị Owner và player `player@admin-test.invalid` trong database test. Bài test đọc password file riêng, không in credential; tắt trace cho flow dùng credential thật. Không dùng production settings cho suite này.

Backend: `./gradlew :server:test`. Để chạy đầy đủ PostgreSQL tests, cấp `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD` bằng database test riêng; các test tạo/drop schema riêng. Không dùng database production.
