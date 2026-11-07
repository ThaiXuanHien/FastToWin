# Room ownership pilot acceptance and staging handoff

This is the bounded private-room slice of [#15](https://github.com/ThaiXuanHien/FastToWin/issues/15), implementing the [approved design](superpowers/specs/2026-11-07-room-match-ownership-design.md) and [plan](superpowers/plans/2026-11-07-room-match-ownership.md). It covers authenticated fixture accounts in private classic `CASUAL` / `ORDER` 1v1 rooms. Matchmaking, ranked games, tournaments, cluster-wide maintenance counts and production replicas remain separate work.

## Local reproduction

Prerequisites: PowerShell 7, local Docker Desktop Linux engine and a JDK 17 or later (`JAVA_HOME`). Run from the checkout root:

```powershell
pwsh -NoProfile -File scripts/tests/room-ownership-local.ps1
pwsh -NoProfile -File scripts/room-ownership-local.ps1 -Suite Fault
pwsh -NoProfile -File scripts/room-ownership-local.ps1 -Suite Load
```

`Load` creates two fresh fixtures in sequence: physically one active backend, then two. Defaults are 20 concurrent rooms, 40 distinct accounts, seed 73, 600 seconds and five offered selections per room per second. Each slot must acknowledge exactly 3,000 scheduled selections, recycling a completed 50-number match. The two-backend load profile pauses a current owner at seconds 60, 180 and 360. Each recovery must finish within 25 seconds. Command timing includes reconnect/retries; scheduled-to-ACK p95 additionally includes the client queue. Catch-up spacing is at least 55 ms per slot. A full profile must finish within 5% + one second of its configured window and sustain at least 95% of the offered rate. Producers have a hard 30-second tail limit.

`-LoadProfiles One` and `-LoadProfiles Two` are diagnostic selectors for rerunning one profile; the default `All` is the required pair.

For launcher diagnostics only, use `-DurationSeconds 10 -Rooms 2`. Short runs explicitly record `fullAcceptanceProfile=false` and cannot replace the two default runs.

The runner rejects remote Docker endpoints, non-loopback control ports and receipt paths through junctions/symlinks. It creates a random `room-pilot-<12 hex>` Compose project, fresh database/Redis volumes and cryptographic synthetic credentials. The seeder refuses an occupied unmarked database and verifies its marker and exact account allowlist. JVM controls independently validate the project, loopback addresses and exact Compose paths. No application or production credentials are loaded.

Only this development pilot opts into `FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE=true`. This raises fixture IP limits so 40 clients sharing loopback can offer 100 selections/s and rapidly recycle rooms: login IP 512/minute; WebSocket connects, create/join IP 5,000/minute; WebSocket messages and selection IP 5,000/second; create/join player 1,000/minute. The normal per-player selection limit remains 20/second. The controller grants 1,000 synthetic daily bonus matches per account. The same settings apply to both load profiles; legacy/production mode cannot enable the pilot.

Receipts live under `.artifacts/room-ownership-local/<run-id>/fault`, `load-1` and `load-2`. They contain synthetic IDs, configuration, image identities, build hashes, assertions, latency/queue/pool measurements and cleanup verification. Authentication material and raw HTTP/SQL error bodies are excluded. A failure preserves a partial fault receipt, then `finally` removes only the owned Compose project and its labelled build images. Shared pulled images remain cached. `cleanup.json` must report no containers, networks, volumes or owned images left behind.

Queue age measures committed events behind known socket cursors; an uncertain first create/join can temporarily under-report until discovery resolves the room. Pool saturation samples active/max Hikari connections and includes a timestamped sample series, p95 and maximum; it does not infer connection wait time. During an intentional process kill, samples from that unavailable process are excluded, while scrape failures from any other process fail the run.

## Fault evidence contract

Each case requires both processes reachable before injecting its fault. An absent or skipped fault fails the suite.

| Case | Injection and proof |
| --- | --- |
| Committed selection | Public ACK, durable selection, SIGKILL owner, higher epoch and unchanged match, board, points and sequence after reconnect. |
| Owner-only PostgreSQL outage | Disable only the owner's Toxiproxy TCP path; the peer and controller retain PostgreSQL access. Recover on the peer, restore the original path, reject the superseded socket and prohibit post-takeover event epoch regression. The controller separately invokes the repository and settlement APIs using the original fence and requires rejection before mutation callbacks execute. The receipt distinguishes these probes from the restored process/socket test. |
| Before commit | Hold the room row with a controller transaction, observe database blocking after sending the command, kill the owner while locked, release the lock, prove the request was absent and retry it once. |
| Committed response lost | Observe the committed request in PostgreSQL before reading its socket response, kill the owner, recover and retry the same request ID once. The cutpoint proves the caller did not read the ACK; it does not claim the server had never emitted a frame. |
| Start response lost | Commit the second ready command without reading `GameStarted`, kill, then recover the same match/board and exactly two finalized quota receipts. |
| Settlement response lost | Commit the final selection and settlement before reading the result, kill and replay; exactly one match/settlement, two quota receipts and two reward ledger rows. Verify reward amounts and accumulated player statistics against match history and the ledger. |
| Redis unavailable | Stop Redis; require `RATE_LIMIT_UNAVAILABLE` for a fresh idle-account connection and no new admission/session/quota changes. An already admitted match may continue selecting under its intentionally local in-game buckets. Restart Redis, admit the idle account and verify match/economy integrity. No RAM fallback for shared admission. |
| PostgreSQL unavailable | Stop PostgreSQL; observe no accepted ACK or optimistic selection. Restart, reconcile the last committed board and retry once. |

Local execution results and measured staging thresholds are recorded below after the full profiles have actually run.

## Local evidence from 07/11/2026

The Fault profile passed all 8 required cases in receipt `a3b1e319c69c42448281d4190d5937e7/fault`. Recovery samples for the SIGKILL/partition cases were 13.4–15.8 seconds. The receipt also records stale-fence mutation, renew, release and settlement probes, exact-once quota, settlement and reward checks, and cleanup. The full one-backend Load profile passed in receipt `8a1a80d5b7b3420eab08db3d0ead599f/load-1` with 20 rooms and 60,000 selections in 624.45 seconds at 96.09 selections/s. Its cleanup receipt has no remaining fixture resources.

Earlier two-backend runs timed out while recycling a room after an owner pause. One four-room run recorded WebSocket request durations around 45 seconds. Those log entries alone do not measure the handshake. Client diagnostics now identify the protocol response awaited. Partly opened pairs of sockets are closed before the next connection attempt.

With the client cleanup and concurrent room reconnects, the four-room diagnostic (`d799f664809a4b2bb5995730254aaf71/load-2`) acknowledged all 2,400 selections across 48 matches. The first takeover took 15.447 seconds. The full two-backend run in receipt `fdd56daca7da49cab85af379422006c7/load-2` acknowledged 60,000 selections across 1,200 completed matches in 602.61 seconds at 99.57 selections/s. The three recorded recoveries took 11.600, 13.682 and 13.721 seconds. Command p95 was 95 ms. Scheduled-to-ACK p95 including the recovery queue was 15.32 seconds. Event queue age peaked at 0.94 seconds and PostgreSQL pool saturation peaked at 1.0 (p95 0.8). The cleanup receipt lists zero remaining resources.

The focused guard and economy suites passed after the settlement lock-order change. A full `:server:test` run on an isolated PostgreSQL 18 `admin_test` database executed 1,062 tests with zero failures and zero errors. Twenty-seven tests were skipped because they require Redis fixtures, so Redis-specific coverage remains in the separate Redis acceptance suites.

## Staging rehearsal procedure

1. Obtain approval for a concrete staging package and resources. Use an isolated marked database, synthetic accounts, dedicated Redis namespaces and two backends on the exact reviewed build. Record image/JAR hashes and resource limits. Do not point the acceptance seeder at an existing application database.
2. Keep `/internal/*` and direct backend ports private. The pilot Caddy proxy exposes game ingress and `/health`, denies public `/ready` and `/internal/*`, and routes only to healthy private `/ready` upstreams. Signed peer routing uses its own 32-byte secret, two stable node identities and allowlisted private addresses; process owner IDs change on every boot.
3. Require healthy PostgreSQL, Redis and both worker readiness checks. Establish the single-backend baseline, then repeat the exact two-backend profile with the measured command-latency margin below. Export only fixed-label technical counters/gauges. Keep public credentials, passwords and player IDs out of metrics/logs.
4. Run one scripted fault per table row. Keep pre/post committed snapshots, epoch/revision, quota/result/ledger counts and end-to-end recovery time. Fail any skipped injection, lost/duplicated selection, changed match identity, duplicate reward, recovery over 25 seconds or unmet throughput/latency threshold.
5. To drain, remove the target from new ingress and send graceful termination. Its shared admission gate first closes new room work, resolves admitted transactions and releases durable leases only after state is committed. It then closes owned sockets for reconnect. Require unavailable readiness while liveness remains reachable during the drain. A crash instead waits for the 12-second PostgreSQL lease to expire; renewal is every three seconds.
6. Stop the rehearsal if database authority is unavailable, Redis fails closed, readiness oscillates, the queue grows without recovery, pool saturation persists, settlement errors rise or any correctness gate fails. Preserve evidence and keep ingress closed pending a decision. Do not automatically reset quotas, restore a database or enable a legacy fallback.
7. Remove only the separately approved staging fixture resources after recording cleanup. Present measured results to the owner before considering any production rollout or replica change.

The pilot remains explicitly development/fixture gated. Enabling staging ingress, extending supported gameplay and changing production replica count require separate changes and approval.
