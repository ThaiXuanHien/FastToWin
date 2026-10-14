# Avatar private object storage

Thiết kế đã duyệt: [avatar-object-storage-design.md](avatar-object-storage-design.md).
Bước này giữ bucket private và phục vụ ảnh qua backend; chưa bật CDN/domain hoặc bulk migration.

## Kích hoạt Cloudflare R2 bằng người vận hành

Chủ dự án đã duyệt năm giai đoạn wizard ngày 12/10/2026. Chưa có account
Cloudflare/domain; mã nguồn và runbook không có nghĩa là provider đã được bật.
Chạy từ Git Bash trong checkout local riêng tư:

```bash
bash scripts/setup-r2.sh
```

Wizard chỉ hướng dẫn trình duyệt, nhận dữ liệu người vận hành nhập và ghi file
ignored `.artifacts/r2-setup/credentials.env`. Nó không gọi Cloudflare API,
không tải secret lên CI/Railway và không bật runtime hay deploy. Không dùng
`bash -x`, `source`, `cat`, ghi transcript/terminal hoặc chụp màn hình secret.
Dừng bằng Ctrl-C; chạy lại giữ giá trị đã ghi nếu nhấn Enter. Chạy từ terminal
tương tác; `ENV_FILE`/`TMPDIR` kế thừa bị bỏ qua. Nếu lần chạy bị kill cứng,
kiểm tra không còn wizard chạy trước khi xóa thư mục rỗng
`.artifacts/r2-setup/.lock`. Không xóa file credentials để xử lý lock.

Đầu ra có marker riêng và chỉ chấp nhận các biến wizard biết; file env khác
sẽ bị từ chối. Mọi ancestor/file symlink hoặc Windows reparse point đều bị
chặn. `umask 077`, thư mục 700/file 600 áp dụng trên POSIX. Trên Windows Git
Bash, `chmod` không đủ bảo vệ NTFS: wizard dùng Windows PowerShell để đặt và
kiểm tra ACL owner-only cho thư mục riêng/file, bao gồm quyền kế thừa cho file
tạm của `write_env`. Nó dừng nếu không làm được; dùng checkout local NTFS do
người chạy sở hữu, không dùng share/sync folder hoặc filesystem bỏ qua quyền.
ACL không bảo vệ khỏi administrator, malware hay tiến trình cùng người dùng.
Không thay quyền các thư mục project/`.artifacts` dùng chung.

Năm giai đoạn đã duyệt:

