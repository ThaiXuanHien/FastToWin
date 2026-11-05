# Redis Presence Production Preparation Plan

> **For agentic workers:** Use `executing-plans` to carry out an approved task with review checkpoints. Track actions using the checkboxes below.

**Goal** — Chuẩn bị bộ triển khai presence dùng Redis, có bằng chứng về tải, quyền truy cập, phục hồi và nghiệm thu để Owner có thể duyệt một cửa sổ production cụ thể.

**Architecture** — Đề xuất dùng Redis private đang phục vụ rate limit, thêm role riêng cho presence và duy trì một backend game. Presence được bật bằng cấu hình riêng. Quyền điều phối phòng và trận trên nhiều backend thuộc bước tiếp theo của #15.

**Tech stack** — Kotlin/Ktor, Lettuce, Redis Lua, Railway, PostgreSQL, Compose Web/Android và Grafana Cloud.

**Spec** — [Thiết kế presence đã duyệt](../specs/2026-11-04-redis-presence-local-design.md). [Issue #12](https://github.com/ThaiXuanHien/FastToWin/issues/12) quản lý tiêu chí nghiệm thu. Tài liệu này là bản chuẩn bị local ngày 04/11/2026.

## Giới hạn và mốc bằng chứng

- Owner yêu cầu làm công việc tiếp theo trong lúc CI tự chạy. Lần chuẩn bị này đọc mã, tài liệu và Issue #12, tạo bản kế hoạch local. Không có thao tác đọc secret hoặc thay đổi hạ tầng trong lần chuẩn bị.
- Nhánh ứng viên là `codex/redis-presence`, PR #73, commit `cedc4571de0ed2d824e2e3e3d5bead0a50ebcd1a`. CI được khởi chạy tại [run 41844089331](https://github.com/ThaiXuanHien/FastToWin/actions/runs/41844089331). Chỉ đọc kết quả khi Owner báo hoàn tất hoặc yêu cầu kiểm tra.
- Lease gia hạn mỗi 20 giây, hết hạn sau 60 giây theo giờ Redis. Client làm mới danh sách đang hiện mỗi 15 giây. Mỗi lô tối đa 50 ID. Mỗi lần đọc/ghi Redis có ngân sách 200 ms. Handshake dùng chung một tác vụ, thử lại cách nhau tối thiểu một giây khi kết nối ban đầu lỗi.
- Giữ namespace rate limit hiện hữu. Khóa presence dùng mẫu riêng `ftw:prod_main:presence:v1:*`. Mọi probe phải dùng ID tổng hợp riêng đã ghi trong receipt.
- Bài tải local trước đó dùng Redis 64 MiB, AOF và noeviction, có 500 kết nối mô phỏng nhưng chỉ hai lượt đọc danh sách mỗi 15 giây. Đây là giới hạn của mẫu đo trước khi chọn cấu hình dùng chung.
- [Bàn giao rate limit ngày 03/11](../../redis-production-handoff-2026-11-03.md) là nguồn lịch sử về service, volume và tài nguyên. Phải đọc lại metadata thực tế trước mọi bước cloud. Các grant SSH, backup và cutover cũ đã kết thúc.
- Lịch backup tự động vẫn là công việc để sau theo lựa chọn của Owner. Lời nhắn backup hằng ngày trong chat chưa tạo scheduler.

## Task 1 — Bổ sung phép đo tải local trước khi chốt hạ tầng

**Files** — Giữ bài đo nền `server/src/test/kotlin/com/hienthai/fastowin/server/presence/RedisPresenceLoadTest.kt`. Bài đo người xem mới ở `server/src/test/kotlin/com/hienthai/fastowin/server/presence/RedisPresenceViewerLoadTest.kt`, dùng runner riêng `.artifacts/redis-presence-production-20261104/load/run-viewer-load.ps1`.

**Interface** — Dùng `PresenceStore.renew(playerId, connectionId, presence)`, `PresenceStore.read(playerIds)` và hai `RateLimitRuntime` độc lập. Giữ role và namespace fixture riêng.

- [x] Đo ba pha có cùng lưu lượng rate limit, gồm baseline không presence, 500 socket với 100 người xem và 500 socket với 500 người xem. Mỗi người xem đọc một roster 50 ID mỗi 15 giây. Rải lịch đọc và renew trên toàn chu kỳ, tránh để các người xem chạy tuần tự làm giảm tải thực tế.
- [x] Chạy container riêng với Redis image `sha256:be7c1cada5a6eb1656adfa2635b99296af725881301b21f84f35a71fac072c7f`, giới hạn 0,25 CPU và 250.000.000 byte như snapshot production. Dùng `maxmemory 64mb`, `noeviction`, `appendonly yes`, `appendfsync everysec`. Container, ACL và dữ liệu chỉ thuộc đợt local.
- [x] Mỗi pha dài ít nhất 5 phút. Đếm số lượt thực hiện so với lịch dự kiến, lỗi đọc/ghi, trạng thái sai, p50/p95/p99 read, p99 rate limit, CPU/RSS, used_memory, evictions, error replies và AOF current/base size. Ghi số liệu mỗi 15 giây.
- [x] Đề xuất ngưỡng ban đầu cho việc dùng chung gồm mọi thao tác đúng kết quả, không timeout/unavailable, zero evictions, p95 read dưới 100 ms và p99 rate limit dưới 200 ms. Dùng ngưỡng bộ nhớ 70% để giữ dư địa. Đây là điều kiện thử local, cần xác nhận cùng thiết kế triển khai.
- [x] Nếu các pha đạt, kéo dài pha 500 người xem thêm 30 phút để quan sát AOF rewrite và RSS. Đã ghi các mẫu bộ nhớ/AOF và thời gian bị trễ. Trong đợt này không có rewrite, nên chưa có bằng chứng đỉnh bộ nhớ lúc rewrite. Không ngoại suy một mẫu 64 giây thành tốc độ tăng đĩa cả tháng.
- [ ] Nếu không đạt, ghi số đo và trình Owner lựa chọn giảm phạm vi, tăng tài nguyên hoặc Redis riêng. Chỉ chốt cấu hình sau bước này. Kết quả tạo receipt trong `.artifacts/redis-presence-production-20261104/load/`.

**Kết quả ngày 04/11/2026** — Receipt `ftw-viewer-0f5deac3b004` đạt 45 phút đo, đủ 72.000 lượt đọc, 60.000 renew và 27.000 consume. P95 đọc từng pha dưới 2,046 ms. Bộ nhớ allocator cao nhất 2.099.472 byte, RSS lấy mẫu cao nhất 24.469.504 byte. Đã dọn fixture. Báo cáo local tại `.artifacts/redis-presence-production-20261104/load/summary.vi.md`. Bằng chứng này đo tải Redis tổng hợp, chưa thay thế nghiệm thu cloud hoặc lúc AOF rewrite.

## Task 2 — Chuẩn bị role presence tồn tại qua restart

**Files** — `deploy/railway/redis/boot.sh`, `deploy/railway/redis/Dockerfile` và kiểm thử local `scripts/tests/RedisPresenceBootstrap.Tests.ps1`. Dockerfile và backup.sh được đối chiếu hash cùng runtime, không cần sửa trong task này.

**Interface** — Biến Redis đề xuất `FASTTOWIN_REDIS_PRESENCE_PASSWORD` là secret riêng dạng 96 ký tự hex, cùng quy ước secret hiện tại. Bootstrap hiện tại chỉ tạo `ftw_app`, `ftw_monitor` và `ftw_backup`.

- [x] Thêm role presence tùy chọn khi biến secret hiện diện. Secret phải hợp lệ và khác mọi secret hiện hữu, kể cả app previous password. Khi không cấu hình, bootstrap tiếp tục tạo đúng các role đang có.
- [x] Dùng đúng các quyền cần bởi Lua đã có, theo ACL đề xuất dưới đây. Không cấp quyền quản lý ACL cho role app hoặc presence.

```text
user ftw_presence on >[secret supplied privately] ~ftw:prod_main:presence:v1:* +hello +client|setinfo +ping +select +eval +time +zadd +zrem +zremrangebyscore +zcount +pexpire
```

- [x] Kiểm thử bằng Redis thật và volume fixture gồm renew/read/revoke đúng, presence bị từ chối trên khóa rate limit, app bị từ chối trên khóa presence, monitor/backup giữ đúng quyền. Kiểm tra role không có quyền `FLUSHALL`, `CONFIG`, `ACL` hoặc đọc khóa ngoài namespace.
- [x] Consume một quota tổng hợp, ghi một lease rồi restart đúng container fixture với cùng AOF volume. Quota phải giữ phần đã dùng, role presence còn đăng nhập được, lease cũ hết hạn tự nhiên và socket mới ghi được.
- [x] Kiểm tra secret vắng mặt trong stdout/stderr, context manifest và archive backup. Bootstrap hiện tại tạo ACL trong `/run` ở mỗi lần khởi động, nên thao tác `ACL SETUSER` thủ công đơn lẻ chưa chứng minh quyền tồn tại qua restart.
- [x] Rà soát riêng thay đổi bootstrap trước khi xin duyệt triển khai Redis, vì restart Redis dùng chung có thể khiến thao tác mới bị giới hạn tạm thời.

**Kết quả đối chiếu ngày 05/11/2026** — Lần chạy cuối ngày 04/11 đạt 90 kiểm tra. Hash của context hiện tại khớp receipt. Báo cáo tại `.artifacts/redis-presence-production-20261104/acl/summary.vi.md`. Receipt ghi đã dọn tài nguyên Docker; kiểm tra filesystem không còn private fixture. Docker Engine đang tắt nên lần kiểm tra inventory bổ sung chưa thực hiện được.

## Task 3 — Đóng băng artifact và cấu hình backend

**Files** — `server/src/main/kotlin/com/hienthai/fastowin/server/presence/PresenceConfiguration.kt`, `RedisPresenceStore.kt`, `deploy/railway/server/Dockerfile` và artifact đóng băng dưới `.artifacts/redis-presence-production-20261104/`.

- [ ] Sau khi Owner báo CI hoàn tất, đối chiếu SHA thực tế của cả 5 job với head PR và các thay đổi đã review. Hoàn tất quyết định tích hợp PR theo phạm vi Owner duyệt.
- [ ] Tạo context Redis từ Task 2 và context backend từ commit tích hợp đã xác minh. Ghi source SHA, runtime manifest, Docker base digest và SHA-256 từng gói. Đối chiếu migration so với backend đang chạy, chỉ ghi nhận không cần migration khi đã có bằng chứng từ runtime.
- [ ] Chuẩn bị ba biến backend dưới dạng sau. Chọn đúng một nguồn URL hoặc URL_FILE. Secret chỉ được đưa vào backend qua kênh được duyệt.

```text
FASTTOWIN_PRESENCE_STORE=redis
FASTTOWIN_PRESENCE_NAMESPACE=prod_main
FASTTOWIN_PRESENCE_REDIS_URL=redis://ftw_presence:[secret supplied privately]@rate-limit-redis.railway.internal:6379/0
```

- [ ] Lưu cấu hình phục hồi feature flag `FASTTOWIN_PRESENCE_STORE=memory` và một gói backend có kiểm chứng. Việc tắt presence cần restart backend có kiểm soát, làm client quay về ảnh chụp từ backend và hết lease cũ sau tối đa 60 giây.
- [ ] Chuẩn bị bản yêu cầu production cụ thể với đúng service/image/volume, thời hạn truy cập, quyền đọc secret, backup, drain, restart Redis, deploy backend, gói phục hồi và bước thu hồi key. Chỉ xin cấp quyền khi artifact và receipt local đã sẵn để review.

## Task 4 — Kiểm tra quan sát và đường xử lý lỗi

**Files** — `deploy/monitoring/redis-monitor-cloud/prepare.mjs`, dashboard `deploy/monitoring/grafana/dashboards/fasttowin-redis-cloud.json` và endpoint backend `/internal/metrics`.

- [ ] Dùng danh sách metrics Grafana hiện có để kiểm tra Redis availability, memory, evictions, AOF enabled/current/base size, fsync và trạng thái rewrite. Kiểm tra nhãn freshness trước khi xem giá trị là dữ liệu mới.
- [ ] Xác nhận hai series nội bộ `fasttowin_presence_redis_failures_total{operation="read"}` và `{operation="write"}` xuất hiện khi feature được bật. Endpoint được bảo vệ bằng đường truy cập metrics hiện hữu.
- [ ] Ghi rõ collector Redis hiện tại chỉ gửi nhóm metric cố định trong `prepare.mjs`. Nó chưa scrape lỗi presence của backend. Khi triển khai đầu tiên, đề xuất đọc hai counter này qua đường nội bộ trong phiên có người trực tiếp theo dõi.
- [ ] Nếu cần cảnh báo lỗi riêng của presence trên Grafana, lập thay đổi allowlist/scrape/destination riêng để duyệt. Nhóm dữ liệu đề xuất chỉ gồm hai nhãn operation cố định, không có ID người chơi hoặc kết nối.
- [ ] Tại fixture local, cắt đường presence nhưng giữ rate limit hoạt động. Xác nhận lần tải đầu còn ảnh chụp cục bộ, làm mới lỗi giữ trạng thái gần nhất và trận vẫn phát sự kiện. Mở lại đường Redis, lần renew/read kế tiếp phải phục hồi.

## Task 5 — Cửa sổ production sau khi được duyệt

**Inputs** — Artifact đóng băng, receipt load/restart/ACL, metadata mới, backup có checksum và restore local đạt, phiên Owner Admin và hai tài khoản QA đã xác định.

- [ ] Đọc lại đúng project FastToWin Alpha `8daf3d1f-df4c-4f39-a07b-61becae674fa`, môi trường `351ee85f-7760-4b57-9649-9693fdcdbfea`, backend `c08bdb48-d6ff-4816-b6bb-d3cdabb794b6`, Redis và volume `3d5db918-8ffa-47ba-8677-3d81f3dc6a99`. Các ID là điểm đối chiếu lịch sử, phải khớp metadata mới.
- [ ] Tạo backup Redis/DB trong phạm vi truy cập mới được Owner duyệt. Giữ ciphertext mã hóa DPAPI, hạn lưu tối đa 14 ngày và receipt restore. Không dùng receipt cũ ngày 02/11 làm bằng chứng backup mới.
- [ ] Đề xuất báo trước 5 phút và chờ hoàn tất tự nhiên tối đa 15 phút. Phải kiểm tra hành vi maintenance của backend đang chạy trước khi chốt thông số. Nếu còn việc hoặc thiếu bằng chứng khi hết ngân sách, hủy cửa sổ trước khi dừng.
- [ ] Chỉ dừng khi MAINTENANCE đã có hiệu lực, tổng công việc đang chạy gồm prepared starts/quyết toán bằng 0, WebSocket bằng 0 và phiên bản policy khớp trên UI/runtime.
- [ ] Triển khai Redis bootstrap đã kiểm chứng bằng đúng volume hiện hữu, giữ rate-limit namespace và secret. Xác nhận AOF phục hồi, role app và presence được xác thực, quota probe tổng hợp tiếp tục giữ lượng đã dùng.
- [ ] Triển khai backend và đúng ba biến presence. Xác nhận health, runtime hashes, maintenance policy, capability `presenceRefreshSupported`, metrics và không có lỗi credential trong log.
- [ ] Sau các chốt đạt, mở lại qua Owner Admin và nghiệm thu QA. Ghi nhận từng intent/ACK, đối chiếu kết quả mơ hồ trước khi quyết định thao tác tiếp.

## Task 6 — Nghiệm thu, phục hồi và bàn giao

- [ ] QA A/B xem Bạn bè và thành viên bang. Khi B online, vào phòng và rời phòng, A phải thấy đúng trạng thái sau lần làm mới hợp lệ, trong điều kiện bình thường tối đa khoảng 15 giây.
- [ ] Đóng socket B và kiểm tra A nhận offline. Đổi socket B và xác nhận socket cũ đóng muộn không xóa lease mới. Với một backend production, bằng chứng cross-instance và dừng cứng backend dùng receipt local để tránh dừng dịch vụ game chỉ cho phép thử này.
- [ ] Sau mở lại, quan sát trực tiếp ít nhất 15 phút. Ghi mẫu health, policy, protected rate-limit counters, hai presence counters, Redis memory/AOF/fsync và tình trạng cảnh báo Grafana. Counter phải so bằng delta cùng process vì restart làm số đếm trở về đầu.
- [ ] Nếu rate limit unavailable, quota probe sai, runtime/config không khớp hoặc AOF lỗi, giữ bảo trì và trình Owner quyết định. Không tự đổi quota namespace, reset quota, restore AOF cũ hoặc chuyển rate limit về RAM.
- [ ] Nếu chỉ presence lỗi và rate limit vẫn đạt, đề xuất tắt riêng feature flag qua một restart backend đã drain. Việc rollback có ảnh hưởng dịch vụ phải nằm trong grant cụ thể hoặc được Owner quyết định lúc có lỗi.
- [ ] Thu hồi đúng key tạm và kiểm tra inventory, dọn fixture/probe theo receipt, giữ backup tới đúng hạn. Ghi bàn giao kèm giới hạn tải đã đo và lịch kiểm tra người vận hành đã nhận.
- [ ] Đối chiếu Issue #12 với bằng chứng trước khi đề xuất đóng. Khả năng nhiều backend đọc cùng presence chưa cấp quyền chạy nhiều backend để sở hữu phòng/trận. Việc điều phối đó tiếp tục theo #15.

## Kiểm tra bản chuẩn bị này

- [x] Đọc đặc tả presence, Issue #12, bootstrap Redis, Lua, cấu hình backend và allowlist monitoring.
- [x] Ghi riêng các giới hạn của bài tải, persistence ACL và quan sát lỗi presence.
- [x] Tách điều kiện hoàn tất local khỏi quyền truy cập và thực thi production.
- [x] Thực hiện Task 1 theo phạm vi đo tải local Owner đã chấp thuận.
- [x] Thực hiện Task 2 theo phạm vi role presence và restart local Owner đã chấp thuận.
- [ ] Thực hiện Task 3–4 sau khi chốt phạm vi tương ứng.
- [ ] Thực hiện Task 5–6 bằng grant production mới, đúng artifact và thời hạn.
