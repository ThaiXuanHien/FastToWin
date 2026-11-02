# Redis production — lựa chọn và dự trù, 02/11/2026

**Đề xuất:** chuẩn bị Redis một node trên Railway, cùng project/environment và
vùng với backend; dự trù usage Redis tăng thêm **$5/tháng**, mức cần xem xét lại
**$10/tháng**. Đây là ngân sách đề xuất để owner duyệt, chưa tạo tài nguyên hay
đổi cấu hình production. Chỉ chuyển production sau đợt nghiệm thu riêng.

**Quyết định owner ngày 02/11/2026:** đã đồng ý Railway một node, mạng private
WireGuard và dự trù Redis tăng thêm $5/tháng để chuẩn bị đợt nghiệm thu cloud.
Chưa cho phép tạo dịch vụ, bật staging hoặc đổi production. Xem
[runbook đợt nghiệm thu](superpowers/plans/2026-11-02-redis-cloud-acceptance.md).

Ưu tiên này theo yêu cầu free/chi phí thấp nhất đáp ứng nhu cầu trong
[lựa chọn hạ tầng trước](infrastructure-options-2026-10-12.md). Tài liệu cũ ghi
Hobby đã trả phí và staging project riêng, thường tắt; đó là lịch sử, chưa xác
minh tài khoản hiện tại. Chưa biết vùng backend, usage thực hay ngân sách owner.
Giá dưới đây là USD đối chiếu nguồn chính thức ngày 02/11/2026, chưa thuế/phí;
phép tính minh họa, không phải báo giá hay số đo sức chứa FastToWin.

## 1. Những gì đã và chưa được nghiệm thu

Theo [runbook local](redis-rate-limit.md) và
[thiết kế đã duyệt](superpowers/specs/2026-11-02-redis-rate-limit-local-design.md),
local đã có 18 bucket chia sẻ, Lua EVAL dùng Redis TIME, timeout lệnh 200 ms,
startup/connect 2 giây, tối đa 128 thao tác đang chờ mỗi backend; hủy được,
không replay và không tự fallback RAM. Fixture Redis 8.10.3 local đã được kiểm
chứng; `rediss://` có hỗ trợ nhưng chưa chứng minh TLS/provider production.

Memory vẫn mặc định. Presence chưa chuyển; #12 còn mở. Phòng/trận vẫn nằm ở
một backend, #15 chưa nghiệm thu ownership/reconnect/settlement nhiều instance.
**Production tiếp tục một backend.** Hai backend trong checklist dưới chỉ là
ứng dụng test cách ly để xác nhận quota chung, không phải rollout game phân tán.

## 2. Hai phương án cần cân nhắc

| Phương án | Lợi ích với dự án | Đánh đổi/quyết định |
| --- | --- | --- |
| Railway Redis một node — ưu tiên ban đầu | Vận hành chung nền tảng, đường private gần backend, không tính theo số lệnh Lua | Chấp nhận Redis là điểm lỗi đơn; tự xác nhận cấu hình, persistence, cảnh báo và restore |
| Upstash TCP/TLS — phương án thay thế | Dịch vụ quản lý, TLS sẵn, PAYG phù hợp lượng dùng rất thấp; Fixed dễ dự trù | Đường mạng ngoài Railway, tính cả lệnh bên trong Lua; cần nghiệm thu semantics trên provider |

### Railway: mạng, quản lý và HA

