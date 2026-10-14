# Lựa chọn gói hạ tầng — FastToWin, 12/10/2026

Phạm vi ban đầu: **tài liệu và checklist chọn gói trước** cho #6, #11,
#12 → #15 và email đăng ký. Tại thời điểm lập lựa chọn, chưa mua gói, tạo tài
nguyên hoặc đổi provider. Kết quả triển khai sau khi duyệt được ghi bên dưới. Giá tham khảo USD đối chiếu nguồn chính thức ngày 12/10/2026,
chưa gồm thuế/phí thanh toán; cần xác nhận lại tại checkout theo tài khoản,
vùng và chu kỳ thanh toán. Các ví dụ tải/chi phí bên dưới là giả định để chọn,
không phải số đo hay hóa đơn hiện tại của FastToWin.

Ưu tiên chủ dự án bổ sung: chọn **free hoặc chi phí thấp nhất đáp ứng nhu cầu**;
triển khai hạ tầng sau task quà Gem/badge #34 và lỗi bàn phím PWA #35. Chưa chốt
ngân sách cho avatar/Redis/email; so sánh Pro bên dưới là phương án để cân nhắc khi public.

Cập nhật triển khai 12/10: chủ dự án đã nâng **Hobby trả phí** và đồng ý staging
theo đợt. Staging dùng project riêng để tránh API áp dụng cấu hình qua nhiều
environment. Xem [runbook và bằng chứng staging](staging-rehearsal.md). Các phần
avatar, Redis và email trong tài liệu này vẫn là lựa chọn, chưa tự mua/chuyển.

## 1. Chọn nhanh theo giai đoạn

Code hiện tại dùng Ktor/JVM, PostgreSQL, web qua Caddy và một backend giữ trạng
thái trận trong RAM. Email có Brevo HTTPS và SMTP; chưa có adapter HTTPS cho
Resend/SES. Chưa có số đo tải đồng thời, RAM/CPU trung bình hoặc tổng email thực
tế để cam kết sức chứa người chơi. Xem [hướng dẫn deploy](railway-deployment.md).

| Giai đoạn | Bộ gói đề xuất | Khi cần chuyển bước |
| --- | --- | --- |
| Alpha/test ít người, một người vận hành | Railway **Hobby**; một backend + web + PostgreSQL; staging chỉ bật theo đợt; **Brevo Free** nếu mọi email còn dư hạn mức | Sắp phát hành công khai/thu tiền, cần tính năng Pro hoặc email gần chạm trần |
| Phát hành công khai, chuẩn bị Google Play | Railway **Hobby** trước; chọn Pro khi cần tính năng/giới hạn của Pro; vẫn một backend; staging theo đợt; **Brevo Starter theo lượng gửi tháng** | Tải đo được vượt khả năng service, email tăng ổn định hoặc cần nhiều backend |
| Mở rộng sau khi có số đo | Pro + **R2 Standard** cho avatar; Redis Railway hoặc **Upstash Fixed 250 MB** nếu phù hợp tải; email so sánh Brevo với **Resend Pro/SES** | Chỉ tăng replica sau khi #12/#15 đạt nghiệm thu hai instance |

Đề xuất cho project lúc này: hoàn thành staging theo đợt trước, giữ Brevo HTTPS;
giữ Hobby đến khi số đo hoặc tính năng vận hành yêu cầu nâng gói. Avatar và Redis triển khai riêng sau khi chọn
dịch vụ. Chưa có căn cứ mua Enterprise, Redis lớn hoặc tăng backend ngay.

### Railway: gói và cách dự trù

