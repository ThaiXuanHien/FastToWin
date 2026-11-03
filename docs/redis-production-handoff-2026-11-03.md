# Redis rate-limit production handoff — 03 November 2026

Status: the Owner-approved backend cutover, two-player Web QA, attended observation and temporary-key cleanup completed on 03 November 2026. Times below are Viet Nam (UTC+7).

## Deployed scope and evidence

The Owner explicitly authorized one attended operation, 08:34:25–10:34:25, under approval `call_3b2f5a3244514691b016c3e2e5866b14`. Only the FastToWin Alpha production backend was replaced. Existing Redis, Web and PostgreSQL deployments were not replaced; no database migration, quota reset, RAM fallback, Play release or provider-flag activation was performed.

| Item | Recorded identity |
| --- | --- |
| Backend service | `c08bdb48-d6ff-4816-b6bb-d3cdabb794b6` |
| Removed backend deployment | `86f53b3f-7af5-4a76-8b8d-6a1e4d8f32d7` |
| New backend deployment | `96dd99bf-52b0-491b-90e5-57946b9e4d42` |
| Runtime source | `79d227afcad580986e90f51f4df84744a53d2b24` |
| Frozen backend ZIP SHA-256 | `22cb29aaf6e09572cc9d4b0e1a36c4f912bf008922865c091a1ecabf4c2a2fbd` |
| Redis deployment | `e9b49229-5e77-4f87-95aa-6aec62ff353c` |
| Redis volume | `3d5db918-8ffa-47ba-8677-3d81f3dc6a99`, `/data`, 5 GB |
| Store / namespace | `redis` / `prod_main` |

Backend limits remain one replica, 8 vCPU and 8,000,000,000 bytes; Firebase start command, `/health` timeout 120 seconds, ON_FAILURE/3 and sleep-off settings were preserved. Redis remains private-only, one replica, 0.25 vCPU and 250,000,000 bytes, with its existing AOF/noeviction configuration and 64-MiB Redis memory limit. Only the three approved rate-limit variables were added, with the app role alone delivered to the backend; no credential is recorded here.

## Actual sequence

- 09:10:15: Owner UI saved planned warning version 22, five-minute warning and 60-minute persisted old-backend limit, with a separate 15-minute operational drain budget.
- 09:15:15: maintenance reached version 24. Fresh UI aggregate work was zero; protected backend metrics showed zero WebSockets and live `/status` matched the policy before stopping.
- 09:16:24: old-backend stop acknowledged; subsequent metadata proved REMOVED with no active backend before staging variables.
- 09:22:08: new application startup logged. All 176 runtime distribution files matched the approved package. Flyway validated 68 migrations and reported V69 up to date, with no migration needed.
- 09:25:32: Owner UI saved ACTIVE version 25; the subsequent UI/runtime observation at 09:25:50 confirmed effective ACTIVE version 25.

One local executor defect was caught before any upload request: its post-stop UI freshness requirement expired while Admin was unavailable on the stopped backend. The fix validates the original closure witness at the acknowledged stop time, without relabeling it fresh. Fresh infrastructure metadata must still prove the exact old deployment REMOVED and no active backend; settings, backup checks and the original operational cutoff remain enforced. Before-stop and new-runtime freshness checks remain unchanged. The reproduction failed before the fix; all 51 local executor tests then passed, and scoped review found no blocker. No stop, variable mutation or upload was replayed.

## Acceptance

The new backend passed health/status, exact runtime hashes and protected finite-counter checks. Financial LIVE, AdMob and mandatory content terms remain false. Startup log review found no credential-bearing Redis URL, private-key marker, exception marker or error-level record; raw logs were not retained.

The separately reviewed two-class synthetic probe ran once and cleaned its owned temporary directory through a successful exit trap. Redis startup took 1,634 ms. The bounded load completed 197 allowed consumes, zero limited/unavailable, with p99 16,639 microseconds; the one-token boundary, outside-namespace denial and unauthenticated-user denial passed. These timings are from the independent synthetic client, not game-route latency.

Owner logged QA53_1028 into Web. Its authenticated WebSocket connected, a private casual room was created, and Owner joined as the second QA player `hienqp`. The UI confirmed both players waiting; the host then closed the room and returned to an empty lobby. No match, ready action, purchase, gift, donation or message was performed. QA53 displayed 50 Gold and 0 Gem before and after. Shared backend counters moved from allowed 0 before reopening to 4, then 16 after QA; limited/unavailable remained zero at those snapshots.

The final snapshot at 09:41:32 was 942 seconds after the confirmed ACTIVE observation at 09:25:50. Health remained OK, policy ACTIVE/25 and counters allowed 16, limited 0, unavailable 0. The 155 inspected startup/observation log lines had no error/exception markers or credential-bearing Redis URL; raw logs were not retained. Fresh infrastructure metadata matched the baseline except the intended backend deployment: one backend replica, unchanged Redis deployment/limits/private exposure/READY volume mount, and unchanged Web/PostgreSQL deployments. Staging was not targeted by any operation.

Railway CPU/RAM charts were inspected and remained well below retained limits. This grant did not permit Redis SSH or use of the monitor credential: direct live AOF byte-growth and eviction counters were not measured. Adding those measurements remains monitoring follow-up; they are not asserted from the provider charts.

Temporary key `91dc1913-0c19-4325-90cf-e2dad65e42af` was revoked after observation. A separate complete inventory check at 09:42:37 confirmed its exact ID, name and fingerprint absent. Owned request directories and plaintext SSH keys were absent; the probe's owned remote directory was cleaned by its successful exit trap. No temporary service was created in this cutover. The encrypted backup archives remain retained under their original deadlines.

## Backup, cost and remaining work

The retained Redis backup was verified on 02 November at 19:51. Owner requests the next attended backup by chat at **18:30 on 03 November**; maximum next completion is **19:51**. No recurring automation was created. Redis archive DPAPI retention ends no later than 16 November at 19:51. The DB backup taken 02 November at 22:43:59 was checksum-verified as ciphertext only in this operation; its earlier isolated PG18 restore receipt was revalidated, without decrypting or reading the DB again. Its retention ends no later than 16 November at 22:43:59.

Redis remains an ongoing paid resource, with the approved estimate of about $5/month and review before $10/month; actual billing has not been measured. Backups remain manual. This is a single Redis node, not HA. Redis failures reject new protected actions; recovery, old-image replay, namespace changes, reset or RAM fallback require a separate Owner decision.

Issue #12 remains open for presence and remaining local limits; #15 remains open for distributed room/match ownership. No issue comment was published or issue closed by this operation. Local sanitized evidence is under `.artifacts/redis-production-rollout-20261102/task4/cutover-preparation-20261103/`, with immutable mutation intents/ACKs in `executor/receipts/`. Historical preparation documents describe earlier checkpoints and are superseded by this actual execution record.
