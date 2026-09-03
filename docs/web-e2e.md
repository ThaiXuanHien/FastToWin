# Web E2E

Bộ test Playwright tại `e2e/` chạy giao diện Compose Web thật, HTTP/WebSocket thật và hai browser context tách biệt. Không giả lập kết quả trận hoặc gọi trực tiếp controller UI.

## Phạm vi

1. Đăng nhập bằng email/mật khẩu; F5 giữ phiên; Back/Forward khớp màn trong app.
2. Tạo phòng công khai, F5 tại phòng chờ, mở URL phòng bằng người chơi thứ hai, sẵn sàng và chọn đủ 50 số; mời/từ chối đấu lại; một người về sảnh không kéo người còn lại đi theo.
3. Người được 260 điểm vẫn thắng người được 240 điểm dù đối thủ chọn số 50 cuối cùng.
4. F5 giữa trận, ngắt WebSocket có kiểm soát, reconnect giữ tiến độ, hoàn thành trận và không còn thông báo kết nối cũ ở phần đấu lại.
5. Responsive trên điện thoại nhỏ `320×568`, điện thoại lớn `430×932`, tablet `834×1112` và màn ngang `932×430`: nội dung mobile được căn giữa, không tràn ngang, bottom bar, hồ sơ và luồng tạo phòng vẫn thao tác được.
6. Smoke test đăng nhập, F5 và Back/Forward trên Firefox và WebKit.

Mỗi ca tạo tài khoản UUID riêng qua API rồi **đăng nhập qua UI**; teardown xóa đúng các tài khoản này. Không dùng tài khoản cá nhân, không reset database. Mặc định backend chạy in-memory, không cần Docker và không có dữ liệu bền vững. Vì vậy bộ này chưa kiểm tra lưu Elo/ví/lịch sử PostgreSQL.

## Chạy trên Windows

Cần JDK 17, Android SDK giống môi trường build project, Node.js 22+ và pnpm 11.19.0. Nếu chưa có pnpm, cài bằng `npm install --global pnpm@11.19.0` sau khi cài Node.js.

Trong PowerShell tại thư mục gốc repo:

```powershell
$env:JAVA_HOME='C:\Users\TEN_WINDOWS_CUA_BAN\.jdks\corretto-17.0.17'
# Đổi JAVA_HOME thành thư mục JDK 17 thực tế trên máy.
.\gradlew.bat :server:installDist :webApp:wasmJsBrowserDevelopmentWebpack --no-daemon --no-configuration-cache
cd e2e
pnpm install --frozen-lockfile
pnpm exec playwright install chromium firefox webkit
pnpm test
```

Mỗi lần sửa Kotlin, chạy lại bước build trước test. `pnpm test` chỉ test bundle đã build, không tự biên dịch Kotlin.

## Chạy trên macOS/Linux

Từ thư mục gốc, đặt `JAVA_HOME` tới JDK 17 (macOS có thể dùng `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`), rồi:

```bash
./gradlew :server:installDist :webApp:wasmJsBrowserDevelopmentWebpack --no-daemon --no-configuration-cache
cd e2e
pnpm install --frozen-lockfile
pnpm exec playwright install chromium firefox webkit
pnpm test
```

Linux CI dùng `pnpm exec playwright install --with-deps chromium firefox webkit` để cài thêm thư viện hệ thống.

## Server test tách biệt

- Mặc định Web ở `http://127.0.0.1:18081`, backend ở `http://127.0.0.1:18080`.
- Playwright khởi động `support/serve.mjs`, phục vụ bundle webpack và processed resources, hỗ trợ SPA fallback, chờ backend `/health` rồi mới test.
- Runner bỏ biến môi trường database/Firebase/maintenance khỏi backend con; không đọc file Firebase của server dev. Web config chỉ trỏ về API local.
- Runner dừng tiến trình backend do chính nó tạo sau test. Không dừng server dev 8080/8081. Cổng test bị chiếm thì báo lỗi, không tự giết tiến trình.
- `E2E_BASE_URL`/`E2E_API_URL` có thể đổi sang cổng loopback HTTP khác. Không cho địa chỉ production.
- `E2E_WEB_ROOT` có thể chỉ tới thư mục bundle webpack khác; mặc định là `webApp/build/kotlin-webpack/wasmJs/developmentExecutable`, resources lấy từ `webApp/build/processedResources/wasmJs/main`.