Template Redis hiện mặc định **một node**, private; có tùy chọn chuyển sang
HA bằng Sentinel/HAProxy. Không coi template mặc định là HA.
[Railway Redis](https://docs.railway.com/databases/redis).
HA tối thiểu gồm ba data node có Sentinel và proxy; failover tự động, chưa có
uptime SLA công bố. Chi phí bảng dưới chỉ cho một node.
[Redis HA](https://docs.railway.com/databases/redis-ha).

Railway quản lý platform/image patches; owner bật backup/HA và quản lý dữ liệu.
Đổi image/start command/biến template hoặc sửa config trong container có thể đưa
database ra ngoài phạm vi hỗ trợ template. Vì vậy cấu hình tùy chỉnh cho
`noeviction`/AOF phải có người chịu trách nhiệm vận hành, không gắn nhãn
“managed HA” cho phương án một node đã tùy chỉnh.
[Trách nhiệm và support scope](https://docs.railway.com/databases).

Private networking cùng environment dùng WireGuard mã hóa giữa service và DNS
`*.railway.internal`. Đề xuất không mở TCP proxy public cho Redis.
[Private networking](https://docs.railway.com/networking/private-networking).
`redis://` qua WireGuard **không phải TLS Redis**: nếu owner chấp nhận mã hóa ở
lớp mạng thì ghi rõ quyết định này. Nếu bắt buộc TLS đến Redis, phải bổ sung
endpoint `rediss://` có chứng chỉ/hostname hợp lệ và nghiệm thu riêng; không
chỉ đổi scheme. Chưa có bằng chứng TLS thực tế cho cả hai phương án.

### Upstash: những điểm đã đối chiếu tài liệu

- Có native Redis TCP, không bắt buộc REST; tài liệu có ví dụ Java qua TLS.
  Đây là cơ sở để thử Lettuce hiện tại, chưa phải chứng nhận tích hợp.
  [Compatibility](https://upstash.com/docs/redis/overall/compatibility),
  [kết nối client](https://upstash.com/docs/redis/howto/connect-client).
- EVAL nguyên tử; `redis.call('TIME')` trong Lua được tài liệu xác nhận cả TCP.
  Script hiện truyền đúng một key trong KEYS; không đổi thuật toán hoặc sang SDK
  rate-limit khác để chọn provider.
  [EVAL, time và reply conversion](https://upstash.com/docs/redis/commands/scripting/eval).
- TLS luôn bật; ACL có ở paid tier. Dùng `rediss://` và credential riêng,
  xác minh peer theo JVM trust store; không bật trust-all/tắt kiểm tra hostname.
  [Security](https://upstash.com/docs/redis/features/security).
- Eviction mặc định tắt: đầy dung lượng thì từ chối ghi. Phải giữ **tắt eviction**;
  không bật chế độ cache tự xóa bucket còn TTL.
  [Eviction](https://upstash.com/docs/redis/features/eviction).
- Tài liệu consistency mô tả eventual consistency và causal consistency theo
  một kết nối; strong mode cũ đã bỏ. EVAL nguyên tử không tự chứng minh quota
  tuyệt đối qua failover/partition giữa nhiều client. Chỉ dùng một primary region,
  không thêm read region; cần kiểm chứng concurrent consume và chốt mức chấp
  nhận mất quota khi failover trước chọn production.
  [Consistency](https://upstash.com/docs/redis/features/consistency).

Throughput công bố cho Fixed nhỏ là 10.000 command/s, request tối đa 10 MB.
Free hiện có chỗ ghi 250 MB và chỗ ghi 256 MB trên cùng bảng giá; dự trù thận
trọng 250 MB, 500.000 command/tháng.
[Giá và giới hạn Upstash](https://upstash.com/pricing/redis).
Giới hạn kết nối, Lua timeout/credits phải kiểm tra ở database được chọn;
API có các trường này, không lấy giá trị response ví dụ làm quota cam kết.
[Schema thông tin database](https://upstash.com/docs/devops/developer-api/redis/enable_tls).

## 3. Chi phí tăng thêm

### Railway: trả theo tài nguyên, không phải số lần consume

Container: RAM $10/GB-tháng, CPU $20/vCPU-tháng, volume $0.15/GB-tháng,
egress $0.05/GB. Đây không phải giá Sandboxes/VMs $50/$50.
[Đơn giá Railway](https://docs.railway.com/pricing/plans).
Các dòng sau tự tính cho tháng quy ước 30 ngày, Redis luôn chạy; GB theo đơn vị
billing. RAM/CPU là **trung bình đo được giả định**, không phải giới hạn cấp phát.

| Giả định Redis riêng | Tính usage/tháng | Ước tính |
| --- | --- | ---: |
| RAM 0,10 GB; CPU 0,01 vCPU; volume dùng 1 GB; backup tăng thêm 1 GB; private traffic | 0,10×10 + 0,01×20 + 2×0,15 | $1,50 |
| RAM 0,25 GB; CPU 0,02 vCPU; volume dùng 1 GB; backup tăng thêm 1 GB | 0,25×10 + 0,02×20 + 2×0,15 | $3,20 |
| RAM 0,50 GB; CPU 0,05 vCPU; volume dùng 2 GB; backup tăng thêm 2 GB | 0,50×10 + 0,05×20 + 4×0,15 | $6,60 |

Các giả định volume là **dung lượng tính phí**, cần chọn capacity đủ lớn hơn để
chừa chỗ rewrite/backup. Railway tính volume theo phần dùng và backup theo phần
incremental; backup có giới hạn, phải đo thực tế.
[Volumes](https://docs.railway.com/volumes/reference),
[backup billing](https://docs.railway.com/volumes/backups).

Hobby $5 gồm $5 usage, Pro $20 gồm $20 usage; credit tính toàn workspace,
reset mỗi kỳ. Với usage hiện tại U và Redis R, tiền tăng thêm xấp xỉ
`max(plan, U+R) - max(plan, U)`, không mặc định cộng thêm một gói Hobby.
Ví dụ minh họa U=$11,90 và R=$3,20: Hobby tổng $15,10, tăng $3,20.
[Included usage](https://railway.com/pricing).

**Đề xuất owner duyệt thêm $5/tháng**, xem lại khi dự báo vượt $5 và xin nâng
trước $10. Đây là trần dự trù vận hành, chưa cấu hình hard cap. Staging theo đợt
tính riêng thời gian compute, volume/backup còn tồn tại vẫn có chi phí.
Ưu tiên soft alert theo **tổng workspace**; hard Compute limit có thể tắt toàn bộ
workload, không chỉ Redis. Không đặt workspace hard cap $5/$10 theo bảng này.
[Cost control](https://docs.railway.com/pricing/cost-control).

### Upstash: phải tính cả lệnh trong Lua

PAYG $0,20/100.000 command; storage vượt 1 GB $0,25/GB-tháng; bandwidth miễn
200 GB/tháng, sau đó $0,03/GB. Fixed 250 MB $10/tháng, 50 GB bandwidth,
không phí theo số command. Prod Pack thêm $200/database-tháng cho SLA/multi-zone
HA và các tính năng khác; bảng dự trù không gồm gói này.
[Giá hiện hành](https://upstash.com/pricing/redis).

Upstash đếm EVAL và lệnh chạy bên trong; không lấy “một round trip” thành một
đơn vị tính phí. [Ví dụ tính Lua chính thức](https://upstash.com/docs/redis/sdks/ratelimit-ts/costs).
Đối chiếu [script hiện tại](../server/src/main/resources/redis/rate_limit.lua):
key mới thường **5** đơn vị (EVAL, TYPE, TIME, HSET, PEXPIRE), key hash đã có
**7** (thêm HLEN, HMGET), kể cả lần hết token vẫn ghi/TTL. Đây là suy luận
đếm lệnh cần đối soát với usage provider, chưa phải hóa đơn đã nghiệm thu.

| Lưu lượng giả định, key đã có | Command/tháng | PAYG riêng command |
| --- | ---: | ---: |
| 100.000 lần consume/tháng | 700.000 | $1,40 |
| 500.000 lần consume/tháng | 3.500.000 | $7,00 |
| 1.000.000 lần consume/tháng | 7.000.000 | $14,00 |

“Consume” là một bucket, không phải một request: login/create tối đa hai,
join tối đa ba, WebSocket connect một. Không thêm heartbeat/presence vào dự toán
vì chưa triển khai. Fixed bắt đầu đáng so ở khoảng 714.000 consume/tháng theo
giả định 7 lệnh; còn phải đạt latency/dung lượng/throughput. Free chỉ khoảng
71.000 consume/tháng ở giả định đó, chưa tính thao tác khác. Không mặc định free
đủ production hoặc paid vẫn có 500K command miễn phí.
PAYG budget cap có thể ngừng/rate-limit thao tác khi hết ngân sách: app phải
coi là unavailable, không fallback RAM.
[Budget semantics](https://upstash.com/pricing/redis#faq).
Đường ngoài Railway còn có egress của backend, cần đo bytes; không nhân 7 lần
bandwidth vì các lệnh nội bộ script không phải bảy network round trip.

## 4. Persistence và sự cố: điều kiện chọn provider

Đề xuất Railway thử `maxmemory 64 MiB`, `noeviction`, container ban đầu 256 MiB;
đo RSS/peak trước tăng. `maxmemory` không phải giới hạn toàn process: AOF buffer,
allocator, rewrite cần headroom. Đầy RAM phải trả unavailable, không xóa quota.
[Redis memory/eviction](https://redis.io/docs/latest/develop/reference/eviction/).

Đề xuất AOF `appendonly yes`, `appendfsync everysec` trên volume bền vững;
xác nhận cấu hình thực và mount đúng, không đoán template đã bật. AOF everysec
có thể mất khoảng một giây ghi khi sự cố; RDB/backup cũ mất nhiều hơn. Mất bucket
cấp burst mới, còn restore snapshot cũ có thể cấp thêm hoặc giữ quota quá chặt.
[Redis persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/).
Backup giúp phục hồi, không thay HA hay quota thời gian thực. Railway restore
chỉ cùng project/environment; diễn tập bằng Redis test riêng, không restore vào
database đang phục vụ. [Railway backups](https://docs.railway.com/volumes/backups).

Upstash luôn có durable storage, paid có replication; đó không đồng nghĩa SLA
multi-zone trong tier $10. [Durability](https://upstash.com/docs/redis/features/durability/).
Backup/restore phải thử trên database cách ly vì restore xóa dữ liệu đích.
[Upstash backup](https://upstash.com/docs/redis/features/backup).

## 5. Nghiệm thu trước production và bước cần duyệt

Checklist này chưa chạy trên cloud, không được đánh dấu đạt nhờ fixture local:

- [ ] Sau owner duyệt, kiểm tra đúng vùng/backend và chốt provider gần nhất;
  staging project riêng, bật theo đợt, database/namespace/secret riêng.
- [ ] Chạy nguyên script TYPE/HLEN/HMGET/TIME/HSET/PEXPIRE qua Lettuce hiện tại;
  kiểm RESP, TTL, policy mismatch và hai backend test độc lập tranh cùng quota.
- [ ] Đo p50/p95/p99 từ backend, cả kết nối lại và key nguội; đề xuất p99 dưới
  100 ms để dư so với 200 ms, startup dưới 2 giây. Không tăng timeout để che
  vùng xa. Nếu TLS: đúng hostname/chain phải đạt; chứng chỉ sai/không tin cậy
  phải bị từ chối. Nếu WireGuard: ghi rõ quyết định transport, không báo TLS đạt.
- [ ] Load test cách ly, tối đa 10 phút/20 consume mỗi giây lúc đầu, có mức
  command/chi phí dừng định trước; kiểm queue 128, hủy, timeout không replay,
  noeviction/OOM và hồi phục. Không chạy tải này vào production.
- [ ] Restart có AOF phải giữ token chưa refill; restore backup thật, ghi thời
  gian phục hồi và quota có thể mất. Nếu provider có failover, diễn tập/xác nhận
  riêng; không suy từ restart đơn node thành HA/không mất dữ liệu.
- [ ] Ngắt Redis: HTTP/admin 503, WebSocket vào mới unavailable; create/join bị
  chặn nhưng socket đã xác thực và trận đang chạy tiếp tục thao tác local.
- [ ] Kiểm metrics allowed/limited/unavailable và reason hữu hạn; theo dõi memory,
  disk, latency, failure, dự báo chi phí; logs/metrics/bằng chứng không chứa URL,
  password, IP/email/user ID/key. Không mở metrics ra Internet.
- [ ] Rollout một backend với revision/cấu hình cũ lưu an toàn; rollback có kế
  hoạch drain/bảo trì. Chuyển Redis sang memory, đổi namespace/provider hoặc
  restore/reset dữ liệu đều có thể tạo quota mới; owner phải chấp nhận tác động,
  không gọi đó là rollback giữ nguyên hạn mức. Không FLUSHDB dữ liệu dùng chung.

**Bước duyệt tối thiểu:** chọn Railway một node, ngân sách Redis thêm $5/tháng
(cần xin lại trước $10), chấp nhận điểm lỗi đơn và private WireGuard hoặc yêu cầu
TLS bắt buộc, cho phép một đợt staging cách ly có giới hạn tải/chi phí. Sau đó
mới kiểm tra vùng/cấu hình thực, lập thay đổi triển khai để duyệt và chạy nghiệm
thu. Nếu chọn Upstash thay thế, chốt PAYG/Fixed, mức budget và consistency trước.
Chưa provision, mua gói, đổi billing/cloud config, deploy hoặc chạy/đọc CI trong
phạm vi tài liệu này.
