# Redis Cloud Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nghiệm thu rate limit trên Railway private bằng dữ liệu test trước khi đề nghị bật Redis production.

**Architecture:** Một Redis một node và một runner tạm, cùng project/environment/region staging riêng. Runner mở hai Ktor Netty application độc lập với hai Redis connection trong cùng container, chỉ bind loopback; Redis là service thật khác container qua mạng private. Không mở rộng game production và không gọi đây là nghiệm thu ownership nhiều instance.

**Tech Stack:** Artifact JVM 17 của FastToWin, Lettuce 7.9.0.RELEASE, Redis image pin digest, Railway private WireGuard, AOF/volume riêng.

**Spec:** [Phương án đã duyệt](../../redis-production-options-2026-11-02.md), [hợp đồng runtime local](../specs/2026-11-02-redis-rate-limit-local-design.md).

## Global Constraints

- Owner đã duyệt provider Railway một node, private WireGuard và dự trù Redis tăng thêm $5/tháng; sau đó đồng ý chuẩn bị và kiểm thử runner có xác thực trên local. Chưa cho tạo tài nguyên, bật staging, cấp SSH key hoặc deploy.
- Private WireGuard là mã hóa lớp mạng; đợt này không được ghi TLS `rediss://` đã đạt. Không mở public Redis/TCP proxy, public runner hoặc endpoint metrics.
- Production vẫn một backend, cấu hình rate-limit hiện tại giữ nguyên; không đọc DB/credential người chơi hoặc sao chép secret production.
- Chỉ 18 bucket entry đã duyệt dùng Redis; frame/chọn số vẫn RAM. Giữ 200 ms/lệnh, 2 giây startup, 128 pending; không replay hoặc fallback sau lỗi Redis.
- Kiểm thử cloud phải chạy thật, không biến test bị skip thành đạt. Không kiểm tra trạng thái/log CI nếu owner chưa yêu cầu.
- Không tự nâng ngân sách; $5/tháng là dự trù Redis tăng thêm, không phải hard cap kỹ thuật của Railway và không phải tổng workspace.

## 1. Target, tài nguyên và giới hạn của đợt đề xuất

Thông tin lịch sử từ [staging runbook](../../staging-rehearsal.md): project `FastToWin Staging`, ID `b5c5db71-2df8-46da-84fb-d9b9df6d7951`, environment `staging`, ID `8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9`; region ghi nhận trước đây `sfo`. Phải đối chiếu metadata hiện tại trước mọi mutation, không tự chọn Singapore hoặc coi lịch sử là trạng thái hiện tại.

| Thành phần đề xuất | Phạm vi/giới hạn |
| --- | --- |
| `redis-acceptance` mới | 1 replica; giới hạn container khởi điểm 256 MiB/0,25 vCPU; Redis maxmemory 64 MiB; `noeviction`; AOF everysec; volume riêng capacity 1 GB, tối đa hai volume 1 GB trong pha restore |
| `redis-acceptance-runner` mới | 1 replica; 1 GiB/0,5 vCPU; JVM heap tối đa 512 MiB; tắt restart tự động; không domain, không healthcheck HTTP vì là one-shot runner |
| Dữ liệu | Chỉ key/credential test mới; namespace ngẫu nhiên mỗi lần chạy, ghi vào manifest riêng; không dùng lại namespace production |
| Thời gian | Tối đa 2 giờ tính từ lúc service đầu tiên bắt đầu chạy; owner xác nhận giờ kết thúc trước khi tạo |
| Tải | Một pha tối đa 10 phút, tối đa 20 consume/giây tổng cộng, 2 runtime; không phải 20 HTTP request/giây |
| Chi phí đợt | Đề xuất usage tăng thêm tối đa $1 cho runner + Redis + volume/backup + build/network nếu có; đây là mục tiêu dừng do người vận hành kiểm soát, chưa phải khoản owner đã duyệt |

Ví dụ compute minh họa với RAM tổng trung bình 1,35 GB và CPU 0,75 vCPU liên tục 2 giờ: `1,35×10×2/720 + 0,75×20×2/720`, khoảng **$0,079**, chưa build/network/volume/backup/thuế. Đây là phép tính theo đơn giá trong tài liệu chọn gói, không phải báo giá hay bảo đảm hard cap. Dung lượng volume/backup còn giữ sau đợt vẫn tính phí.

