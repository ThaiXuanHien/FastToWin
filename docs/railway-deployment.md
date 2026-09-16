# Triển khai alpha trên Railway

Đây là luồng deploy riêng, không thay thế `start-dev-all.cmd` hoặc Docker
Compose production. Chỉ dùng dữ liệu alpha mới; không upload database local
hay tài khoản test đầy đủ lên Internet.

## Kiến trúc và giới hạn

- Một project, ba dịch vụ cùng region: `Postgres`, `server`, `web`.
- `Postgres` và `server` chỉ có private networking. Không tạo TCP proxy cho
  database và không tạo public domain cho backend.
- Chỉ `web` có public domain HTTPS của Railway. Caddy phục vụ Web và proxy
  `/game`, `/auth/*`, `/api/*`, `/status`, `/health` sang backend nội bộ.
- Backend chạy `FASTTOWIN_ENV=prod`, một replica, không bật Serverless/sleep.
  Không tăng replica khi trạng thái realtime vẫn nằm trong một instance.
- Railway kết thúc TLS tại edge. Caddy bên trong nghe HTTP trên `$PORT`;
  backend chỉ tin proxy headers vì không được truy cập công khai trực tiếp.
- Chưa triển khai stack Grafana/Loki/Prometheus ở alpha để tiết kiệm tài nguyên.

Trial hiện có 30 ngày hoặc $5 credit, tùy giới hạn nào hết trước. Kiểm tra
Dashboard trước khi deploy; không thêm thẻ hoặc nâng cấp nếu chưa quyết định
ngân sách. Trial không phải hosting miễn phí lâu dài. Cấu hình giới hạn CPU/RAM
không phải giới hạn tổng hóa đơn.

## 1. Build và đóng gói tại Windows

Không deploy repo trực tiếp bằng autodetection: runtime Dockerfiles hiện tại
nhận artifact có sẵn, còn repo Gradle chứa cả Android/iOS/Web. Build tại máy
development rồi upload hai Docker contexts chỉ chứa artifact giúp tránh cài
Android SDK trên Railway.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-railway.ps1
```

Script chạy `:server:test`, `:server:installDist` và
`:webApp:composeCompatibilityBrowserDistribution`; lỗi build sẽ dừng trước
khi tạo package. Kết quả nằm trong một thư mục mới
`.artifacts/railway/<release>/server` và `web`. Không upload cả thư mục gốc
project, `deploy/secrets`, `.env` hoặc `.artifacts/railway-tests`.

Build production Wasm cần nhiều RAM hơn build development. Script cấp heap
4GB riêng cho Kotlin compiler bằng `kotlin.daemon.jvmargs`, giới hạn một
worker và tắt build song song; không thay đổi cấu hình development hoặc RAM
backend trên Railway. Đóng emulator và ứng dụng nặng trước khi build. Nếu
vẫn báo `GC overhead limit exceeded` hoặc `Java heap space`, máy có đủ RAM
trống thì có thể thử heap 6GB:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-railway.ps1 -CompilerHeapMb 6144
```

