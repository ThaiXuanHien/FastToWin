# Lựa chọn hạ tầng — 12/10/2026

Tài liệu để chủ dự án chọn/tìm hiểu cho #6, #11, #12 → #15. Chưa tạo tài nguyên,
đăng ký gói, mua domain hoặc thay đổi production. Giá USD dưới đây đối chiếu tài
liệu chính thức ngày 12/10/2026, chưa gồm thuế/phí thanh toán; không phải báo giá
cho tải thực tế FastToWin. Có thể chọn từng phần, không cần mua cùng lúc.

## 1. Staging riêng (#6)

**Đề xuất:** environment `staging` trên Railway hiện có, một backend + web +
PostgreSQL riêng. Ít nền tảng phải vận hành; dùng bản phát hành giống production
nhưng dữ liệu và bí mật riêng. Environment trống giúp cấu hình có chủ ý;
Duplicate Environment sao chép cả services/variables/configuration nên cần kiểm
tra mọi tham chiếu DB/secret trước khi deploy. [Railway environments](https://docs.railway.com/environments).

| Phương án | Phù hợp | Điều cần cân nhắc |
| --- | --- | --- |
| Staging chạy liên tục trên Railway | Test thường xuyên, giống production, dễ theo dõi | Trả thêm CPU/RAM/disk/network của từng service |
| Staging chỉ bật khi diễn tập | Ngân sách thấp, kiểm thử theo đợt | Cần thủ tục bật/tắt; volume/backup vẫn có thể phát sinh phí |
| Docker Compose trên máy/VPS riêng | Muốn học vận hành hoặc đã có máy | Tự chịu domain/TLS, cập nhật OS, giám sát và backup; local không thay thế nghiệm thu môi trường cloud |

Cần chốt: giới hạn chi tiêu thêm mỗi tháng; chạy liên tục hay theo đợt; khu vực
gần production; dùng domain Railway cung cấp hay subdomain riêng; ai truy cập
staging. Chưa cần mua domain để bắt đầu. Railway Hobby có mức thuê bao **$5/tháng
kèm $5 usage credit**, không có nghĩa toàn bộ production + staging chỉ $5.
Chi phí tài nguyên vượt credit tính thêm. [Giá Railway](https://docs.railway.com/pricing/plans).

Chuẩn bị và nghiệm thu:

1. Database/volume, Owner test, khóa JWT/CSRF, cookie/origin và cấu hình client
   trỏ riêng. Dữ liệu mẫu; email dùng sink, push tắt, provider sandbox hoặc tắt.
2. Backup có checksum và chính sách lưu/xóa; restore **thực sự** vào DB cách ly,
   chạy app kiểm tra schema/tài khoản/ví, không chỉ đọc danh mục dump.
3. Diễn tập planned/emergency maintenance, drain/reconnect và khôi phục ACTIVE.
4. Deploy revision mới rồi rollback backend/web đồng bộ; xác minh tương thích
   schema trước rollback, đo thời gian phục hồi và lượng dữ liệu có thể mất.
5. Lưu bằng chứng/log không chứa bí mật, cấu hình cảnh báo và theo dõi chi tiêu.

Ngân sách khởi điểm **để bạn lựa chọn**, không phải giá cam kết: đặt trần thêm
$10, $20 hoặc $30/tháng rồi đo RAM/CPU/disk thực tế trước khi bật liên tục. Nếu
không đủ cho cấu hình tối thiểu, dùng staging theo đợt hoặc điều chỉnh trần.

## 2. Avatar object storage/CDN (#11)

**Đề xuất:** Cloudflare R2 Standard. Backend kiểm tra và chuẩn hóa ảnh; ghi object
với tên phiên bản ngẫu nhiên, DB giữ metadata/key. Upload cần xác thực và hạn
mức; chỉ đường đọc avatar đã chuẩn hóa được công khai. Tách bucket/prefix test
và production, token giới hạn quyền; không chuyển cả database lên bucket.

| Phương án | Điểm mạnh | Điều cần chuẩn bị |
| --- | --- | --- |
| Cloudflare R2 | Phù hợp avatar nhỏ, egress R2 ra Internet miễn phí | Cloudflare/R2, billing theo tài khoản, bucket/token, domain cho CDN production |
| Amazon S3 + CloudFront | Hợp nếu đã dùng AWS, nhiều tùy chọn vùng/quyền | AWS/IAM, vùng, CloudFront, theo dõi nhiều loại phí; cần tính giá theo tải cụ thể |
| Giữ backend hiện tại tạm thời | Không thêm dịch vụ ngay | Chưa giải quyết tải đọc ảnh/DB; #11 tiếp tục mở |

R2 Standard có free tier hàng tháng **10 GB-month**, **1 triệu Class A** và
**10 triệu Class B**. Phần vượt: lưu trữ **$0.015/GB-month**, Class A
**$4.50/triệu**, Class B **$0.36/triệu**; egress trực tiếp từ R2 miễn phí.
Các dịch vụ nối thêm có thể tính phí riêng; nhiều lượt đọc vẫn có thể làm tăng
chi phí dù dung lượng nhỏ. [Giá R2](https://developers.cloudflare.com/r2/pricing/).

CDN/cache qua R2 custom domain cần domain bạn kiểm soát trên Cloudflare.
`r2.dev` bị giới hạn và dành cho phát triển, không dùng làm URL production.
Nếu chưa có domain, có thể chọn backend đọc private bucket trước; muốn CDN
production thì bổ sung domain hoặc chọn phương án CDN khác.
[Public buckets/custom domain](https://developers.cloudflare.com/r2/buckets/public-buckets/).

Nghiệm thu gồm ảnh lỗi/kích thước/MIME, quyền ghi, cache phiên bản, thay/xóa avatar,
migration ảnh cũ có thể tiếp tục phục vụ, retry và fallback khi storage lỗi.
Cần bạn chọn dịch vụ, cho biết đã có domain chưa và mức ngân sách lưu trữ/traffic.

## 3. Redis và nhiều backend (#12 → #15)

**Đề xuất:** Redis cùng Railway trước, production vẫn một backend trong giai
đoạn chuyển đổi. Dùng shared presence/rate limit có TTL và thao tác nguyên tử;
sau đó mới thiết kế owner của room/match, định tuyến và phục hồi WebSocket.
Sticky session đơn lẻ không giải quyết trạng thái trận nằm trong RAM.

| Phương án | Khi chọn | Điều phải kiểm tra |
| --- | --- | --- |
| Redis trên Railway | Muốn giữ cùng nền tảng, kết nối nội bộ | Resource/volume, cấu hình riêng tư, memory/eviction, backup và phục hồi; template không tự chứng minh HA |
| Upstash Redis | Muốn Redis được quản lý, mô hình tính phí theo lệnh | Vùng gần backend, độ trễ/kết nối TCP/TLS, Lua/atomic operation thực dùng, rate limit/command budget |

Railway có hướng dẫn dựng Redis và các biến kết nối; chi phí phụ thuộc tài
nguyên sử dụng. [Redis Railway](https://docs.railway.com/databases/redis).
Upstash Free hiện có **250 MB và 500.000 lệnh/tháng**; Pay as You Go
**$0.20/100.000 lệnh**, kèm quy tắc storage/bandwidth. Free không đủ để suy ra
chi phí presence nhiều người; chạm budget cap có thể bị rate limit.
[Giá/giới hạn Upstash](https://upstash.com/pricing/redis).

Ví dụ tính riêng heartbeat: 100 kết nối × 2 lệnh/phút × 43.200 phút ≈
8,64 triệu lệnh/tháng nếu luôn online. Đây chỉ là giả định minh họa, chưa gồm
rate limit, matchmaking, retry hoặc pub/sub; cần đo trước khi chọn gói.

Trước khi tăng replica phải có: ownership/lease và fencing tránh hai instance
cùng xử lý trận; reconnect đến đúng owner; pub/sub/replay thích hợp; ghi kết quả
và ví một lần; trạng thái khi Redis lỗi; drain khi deploy; kiểm thử hai instance,
owner chết, Redis restart/network partition và giới hạn tải trên staging.
Nguồn activity mới cũng cần hợp đồng coverage toàn cụm trước khi scale.

Cần bạn chọn Railway Redis hay Upstash, trần chi tiêu, vùng và mục tiêu tải ban
đầu. Chưa cần tăng số backend ngay. Thứ tự đề xuất: **staging → avatar → Redis →
hai backend trên staging → production sau khi đạt nghiệm thu**.