Nếu region staging khác production hiện tại, ghi rõ kết quả chỉ áp dụng đường mạng staging; phải cùng region được chọn cho production hoặc diễn tập lại ở region đó trước bật production.

Không bật bốn service cũ `staging-server`, `staging-web`, `staging-postgres`, `staging-mail`. Runner dùng repository/auth giả lập dữ liệu trong RAM theo acceptance hiện có, không cần PostgreSQL hoặc email. Không bật toàn bộ project một cách hàng loạt.

## 2. Chuẩn bị local trước khi xin thực thi cloud

### Task 1: Đóng gói runner có xác thực và manifest

**Files cần dùng:** `server/src/test/kotlin/com/hienthai/fastowin/server/RedisNettyAcceptanceTest.kt`, `server/src/test/kotlin/com/hienthai/fastowin/server/ratelimit/RedisFaultProxy.kt`, `server/build.gradle.kts`; artifact và evidence dưới `.artifacts/redis-cloud-acceptance-20261102/`, không đưa secret vào Git.

**Consumes:** API `openRateLimitRuntime`, `RateLimitRuntime.consume/renderMetrics/close` đã nghiệm thu; fixture Redis riêng có auth; private URL qua secret environment.

**Produces:** Một gói runner JVM không cần Gradle/Android SDK khi chạy cloud, manifest SHA-256 toàn bộ file và báo cáo JSON hữu hạn. Gói local đã được chuẩn bị tại revision `67d11901bae53b1df3fb8f0497e561360a2956cf`; không dùng `server:installDist` thay cho test distribution.

- [x] Tạo regression local cho fixture Redis có password/ACL: hai runtime và proxy sự cố đều kết nối đúng; sai password bị từ chối, URI/password không vào exception/report.
- [x] Sửa test transport để chuyển tiếp auth và database của target. Proxy hiện giữ auth/database và từ chối TLS chưa hỗ trợ; không bỏ auth ở service thật để tránh sửa test.
- [x] Runner không dùng logging test có quyền tạo ACL làm credential ứng dụng thường. Dành credential quản lý test riêng cho thao tác ACL/CONFIG/backup nếu thực sự cần; không đưa nó cho runtime application.
- [x] Không dùng nguyên logging test hiện tại qua cloud: nó tự tạo ACL user toàn quyền. Chỉ chạy trong fixture riêng đã duyệt và xóa user sau test; credential wrong-password kiểm riêng cũng phải không lộ secret.
- [x] Xuất test runtime classpath thành gói JVM riêng, có một entrypoint và danh sách case rõ ràng; không upload toàn repo/cache, không fetch dependencies khi container bắt đầu. Production distribution không thay đổi.
- [x] Chạy artifact đóng gói qua Docker network local với Redis auth ở container khác, hai Ktor listener chỉ loopback trong runner; kiểm không skip, mã thoát khác 0 nếu thiếu URL hoặc case thiếu.
- [x] Báo cáo có revision, artifact checksum, số case pass/fail, p50/p95/p99, tổng consume, max pending và outcome/reason cố định. Pending 128 là cấu hình; không báo thành occupancy SDK đo được. Chỉ ghi service/deployment IDs vào evidence riêng; không URL, IP/email/user ID/key hay secret vào log/metrics.
- [x] Kiểm tra image chạy non-root, không cần Docker socket và không có credential production. SHA-256 artifact phải khớp bản local đã chạy.

Prerequisite local đã hoàn tất và qua task/final/scoped review. [Cách dùng runner](../../redis-cloud-runner.md). Manifest `SHA256SUMS` gồm 203 file, SHA-256 `305c199a3568aa43513ecbad713f266194f178c230ce9dddae7c86ff63cf118d`. Image đã kiểm tra: `ftw-redis-acceptance:6868cace6b6141c0905a06846f304ba9`, ID `sha256:e93ca01c58d66b286e57ed5bcbf31db5a3481f3f7d779bb1496fbb76861e845e`.

