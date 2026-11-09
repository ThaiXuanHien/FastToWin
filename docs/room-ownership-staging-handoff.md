# Room ownership staging handoff

This handoff is for the bounded private-room slice of [#15](https://github.com/ThaiXuanHien/FastToWin/issues/15). It is a review package only. No staging service, ingress, database, Redis namespace, or deployment is created by this document.

## Reviewed build and local evidence

- Source commit: `48e54ea` (`test(server): complete room ownership acceptance`).
- Scope: authenticated private classic `CASUAL` / `ORDER` 1v1 rooms.
- Excluded scope: matchmaking, ranked games, tournaments, cluster-wide maintenance counts, production replicas, and live player data.
- Fault receipt: `.artifacts/room-ownership-local/a3b1e319c69c42448281d4190d5937e7/fault/faults.json` — 8/8 cases accepted; recovery samples 13.4–15.8 seconds.
- One-backend load receipt: `.artifacts/room-ownership-local/8a1a80d5b7b3420eab08db3d0ead599f/load-1/load.json` — 60,000 selections, 96.09 selections/s, full acceptance profile.
- Two-backend load receipt: `.artifacts/room-ownership-local/fdd56daca7da49cab85af379422006c7/load-2/load.json` — 60,000 selections, 1,200 completed matches, 99.57 selections/s, command p95 95 ms, scheduled-to-ACK p95 15.32 s, recovery p95 13.721 s.
- Full isolated server test: 1,062 executed tests, 0 failures, 0 errors; 27 Redis-fixture tests skipped and covered by the separate acceptance suites.
- All latest fault/load cleanup receipts report zero owned containers, networks, volumes, and images.

## Private Railway smoke evidence from 09/11/2026

- The bounded staging smoke used five disposable services in the isolated FastToWin Staging environment: PostgreSQL, Redis, two backends and one runner. All fixture accounts, passwords, namespaces and database rows were synthetic and run-specific.
- Final receipt: .artifacts/room-ownership-staging-20261108-5e195bfa/cloud-room-pilot-4ef52dfe2ff3-dns-fix-2a7e4c42/receipt.json.
- Result: both backends ready; online presence observed from both backends; the shared login quota enforced across backends; one committed selection produced the same snapshot on both clients; two quota receipts finalized. The result line was {readyBackends:2,onlineSeenFromBothBackends:true,sharedLoginQuota:true,sameCommittedSelection:true,finalizedQuotaReceipts:2}.
- Cleanup: all five temporary deployments stopped, no cleanup errors and no permanent volumes created. The smoke did not exercise the full fault matrix, ten-minute load profile or production traffic, so #15 remains a staging smoke pass rather than full production rollout approval.
- The peer hop initially returned 401 because Ktor's private socket address may include a port and Railway's private egress address may differ from the peer DNS answer. The fix parses bracketed and unbracketed IPv6 host/port forms and accepts only private non-loopback sources before the existing HMAC sender/target/nonce checks. The smoke also waits for Redis private DNS propagation before the pilot deploy. Focused ownership tests and the final Railway smoke passed after these fixes.

## Local acceptance recheck before the performance fixes on 09/11/2026

- Full Fault passed all 8 required cases in `.artifacts/room-ownership-local/a50fc6dcf635447c9a2702db63bf4891/fault/faults.json`. Recovery samples were 12.873–15.591 seconds. Redis fail-closed, PostgreSQL no-optimistic-ACK, settlement exactly-once and cleanup all passed.
- The one-backend 20-room/600-second load profile did not meet the 630-second bounded window in three attempts. It reached 51,622, 51,346 and 51,194 acknowledged selections, with cleanup successful. The logs recorded WebSocket request durations of 4–7 seconds near the tail and 1,181–1,191 matches. Those request durations include the socket lifetime and do not isolate handshake latency or establish the throughput bottleneck. The latest receipt is `.artifacts/room-ownership-local/d1913dff406a4d5b8f2ad8b1a5210d4c/load-1`.
- The two-backend load profile stopped at 139 matches after a room creation retry exceeded 25 seconds. It recorded 10 takeovers with recovery under 5 seconds, zero settlement failures and successful cleanup. WebSocket request lifetimes reached 45 seconds while internal ownership commands returned 17–137 ms.
- A 20-room/60-second diagnostic reproduced the bottleneck at 87 selections/s with PostgreSQL pool saturation at 1.0. Increasing the fixture pool to 20 lowered saturation in a control run but did not improve throughput, so that fixture-only setting was reverted. The repository now skips the event-pruning DELETE until revision 257, with a focused regression test. The load gate still needs a faster acceptance environment or a further measured query optimization.
- Planned drain passed `RoomOwnershipReadinessTest`. At this checkpoint the local full-load gate was still open. The later performance revalidation below supersedes that local gate status.

## Local performance revalidation from 09/11/2026

- JFR identified repeated full-room deserialization and its stored retry replies as a major CPU cost. Routing now reads the owner fence, and delivery omits only `processedRequests` while retaining authority and public state. Full command/replay reads retain the original replies. The runner also removes a global room-recycling mutex that unnecessarily blocked independent account pairs behind connection and retry waits.
- Full receipts are `.artifacts/room-ownership-local/9a63f71b5f904e878777b4920de49e57/load-1/load.json` and `load-2/load.json`. Both record `fullAcceptanceProfile=true`, exactly 60,000 selections, 1,200 completed matches and zero selection retries. One backend finished in 603.438 seconds at 99.430 selections/s. Two backends finished in 603.314 seconds at 99.451 selections/s, with recoveries of 15.792, 13.597 and 13.539 seconds.
- Command p95 was 203.373 ms for one backend and 91.881 ms for two. Scheduled-to-ACK p95 was 4.944 and 12.873 seconds. Queue-age maxima were 1.025 and 0.993 seconds, and pool-saturation p95 was 1.0 and 0.8. These observed latency and saturation values inform the later staging envelope.
- Both fixtures passed economy reconciliation and verified complete cleanup. Both build receipts record server JAR SHA-256 `61ee97f7293ec5b09881e9a2e1d5753a6cdf06579ff6378eebd8d2d1f3ff5065`. All 61 focused ownership tests passed against isolated PostgreSQL 18 with zero failures, errors or skips, including planned drain and the new owner/projection regressions. Independent source review found no blocker.
- The final Fault rerun passed all eight cases in `.artifacts/room-ownership-local/a3c43b16feaa4c75acf3c713a9d33baa/fault/faults.json` on that same server JAR. Recovery samples were 12.161–15.498 seconds. Redis fail-closed, PostgreSQL no-optimistic-ACK and exact-once start/settlement checks passed. The fixture cleanup receipt reports zero remaining owned resources.
- The local full-load throughput gate is closed. The cloud fault/load rehearsal and a production rollout decision remain open.

## Proposed staging package

The staging rehearsal requires a separately approved package with:

1. Two backend instances running the exact reviewed build and a private web route.
2. A fresh marked PostgreSQL database and dedicated Redis namespaces; no production database, Redis, credentials, or player IDs.
3. Synthetic `@staging.invalid` accounts and a private SMTP test service if account verification is needed.
4. Private backend ports and `/internal/*`; public ingress only through the staging web route and `/health`.
5. Resource limits recorded before launch. Start with the existing staging envelope: backend 1 vCPU / 1 GiB, PostgreSQL 0.5 vCPU / 0.512 GiB, web and mail 0.25 vCPU / 0.128 GiB, one replica per service.
6. A hard two-hour rehearsal window and a written cleanup time. Stop compute after evidence is recorded; keep or delete volumes only by a separate decision.

## Acceptance gates

- Both backends, PostgreSQL and Redis pass readiness before traffic starts.
- The one-backend baseline and two-backend load profile use the same 20-room, 10-minute configuration as local acceptance.
- Every fault row is injected and recorded; a skipped injection fails the rehearsal.
- Recovery completes within 25 seconds, no selection or reward is lost or duplicated, match identity and epoch remain consistent, and Redis admission fails closed.
- Command latency, scheduled-to-ACK latency, event queue age, and PostgreSQL pool saturation stay within the measured local envelope plus the approved staging margin.
- Planned drain reaches `DRAINING → MAINTENANCE` only after active work is zero, then returns to `ACTIVE`; liveness remains reachable during drain.
- Evidence contains only bounded technical metrics, counts, hashes, route choices, leases, epochs, and cleanup status. Do not export tokens, passwords, email addresses, player IDs, raw request/response bodies, or database contents.

## Stop and cleanup rules

Stop the rehearsal and keep ingress closed if database authority is unavailable, Redis does not fail closed, readiness oscillates, the queue grows without recovery, pool saturation persists, settlement errors rise, or any correctness gate fails. Do not reset quotas, restore a database, or enable a legacy fallback automatically.

After review evidence is saved, remove only the separately approved staging fixtures, verify no deployment remains active, and revoke any temporary access key. Production remains unchanged. A production rollout or replica change requires a new approval.

