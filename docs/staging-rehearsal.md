# Staging rehearsal — FastToWin (#6)

Môi trường này chạy **theo đợt**, dùng dữ liệu mẫu. Không dùng để kiểm thử mua
Gem thật, gửi push production hoặc nghiệm thu bàn phím trên thiết bị thật.
Issue và kết quả cuối cùng: [#6](https://github.com/ThaiXuanHien/FastToWin/issues/6).

## Cách ly và chi phí

- Project riêng `FastToWin Staging`, ID `b5c5db71-2df8-46da-84fb-d9b9df6d7951`.
- Environment duy nhất `staging`, ID `8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9`.
- Cùng workspace và region `sfo` với production; không đổi cấu hình production.
- Bốn service `staging-server`, `staging-web`, `staging-postgres`, `staging-mail`;
  mỗi service một replica. Chỉ web có domain HTTPS, không có TCP proxy.
- Domain: `https://staging-web-staging-b446.up.railway.app`. Khi dừng compute,
  domain được giữ nhưng không phục vụ app. Không xem một domain đã tạo là bằng
  chứng deployment healthy.
- DB/volume, Owner, mật khẩu SMTP và mật khẩu người chơi thử được tạo riêng.
  Không duplicate environment hay sao chép credential/DB production.
- `FASTTOWIN_ENV=prod`: giữ nguyên production guards, cookie và xác minh email;
  Firebase/push, Google Play, financial provider và rewarded-ad provider không
  được cấu hình. Web dùng `deploy/railway/staging/config.js`, không Firebase config
  production.

Chủ dự án đã chọn và kích hoạt **Hobby trả phí** ngày 12/10/2026. Preflight phải
kiểm tra `customer.isTrialing=false` và subscription `active`; chỉ trường
`workspace.plan=HOBBY` **không đủ**. Trước khi nâng, API trả plan Hobby nhưng
subscription vẫn trial và chặn tạo service thứ sáu trong workspace.

Hobby $5/tháng gồm $5 usage cho toàn workspace; tổng usage vượt $5 sẽ tính phần
chênh lệch. Không phải $5 riêng cho mỗi project. Không đặt hard limit workspace
để giới hạn riêng staging vì nó có thể dừng cả production.
[Giá Railway](https://docs.railway.com/pricing/plans),
[Free trial](https://docs.railway.com/pricing/free-trial),
[cost control](https://docs.railway.com/pricing/cost-control).

Giới hạn container để thử: backend 1 GB/1 vCPU, Postgres 0.512 GB/0.5 vCPU,
web và mail mỗi service 0.128 GB/0.25 vCPU. Giới hạn không phải usage thực tế.
Mục tiêu mỗi đợt tối đa hai giờ; dừng compute sau khi xong. Volume vẫn có thể
phát sinh phí khi giữ lại. Không nâng Pro chỉ để chạy đợt thử này.

## Artifact, TLS và biến môi trường

Dùng artifact server/web đã có checksum và bằng chứng build/test cho đúng
revision; không upload cả repo với `--no-gitignore`. Kiểm tra toàn bộ manifest,
không chỉ `server.jar`. Tạo bản copy dành cho staging rồi:

1. Thay Dockerfile backend bằng `deploy/railway/staging/server/Dockerfile`.
   Sinh CA riêng và chứng chỉ SMTP có SAN `staging-mail.railway.internal`.
   Chỉ đặt **CA public** tại `server/staging-mail-ca.crt`; không đưa private key
   vào build context. Dockerfile thêm CA vào bản sao truststore Java, giữ CA
   công khai hiện có. Không tắt xác minh TLS hoặc bật trust-all.
2. Thay `web/site/config.js` bằng config staging cùng origin HTTPS/WSS.
   Dùng Caddyfile web hiện có. Đối chiếu public asset checksum sau mỗi deployment.
3. Deploy Mailpit bằng context `deploy/railway/staging/mail`, pin `v1.31.4`.
   Private key/certificate được đưa qua Railway variables và viết vào `/tmp`
   với quyền hạn chế khi container khởi động. `MP_SMTP_AUTH` và `MP_UI_AUTH`
   là credential ngẫu nhiên riêng. Không tạo public domain cho hộp thư.
4. Mailpit buộc STARTTLS + auth, chỉ chấp nhận `@staging.invalid`; forwarding,
   relay và webhook bị từ chối. Đọc mã xác minh/reset qua API private có auth
   bằng SSH, không dùng code preview development hay đọc token trực tiếp từ DB.

Chứng chỉ đợt đầu hết hạn **11/11/2026**. Trước đợt tiếp theo kiểm tra hạn; nếu
hết hạn, sinh CA/chứng chỉ mới, cập nhật mail variables và rebuild truststore
backend cùng nhau. Không tái sử dụng secret production.
[Mailpit SMTP](https://mailpit.axllent.org/docs/configuration/smtp/),
[Docker image](https://mailpit.axllent.org/docs/install/docker/).

Biến backend cần đặt bằng secret store, không commit giá trị bí mật:

| Biến | Giá trị/cách chọn |
| --- | --- |
| `FASTTOWIN_ENV` | `prod` |
| `FASTTOWIN_PUBLIC_URL`, `FASTTOWIN_WEB_ORIGINS`, `FASTTOWIN_ADMIN_ORIGIN` | Đúng origin staging, không thêm production origin |
| `FASTTOWIN_ADMIN_ENABLED` | `true` |
| `DATABASE_URL` | JDBC tới private hostname `staging-postgres.railway.internal`, DB `staging_only` hoặc DB restore được ghi trong đợt thử |
| `DATABASE_USER`, `DATABASE_PASSWORD` | Role và mật khẩu riêng staging |
| `DATABASE_POOL_SIZE` | `3` |
| `FASTTOWIN_EMAIL_PROVIDER` | `smtp` |
| `FASTTOWIN_SMTP_HOST`, `FASTTOWIN_SMTP_PORT` | `staging-mail.railway.internal`, `1025` |
| `FASTTOWIN_SMTP_USERNAME`, `FASTTOWIN_SMTP_PASSWORD` | Khớp credential Mailpit riêng |
| `FASTTOWIN_SMTP_FROM_EMAIL` | `no-reply@staging.invalid` |
| `FASTTOWIN_SMTP_STARTTLS`, `FASTTOWIN_SMTP_SSL` | `true`, `false` |
| `FINANCIAL_GOOGLE_ENABLED` | `false` |

Web: `BACKEND_UPSTREAM=http://staging-server.railway.internal:8080`, `PORT=8080`.
Postgres dùng volume riêng tại `/var/lib/postgresql/data`, `PGDATA` là thư mục
`pgdata` bên dưới; `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` riêng.
Không gắn public domain/TCP proxy cho DB, mail hoặc backend. Xác nhận private
hostname sau deployment, không suy từ tên service nếu endpoint đã được đổi tên.

Railway CLI/API 5.59 hiện nhận builder enum `RAILPACK`; đặt
`dockerfilePath=/Dockerfile` để chọn Dockerfile. Metadata deployment báo
`DOCKERFILE`. Đặt healthcheck `/health`, timeout 120 giây cho server/web,
restart `ON_FAILURE` tối đa 3 lần, một replica, không sleep. Không copy
`railway.json` cũ để kỳ vọng platform tự áp dụng config-as-code cho service mới.

Bootstrap Owner bằng `AdminBootstrapKt` qua SSH, với password file tạm và
`FASTTOWIN_ADMIN_BOOTSTRAP_EMAIL=owner@staging.invalid`; truyền mật khẩu qua stdin,
không argument/log. Xóa file và thu hồi SSH key tạm sau diễn tập.

## Chạy một đợt thử

1. Đọc issue #6; kiểm tra subscription/quota, target manifest, source revision,
   checksum artifact, chứng chỉ SMTP và giờ kết thúc. Giữ snapshot deployment
   IDs production để đối chiếu sau đợt thử.
2. Tạo/cập nhật từng service bằng project/environment/service IDs staging rõ
   ràng. API có thể áp dụng service settings cho các environment không phải
   fork trong cùng project: vì vậy staging dùng project riêng, một environment.
3. Bật Postgres và mail trước, backend tiếp theo, web sau cùng. `railway up`
   phải chỉ tới context đã kiểm tra với `--project`, `--environment`, `--service`,
   `--path-as-root`, `--no-gitignore`, `--detach`. Khi image hết hạn lưu trữ,
   deploy lại artifact đã kiểm chứng; không kỳ vọng rollback cũ luôn còn image.
4. Xác minh `/health` trả `200 OK`, `/api/status` `ACTIVE`, `/internal/*` `404`;
   admin API và mailbox chưa đăng nhập `401`. Kiểm tra CORS/origin, cookie
   `Secure`/`HttpOnly`/`SameSite=Strict`, login UI, Owner và WSS reconnect.
5. Đăng ký hai tài khoản `@staging.invalid`, xác minh và reset mật khẩu qua
   email private. Chơi một trận hai người, đối chiếu kết quả và ví đã ghi DB.
6. Planned maintenance: chặn trận mới, trận hiện tại tiếp tục và kết thúc;
   emergency: đóng socket; tắt bảo trì rồi reconnect. Phải trả về `ACTIVE`.
7. Backup PostgreSQL custom format, tải về thư mục ignored, so SHA-256 ở hai
   đầu. Tạo DB **mới**, restore thật với `pg_restore --exit-on-error --no-owner
   --no-acl`. Không dùng `--clean` trên DB đang chạy. Đối chiếu schema, tài khoản,
   profile, trận và bút toán ví; chạy app trỏ DB restore rồi login/WSS đọc số dư.
   `pg_restore --list` một mình chưa đáp ứng nghiệm thu restore.
8. Khi không còn trận, bật bảo trì để rollback cặp backend/web. Kiểm tra target
   deployment thuộc staging, ghi image digest cũ; rollback trong hạn image còn
   giữ. Xác minh digest của deployment mới bằng digest target, schema tương
   thích, login/API/WSS và assets đúng bản. Không rollback schema tự động.
   Nếu migration không tương thích, dừng và chọn fix tiến hoặc restore theo
   kế hoạch chấp nhận mất dữ liệu đã thống nhất.
9. Tắt bảo trì và xác minh `ACTIVE`, ghi bằng chứng; logout phiên thử, thu hồi
   key tạm, rồi **dừng cả bốn service** và xác nhận không còn deployment active.
   Giữ volume/backup theo nhu cầu; xóa volume là bước riêng cần chọn rõ dữ liệu.

Dữ liệu chứa token/password/dump và log riêng đặt trong `.artifacts/`, không
commit. Bằng chứng công khai chỉ chứa trạng thái, IDs, checksum và số đếm.

## Công cụ kiểm tra/dừng staging

`target.json` ngoài Git chứa `projectId`, `environmentId`, `origin`, và `services`
với bốn key `server`, `web`, `postgres`, `mail`; mỗi key có `id`, `name`.
Tên service tương ứng `staging-<key>`. Không dùng file này để lưu mật khẩu.

```powershell
python scripts/staging_rehearsal.py status --manifest .artifacts/staging-20261012/target.json --railway <railway.exe>
python scripts/staging_rehearsal.py stop --manifest .artifacts/staging-20261012/target.json --railway <railway.exe>
python -B -m unittest discover -s scripts -p "staging*_test.py"
```

Công cụ kiểm tra project/workspace/environment live, từ chối production IDs,
origin và service không thuộc staging; yêu cầu danh sách service live khớp đủ
bốn service trong manifest, từ chối service phát sinh ngoài danh sách. `stop` dùng `deploymentRemove`, kiểm tra
ownership từng deployment, bao gồm build đang chạy và đợi đến khi không còn
active deployment. Nó không xóa project, domain, database, volume hay backup.
Nếu có deployment mới xuất hiện trong lúc cleanup, không được báo hoàn tất;
kiểm tra rồi chạy lại. Công cụ không thay CLI linked target hoặc billing.

## Bằng chứng đợt 12/10/2026

- Bốn service từng đạt `SUCCESS`, HTTPS health `200 OK`; chỉ web public, không
  TCP proxy. Cặp artifact mới tương ứng master `7a6c61d`, runtime `08d6752`;
  198 file/183.778.891 byte được đối chiếu manifest trước khi đóng gói staging.
- Hai tài khoản đăng ký/xác minh bằng SMTP STARTTLS private; reset mật khẩu và
  login thành công. Không lộ code development, không gửi thư ra provider thật.
- Cookie secure/HttpOnly/Strict; origin production bị từ chối; browser không có
  uncaught page error; WSS reconnect, trận 50 số và kết quả được ghi nhận.
- Planned: `DRAINING → MAINTENANCE → ACTIVE`; emergency:
  `MAINTENANCE → ACTIVE`, socket đóng `1001`, reconnect lại thành công.
- Dump mẫu 179.748 byte, SHA-256
  `44dd9a3c6fcd5fafc90db4899a78c61c743f7d102ce4c535607b831937aa2654`.
  Restore thật vào `staging_restore`: schema 60, 2 tài khoản registered,
  4 profile kể cả guest, 2 trận, 6 bút toán; tổng 400 gold, 0 gem, 104 XP khớp.
  Lần thử này restore mất 4,86 giây, backup/tải/restore/đối chiếu 35,19 giây;
  chưa phải cam kết RTO/RPO production hay kiểm thử DB lớn.
- App runtime `0a88a75` và `08d6752` đều login được với DB restore V60 và đọc
  đúng số dư qua WSS: 360/40 gold, 88/16 XP, gem 0. Owner vẫn đăng nhập được.
- Rollback thật từ runtime `08d6752` về `0a88a75`: cả hai deployment đạt SUCCESS,
  image digest khớp target; giữ schema V60, số dư và bút toán không đổi. Cả hai
  service healthy sau khoảng 10,8 giây tính từ yêu cầu rollback đến trạng thái
  SUCCESS cuối cùng của platform; thời gian kiểm tra và mở lại dịch vụ dài hơn.
  Sau khi mở lại, API/WSS, hai tài khoản, Owner và checksum index web đều đạt.
- Đã dừng compute cả bốn service, xác minh không còn deployment active;
  thu hồi SSH key tạm và xóa bản key trên máy. Domain và volume được giữ lại,
  volume vẫn có thể tính phí. Production giữ nguyên deployment IDs và health.
  Đợt tiếp theo deploy artifact hiện hành đã kiểm chứng; không chỉ redeploy
  bản cũ vừa dùng làm mốc rollback.

Các bước thiết bị thật, Google Play, Apple, push/provider và tải nhiều instance
vẫn nghiệm thu ở issue riêng. Restore mẫu không thay thế kiểm tra backup
production theo chính sách dữ liệu được duyệt.