Kết quả Docker local: 13 case thật và 10 consume đạt; thiếu URL/sai auth/checksum bị sửa đều thoát lỗi và không lộ sentinel secret. Inspector riêng xác nhận app credential bị từ chối FLUSHDB/FLUSHALL/CONFIG/ACL. Đã dọn fixture/network và credential; image cuối được giữ, image thử trung gian đã dọn. Startup 8 ms là phép đo warmed, không phải cold; p99 1.391 µs chỉ là mẫu local ngắn. Chưa chạy cloud, AOF/OOM/backup/restore; không coi Task 1 đạt là production đã nghiệm thu.

### Task 2: Kiểm tra sẵn sàng thực thi

- [ ] Ghi `target.json` ignored với project/environment IDs, region đã đối chiếu, hai service IDs mới khi tạo, run ID, namespace và danh sách volume/backup được phép dọn. Không đưa password vào manifest công khai.
- [ ] Xác nhận subscription/quota và usage hiện tại qua metadata/billing, không đọc biến secret không liên quan. Chỉ đọc tên biến Redis cần tham chiếu, không in giá trị.
- [ ] Ghi trạng thái/deployment IDs của bốn service staging cũ và production để phát hiện thay đổi ngoài phạm vi; không sửa chúng.
- [ ] Chốt giờ bắt đầu/kết thúc, người giám sát và ngưỡng dừng. Không tạo lịch theo dõi tự động nếu owner chưa yêu cầu; không để đợt chạy mà không có người chịu trách nhiệm dừng.
- [ ] Trình owner một lần duyệt thực thi cụ thể: hai service mới, volume test 1 GB và volume restore tối đa 1 GB, một backup test, tối đa 2 giờ/$1 usage mục tiêu và dọn dữ liệu test. Quyền chuẩn bị hiện tại không bao gồm các mutation này.

## 3. Thực thi sau khi được duyệt đợt cloud

### Task 3: Redis và kết nối private

- [ ] Chỉ tạo Redis trong target staging đã đối chiếu. Pin image digest đã chạy local hoặc pin revision mới sau nghiệm thu riêng; không dùng `latest` hay mặc nhiên lấy template đang thay đổi.
- [ ] Gắn volume mới tại đúng data directory, đặt `appendonly yes`, `appendfsync everysec`, `maxmemory 64mb`, `maxmemory-policy noeviction`. Xác nhận cấu hình runtime và mount; không chỉ nhìn config file.
- [ ] Tạo credential test ngẫu nhiên qua secret store, không CLI argument/history. Dùng private DNS thực tế từ service metadata; không suy ra hostname từ display name. Không bật public proxy để máy local truy cập.
- [ ] Không lấy `REDIS_URL` mặc định nếu nó trỏ public. Dùng URL private đã kiểm chứng, tham chiếu secret store tới runner; runner chỉ trả kết quả auth/PING đã khử dữ liệu nhạy cảm.
- [ ] Redis có auth, ứng dụng sai auth bị chặn; kiểm startup tối đa 2 giây từ runner và liveness còn đúng khi Redis gián đoạn.

### Task 4: Nghiệm thu hành vi và tài nguyên

- [ ] Hai Netty application dùng hai connection độc lập, cùng namespace: tổng allowed không vượt capacity; namespace khác tách biệt. Đủ login/admin, WS connect, create/join và 18 bucket.
- [ ] Kiểm refill/TTL, hash hỏng/policy mismatch và quota 429/Retry-After; lỗi Redis trả 503/`RATE_LIMIT_UNAVAILABLE`, không cấp phiên/thực thi nghiệp vụ bị chặn.
- [ ] Dùng proxy byte bên trong runner để hold/drop/malformed response và cancel/overload. Không giả grant/deny. Ongoing match chọn số vẫn hoạt động, socket đã auth giữ được, tạo/tham gia phòng báo lỗi rồi phục hồi ở lần yêu cầu mới; không replay nghiệp vụ cũ.
- [ ] Đo p99 consume dưới 100 ms làm mục tiêu headroom so với timeout 200 ms; startup lạnh và reconnect riêng, không chỉ warmed client. Nếu không đạt, dừng để đổi vùng/cấu hình, không tự tăng timeout.
- [ ] Load theo giới hạn đã chốt; ghi RSS, CPU, AOF/disk và memory peak. Runner hết 10 phút phải dừng phát tải; hết thời hạn đợt phải dừng compute kể cả đang điều tra lỗi.
- [ ] Thử OOM/noeviction chỉ trên Redis test độc quyền, giữ riêng guard ID. Giới hạn dữ liệu bổ sung tối đa 2 MiB bằng prefix test; giảm ngưỡng test tạm quanh used_memory nếu phù hợp, luôn khôi phục `maxmemory` sau test. Không fill không giới hạn, không tác động Redis khác.
- [ ] Đầy bộ nhớ trả unavailable, key quota còn TTL không bị evict. Khôi phục dung lượng rồi request mới chạy được, không bù/replay request đã mất phản hồi.

