# Kiến trúc, dịch vụ và ngân sách production

Tài liệu này là cơ sở lập kế hoạch cho Fast To Win, cập nhật ngày
13/09/2026. Các con số là **khoảng ngân sách trước thuế**, không phải báo giá.
Trước khi thuê cấu hình lớn phải load test bản release và nhập kết quả thực vào
AWS Pricing Calculator.

## 1. Hiểu đúng ba loại quy mô

Ba khái niệm sau không thể dùng thay cho nhau:

| Chỉ số | Ý nghĩa | Tác động chính |
| --- | --- | --- |
| Người dùng đăng ký/MAU | Tài khoản có trong hệ thống hoặc hoạt động trong tháng | Dung lượng PostgreSQL, avatar, email và push |
| CCU | Số client đang giữ kết nối WebSocket cùng lúc | Load balancer, RAM/connection của Ktor và presence trong Valkey |
| RPS | Số thao tác HTTP/WebSocket được xử lý mỗi giây | CPU, Valkey, PostgreSQL, log và băng thông |

Ví dụ, 100.000 tài khoản có thể chỉ tạo 1.000–2.000 CCU vào giờ cao điểm.
Ngược lại, 100.000 CCU là một hệ thống realtime rất lớn. Cụm từ “100.000
request đồng thời” vẫn chưa đủ để chọn cấu hình: cần biết request kéo dài bao
lâu, tỷ lệ đọc/ghi, số byte broadcast và RPS duy trì.

Các bảng dưới đây dùng hai kịch bản riêng:

- **Kịch bản game thực tế:** mỗi người đang online tạo trung bình một thao tác
  mỗi 5 giây, tức 0,2 RPS/CCU; khoảng 2,5 KB qua hệ thống cho mỗi thao tác;
  dự phòng tối thiểu 30%.
- **Kịch bản tải độc lập:** 10.000/50.000/100.000 RPS được duy trì liên tục. Đây
  là tải lớn hơn rất nhiều so với cùng số tài khoản đăng ký.

## 2. Dịch vụ cần thuê

Kiến trúc tham chiếu dùng AWS Singapore (`ap-southeast-1`) để gần người chơi
Việt Nam và gom trách nhiệm vận hành về một nhà cung cấp:

```text
Android / iOS / Web
        |
Route 53 + CloudFront + WAF
        |
Application Load Balancer (HTTPS/WSS)
        |
ECS Fargate: nhiều Ktor server
        |          |               |
        |          |               +-- SQS -> worker email/push/tác vụ nền
        |          +-- ElastiCache Valkey: realtime, presence, rate limit, pub/sub
        +-- RDS PostgreSQL Multi-AZ: tài khoản, ví, lịch sử, bang, tiến trình

S3 + CloudFront: Web build, avatar, logo bang và tài nguyên tĩnh
CloudWatch hoặc Prometheus/Grafana/Loki: metrics, log và cảnh báo
SES: email xác minh tài khoản và quên mật khẩu
FCM/APNs: push notification
```

### Danh sách thuê và mục đích

| Nhu cầu | Dịch vụ đề xuất | Có bắt buộc ngay? |
| --- | --- | --- |
| Domain và DNS | Domain registrar + Route 53 | Có |
| TLS, Web/PWA và CDN | ACM + CloudFront + S3 | Có cho Web production |
| Chống request xấu | AWS WAF trước CloudFront/ALB | Có khi public Internet |
| Cân bằng HTTP/WebSocket | Application Load Balancer | Có khi chạy từ hai backend trở lên |
| Backend Ktor | ECS Fargate ARM64 hoặc EC2 Auto Scaling | Có |
| Database bền vững | RDS PostgreSQL Multi-AZ | Có |
| State realtime dùng chung | ElastiCache for Valkey Multi-AZ | Có trước khi scale ngang |
| Hàng đợi tác vụ nền | SQS + một ECS worker | Khuyến nghị |
| Avatar/logo/tài nguyên | S3 private origin + CloudFront | Có trước khi lượng ảnh tăng |
| Email giao dịch | Amazon SES | Có cho đăng ký/quên mật khẩu |
| Push | Firebase Cloud Messaging; APNs qua Firebase cho iOS | FCM không thu phí; iOS cần Apple Developer |
| Secret | Secrets Manager hoặc SSM Parameter Store + KMS | Có |
| Metrics/log/cảnh báo | CloudWatch, hoặc stack Prometheus/Grafana/Loki đang có | Có |
| Backup ngoài database | RDS automated backup + snapshot/PITR; export/copy sang S3/account khác | Có |

