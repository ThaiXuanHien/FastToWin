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
