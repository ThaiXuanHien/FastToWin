# Avatar trên object storage — thiết kế #11

Trạng thái: đề xuất kỹ thuật để chủ dự án xem trước triển khai.
Issue: https://github.com/ThaiXuanHien/FastToWin/issues/11
Ngày: 12/10/2026. Người dùng chưa có tài khoản Cloudflare hoặc tên miền.

## Mục tiêu và lựa chọn

Dùng Cloudflare R2 Standard theo phương án ưu tiên free/chi phí thấp đã chọn.
Làm hai bước để chưa phải mua domain:

1. Tích hợp bucket private qua S3 API; client tiếp tục lấy ảnh từ backend hiện
   có. Tách dữ liệu ảnh khỏi DB, bảo vệ upload và giữ ảnh cũ.
2. Khi có domain do chủ dự án kiểm soát trên Cloudflare, bật CDN riêng, cache
   ảnh theo phiên bản và nghiệm thu purge/xóa. Không dùng r2.dev production.

Giữ ảnh trong DB là phương án tạm thời khi R2 chưa bật. S3 + CloudFront cần
thêm tài khoản/cấu hình AWS; không có lợi thế rõ trong nhu cầu hiện tại.
Không thêm Workers trả phí hay tăng backend replica trong bước đầu.

R2 Standard có free tier 10 GB-month, 1 triệu Class A và 10 triệu Class B/tháng.
Đây là hạn mức bao gồm, không phải trần chi tiêu; vượt mức sẽ tính phí.
Kích hoạt R2 có bước subscription/checkout do chủ dự án thực hiện. Backend
proxy vẫn có usage/egress Railway; chưa có domain thì chưa giảm traffic qua
backend bằng CDN. Nguồn: [R2 pricing](https://developers.cloudflare.com/r2/pricing/),
[public buckets](https://developers.cloudflare.com/r2/buckets/public-buckets/).

## Hiện trạng đã kiểm tra

- `UpdateAvatar(base64Data)` đi qua WebSocket đã xác thực; payload không nhận
  player ID của người khác. Server giới hạn frame 64 KiB.
- `profiles.avatar_data` giữ chuỗi base64. `avatar_url` hiện giữ mã avatar có
  sẵn trong game, phải giữ nguyên ý nghĩa này.
- `/api/avatar/{playerId}?v=...` giải mã DB rồi trả JPEG và no-store. Shared
  client đã dùng cùng URL trên Android/iOS/Web.
- Web/iOS đã nén ảnh về khoảng 42–45 KiB; Android mới nén JPEG 80%/512px,
  chưa đảm bảo giới hạn bytes. Luồng Android cần đồng bộ giới hạn này.
- Xóa tài khoản hiện xóa `users`, cascade dữ liệu liên quan. Object storage
  cần thêm công việc xóa bền vững, tránh bỏ sót ảnh khi row DB bị xóa.

## Bước 1: bucket private và luồng upload

Cấu hình `FASTTOWIN_AVATAR_STORAGE=database|r2`, mặc định `database` để bản
chưa cấu hình vẫn chạy. Chọn r2 mà thiếu endpoint/bucket/credential thì từ
chối khởi động với lỗi không chứa secret. Không âm thầm ghi blob vào DB khi
R2 đã bật nhưng lỗi, vì như vậy ảnh có thể khác nhau giữa các lần đọc.

Backend dùng S3 client chính thức, credential nằm ở Railway secret variables;
không gửi key cho app, không nhận URL/endpoint tùy ý từ client. Endpoint R2
HTTPS theo account ID, region auto, bucket/token riêng staging và production.
Quyền token chỉ Object Read & Write cho bucket được chọn, không Account Admin.

Server kiểm tra độ dài base64 trước decode, tối đa 45 KiB sau decode, xác định
JPEG/PNG từ bytes và decoder thực; từ chối SVG, file giả ảnh hoặc ảnh lỗi.
Đọc dimensions trước cấp phát bitmap: cạnh tối đa 4096px, tối đa 8 triệu pixel.
Chuẩn hóa JPEG tối đa 512px, xóa metadata qua decode/re-encode, giữ output
không quá 45 KiB. Không tăng giới hạn frame WebSocket để chứa ảnh lớn.

Rate limit riêng trước decode/storage: player 3 lần/10 phút, IP 10 lần/10
phút, dùng limiter sẵn có; giới hạn chia sẻ nhiều instance sẽ theo #12.
Chỉ ghi avatar cho danh tính đã xác thực trên connection, kiểm tra profile còn
hoạt động trước lưu. Upload thất bại không thay avatar hiện tại.

Object key do server sinh: `avatars/<player-uuid>/<random-version>.jpg`.
Mỗi lần upload có key mới; không ghi đè key cũ, không đưa email vào key.
DB thêm metadata gồm key, content type, số byte, SHA-256, dimensions, thời
gian cập nhật. Metadata là row riêng, không đổi ý nghĩa avatar cosmetic ID.

Luồng cập nhật: ghi intent dọn object vào DB trước upload; upload thành công
mới đổi tham chiếu bằng compare-and-set trong transaction. Nếu mất mạng/lỗi
DB hoặc hai upload cạnh tranh, giữ ảnh đang dùng và dọn object không được
tham chiếu sau khoảng an toàn. Transaction cập nhật metadata cũng đưa key
cũ vào hàng đợi cleanup. Không giữ transaction DB trong lúc gọi R2.

Hàng đợi cleanup bền vững lưu key, thời điểm được xóa và retry, không cascade
mất theo user. Trước mỗi delete kiểm tra key không còn được tham chiếu. Xóa
tài khoản đưa key hiện hành vào cleanup trong cùng transaction trước cascade.
Object cũ sau thay ảnh chờ 24 giờ; object do tài khoản bị xóa được xử lý sớm.
Nếu R2 lỗi, retry có giới hạn/backoff và log chỉ mã lỗi, không credential.

## Đọc ảnh, fallback và cache

Giữ endpoint avatar hiện có để mobile cũ tiếp tục hoạt động. UUID sai trả
400, người chơi/ảnh không tồn tại trả 404; không để lỗi UUID thành lỗi DB 500.
Có metadata R2 thì đọc private object có timeout/giới hạn bytes, đối chiếu
metadata và trả đúng Content-Type với nosniff. R2 tạm lỗi trả 503; không báo
upload thành công hoặc thay ảnh bằng dữ liệu rỗng.

Chưa có metadata thì đọc `avatar_data` cũ. Không bulk migration trong bước
đầu; fallback đáp ứng chiến lược chuyển đổi không mất ảnh. Khi người dùng
đổi avatar thành công qua R2, có thể bỏ blob hiện hành sau khi metadata được
commit; backup cũ vẫn theo chính sách dữ liệu hiện có. Tắt R2 không tự lấy lại
ảnh mới đã chỉ lưu object: rollback code phải giữ khả năng đọc metadata R2.

Bước 1 giữ URL mutable no-store để không hiển thị ảnh cũ; client tăng revision
sau cập nhật thành công và reconnect/profile load, kiểm tra chính sách cache
của image loader. 404/lỗi vẫn dùng avatar mặc định hiện có. Không thay thiết
kế màn hình hoặc tên/icon avatar.

## Bước 2: CDN, nghiệm thu riêng khi có domain

Custom domain chỉ phân phối ảnh đã chuẩn hóa. Backend pointer không cache;
URL object có phiên bản dùng cache immutable, không purge khi chỉ thay avatar.
Xóa ảnh/tài khoản phải phối hợp object delete và CDN purge, có retry/bằng chứng
trước production. Cần CORS phù hợp Web và kiểm thử image loader cả ba nền tảng.
Chưa bật redirect CDN khi chưa nghiệm thu các bước này.

## Kiểm thử và triển khai

- Unit: base64 lỗi/quá lớn, MIME giả, dimensions quá lớn, chuẩn hóa và limits.
- Service/fake storage: upload lỗi, metadata lỗi, cạnh tranh update, key không
  bị overwrite, cleanup không xóa ảnh đang dùng, timeout không mất avatar cũ.
- PostgreSQL: migration additive, CAS metadata, legacy fallback, delete-account
  enqueue trước cascade và cleanup vẫn còn sau restart.
- HTTP/WebSocket: xác thực/quyền sở hữu/rate limit, 400/404/503, content type,
  cache/nosniff, payload dưới 64 KiB, reconnect.
- Shared/Android: giới hạn JPEG đồng nhất, avatar cập nhật sau reconnect.
  Áp dụng UI/UX Pro Max cho phần tương tác chọn ảnh; giữ thiết kế hiện có.
- Staging R2 thật chỉ thực hiện sau khi chủ dự án tạo/kích hoạt tài khoản và
  cung cấp credential bằng file ignored/secret store; không paste secret vào
  chat/GitHub. Test vài ảnh mẫu, outage/retry/delete, rồi mới chuyển production.

Issue #11 giữ mở đến khi có bằng chứng provider thật, CDN và thiết bị. Không
đánh dấu ảnh trên thiết bị iOS/Android thật đã đạt chỉ bằng unit/simulator.

## Các bước chỉ chủ dự án thực hiện

Wizard dự kiến 5 bước, có xác nhận riêng trước checkout:

1. Tạo Cloudflare account và xác minh email — không lưu mật khẩu tài khoản.
2. Kích hoạt R2, xem checkout/pay-as-you-go — người dùng nhập billing trực tiếp.
3. Tạo bucket Standard private riêng staging và production — lưu tên bucket.
4. Tạo token giới hạn bucket — nhập ẩn Access Key ID/Secret Access Key; lưu
   vào file ignored `.artifacts/r2-setup/credentials.env`, không GitHub secrets.
   Account ID là public, endpoint được dựng từ giá trị này.
5. Kiểm tra các giá trị đã lưu và hướng dẫn nối staging — chưa tự deploy hoặc
   chuyển production, chưa mua tên miền. Có thể thu hồi token từ Dashboard.

Wizard không tự mua gói, không mở bucket public, không in giá trị secret và
không coi thao tác confirm trong script là bằng chứng R2 hoạt động.