Heap là mức tối đa cho compiler, không phải tổng RAM của build; Gradle,
Node và hệ điều hành vẫn cần bộ nhớ riêng. Không tăng heap vượt khả năng
máy và không dùng `-PackageOnly` khi build production chưa thành công.
Xem [cấu hình Kotlin daemon](https://kotlinlang.org/docs/gradle-compilation-and-caches.html#setting-kotlin-daemon-s-jvm-arguments).

`-PackageOnly` chỉ dành cho trường hợp đã build và kiểm thử đúng revision;
không sử dụng để vượt qua build lỗi. Script không sửa artifact đầu vào và
không ghi đè package cũ. Kiểm tra bộ đóng gói:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\tests\RailwayPackage.Tests.ps1
```

Nếu Gradle báo `Unable to establish loopback connection`, build đang bị lỗi
socket nội bộ của Java trước khi xử lý project. Không dùng
`sun.nio.ch.PollSelectorProvider` trên Windows, không tắt firewall để thử
deploy và không dùng artifact cũ như một bản build mới đã được xác minh.

Nếu Web của đúng revision đã build thành công và thay đổi sau đó chỉ nằm ở
backend/tài liệu, có thể tránh build lại Web bằng lệnh dưới đây. Chế độ này vẫn
chạy toàn bộ `:server:test` và tạo lại server distribution trước khi đóng gói:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-railway.ps1 -ReuseVerifiedWebBuild
```

Không dùng tùy chọn này sau khi thay đổi `webApp`, `shared`, `protocol`, Gradle
hoặc tài nguyên Web vì package khi đó có thể trộn hai revision khác nhau.

## 2. Chuẩn bị project Railway

1. Dashboard → New Project → Empty Project; đặt tên `FastToWin Alpha`.
2. Thêm PostgreSQL, rồi hai Empty Services tên `server`, `web`.
3. Chọn cùng region. Kiểm tra trial còn hiệu lực trước khi tạo dịch vụ.
4. Giữ một replica cho backend và tắt Serverless/sleep. Chọn RAM phù hợp với
   trial; Java heap mặc định 512MB cần thêm RAM cho metaspace/native memory.
5. Không kết nối GitHub autodeploy cho hai dịch vụ artifact-only. GitHub App
   có thể đã được cấp quyền repo, nhưng đó không đồng nghĩa Docker có artifact
   để build từ source. Mỗi lần phát hành phải build và upload lại package.
6. Với dịch vụ mới, không dựa vào `railway.json`: UI Railway hiện thông báo
   dịch vụ chưa từng dùng Config as Code không thể bật tính năng này từ
   2026-08-28. Cấu hình trực tiếp trong Settings của **cả server và web**:
   Builder = Dockerfile, Dockerfile Path = `/Dockerfile`, Root Directory để
   trống (CLI upload từng context với `--path-as-root`), Healthcheck Path =
   `/health`, Healthcheck Timeout = 120 giây, Restart Policy = On Failure,
   Max restart retries = 3. Giữ Serverless tắt cho backend. File JSON trong
   gói là metadata cũ, không thay thế việc kiểm tra Settings thực tế.

## 3. Biến môi trường

Nhập secrets trực tiếp trong Railway Variables, không commit hoặc gửi vào chat.
Các tên dịch vụ trong reference phải đúng tên đã tạo trên Dashboard.

**server:**

| Biến | Giá trị |
| --- | --- |
| `PORT` | `8080` |
| `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DATABASE_USER` | `${{Postgres.PGUSER}}` |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `DATABASE_POOL_SIZE` | `4` |
| `FASTTOWIN_PUBLIC_URL` | `https://<domain-của-web>` |
| `FASTTOWIN_WEB_ORIGINS` | Cùng HTTPS origin ở trên, không path hay wildcard |
| `FASTTOWIN_WEB_BASE_URL` | Cùng HTTPS origin ở trên |
| `FASTTOWIN_EMAIL_PROVIDER` | `brevo` |
| `FASTTOWIN_BREVO_API_KEY` | API key tạo trong Brevo; nhập như secret |
| `FASTTOWIN_EMAIL_FROM_EMAIL` | Địa chỉ người gửi đã xác minh trong Brevo |
| `FASTTOWIN_EMAIL_FROM_NAME` | `Fast To Win` |

Không gán `DATABASE_URL` bằng nguyên PostgreSQL URL chứa username/password:
backend cần JDBC URL và nhận credentials bằng biến riêng. Dùng private
`PGHOST`, không dùng public TCP proxy. Railway Trial/Hobby chặn outbound SMTP,
vì vậy cấu hình alpha dùng Brevo qua HTTPS API. Production bắt buộc cấu hình
email hợp lệ; không điền dữ liệu giả hoặc chuyển sang `dev` để bỏ kiểm tra.

**web:**

| Biến | Giá trị |
| --- | --- |
| `PORT` | `8080` |
| `BACKEND_UPSTREAM` | `${{server.RAILWAY_PRIVATE_DOMAIN}}:8080` |

Chỉ sau khi xác nhận công khai Web, chọn web → Settings → Networking →
Generate Domain với target port 8080. URL này dùng cho ba biến origin/base URL
của server. Không cần mua domain để alpha.

Push thực tế cần server credential Firebase qua cơ chế secret riêng; bộ
package không chứa credential này. Không đưa service-account JSON vào Docker
image. Chưa có credential thì chưa coi kiểm thử push là hoàn tất. Quảng cáo
thưởng và mua Gem production giữ chế độ an toàn mặc định, không giả lập receipt.

## 4. Upload artifact qua CLI

Cài CLI từ [nguồn chính thức](https://docs.railway.com/cli), đăng nhập và link
đúng project alpha. Dùng `railway up --help` để kiểm tra flags của phiên bản
đang cài trước khi upload. Ví dụ chạy từ thư mục gốc project:

```powershell
railway login
railway link
railway up .artifacts/railway/<release>/server --service server --path-as-root --no-gitignore
railway up .artifacts/railway/<release>/web --service web --path-as-root --no-gitignore
```

`--no-gitignore` chỉ dùng với hai contexts đã kiểm tra, vì `.artifacts` bị Git
ignore. Không chạy flag này với cả repo. Upload có thể tiêu hao trial credit;
không lặp lại deploy liên tục khi thiếu cấu hình email hoặc biến môi trường.

## 5. Điều kiện hoàn tất

- Backend và Web deployment healthy; đọc log nếu healthcheck thất bại.
- `https://<web-domain>/health` trả HTTP 200 và dữ liệu backend, không phải HTML.
- Đăng ký, đăng nhập, tải avatar và quên mật khẩu hoạt động qua HTTPS origin.
- Email được gửi thật, cookie phiên secure và WebSocket dùng WSS.
- Hai tài khoản alpha khác nhau chơi một trận, realtime và kết quả đồng bộ.
- Reload/deep link Web, Back/Forward, nền tối và JS fallback được kiểm tra.
- Không có public endpoint cho PostgreSQL/backend; `/internal/*` không truy cập
  từ Web. Không có secrets trong package, Git hoặc log.

Chỉ bộ đóng gói có test không đủ để kết luận đã deploy thành công. Kiểm thử
push, thanh toán và quảng cáo production phải ghi rõ phần nào chưa cấu hình.

## Trạng thái alpha ngày 16/09/2026

- Project: `FastToWin Alpha`; Web: https://fasttowin.up.railway.app.
  Đã đổi domain Railway sang tên gọn này và cập nhật cả ba biến URL/origin
  của server; khi chuyển domain, người dùng cần đăng nhập lại nếu chưa có
  phiên trên origin mới.
- Ba dịch vụ `Postgres`, `server`, `web` đã deploy thành công, cùng region
  `sfo`, mỗi dịch vụ một replica. PostgreSQL dùng volume 500MB, dữ liệu alpha
  mới; không import database hoặc tài khoản test local.
- Server deployment: `80a6d272-71d0-47c8-8021-2aa9ce60e828`; Web deployment:
  `47dfc088-f856-4ffe-9677-92cb010c3ca9`. Web dùng package
  `.artifacts/railway/20260916-203249-1f16a4f9`, chứa bundle Wasm và JS fallback
  mới cùng ô nhập HTML native để thử nghiệm bàn phím Safari/iPhone.
- Đã xác minh `/health` HTTP 200 (`OK`), `/status` HTTP 200 (không bảo trì),
  `/` và `/rooms` HTTP 200; `/internal`, `/internal/health` và
  `/internal/metrics` HTTP 404. Database không có TCP proxy/public domain;
  backend không có public domain.
- Đã mở giao diện, hướng dẫn và sảnh chế độ khách trong trình duyệt; nền tối
  tải được. Bộ kiểm thử ô nhập đạt 8/8 ca Chromium/WebKit; vẫn cần xác nhận
  bàn phím ảo trên Safari iPhone thật. Chưa xác minh đăng ký/đăng nhập tài
  khoản thật, email Brevo gửi thật, avatar, hoặc trận hai người trên alpha.
- Server khởi động ở `prod`, storage PostgreSQL và email provider Brevo;
  đã áp dụng 47 migrations, schema v48. Push tắt vì chưa cung cấp Firebase
  Admin credential. Thanh toán và quảng cáo thưởng production chưa được
  coi là đã cấu hình/kiểm thử.
- Test routing thực tế (cần Caddy executable chính thức):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\tests\CaddyRouting.Tests.ps1 -CaddyExecutable <duong-dan-caddy.exe>
```

Test chạy listener loopback tạm thời, kiểm tra cả hai Caddyfile chặn đường
dẫn nội bộ trước SPA fallback và vẫn phục vụ trang chủ/deep link.
