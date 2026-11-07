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

