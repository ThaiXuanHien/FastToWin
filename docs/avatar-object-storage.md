# Avatar private object storage

Thiết kế đã duyệt: [avatar-object-storage-design.md](avatar-object-storage-design.md).
Bước này giữ bucket private và phục vụ ảnh qua backend; chưa bật CDN/domain hoặc bulk migration.

## Cấu hình backend

| Biến | Giá trị |
| --- | --- |
| `FASTTOWIN_AVATAR_STORAGE` | `database` (mặc định) hoặc `r2` |
| `FASTTOWIN_AVATAR_R2_ACCOUNT_ID` | Cloudflare Account ID, 32 ký tự hex |
| `FASTTOWIN_AVATAR_R2_BUCKET` | Bucket private riêng cho môi trường, 3–63 chữ thường/số/dấu gạch ngang; bắt đầu/kết thúc bằng chữ hoặc số |
| `FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID` | R2 S3 Access Key ID, 32 ký tự hex |
| `FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY` | R2 S3 Secret Access Key, 64 ký tự hex |

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
biến R2 để backend vẫn đọc metadata R2 đã lưu và chạy cleanup. Nếu bỏ credential
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