### Task 5: Restart, backup và kết thúc

- [ ] Dùng bucket capacity 1/window 24 giờ, tiêu thụ token; đợi xác nhận AOF được fsync rồi restart Redis test giữ nguyên volume. Token chưa refill phải tiếp tục bị từ chối. Không coi stop/start dịch vụ khác hay tạo Redis trống mới là persistence test.
- [ ] Ghi nhận AOF everysec có cửa sổ mất ghi khi crash, không hứa zero loss. Backup không thay thế persistence hoặc HA.
- [ ] Sau khi đã lưu kết quả các pha khác và dừng phát tải, tạo marker test và bucket window 24 giờ, tiêu thụ quota rồi backup Redis test. Sau backup đổi marker để phân biệt dữ liệu mới/cũ; dừng runner trước restore. Đích restore là chính service `redis-acceptance` độc quyền của run, tuyệt đối không Redis production hoặc dữ liệu của người khác.
- [ ] Restore backup test bằng Railway, kiểm staged change trước deploy: platform gắn volume restore mới vào cùng mount path và giữ volume gốc nhưng unmount. Manifest phải ghi đủ volume cũ/mới và backup IDs; không tạo service thứ ba. Sau deploy chạy riêng pha kiểm restore: marker quay về giá trị snapshot, bucket chưa refill vẫn bị giới hạn, Redis có auth/AOF/noeviction đúng. Snapshot cũ có thể mất cập nhật sau thời điểm backup; ghi rõ phạm vi dữ liệu khôi phục. [Cơ chế restore Railway](https://docs.railway.com/volumes/backups).
- [ ] Lưu báo cáo không secret, xác minh production và bốn service staging cũ không đổi; dừng runner/Redis test. Chỉ xóa hai service/volume/backup của run khi đã kiểm tra đúng ID, owner duyệt dữ liệu test được bỏ và evidence đã lưu.
- [ ] Xóa credential test/thu hồi key tạm nếu có. Không dùng `FLUSHDB` hoặc lệnh dọn toàn project/workspace. Ghi tài nguyên lưu trữ nào còn giữ và phí tiếp tục tính.

## 4. Điều kiện hoàn thành và bước production riêng

Cloud chỉ đạt khi có đủ artifact có checksum, auth/private transport, hai application quota chung, latency/cold startup, sự cố/khôi phục, OOM/noeviction, AOF restart, restore cách ly và cleanup thật; mỗi mục phải có bằng chứng, không skip.

Nếu restore chưa được duyệt hoặc runner chưa hỗ trợ auth, trạng thái là **chưa hoàn tất**, không lấy kết quả local bù cloud. Railway một node vẫn là điểm lỗi đơn dù mọi case đạt. Chưa nghiệm thu TLS, HA hoặc game distributed.

Sau đợt đạt: lập thay đổi production gồm đúng service Redis mới, vùng, volume/AOF/credential/namespace, rollout một backend, thời gian drain/bảo trì, kiểm tra sau triển khai và rollback. Chỉ chuyển rate-limit production khi owner duyệt gói thay đổi này; không coi việc duyệt ngân sách hoặc đợt staging là quyền deploy production.

Rollback sang memory, đổi namespace hoặc phục hồi dữ liệu cũ có thể reset quota/cấp burst mới; phải ghi rõ và được chấp nhận trước. Giữ nguyên DB, AdMob, UGC, LIVE revenue và replica production trong giai đoạn rate-limit này. Presence và #15 là công việc riêng tiếp theo.