1. [Sign up](https://dash.cloudflare.com/sign-up): **Email**, **Password** →
   **Create Account**, mở email xác minh. Kiểm tra **My Profile → Email Address**
   có `(verified)`; gửi lại bằng **Send verification email** nếu cần. Không nhập
   mật khẩu Cloudflare vào terminal. [Hướng dẫn account](https://developers.cloudflare.com/fundamentals/account/create-account/),
   [xác minh email](https://developers.cloudflare.com/fundamentals/user-profiles/verify-email-address/).
2. **Storage & databases → R2 → Overview**: wizard hỏi xác nhận hiểu billing
   trước khi người vận hành hoàn tất checkout. Đọc tên nút trên màn hình thực tế;
   tài liệu không cam kết tên nút cuối. Chỉ nhập payment/3D Secure trong browser.
   Nếu cần: **Billing → Subscriptions → Payment methods → Add Payment Method**.
   [R2 get started](https://developers.cloudflare.com/r2/get-started/),
   [billing profile](https://developers.cloudflare.com/billing/get-started/create-billing-profile/).
3. **R2 → Create bucket** hai lần: bucket staging và production khác nhau,
   **Standard**, location tự động (**Automatic/None**), jurisdiction mặc định.
   Giữ **Public Development URL** tắt và không custom domain. Lấy **Account ID**
   tại **R2 Overview → Account Details**. Tên bucket 3–63 ký tự `a-z`, `0-9`, `-`,
   bắt đầu/kết thúc bằng chữ hoặc số. [Tạo bucket](https://developers.cloudflare.com/r2/buckets/create-buckets/),
   [S3 setup](https://developers.cloudflare.com/r2/get-started/s3/).
4. **R2 → Overview → Account Details → API Tokens → Manage**: tạo một token
   riêng cho mỗi bucket; ưu tiên **Create Account API token**, hoặc **Create
   User API token**. Chọn **Object Read & Write → Apply to specific buckets only**,
   chỉ tick bucket môi trường tương ứng. Không chọn Admin/all buckets. Wizard
   nhận token name, loại `account|user`, expiry UTC hoặc `none`, rồi nhận cả
   **Access Key ID** và **Secret Access Key** bằng input ẩn. Secret xuất hiện một
   lần; lưu thêm trong secret manager. Đây là S3 keys, không phải API token value.
   Token name dùng bộ ký tự an toàn riêng của wizard, không phải tuyên bố giới
   hạn Cloudflare. Keys được xem như dữ liệu opaque một dòng, không rỗng, tối đa
   4096 ký tự hiển thị; không suy diễn định dạng provider. Backend hiện kiểm tra
   định dạng riêng như bảng runtime phía trên, vì vậy xác minh startup staging
   trước khi sử dụng. Tài liệu chưa xác nhận nhãn widget expiry R2; xem control
   thực tế, ghi đúng thời điểm UTC và lập lịch xoay key trước hạn. User token phụ
   thuộc quyền/người dùng còn trong account. [S3 credential journey](https://developers.cloudflare.com/r2/get-started/s3/),
   [token lifecycle](https://developers.cloudflare.com/r2/api/tokens/),
   [expiry](https://developers.cloudflare.com/fundamentals/api/how-to/restrict-tokens/).
5. Xem tên biến đã ghi, ánh xạ staging bên dưới, rồi thu xếp smoke có ủy quyền.
   Production token mới chỉ được chuẩn bị, không được wizard kích hoạt.

### Chi phí và budget alert

Standard có phần miễn phí **chung toàn account mỗi tháng**: 10 GB-month,
1 triệu Class A và 10 triệu Class B. Hai bucket không nhân đôi allowance.
Vượt phần bao gồm tính pay-as-you-go; R2 egress trực tiếp miễn phí nhưng backend
proxy vẫn phát sinh usage/egress Railway. Không chọn Infrequent Access cho quy
trình này. Xem [giá hiện hành](https://developers.cloudflare.com/r2/pricing/)
và [storage classes](https://developers.cloudflare.com/r2/buckets/storage-classes/).

Wizard cho chọn mở hướng dẫn alert: **Manage Account → Billing → Billable Usage
→ Create budget alert**; điền **Alert name**, **Description** tùy chọn, **Budget
threshold (USD)**, **Email recipients** → **Save**. Alert theo usage spend toàn
account, gửi email khi vượt ngưỡng; **không dừng usage hoặc đặt spending cap**.
Có thể chủ động bỏ qua; wizard không tự thay billing. Không hứa tự dừng ở free
tier. [Budget alerts](https://developers.cloudflare.com/billing/manage/budget-alerts/),
[billing lifecycle](https://developers.cloudflare.com/billing/understand/how-billing-works/).

### Ánh xạ đầu ra theo môi trường

File không phải script shell; **không source** nó. Chuyển từng giá trị bằng
secret manager vào môi trường tương ứng, không sao chép cả file vào log/chat.
Thay `<ENV>` bằng `STAGING` hoặc `PRODUCTION`:

| Biến trong credentials.env | Runtime của đúng môi trường |
| --- | --- |
| `<ENV>_R2_ACCOUNT_ID` | `FASTTOWIN_AVATAR_R2_ACCOUNT_ID` |
| `<ENV>_R2_BUCKET` | `FASTTOWIN_AVATAR_R2_BUCKET` |
| `<ENV>_R2_ACCESS_KEY_ID` | `FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID`, hoặc file một giá trị qua `FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID_FILE` |
| `<ENV>_R2_SECRET_ACCESS_KEY` | `FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY`, hoặc file một giá trị qua `FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY_FILE` |
| `<ENV>_R2_TOKEN_NAME`, `<ENV>_R2_TOKEN_TYPE`, `<ENV>_R2_TOKEN_EXPIRY` | Metadata vận hành/rotation, không có biến runtime tương ứng |

Hai environment có Account ID giống nhau trong wizard này, bucket/key pair riêng.
Không map staging keys vào production. `*_FILE` trỏ đến file UTF-8 chỉ chứa một
key value; không trỏ vào `credentials.env`. Mỗi credential chọn một nguồn trực
tiếp hoặc file, không đặt cả hai. Endpoint private HTTPS được backend dựng từ
Account ID, region `auto`; chưa cần domain, DNS hay public access. Không có CI
secret mới cần tạo.

### Checklist nghiệm thu và trạng thái #11

Đã hoàn thành ở mã nguồn: backend private R2/cấu hình và cơ chế cleanup, cập nhật
client và wizard/runbook có thể review. Controller xác nhận tại `65dafbc`:
server 713 tests/111 suites và client 274 tests/46 suites đều không failure/error/skip;
hai ca UI trì hoãn tải ảnh phone/desktop đạt 2/2, ảnh chụp đã được xem. Đây là kiểm
thử local/PostgreSQL và browser; chưa chứng minh provider R2 thật hoặc thiết bị
Android/iOS thật hoạt động. Controller cập nhật trạng thái GitHub
sau review; issue #11 vẫn mở, PR dùng `Refs #11`, không dùng `Closes #11`.

Bổ sung final-fix ngày 14/10/2026: editor chờ upload avatar thành công rồi mới
lưu tên, chỉ báo hoàn tất sau cả hai bước; lỗi upload/rate-limit giữ qua refresh
và reconnect để retry, kể cả khi các phản hồi friends/leaderboard xóa lỗi chung.
Cleanup claim từng object ngay trước delete; fixture ARGB và metadata PNG/JPEG
kiểm tra nền trắng/loại metadata. Backend **716 tests/111 suites**, client mới nhất
**285 tests/46 suites**, không failure/error/skip; Android compile, server
installDist và Wasm webpack đều thành công. Browser phone/desktop đạt **2/2**
(57,4 giây): rate-limit player thật, lỗi và draft/preview còn sau reconnect,
Save cho phép retry, Save bị khóa trong 2,5 giây decode và không tràn ngang.
Controller đã xem ảnh chụp retry sau reconnect ở cả hai kích thước; đây vẫn là
kiểm thử local, không thay thế nghiệm thu R2 thật hoặc thiết bị Android/iOS.

Bổ sung N1 sau review ngày 14/10/2026: snapshot gián tiếp từ thưởng, tài sản,
giải đấu và bang hội dùng refresh nền, không xác nhận thao tác lưu đang chờ.
GetProfile và phản hồi ghi profile/avatar/cosmetics/push preferences vẫn là
foreground. Thông báo nhận thưởng giữ editor và ảnh đã chọn; chỉ thông báo lưu
hồ sơ thành công mới đóng editor. Fixture screenshot dùng callback ba tham số.
Lượt kiểm tra mới: **716 backend + 287 client host**, **145 screenshot**, đều
không failure/error/skip; Android/Web build thành công. **4/4 ca browser** ở
kích thước phone/desktop đạt, gồm hai ca thông báo daily/mission giữ draft rồi
lưu thành công và hai ca upload/rate-limit/reconnect; ảnh chụp đã được xem.
Test browser thông báo thưởng dùng frame test qua WebSocket local; kiểm thử
engine xác nhận cờ refresh từ các handler thật. Chưa nghiệm thu provider/device.

- [ ] Chủ dự án thực hiện signup/verify, checkout và quyết định budget alert.
- [ ] Tạo/kiểm tra hai bucket Standard private và token scope/expiry; giữ secret an toàn.
- [ ] Có ủy quyền staging smoke; đặt các biến staging và PostgreSQL, chỉ sau đó
  bật `FASTTOWIN_AVATAR_STORAGE=r2` trên staging.
- [ ] Smoke R2 thật: upload JPEG/PNG, đọc lại, thay ảnh, upload lỗi vẫn giữ ảnh cũ;
  legacy còn đọc; restart/account deletion/cleanup 24 giờ và không xóa key còn
  tham chiếu. Thu bằng chứng không chứa secret, base64 hoặc blob.
- [ ] Kiểm tra device Android/iOS/Web và rollback giữ R2 read credentials như mục trên.
- [ ] Production activation/deployment cần quyết định vận hành riêng sau nghiệm thu.
- [ ] Domain/CDN/purge là giai đoạn sau: domain do chủ dự án kiểm soát, zone cùng
  account R2; kiểm tra cache theo version, purge/xóa trước public rollout.
  Không dùng `r2.dev` production. [Public buckets](https://developers.cloudflare.com/r2/buckets/public-buckets/).

## Cấu hình backend

| Biến | Giá trị |
| --- | --- |
| `FASTTOWIN_AVATAR_STORAGE` | `database` (mặc định) hoặc `r2` |
| `FASTTOWIN_AVATAR_R2_ACCOUNT_ID` | Cloudflare Account ID, 32 ký tự hex |
| `FASTTOWIN_AVATAR_R2_BUCKET` | Bucket private riêng cho môi trường, 3–63 chữ thường/số/dấu gạch ngang; bắt đầu/kết thúc bằng chữ hoặc số |
| `FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID` | R2 S3 Access Key ID, 32 ký tự hex |
| `FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY` | R2 S3 Secret Access Key, 64 ký tự hex |
| `FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID_FILE` | Đường dẫn file UTF-8 chỉ chứa Access Key ID, thay cho biến trực tiếp tương ứng |
| `FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY_FILE` | Đường dẫn file UTF-8 chỉ chứa Secret Access Key, thay cho biến trực tiếp tương ứng |

Mỗi credential chọn một nguồn: biến trực tiếp hoặc `*_FILE`. Có thể dùng cả
hai file hoặc phối hợp một credential trực tiếp và một credential từ file.
Không đặt đồng thời biến trực tiếp và `*_FILE` của cùng credential; backend
từ chối cấu hình xung đột. File không đọc được, trống hoặc sai định dạng cũng
làm startup thất bại; thông báo không chứa đường dẫn, nội dung hay nested cause.
Dòng mới CR/LF cuối giá trị được bỏ theo helper secret hiện có; không thêm
khoảng trắng vào credential.

Ví dụ chỉ dùng file mount cho hai credential:

```text
FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID_FILE=/run/secrets/avatar-r2-access-key-id
FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY_FILE=/run/secrets/avatar-r2-secret-access-key
```

Mỗi file trên chỉ chứa giá trị của một credential, không phải toàn bộ file
`credentials.env` nhiều biến do wizard chuẩn bị.

R2 cần PostgreSQL để lưu metadata và cleanup bền vững. Cấu hình thiếu/sai làm
startup thất bại bằng thông báo chỉ nêu tên biến, không in giá trị. Endpoint
được dựng cố định thành `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`, region
`auto`; client không gửi endpoint, object key hay credential.

Lưu credential trong Railway secret variables hoặc file ignored do wizard
chuẩn bị, không commit/paste vào chat. Token chỉ cấp Object Read & Write cho
bucket được chọn; không cần quyền Account Admin hoặc bucket public. Chỉ bật
staging sau khi chủ dự án đã tạo account/bucket/token và cho phép smoke test.

Backend dùng AWS SDK S3 và URL Connection client cùng phiên bản 2.55.14.
Path style và tắt chunked encoding theo
[ví dụ Java của Cloudflare](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/).
Checksum SDK chỉ dùng khi API yêu cầu theo
[cấu hình S3-compatible của AWS](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/s3-checksums.html);
backend vẫn đối chiếu SHA-256 với metadata cho mọi object được đọc.
Timeout: connect 2 giây, socket 5 giây, mỗi attempt 8 giây, toàn API call
15 giây; đọc object tối đa 45 KiB, kể cả response chunked/không có độ dài.
Tên bucket theo [quy tắc R2](https://developers.cloudflare.com/r2/buckets/create-buckets/).

## Upload, đọc và rollback

Upload mới qua `UpdateAvatar(base64Data)` dùng player trên phiên WebSocket
đã xác thực. Frame vẫn tối đa 64 KiB. Limiter hiện có chặn trước decode/storage:
player 3 lần/10 phút, IP 10 lần/10 phút; đây là token bucket trong mỗi instance,
chưa phải distributed limiter (#12).

JPEG/PNG thật mới được nhận; data URI phải khớp loại file. Giới hạn input
45 KiB, cạnh 4096px, 8 triệu pixel được kiểm tra trước khi cấp phát raster.
Decode/re-encode xóa metadata, nền alpha trắng, đầu ra JPEG tối đa 512px và
45 KiB. File lỗi/SVG/giả MIME không thay ảnh hiện tại.

`GET /api/avatar/{playerId}?v=...` giữ URL mutable: UUID sai trả 400,
người chơi/ảnh chưa có trả 404, lỗi database/provider hoặc integrity trả 503.
Ảnh thành công là `image/jpeg`; mọi response của route có
`Cache-Control: no-store, max-age=0` và `X-Content-Type-Options: nosniff`.

Không có metadata R2 thì đọc `profiles.avatar_data`. Riêng legacy input cho
phép tối đa 64 KiB decoded để giữ ảnh từ frame cũ có thể lớn hơn upload cap
45 KiB; dimensions và output vẫn chịu đúng giới hạn trên. Không sửa blob cũ
khi đọc. Ảnh legacy vượt 64 KiB hoặc không hợp lệ trả 503 để không phục vụ
dữ liệu không an toàn.

R2 bật mà upload lỗi sẽ trả thất bại, không tự ghi fallback DB. Khi metadata
commit thành công mới xóa blob legacy hiện hành. `avatar_url` vẫn là cosmetic ID.

Đổi `FASTTOWIN_AVATAR_STORAGE=database` chỉ đổi nơi ghi ảnh mới. Giữ cả bốn
giá trị R2 (Account ID/bucket và hai credential trực tiếp hoặc qua `*_FILE`)
để backend vẫn đọc metadata R2 đã lưu và chạy cleanup. Nếu bỏ credential
khi còn metadata thì ảnh R2 trả 503; không tự quay về blob cũ. Rollback binary
phải giữ service đọc metadata R2 và migration additive V61.

## Cleanup và khôi phục

Mỗi upload có key ngẫu nhiên `avatars/<player-uuid>/<random-version>.jpg`.
Transaction ghi intent trước PUT; intent có hạn 1 giờ. Transaction CAS sau
PUT khóa player/profile và intent, kiểm tra revision cùng intent còn
`pending` và chưa hết hạn. CAS thua/lỗi hoặc account đã xóa không công bố key.

Transaction thay ảnh đưa key cũ vào cleanup chờ 24 giờ. Trigger
`users_enqueue_avatar_before_delete` chạy BEFORE DELETE, enqueue key hiện
hành trong cùng transaction trước cascade. Cleanup không có FK tới user nên
còn sau account deletion/restart.

Worker chạy mỗi phút, batch 25, dùng `FOR UPDATE SKIP LOCKED` để claim
`pending -> deleting` và kiểm tra không còn tham chiếu active. CAS không thể
promote intent đã claim/cleaned. Lease delete 1 phút, retry exponential
60 giây đến tối đa 1 giờ, tối đa 8 lần; lỗi DB khi ghi retry vẫn giữ lease/work.
Sau retry cap hoặc crash ở claim cuối, row chuyển `failed` để xử lý thủ công;
delete thành công giữ row `deleted` và không được retry tự động.

Kiểm tra bằng tài khoản DB vận hành có quyền phù hợp:

```sql
SELECT object_key, state, attempts, available_at, last_error_code
FROM avatar_cleanup
WHERE state = 'failed'
ORDER BY updated_at;
```

Sau khi đã sửa nguyên nhân, cho một key cụ thể chạy lại bằng query có tham
số; không dựng SQL bằng nội suy key:

```sql
UPDATE avatar_cleanup
SET state = 'deleting', attempts = 0, available_at = NOW(),
    last_error_code = NULL, updated_at = NOW()
WHERE object_key = ? AND state = 'failed';
```

Giữ state `deleting` để intent không thể được promote lại. Worker vẫn kiểm
tra tham chiếu trước delete. Log chỉ thông báo lỗi cố định; không in exception
provider/SQL, credential hoặc base64/blob.

## Bằng chứng kiểm thử

Unit dùng ảnh ImageIO thật. PostgreSQL integration dùng localhost
`127.0.0.1:15432` và fail nếu thiếu cấu hình; không dùng cloud DB. SDK test chỉ
gọi HTTP server localhost với credential giả, gồm wire body PUT không chunked,
read limit/Content-Type/404/503. Đây chưa là bằng chứng provider R2 thật.

Smoke staging R2 thật, CDN/purge và thiết bị Android/iOS vẫn là các cổng nghiệm
thu riêng; issue #11 còn mở đến khi có đủ bằng chứng.