Hobby **$5/tháng gồm $5 usage**, Pro **$20/tháng gồm $20 usage**. Container tính
RAM **$10/GB-tháng**, CPU **$20/vCPU-tháng**, volume **$0.15/GB-tháng**, egress
**$0.05/GB**. Tổng tiền là mức tối thiểu của gói hoặc usage nếu cao hơn, không
cộng lại toàn bộ hai khoản. [Giá Railway](https://docs.railway.com/pricing/plans).

Ví dụ tự tính cho một tháng quy ước 30 ngày, cộng tất cả service trong workspace:

```text
U = RAM trung bình × 10 + CPU trung bình × 20
    + volume tính phí × 0.15 + egress tính phí × 0.05
Hobby ≈ max(5, U); Pro ≈ max(20, U)
```

| Giả định minh họa, không phải số đo | Usage/tháng | Hobby | Pro |
| --- | ---: | ---: | ---: |
| Production: RAM 1.05 GB, CPU 0.05 vCPU trung bình; volume 1 GB, egress 5 GB | $11.90 | $11.90 | $20.00 |
| Production như trên + staging tương tự chạy 60/720 giờ; thêm volume 1 GB tồn tại cả tháng và egress 1 GB | ~$13.06 | ~$13.06 | $20.00 |
| Production và staging đều chạy liên tục với tải như dòng đầu | $23.80 | $23.80 | $23.80 |
| Production tăng: RAM 2 GB, CPU 0.2 vCPU trung bình; volume 5 GB, egress 20 GB | $25.75 | $25.75 | $25.75 |

Chưa gồm backup/dịch vụ phụ/domain/email; tháng thực tế và số đo thay đổi sẽ làm
đổi kết quả. Với dòng hai, tổng dự trù là **$13.06 + phí email + phí khác** trên
Hobby, hoặc **$20 + phí email + phí khác** trên Pro. Nâng Pro không tự tăng RAM,
không tự làm database HA và không giải quyết trạng thái trận giữa nhiều replica.

Mức giới hạn RAM **để thử và đo**, chưa phải cấu hình đã áp dụng: backend 1 GiB,
PostgreSQL 512 MiB, web 128 MiB. Dockerfile backend đang cho heap 512 MiB và
metaspace tối đa 192 MiB, còn cần native/thread memory; không đặt cả container
512 MiB. Theo dõi peak/OOM/GC, CPU, số kết nối DB và độ trễ trước khi chỉnh.
Giới hạn RAM không phải lượng RAM trung bình dùng để tính bảng giá trên.

Ưu tiên cảnh báo chi phí mềm. Railway hard limit có thể làm **toàn bộ workload
trong workspace ngừng chạy**; environment staging chung workspace không có trần
độc lập theo cơ chế này. [Cost control](https://docs.railway.com/pricing/cost-control).

## 2. Staging riêng (#6)

**Đã triển khai:** project riêng `FastToWin Staging`, chỉ một environment
`staging`; backend + web + PostgreSQL + Mailpit riêng, chạy theo đợt. Dùng
artifact đã kiểm chứng, dữ liệu mẫu, tài khoản và bí mật riêng. Không duplicate
environment production vì có thể sao chép biến/tham chiếu. Project riêng cũng
tránh tác động chéo từ API cấu hình service qua nhiều environment. Đợt đầu
đã hoàn tất và dừng compute; xem [bằng chứng và cách chạy lại](staging-rehearsal.md). [Environments](https://docs.railway.com/environments).

| Lựa chọn | Khi phù hợp | Chi phí/vận hành |
| --- | --- | --- |
| Railway staging theo đợt — ưu tiên | Test phát hành, bảo trì, restore theo lịch | Tắt compute sau diễn tập; volume/backup vẫn có thể tính phí |
| Railway staging liên tục | Test hằng ngày, có ngân sách và người theo dõi | Cộng toàn bộ tài nguyên vào usage; bảng trên có ví dụ |
| Docker Compose trên máy/VPS | Đã có máy hoặc muốn tự vận hành | Tự quản lý OS/TLS/backup; local không thay nghiệm thu cloud |

Backend realtime production cần chạy liên tục. Không dựa vào Serverless để bảo
đảm staging tự ngủ: outbound DB/heartbeat có thể giữ service thức. Nguồn activity
30 giây hiện tại cũng cần được tính đến. [Serverless](https://docs.railway.com/deployments/serverless).

Checklist chuẩn bị và nghiệm thu:

- [x] Chọn giờ bật staging/tháng, vùng gần production, domain Railway hoặc
  subdomain riêng; chưa cần mua domain để bắt đầu. Không đổi vùng production
  chỉ để làm staging.
- [x] DB/volume, Owner test, JWT/CSRF, cookie/origin và client trỏ riêng; dùng
  dữ liệu mẫu, email sink, push tắt và provider sandbox/tắt.
- [x] Backup có checksum/chính sách lưu; restore **thực sự** vào DB cách ly rồi
  kiểm tra schema, tài khoản, ví; ghi lại thời gian phục hồi/lượng dữ liệu có thể mất.
- [x] Diễn tập planned/emergency maintenance, drain/reconnect và về ACTIVE.
- [x] Deploy rồi rollback backend/web đồng bộ, xác minh tương thích schema;
  lưu bằng chứng không chứa bí mật, tắt staging sau khi xong nếu chọn theo đợt.

## 3. Avatar object storage/CDN (#11)

**Chọn đề xuất:** Cloudflare **R2 Standard**, chưa cần Infrequent Access cho
avatar được đọc thường xuyên. Backend chuẩn hóa ảnh, ghi object có tên phiên
bản, DB giữ key/metadata; xác thực và giới hạn upload. Tách test/production,
token tối thiểu quyền, chỉ công khai ảnh đã chuẩn hóa.

| Lựa chọn | Khi phù hợp | Cần chuẩn bị |
| --- | --- | --- |
| R2 Standard — ưu tiên | Avatar nhỏ, muốn giảm phí traffic đọc ảnh | Cloudflare/R2, billing, bucket/token, domain CDN production |
| S3 + CloudFront | Đã dùng AWS và cần tùy chọn vùng/quyền | IAM, vùng, CDN và bảng tính đầy đủ storage/request/egress |
| Giữ cách hiện tại tạm thời | Chưa chọn dịch vụ/ngân sách | #11 tiếp tục mở; chưa giảm tải đọc ảnh cho backend |

R2 Standard miễn phí hàng tháng **10 GB-month, 1 triệu Class A, 10 triệu Class B**;
vượt mức: **$0.015/GB-month, $4.50/triệu A, $0.36/triệu B**; egress R2 miễn phí.
Đơn vị tính phí được làm tròn lên; dịch vụ nối thêm có thể tính riêng.
[Giá R2](https://developers.cloudflare.com/r2/pricing/).

Ví dụ riêng của project: 10.000 avatar × 200 KB ≈ 2 GB, chưa gồm ảnh cũ/phiên bản.
Dung lượng này có thể nằm trong free tier nhưng vẫn phải kiểm tra số request.
`r2.dev` dành cho phát triển; CDN production cần custom domain trên Cloudflare.
Nếu chưa có domain có thể để backend đọc bucket private trước.
[Public buckets/custom domain](https://developers.cloudflare.com/r2/buckets/public-buckets/).

- [ ] Chọn R2 hay S3, domain đã sở hữu, ngân sách và chính sách ảnh cũ.
- [ ] Nghiệm thu MIME/kích thước/quyền ghi, cache phiên bản, thay/xóa ảnh,
  migration ảnh cũ, storage lỗi và fallback trước khi chuyển production.

## 4. Redis và nhiều backend (#12 → #15)

**Chọn đề xuất:** Redis trên Railway trước để giữ cùng nền tảng; vẫn một backend
trong giai đoạn chuyển đổi. Dùng TTL/thao tác nguyên tử cho presence/rate limit,
rồi xử lý ownership trận và định tuyến WebSocket. Sticky session đơn lẻ không
giải quyết trạng thái trận trong RAM.

| Lựa chọn | Khi phù hợp | Điểm cần kiểm tra |
| --- | --- | --- |
| Redis Railway — ưu tiên vận hành chung | Muốn mạng nội bộ/cùng nền tảng | Đo RAM/CPU/volume để tính usage; private access, eviction/backup; template không tự chứng minh HA |
| Upstash Free | Thử tích hợp trên staging nhỏ | 250 MB, 500.000 lệnh/tháng; không mặc định đủ cho production |
| Upstash Pay as You Go | Tải thấp/không đều, muốn trả theo lệnh | $0.20/100.000 lệnh, còn storage/bandwidth; budget cap có thể rate limit |
| Upstash Fixed 250 MB | Lượng lệnh đều/cao, dữ liệu còn nhỏ | $10/tháng, 50 GB bandwidth, 10.000 lệnh/giây; vẫn phải đo memory/latency/giới hạn |

Nguồn: [Redis Railway](https://docs.railway.com/databases/redis),
[gói Upstash](https://upstash.com/pricing/redis).

Ví dụ giả định 100 kết nối luôn online × 2 lệnh/phút × 43.200 phút = 8,64 triệu
lệnh/tháng, **$17.28 riêng tiền lệnh** theo PAYG, chưa gồm thao tác khác/phí khác.
Fixed $10 đáng so sánh từ khoảng 5 triệu lệnh/tháng nếu các giới hạn còn đáp ứng.
Không suy từ ví dụ này ra số người chơi FastToWin thực tế hỗ trợ được.

- [ ] Chọn provider/vùng/budget, đo command rate, RAM, bandwidth, TCP/TLS và Lua
  thực dùng; kiểm tra Redis restart/mất mạng và cách phục hồi.
- [ ] Trước nhiều backend: ownership/lease/fencing, reconnect đúng owner,
  pub/sub/replay, ghi kết quả/ví một lần, drain khi deploy và coverage activity
  toàn cụm; nghiệm thu hai instance/owner chết/network partition trên staging.

Thứ tự triển khai: **staging → avatar → Redis → hai backend trên staging →
production sau nghiệm thu**.

## 5. Email: nếu vượt 300 thư/ngày

**Đề xuất:** giữ Brevo HTTPS khi alpha còn dư quota; gần vượt thì nâng Brevo theo
lượng gửi tháng. So sánh Resend Pro/SES khi tải tăng ổn định hoặc cần cải thiện
vận hành/delivery. Không bắt buộc đổi dịch vụ chỉ vì vượt 300/ngày.

### Các gói đáng cân nhắc

| Gói | Hạn mức/giá tham khảo | Phù hợp FastToWin |
| --- | --- | --- |
| Brevo Free | $0; 300 thư/ngày | Alpha nhỏ; tính cả xác minh, gửi lại và reset |
| Brevo Starter | Từ $9/tháng cho 5.000 thư/tháng; bỏ trần gửi theo ngày | Ít thay đổi code nhất; chọn tier tháng đủ tải, không mặc định tier $9 đủ |
| Resend Free | $0; 100/ngày và 3.000/tháng | Không giải quyết nhu cầu vượt 300/ngày |
| Resend Pro | $20/tháng cho 50.000; thêm $0.90/1.000 khi bật PAYG | Mốc so sánh rõ cho 15–50 nghìn/tháng; cần tích hợp HTTPS mới nếu dùng Hobby |
| SES Essentials / à la carte | $0.16 / $0.10 mỗi 1.000 người nhận; data/tính năng thêm tính riêng | Phí gửi thấp, cần thêm tích hợp và vận hành AWS; không chọn chỉ dựa giá mỗi thư |

Nguồn: [Brevo plans](https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans),
[Resend pricing](https://resend.com/pricing), [SES pricing](https://aws.amazon.com/ses/pricing/).
Giá SES mới mặc định Essentials cho tài khoản/vùng đủ điều kiện từ 21/07/2026;
có thể chọn à la carte. Chưa có báo giá Brevo tier 15K/50K theo tài khoản này;
lấy giá tại màn chọn gói trước khi quyết định. Kiểm tra cả giới hạn contact CRM
và auto-upgrade; contact lưu trong Brevo khác số tài khoản game. Code hiện chỉ
gọi endpoint gửi thư, không tạo contact CRM.

Railway Free/Trial/Hobby **chặn SMTP**, cần HTTPS. Pro trở lên mở SMTP sau redeploy.
Hiện code chỉ có Brevo HTTPS và SMTP, nên chuyển Resend/SES trên Hobby cần adapter
HTTPS mới. Không cần nâng Railway chỉ để tăng quota Brevo.
[Outbound networking](https://docs.railway.com/networking/outbound-networking).

SES mới ở sandbox: 200 thư/24 giờ, 1/giây và người nhận đã xác minh; cần được
duyệt production access. Ra sandbox vẫn có quota theo tài khoản/vùng, không
đồng nghĩa gửi vô hạn. [Production access](https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html),
[sending quotas](https://docs.aws.amazon.com/ses/latest/dg/manage-sending-quotas.html).

### Tính đủ thư, không chỉ đếm đăng ký

```text
Thư/ngày = đăng ký × số thư xác minh trung bình (kể cả gửi lại)
           + quên mật khẩu + thư khác
Quota tháng dự kiến = tổng thư tháng × 1.25 (dự phòng 25%, đề xuất của project)
```

| Giả định 30 ngày, chưa phải lưu lượng thực | Tổng/ngày | Tổng/tháng | Sau dự phòng 25% |
| --- | ---: | ---: | ---: |
| 200 đăng ký × 1.1 + 20 reset | 240 | 7.200 | 9.000 |
| 300 đăng ký × 1.2 + 40 reset | 400 | 12.000 | 15.000 |
| 1.000 đăng ký × 1.2 + 100 reset | 1.300 | 39.000 | 48.750 |

Trường hợp 400 thư/ngày đều đặn cần ít nhất khoảng 15K/tháng theo giả định trên,
không phải Starter 5K. Trường hợp một ngày 400 nhưng cả tháng dưới 5K có thể chọn
tier nhỏ hơn. Ở 39K/tháng, lấy báo giá Brevo đủ gần 50K để so với Resend Pro;
SES riêng phí người nhận khoảng $6.24 Essentials/$3.90 à la carte, **chưa gồm
data, vận hành và phát triển**. Giá rẻ chưa chứng minh thư đến inbox tốt hơn.

| Tình huống | Cách chọn đề xuất |
| --- | --- |
| Đỉnh còn dưới 240 thư/ngày, tăng chậm | Có thể giữ Free, theo dõi cả đỉnh và dự báo |
| Gần 240–270/ngày hoặc sắp mở đăng ký công khai | Chuẩn bị nâng trước khi chạm 300; đây là ngưỡng vận hành đề xuất, chưa có cảnh báo tự động |
| Vượt 300 do tăng trưởng đều | Ưu tiên nâng Brevo, chọn quota tháng + dự phòng; so giá thực với Resend Pro |
| Chỉ bùng một đợt ngắn | So gói tháng với prepaid Brevo; xác nhận giá/cơ chế credit trước mua |
| Bùng do spam/gửi lại bất thường | Kiểm tra abuse/rate limit trước, nâng quota không giải quyết nguyên nhân |
| Tải lớn ổn định, muốn tối ưu lâu dài | Đánh giá SES theo cả công tích hợp, duyệt access, bounce/complaint và giám sát |

Prepaid Brevo bắt đầu 5.000 credit, không hết hạn; trên Free nó **thay thế**
300 credit miễn phí/ngày. [Add-ons](https://help.brevo.com/hc/en-us/articles/4409354969746-Customize-your-plan-with-add-ons).
Marketing và transactional dùng chung credit. Free có thể giữ thư vượt quota
trong hàng đợi, nhưng không dùng cách đó để xử lý mã xác minh/reset hết hạn
15 phút của app. [Brevo quota FAQ](https://help.brevo.com/hc/en-us/articles/8292912279954-Add-or-remove-emails-from-your-plan).

### Checklist nâng gói/chuyển provider sau khi chọn

- [ ] Đọc dashboard 7–14 ngày: tổng gửi, đỉnh ngày/giờ, resend/reset, lỗi,
  delivery/bounce/complaint, dự báo ngày mở app; xác nhận timezone reset quota.
- [ ] Chốt quota/ngân sách/chu kỳ, giá checkout, contact CRM, overage bật hay tắt;
  không dùng nhiều tài khoản Free để lách giới hạn. Chủ dự án xác nhận gói trước mua.
- [ ] Chuẩn bị sender/domain, DNS SPF/DKIM/DMARC và secret riêng cho staging/prod;
  nếu SES, xin production access/quota và quyền IAM tối thiểu trước cutover.
- [ ] Nâng Brevo: xác nhận API key/sender và quota mới, test có kiểm soát.
  Đổi provider: làm adapter HTTPS hoặc xác nhận SMTP thực sự dùng được trên Pro,
  kiểm thử auth, 429, timeout, quota, nội dung/mã và lỗi an toàn trên staging.
- [ ] Nếu làm outbox/retry sau này: có TTL, chống gửi trùng, giới hạn thử lại và
  trạng thái không chắc provider đã nhận. Không tự gửi lại/chuyển provider ngay
  khi timeout; không phát lại mã cũ đã hết hạn. API chấp nhận chưa chắc đã giao thư.
- [ ] Kiểm thử thư đến địa chỉ test được phép, xác minh và reset hoàn tất trong
  TTL; kiểm tra bounce/complaint/suppression trước rollout có giám sát.
- [ ] Cutover có revision/backup cấu hình và điều kiện rollback; giữ provider cũ
  trong thời gian quan sát. Rollback không phát lại các lần gửi chưa rõ kết quả.

Hiện có rate limit tài khoản/IP và gửi Brevo HTTPS. Dashboard quota tổng, cảnh
báo 80%/90%, outbox bền vững và webhook delivery **chưa triển khai** trong phạm
vi này; checklist trên là đầu việc cho giai đoạn sau khi chọn gói.

## 6. Phiếu quyết định trước triển khai

- [x] Railway: **Hobby trả phí** do chủ dự án kích hoạt 12/10/2026; staging theo đợt.
- [ ] Ngân sách toàn workspace `___ USD/tháng`;
  ngưỡng cảnh báo mềm `___`; đã hiểu tác động downtime nếu đặt hard limit.
- [ ] Staging: **theo đợt / liên tục**; `___ giờ/tháng`; vùng `___`;
  ai truy cập `___`; thời gian phục hồi và mức mất dữ liệu chấp nhận `___`.
- [ ] Avatar: **giữ tạm / R2 Standard / S3**; domain sở hữu `___`;
  ngân sách `___`; số ảnh/dung lượng/lượt đọc dự kiến `___`.
- [ ] Redis: **chưa bật / Railway / Upstash**; budget `___`;
  RAM/commands/bandwidth đã đo `___`; mục tiêu kết nối đồng thời `___`.
- [ ] Email: **Brevo Free / Starter / prepaid / Resend Pro / SES**;
  đỉnh `___ thư/ngày`, `___ thư/tháng`; quota sau dự phòng `___`;
  giá checkout `___`, sender/domain `___`, overage `___`.
- [ ] Xác nhận các con số bằng usage thực tế; chọn từng phần rồi mới tạo tài
  nguyên/tích hợp/diễn tập và cập nhật bằng chứng vào issue tương ứng.

Không cần quyết định tất cả cùng lúc. Railway Hobby và staging theo đợt đã
được chọn; bước kế tiếp là avatar. Email ưu tiên nâng Brevo khi lượng gửi
chứng minh cần thiết.