**Quên mật khẩu và xác minh email:** backend hiện đã gửi bằng SMTP. Với kiến
trúc này, tạo domain gửi trong Amazon SES, xác minh DNS DKIM/SPF/DMARC, lấy SMTP
credentials và đặt vào `FASTTOWIN_SMTP_*`. Không dùng Gmail cá nhân. SES tính
theo số email; trang giá hiện niêm yết à-la-carte 0,10 USD/1.000 email, còn tài
khoản mới có thể bắt đầu ở Essentials 0,16 USD/1.000 email. Xem
[AWS SES pricing](https://aws.amazon.com/ses/pricing/).

**Push:** Firebase liệt kê Cloud Messaging, Crashlytics và Performance
Monitoring trong nhóm sản phẩm không thu phí. Xem
[Firebase pricing](https://firebase.google.com/pricing). iOS vẫn cần APNs key và
Apple Developer Program; phí thành viên hiện là 99 USD/năm. Xem
[Apple Developer memberships](https://developer.apple.com/support/compare-memberships/).

**Phát hành Android:** Google Play Console có phí đăng ký một lần 25 USD; giao
dịch hàng hóa số còn chịu service fee theo chương trình/khu vực. Tài liệu Google
hiện nêu mức 15% cho 1 triệu USD doanh thu đầu tiên mỗi năm khi tham gia tier
tương ứng. Xem [điều kiện Play Console](https://support.google.com/googleplay/android-developer/answer/14659200)
và [service fee](https://support.google.com/googleplay/android-developer/answer/112622).

## 3. Việc phải sửa trước khi mở rộng ngang

Stack Docker một VPS trong `compose.production.yaml` phù hợp beta/MVP, nhưng
không thể chỉ tăng số container Ktor để đạt các mốc CCU lớn. Hiện room/game
engine và rate limiter còn giữ state trong tiến trình.

Trước khi chạy nhiều instance phải:

1. Chuyển presence, matchmaking, room đang hoạt động, lượt chọn realtime,
   idempotency ngắn hạn và rate-limit sang Valkey.
2. Dùng distributed lock hoặc một room actor có owner rõ ràng; mọi command của
   cùng room phải được xử lý tuần tự.
3. Dùng Pub/Sub hoặc stream để chuyển snapshot/event đến WebSocket nằm ở
   instance khác; không phụ thuộc sticky session để bảo đảm đúng dữ liệu.
4. PostgreSQL tiếp tục là nguồn bền vững cho tài khoản, ví, quyên góp, phần
   thưởng, lịch sử, bang và mùa giải. Transaction kinh tế không chuyển sang
   cache.
5. Chuyển `avatar_data` khỏi PostgreSQL sang S3; database chỉ giữ object key,
   checksum và metadata. Upload bằng URL ký ngắn hạn, CDN chỉ đọc biến thể đã
   kiểm duyệt.
6. Đưa email, push, chốt mùa và tác vụ thử lại vào SQS worker; command phải có
   idempotency key và dead-letter queue.
7. Hỗ trợ connection draining khi deploy, readiness riêng với liveness, rolling
   update và reconnect có backoff/jitter.
8. Giới hạn log theo mẫu và sampling; không ghi từng tick hoặc payload chứa
   token/email. CloudWatch tính tiền theo lượng log ingest và truy vấn, nên log
   quá chi tiết có thể đắt hơn compute.

AWS tính ALB theo giờ và LCU; một LCU bao gồm tối đa 3.000 kết nối đang hoạt động
mỗi phút nhưng hóa đơn dùng chiều lớn nhất giữa kết nối mới, kết nối hoạt động,
byte xử lý và rule evaluation. Xem
[Elastic Load Balancing pricing](https://aws.amazon.com/elasticloadbalancing/pricing/).
Valkey Serverless tính theo GB-hour và ECPU; node-based tính theo node-hour. Xem
[ElastiCache pricing](https://aws.amazon.com/elasticache/pricing/).

## 4. Cấu hình khởi điểm theo giai đoạn

Đây là cấu hình để đưa vào load test, không phải cam kết mỗi task chịu được một
số kết nối cố định.

| Mức | Backend | PostgreSQL | Valkey | Ghi chú |
| --- | --- | --- | --- | --- |
| Beta/MVP, dưới khoảng 500 CCU | 1 VPS 4 vCPU/8 GB hoặc 2 task 1 vCPU/2 GB | Single-AZ, backup hằng ngày | Có thể hoãn nếu chỉ một instance | Stack hiện tại dùng được; chấp nhận downtime ngắn |
| 10.000 CCU / ~2.000 RPS | Tối thiểu 6–10 task 2 vCPU/4 GB, auto scale | Multi-AZ 4–8 vCPU, 100–250 GB, PgBouncer | 2–3 node Multi-AZ hoặc serverless | Load test 15.000 CCU, canary deploy |
| 50.000 CCU / ~10.000 RPS | Khoảng 20–40 task 2–4 vCPU, chia AZ | Multi-AZ 16–32 vCPU, read replica nếu truy vấn bảng hạng nặng | Cluster có shard + replica | Tách worker, leaderboard cache và event stream |
| 100.000 CCU / ~20.000 RPS | Khoảng 40–80 task; cân nhắc EC2/EKS nếu Fargate kém kinh tế | Multi-AZ 32–64 vCPU, replica, partition bảng lịch sử | Nhiều shard, replica, kiểm tra hot key | Load test 130.000 CCU và diễn tập mất một AZ |

Không tăng Hikari pool theo số WebSocket. Mỗi instance chỉ cần pool theo số truy
vấn đồng thời thực tế; tổng connection phải nằm dưới giới hạn RDS và nên qua
PgBouncer/RDS Proxy nếu pattern kết nối yêu cầu.

## 5. Khoảng ngân sách hằng tháng

Giả định on-demand, Multi-AZ, 730 giờ/tháng, Singapore, chưa có Savings Plans,
chưa gồm VAT, nhân sự, quảng cáo mua user, phí giao dịch store và chi phí chống
DDoS nâng cao. Khoảng dưới đây đã gồm compute, ALB, PostgreSQL, Valkey, object
storage/CDN ở mức hợp lý, hàng đợi, secrets, backup và monitoring cơ bản.

### Game thực tế theo CCU

| Đỉnh kết nối đồng thời | RPS trung bình giả định | Khoảng ngân sách/tháng |
| ---: | ---: | ---: |
| 10.000 CCU | 2.000 RPS | **2.000–5.000 USD** |
| 50.000 CCU | 10.000 RPS | **8.000–20.000 USD** |
| 100.000 CCU | 20.000 RPS | **15.000–40.000 USD** |

### Nếu ý nghĩa là RPS duy trì độc lập

| Tải duy trì 24/7 | Khoảng ngân sách/tháng |
| ---: | ---: |
| 10.000 RPS | **8.000–20.000 USD** |
| 50.000 RPS | **35.000–90.000 USD** |
| 100.000 RPS | **70.000–180.000 USD** |

Khoảng rất rộng vì băng thông broadcast, hit-rate cache và write amplification
PostgreSQL quyết định phần lớn hóa đơn. Fargate tính theo vCPU, RAM và thời gian
task chạy; xem [AWS Fargate pricing](https://aws.amazon.com/fargate/pricing/).
RDS Multi-AZ duy trì standby đồng bộ và thường gần gấp đôi phần compute/storage
so với Single-AZ; xem
[RDS on-demand and Multi-AZ](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_OnDemandDBInstances.html).

CloudFront/S3 nên phục vụ Web build và ảnh để byte tĩnh không đi qua Ktor. S3
tính riêng storage, request và data transfer; xem
[S3 pricing](https://aws.amazon.com/s3/pricing/). CloudFront hiện có cả
pay-as-you-go lẫn flat-rate plan, trong đó origin transfer từ AWS origin đến
CloudFront được miễn; xem
[CloudFront pricing](https://aws.amazon.com/cloudfront/pricing/).

WAF cơ bản tính theo Web ACL, rule và request. Ví dụ chính thức 10 triệu request
với một Web ACL và 19 rule là 30 USD/tháng; tính năng bot/fraud nâng cao có thể
tăng chi phí rất mạnh, vì vậy chỉ áp dụng rule đắt cho endpoint đăng nhập/đăng
ký. Xem [AWS WAF pricing](https://aws.amazon.com/waf/pricing/).

## 6. Cách biến khoảng ước tính thành dự toán thật

1. Đo payload WebSocket p50/p95 và số người nhận mỗi event trên một trận thật.
2. Chạy k6/Gatling hoặc client chuyên dụng: 1k → 5k → 10k kết nối; giữ mỗi mức
   ít nhất 30 phút rồi chạy spike, reconnect storm và mất một instance.
3. Ghi CPU/RAM/task, event-loop latency, GC pause, RPS/command, Redis ECPU,
   cache hit, DB TPS, lock wait, pool wait và network GB.
4. Chấp nhận cấu hình chỉ khi p95 command đạt mục tiêu, không mất/nhân đôi phần
   thưởng, reconnect khôi phục đúng và còn ít nhất 30% headroom.
5. Nhập đúng task-hour, RDS class/storage/IOPS, Valkey ECPU/node, ALB LCU,
   request CDN/WAF và GB egress vào AWS Pricing Calculator.
6. Đặt AWS Budget ở 50/80/100%, cảnh báo forecast và cost anomaly trước khi mở
   traffic công khai.

## 7. Lộ trình thuê tiết kiệm

- **Bản thử nghiệm ít người:** một VPS, PostgreSQL cùng máy, Caddy và monitoring
  hiện tại; đồng thời sao chép backup ra object storage khác máy.
- **Có doanh thu/traffic ổn định:** chuyển database sang RDS, Web/ảnh sang
  S3/CloudFront, email sang SES; chạy hai backend sau ALB.
- **Chuẩn bị vượt 1.000–2.000 CCU:** hoàn tất state Valkey/SQS và load test. Không
  mở auto scaling nhiều backend trước thay đổi này.
- **10.000 CCU trở lên:** Multi-AZ đầy đủ, capacity reservation khi cần, load
  test mỗi release lớn và ký cam kết Savings Plans/Reserved chỉ sau 1–2 tháng có
  số liệu thật.

Kiến trúc này không bắt buộc dùng AWS mãi mãi. Có thể thay ECS/RDS/Valkey/S3/SES
bằng các dịch vụ tương đương, nhưng nên giữ ranh giới compute, durable database,
realtime state, object storage, queue và transactional email để tránh quay lại
mô hình một máy chủ không thể mở rộng.