Chỉ khi chủ động muốn dùng server dev đang mở, có thể opt-in từ thư mục `e2e`:

```powershell
$env:E2E_REUSE_SERVERS='1'
$env:E2E_BASE_URL='http://localhost:8081'
$env:E2E_API_URL='http://127.0.0.1:8080'
pnpm test
Remove-Item Env:E2E_REUSE_SERVERS, Env:E2E_BASE_URL, Env:E2E_API_URL
```

Chế độ này tạo/xóa tài khoản dùng một lần trong database dev và chơi các trận test. Nếu tiến trình bị kill trước teardown, có thể còn dữ liệu test; không tự xóa dữ liệu người dùng để dọn. Nên ưu tiên backend in-memory mặc định.

## Báo cáo và trình duyệt

```bash
pnpm report
pnpm test:list
pnpm exec playwright show-trace test-results/DUONG_DAN_CA_LOI/trace.zip
```

Báo cáo ở `e2e/playwright-report/`; ảnh và trace khi lỗi ở `e2e/test-results/`. Trace có thể chứa token/mật khẩu **tài khoản test**, không chia sẻ công khai khi còn phiên hiệu lực. Các thư mục này đã được gitignore.

CI có job **Web E2E (responsive + multi-browser)** và lưu báo cáo/ảnh/trace tối đa 7 ngày. Mặc định không retry để tránh che lỗi không ổn định.

Các lệnh chạy theo nhóm:

```bash
pnpm test:chromium      # 4 luồng game cốt lõi
pnpm test:responsive    # 4 kích thước trên Chromium
pnpm test:cross-browser # Firefox và WebKit smoke test
pnpm test               # toàn bộ ma trận 10 ca
```

Playwright WebKit không thay thế Safari/iOS thật; Chromium không thay thế đầy đủ Chrome/Edge thật. Service worker bị chặn trong bộ này để tránh cache cũ và push; cần test PWA/update/push riêng. Ma trận responsive hiện chưa mô phỏng font scale hệ điều hành hoặc bàn phím ảo thật.

## Ghi chú Compose Web

- Compose 1.11.1 xuất `testTag` thành DOM `id` trong shadow root, nên cấu hình `testIdAttribute: 'id'`.
- Accessibility DOM trong suốt; helper lấy bounds từ semantics và click canvas bằng chuột thật. Nhập liệu dùng bàn phím vì Compose thay input khi focus.
- Không khẳng định nút disabled qua HTML: semantics hiện chưa xuất đủ thuộc tính disabled. Bộ này kiểm tra chữ **ĐÃ MỜI**, popup người nhận và trạng thái sau từ chối; kiểm tra màu tối/khóa bấm cần screenshot/UI test riêng.
- F5 tại phòng chờ và kết quả là các bước kiểm tra rõ ràng trong kịch bản, không phải retry ngầm. Bộ này chưa đảm bảo cây accessibility được khôi phục đầy đủ sau mọi dialog.

Cách quản lý server và ngắt WebSocket dựa trên [Playwright webServer](https://playwright.dev/docs/test-webserver) và [WebSocketRoute](https://playwright.dev/docs/api/class-websocketroute).

## Xác minh — 03/09/2026

- Ba ca đăng nhập/F5/Back–Forward, trận đủ 50 số + từ chối đấu lại + về sảnh độc lập, và reconnect đã chạy đạt trên Chromium với dev server hiện tại.
- E2E phát hiện lỗi người chọn số cuối bị xử thắng dù ít điểm hơn. Đã sửa backend và chạy trực tiếp Kotlin/JUnit: **53/53 test GameEngine đạt**, gồm 3 test hồi quy mới.
- CI #67 đã chạy đạt cả Build/test và 4 ca Web E2E Chromium cốt lõi.
- Bốn kích thước responsive và Playwright WebKit đã chạy đạt local. Firefox headless trên máy Windows hiện tại không khởi tạo được framebuffer phần mềm; job Ubuntu CI là bước xác minh Firefox độc lập với giới hạn máy local này.
