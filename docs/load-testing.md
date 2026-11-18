# Baseline load HTTP/WebSocket

Issue: [#5](https://github.com/ThaiXuanHien/FastToWin/issues/5).

`scripts/load-test.mjs` starts its own server JVM on a random loopback port.
It has no target URL option and requires that JVM's listener-ready log before
sending requests. The child has an allowlisted environment, empty working directory,
512 MiB maximum heap, no database/provider credentials inherited from the shell,
and a four-minute deadline. It stops only its own child process.

## Workload and safeguards

Each pair uses two newly registered, verified synthetic accounts. Setup is outside
the measured interval. The measured workload logs in, connects both WebSockets,
lists rooms, creates/joins an ORDER casual room, starts the game, acknowledges all
50 selections, disconnects/resumes one opponent midway, then acknowledges both
room leaves. Completed matches must have the expected winner. PostgreSQL runs
also wait for all completed matches to be committed before collecting DB statistics.

- Default: 4 pairs, 1 round; maximum: 16 pairs, 2 rounds. Inputs outside the
  limits or unsupported options fail before starting a server.
- Selections are paced at 200 ms. Normal backend rate limits remain enabled;
  rate-limit errors count as failures, not success after silent retries.
- HTTP operations and socket events have deadlines. Global timeout/interruption
  cancels setup and requests, then cleans up the owned runtime.
- PostgreSQL mode only accepts `jdbc:postgresql://127.0.0.1:15432/admin_test` without
  query parameters. It creates a unique `loadtest_<UUID>` schema, runs migrations
  there, and drops that schema after the owned JVM stops. Ambiguous CREATE errors
  still trigger `DROP SCHEMA IF EXISTS` for the generated name.
- No emails, FCM device tokens or purchases are created. Runtime's empty working
  directory prevents reading the project's fallback Firebase credential file.
- Reports contain aggregates, resource counters and hardware, never credentials,
  access/purchase tokens, emails or passwords. Runtime logs stay in ignored artifacts.

## Re-run

Use Node 22+ and Java 17+; `JAVA_HOME` selects the JVM. Build the distribution first:

```sh
./gradlew :server:installDist --no-configuration-cache
node --test scripts/tests/load-test-utils.test.mjs
node scripts/load-test.mjs --pairs 4 --rounds 1
```

For PostgreSQL, prepare a **local test** settings JSON outside Git:

```json
{
  "url": "jdbc:postgresql://127.0.0.1:15432/admin_test",
  "user": "fasttowin",
  "passwordFile": "<absolute local test password file>"
}
```

```sh
node scripts/load-test.mjs --pairs 4 --postgres-settings .artifacts/admin-postgres/test-settings.json
```

`psql` must be on PATH, or pass `--psql <local psql executable>`. Windows uses
`gradlew.bat` and the short `.tmp/jvm-sockets` directory for JVM loopback sockets.
The harness never connects to a pre-existing HTTP server or a Railway domain.

Results: `.artifacts/load-tests/<UTC timestamp>/report.json`; exit code is nonzero
on setup errors, failed pairs, timeout, interruption or incomplete cleanup. CI runs
a two-pair PostgreSQL baseline and publishes `isolated-load-baseline` JSON for 7 days.
CI does not gate absolute latency against this workstation's measurements.

## First measured baseline — 2026-10-11

Windows 10.0.26200, Intel Core i5-14600K, 20 logical CPUs, 31.7 GiB RAM,
OpenJDK 25.0.2, Node 24.19.0; PostgreSQL 17.11 on the same machine, pool size 10.
Application source: `2bf521f`; harness introduced by `codex/backlog-readiness`.
Each run: 4 pairs / 8 accounts / 4 matches / 200 selections / 4 resumes / 8 leaves.
Both passed with zero operation errors; PG persisted all 4 matches and removed its schema.

| Observation | Memory | PostgreSQL |
| --- | ---: | ---: |
| Measured duration | 11.70 s | 11.78 s |
| Selection attempts/second | 17.09 | 16.97 |
| Selection p50 / p95 / p99 | 15.82 / 16.05 / 16.11 ms | 11.95 / 16.10 / 56.74 ms |
| Login p95 | 52.70 ms | 69.98 ms |
| Create-room p95 | 13.10 ms | 97.37 ms |
| Resume p95, including 200 ms disconnect pause | 233.45 ms | 234.67 ms |
| Maximum sampled JVM used heap | 87.2 MiB | 89.9 MiB |
| Maximum sampled process working set | 233.6 MiB | 303.3 MiB |

Shareable raw aggregates: [memory](baselines/load/2026-10-11-memory.json) and
[PostgreSQL](baselines/load/2026-10-11-postgresql.json). A separate memory run
also passed two rounds per pair (8 matches), confirming re-use after room leave.

These are deliberately light baselines, not saturation tests or a 10k-user
capacity claim. The offered rate is controlled by pacing and acknowledgement;
17 selections/s is workload throughput, not maximum server throughput. Small
sample counts make tail percentiles noisy. Latency includes client polling
resolution; setup/JIT warm-up, OS scheduling and local DB cache affect comparisons.

PostgreSQL adds visible create-room latency and a larger selection tail. Relation
counters show repeated active-room snapshot writes; no bottleneck or saturation
has been demonstrated. DB measurements are schema-scoped rows/size/buffer I/O,
not isolated PostgreSQL process CPU or network/disk saturation. JVM heap and process
CPU/working set are sampled roughly every 2 s. Windows CPU is cumulative seconds;
Unix `ps` reports lifetime CPU percent, so compare within the same OS/JVM only.

To compare a change, retain before/after JSON, use identical JVM, hardware, storage,
pairs/rounds and pacing, run on an otherwise quiet machine, and compare errors,
operation-specific tails and resource counters. Repeat if a result differs materially;
do not raise concurrency against production to validate these figures.

## Kiểm tra số kết nối đồng thời trên staging (SCL-01)

`scripts/k6/connections.js` đo trần số kết nối đồng thời (CCU) của **một bản staging do dự án sở hữu**; không chạy script này vào production. Mỗi virtual user kết nối dưới dạng khách, chờ `session_ready`, giữ socket mở và cứ 30 giây gửi `get_presence` một lần, giống người chơi đang ở sảnh.

```bash
k6 run -e TARGET=https://staging.example.com -e PEAK=500 -e RAMP=10m -e HOLD=30m scripts/k6/connections.js
```

- Chạy lần lượt các mức 500 → 1.000 → 2.000 → 5.000 CCU, mỗi mức giữ 30 phút. Ghi lại p95 của `ftw_session_ready_ms`, CPU, RAM, `fasttowin_db_pool_pending_threads` và số `fasttowin_websocket_connection_limit_rejected_total`.
- Server giới hạn theo IP nguồn (60 socket mới mỗi phút, tối đa `FASTTOWIN_WS_MAX_PER_IP` socket đang mở), nên một máy chỉ tăng tải được chậm. Hãy chạy từ nhiều máy hoặc IP (k6 Cloud, hoặc vài VM) và chia đều `PEAK` cho từng máy. Có thể tạm tăng `FASTTOWIN_WS_MAX_PER_IP` trên staging trong lúc đo, rồi trả về mặc định sau khi đo xong.
- Để diễn tập deploy (SCL-05): trong lúc đang giữ tải, deploy lại staging. Kiểm tra `/ready` trả 503 trong khoảng `FASTTOWIN_DRAIN_SECONDS`, các client kết nối lại trải đều thay vì dồn vào một thời điểm, và không có lỗi tăng đột biến.
- Kịch bản trận đấu (chọn số, kết nối lại giữa trận) vẫn đo bằng `scripts/load-test.mjs` ở phần trên.

## SCL-01: nhiều người chơi trên một backend — 18/11/2026

> **Đây là máy local, không phải Railway.** Server, Postgres, Redis và công cụ tạo tải chạy chung một
> máy (Windows 11, Intel Core i5-14600K, 20 CPU logic, 31,7 GiB RAM, JBR 21, Node 24). Server bị ghim
> vào 4 CPU logic (`--server-cpus 4`, xấp xỉ 2 lõi vật lý có hyper-threading) với cờ JVM giống production
> (`-Xmx512m`). Postgres 17 (production dùng 18.6) và Redis chạy trong Docker. Độ trễ mạng gần như 0, còn
> Railway có độ trễ mạng nội bộ thật. Hãy dùng các con số dưới đây để **so sánh trước/sau** và tìm nút thắt,
> không coi chúng là cam kết dung lượng của production.

### Công cụ

`scripts/load-test-scale.mjs` dùng lại các nguyên tắc an toàn của baseline: không có tuỳ chọn URL đích;
script tự khởi động JVM server riêng trên loopback; chỉ chấp nhận Postgres `127.0.0.1:15432/admin_test`
với schema `loadtest_<uuid>` riêng và xoá sau khi chạy; chỉ chấp nhận Redis loopback; chỉ tạo tài khoản
giả `@example.invalid`, không email/FCM/mua hàng; báo cáo không chứa token, email hay mật khẩu.

- Tăng tải theo bậc (`--steps 250,500,1000,2000`). Mỗi bậc gồm một đoạn tăng tải (`--ramp-per-second`)
  và một đoạn giữ tải đo đạc (`--hold-seconds`). Có tối đa 16 worker thread, mỗi worker giữ hàng nghìn WebSocket.
- 60% người chơi (`--playing-percent`) chơi theo cặp: tạo phòng ORDER casual, vào phòng, sẵn sàng,
  chọn xen kẽ 50 số (`--select-interval-ms 500`), rời phòng, nghỉ khoảng 8 giây, rồi lặp lại. Số còn lại
  ngồi ở sảnh: cứ 15 giây gửi `get_presence`, cứ 30 giây gửi `list_rooms`, giống client thật.
- Mỗi người chơi giả có một địa chỉ IP riêng trong dải 10.x, gửi qua `X-Forwarded-For`; server bật
  `FASTTOWIN_TRUST_PROXY_HEADERS=true`, giống production chạy sau Caddy. Như vậy các giới hạn theo IP được
  giữ nguyên mặc định mà không chặn nhầm. Tài khoản giả được cộng lượt chơi trong schema riêng.
- Mẫu tài nguyên lấy mỗi 5 giây: CPU tiến trình server, heap, `fasttowin_db_pool_*`, số transaction/giây
  của Postgres, CPU container, Redis `INFO` (ops/giây, CPU, bộ nhớ, thời gian trung bình của EVAL), và các
  bộ đếm lỗi rate limit/presence. Độ trễ event loop của chính worker được ghi lại, để biết khi nào công cụ
  tạo tải mới là nút thắt.
- `--reconnect-storm --reconnect-jitter-ms 3000`: sau bậc cuối, đóng toàn bộ socket rồi cho tất cả kết nối
  lại bằng resume token, mô phỏng một lần deploy.
- `--server-dist <tên>` chạy một bản `installDist` đã lưu dưới `.artifacts/dist/<tên>` để so sánh nhánh.
  `--jfr` ghi Java Flight Recorder.

```sh
node scripts/load-test-redis-local.mjs --cpus 1            # Redis giống production: 64 MB, noeviction, AOF, ACL
./gradlew :server:installDist --no-configuration-cache
node scripts/load-test-scale.mjs --steps 250,500,1000,2000 --hold-seconds 60 --ramp-per-second 50 \
  --server-cpus 4 --postgres-settings .artifacts/admin-postgres/test-settings.json \
  --redis-settings .artifacts/loadtest-redis/settings.json --psql docker:ftw-test-pg
```

`--psql docker:<container>` chạy psql bên trong container test (máy không cần cài psql). Bậc được coi là
**khỏe** khi lỗi ≤ 1%, p95 chọn số ≤ 250 ms, ≥ 99% người chơi kết nối được và client không bị nghẽn.

### Kết quả: `master` (`cfaf5fb7`) và từng bản tối ưu

Cùng một cấu hình: pool DB = 4 (giống production), tăng 50 người/giây, giữ 60 giây, Redis 1 CPU.
Mỗi chữ cái tương ứng một PR, đo cộng dồn:
A = ghi activity theo chu kỳ; B = cache xác thực/kiểm duyệt trên đường nóng; C = giới hạn bản ghi chống
trùng request; D = băm mật khẩu tối đa nửa số lõi; E = ghi phòng song song theo từng phòng; F = encode
mỗi tin một lần và không broadcast sảnh mỗi lần kết nối.

| Bản | Mục tiêu | Kết nối giữ được | Chọn số/giây | p95 / p99 chọn số (ms) | p95 kết nối (ms) | Lỗi | DB pool chờ (max) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| master | 250 | **165** | 65 | 45 / 209 | 15.041 (85 quá hạn) | 0,96% | 1 |
| master | 500 | **202** | **5** | **13.094** / 15.012 | 15.342 | 5,9% | **58** |
| A | 500 | 500 | 144 | 819 / 3.761 | 1.512 | 0% | 15 |
| A | 1000 | 981 | 119 | 1.102 / 3.687 | 4.962 | 1,1% | 8 |
| A+B | 500 | 500 | 177 | 509 / 2.203 | 1.397 | 0,02% | 25 |
| A+B+C | 1000 | 1000 | 212 | 1.124 / 13.094 | 4.494 | 0,06% | 9 |
| A..E | 500 | 500 | 210 | **127** / 2.292 | 336 | 0% | 4 |
| A..F | 500 | 500 | 210 | 158 / 1.918 | 230 | 0,01% | 19 |
| A..F | 1000 | 1000 | 259 | 1.637 / 5.479 | 658 | 0,12% | 21 |
| A..F | 2000 | 1896 | 218 | 1.059 / 5.588 | 1.265 | 1,4%* | 21 |
| A..F | 3000 | 2790 | 188 | 1.370 / 5.588 | 1.483 | 1,4%* | 26 |

\* Gần như toàn bộ là đăng nhập HTTP quá hạn 15 giây ở phía client: với D, đăng nhập xếp hàng chờ PBKDF2
thay vì làm treo game. Không có `MAINTENANCE`, không có lỗi kết nối WebSocket.

Ngưỡng **khỏe** (p95 ≤ 250 ms) đi từ **dưới 165 người** (`master`) lên **500 người** (A..F), cùng 2 lõi.
Ở 3.000 người, server vẫn giữ 2.790 kết nối, nhưng thông lượng chọn số bão hoà ở ~200–260/giây và
heap lên 467/512 MiB. Bão kết nối lại sau bậc 3.000 (jitter 3 giây): **2.790 socket kết nối lại trong
7,4 giây, 0 lỗi**, p95 4,4 giây, Redis đỉnh 4.665 ops/giây.

Đã thử thêm và **bỏ**: gộp các lần ghi snapshot phòng thành một transaction (group commit, vẫn chỉ trả
lời sau khi DB ghi xong). Một luồng flush duy nhất làm mất khả năng ghi song song trên 4 kết nối mà E
vừa mở ra. Ở 2.000 người, lượt chọn giảm từ 218 xuống 163/giây; ở 3.000 người, các trận đứng hẳn và bão
kết nối lại có 6,4% lỗi. Không mở PR cho hướng này.

Thử pool DB = 10 với A..F (SCL-04): bậc 2.000 đạt 266 lượt chọn/giây so với 218, p95 835 ms so với
1.059 ms. Tăng pool có ích nhưng không phải đòn bẩy chính.

### Nút thắt đã đo được (theo thứ tự xuất hiện)

1. **ActivityTracker** (sửa ở A): mỗi lần mở hoặc đóng socket tài khoản, server ghi lại *toàn bộ* người
   online (một `SELECT … FOR UPDATE` trên `users` cộng một UPDATE cho mỗi người) dưới một mutex, trước
   khi gửi `session_ready`. Kết nối phải chờ 9–15 giây ở 250 người, và gây deadlock với `match_players`.
2. **Đọc kiểm duyệt/xác thực trên mỗi tin nhắn** (B): khoảng 13 transaction Postgres cho mỗi lượt chọn
   số (2 lần đọc block, 1 join session, đọc lại toàn bộ suppression mỗi lần gửi, và vòng quét block 5 giây
   chạy 1 transaction cho mỗi socket). Sau B còn ~5,7.
3. **`processedRequests` lưu kèm snapshot** (C): bản ghi phòng trung bình 60 KB, tối đa 151 KB JSON, ghi
   lại sau *mỗi* lượt chọn (kích thước tăng theo bình phương số lượt).
4. **PBKDF2 trên `Dispatchers.Default`** (D): 1.000 lượt đăng nhập trong 20 giây chiếm hết CPU, việc đọc
   trạng thái bảo trì trễ quá 4 giây, server tự đóng cửa và trả `MAINTENANCE`/HTTP 503 cho người chơi mới.
   Với D: 0 lỗi `MAINTENANCE`, p50 kết nối WS 52 ms (trước đó 3.085 ms).
5. **Một khóa ghi toàn cục** (E): JFR cho thấy 65% số lần đọc DB lâu (> 10 ms) là
   `ActiveRoomRepository.save`, xếp hàng qua `persistenceMutex`, trong khi Postgres mới dùng ~50% CPU.
6. **Broadcast O(N²)** (F): mỗi lần kết nối gửi danh sách phòng cho mọi người, và mỗi tin bị encode JSON
   lại cho từng socket (36% lượng cấp phát bộ nhớ khi tăng 1.000 → 2.000 người).

Còn lại sau A..F: mỗi lượt chọn vẫn ghi đồng bộ snapshot phòng (~1.700 transaction/giây ở 3.000 người),
và heap 512 MiB gần đầy. Bước tiếp theo cần **quyết định của chủ dự án**: hoặc gộp các lần ghi phòng
(trả lời trước, ghi bản mới nhất sau vài trăm ms, nghĩa là có thể mất vài lượt chọn đã xác nhận nếu
server sập), hoặc tăng RAM/heap. Production hiện có ~18 tài khoản, còn rất xa các ngưỡng này.

### Redis (SCL-01 phần 2)

`RedisCapacityBenchmark` (chỉ có trong test) chạy đúng các lớp production `RedisRateLimiter` và
`RedisPresenceStore` (128 permit, timeout 200 ms, một kết nối Lettuce). Tải được đưa vào theo kiểu mở
(open-loop), nhắm vào container Redis dựng từ `deploy/railway/redis` trên loopback, giới hạn 1 CPU.

| Thao tác | Ổn định (lỗi < 1%) | p99 tại mức ổn định | Lỗi xuất hiện đầu tiên |
| --- | ---: | ---: | --- |
| Rate limit (`EVAL rate_limit.lua`) | ~20.000 ops/giây | 2,8 ms | `OVERLOADED` (hết 128 permit): 5% ở 40.000/giây, tối đa ~42.000/giây |
| Presence ghi (renew/revoke) | ~20.000/giây | 2,4 ms | hàng đợi Lettuce đầy, ghi thất bại (3,4% ở 40.000/giây) |
| Presence đọc (20 bạn/lượt) | ~5.000/giây | 2,6 ms | timeout 200 ms (3% ở 10.000/giây) |
| Bộ nhớ | 229 byte mỗi khóa rate limit | – | `maxmemory 64mb` + `noeviction` khoảng 270.000 khóa sống |

Timeout 200 ms không phải thứ hỏng đầu tiên: độ trễ vẫn dưới 5 ms khi giới hạn 128 permit đã bị chạm.
Trong load test, Redis tốn khoảng 0,4–0,6 ops/giây cho mỗi người online (2 ops mỗi lần kết nối lại,
cộng heartbeat presence 20 giây và rate limit khi tạo/vào phòng), CPU Redis ~2% và ~10 MiB ở 3.000 người.

**Ước tính cho Redis đơn hiện tại:** khoảng **20.000–30.000 người online**, cao hơn rất nhiều so với giới
hạn của một backend. Thứ hỏng đầu tiên nhiều khả năng là **bộ nhớ 64 MB**: khóa rate limit sống ít nhất
15 phút, mỗi người online để lại ~2–3 KB khóa. Khi chạm `noeviction`, Redis từ chối ghi, rate limit
fail-closed, và đăng nhập/kết nối bị chặn. Tiếp theo là bão kết nối lại không jitter (`OVERLOADED`).
Khi người chơi tăng: nâng `maxmemory` (rẻ) và theo dõi `used_memory`. Đây không phải việc của nhóm C.

Chạy lại benchmark (Redis từ `load-test-redis-local.mjs`; classpath test từ `:server:clanRaceAcceptanceClasspath`):

```sh
BENCH_KIND=rate-limit BENCH_RATES=2000,5000,10000,20000,40000,80000 BENCH_NAMESPACE=loadtest \
BENCH_RATE_LIMIT_URL_FILE=.artifacts/loadtest-redis/rate-limit.url \
BENCH_PRESENCE_URL_FILE=.artifacts/loadtest-redis/presence.url \
java -Djdk.net.unixdomain.tmpdir=<thư mục ngắn> -cp <classpath test> com.hienthai.fastowin.server.ratelimit.RedisCapacityBenchmark
```

`BENCH_KIND` nhận `rate-limit`, `presence-renew`, `presence-read` hoặc `memory`. Benchmark từ chối mọi Redis
không phải `redis://…@127.0.0.1:<port>` và chỉ in số liệu tổng hợp.
