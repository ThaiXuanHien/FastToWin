# Room Ownership Load Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Complete the existing #15 cloud Load/Topology and release gates without weakening600seconds,20rooms,40accounts,60,000selections,1,200matches,default rate policies,pools20 or fail-closed access/presence semantics.

**Architecture:** Use the existing moderation read module for a bounded first optimization: merge restriction-row lookup plus legacy users-status fallback into one fresh parameterized SELECT. Preserve all result precedence/defaults, transaction boundaries, mutation locks, JDBC timeout and error handling. Do not cache authorization or suppress presence repair. Measure this candidate against the fixed3ms local RTT baseline before calling it a throughput fix; further optimization requires its own evidence and review.

**Tech Stack:** Kotlin/JVM17,Ktor,PostgreSQL18,JDBC/Hikari,Gradle tests,isolated Docker fixtures.

**Spec:** Existing closure requirements in2026-11-11-room-ownership-rollout-closure.md; measured failures R11/R12 and local experiment conclusions below.

## Evidence and constraints

- R10 Fault8/8accepted and cleaned. R11 Load221s protocol rejection remains unexplained; R12 Load630s timeout46,972ACKs/929matches and cleanup are verified. No cloud Load or Topology acceptance exists.
- No-delay local Load9c504ede72fd passed60,000/1,200in601.337s. Fixed3ms RTT experiment84a45695c843 exposed synchronized Load owner cohorts; test-only correction86dbfc8d passed settings and review. Repeat0d5a37af3f5d now reaches630s timeout24,140ACKs/473matches with phase command mean379.085ms and recreation mean3,075ms; cleanupallzero.
- The model has comparable preflight SQL SELECT3.64-3.77ms/COMMIT4.55-4.60ms but is quantitatively slower than cloud. It tests sensitivity, not equivalence. Preserve all snapshots and receipts.
- A measured180second window has8,701incomingmessages/131,281ownership read transactions. Three early samples show JDBC moderation/auth/ownership/social reads. These counts motivate reducing proven extra statements, not speculative removal of security checks.
- Redis outage recovery on next unchanged-state selection must remain. A naive semantic-presence cache was rejected by an existing integration test. Current socket-generation/membership checks stay intact.
- Every runtime JAR change invalidates the frozen candidate acceptance for that new candidate. Repeat appropriate real-DB regressions and full local profiles before proposing a new concrete cloud scope. Existing paid scopes cannot be reused.

## Task1: Prove the restriction-read round-trip defect

Files: existing moderation/PostgresModerationRepositoryTest.kt; implementation moderation/PostgresModerationRepository.kt.

- [ ] Add a real-PostgreSQL test using a counting connection around the actual JdbcModerationTransaction read. Require one SELECT for normal,unknown,legacy-blocked,explicit-block/unblock and conflicting legacy/status rows.
- [ ] Assert existing restriction record version/blocked takes precedence, absent record falls back to users.status, absent user remains defaultfalse, and committed changes are observed on the next read without cache.
- [ ] Run against an isolated loopback PostgreSQL fixture; observe RED on existing two-SELECT normal-user path. Keep exact-ID cleanup and no unexpected skips.

## Task2: One fresh restriction SELECT

- [ ] Replace the fallback chain with one bound SELECT over a one-row UUID input LEFT JOINed to restriction and user tables. Return existing view defaults/precedence exactly.
- [ ] Keep repository.read transaction/readonly path and exception translation unchanged. No side-effecting SQL, permission/timeout/caching change.
- [ ] Run all moderation repository/service/integration and established-connection-drop tests relevant to this seam. Verify the new query test GREEN and fresh-state/rollback tests unchanged.
- [ ] Independent source/spec review before committing. Do not describe this as resolving cloud throughput until measured.

## Task3: Controlled performance gate

- [ ] Build a fresh candidate and bind complete classpath, source, image/context and migration manifests.
- [ ] Repeat the identical fixed3ms local controller with corrected cohorts. Retain phase summary, workload metric deltas, rejection diagnostic, ACKcount,all3recoveries and exact cleanup.
- [ ] Compare command/recycle timing and query counts against0d5a37af3f5d. If full target is not met, continue evidence-driven profiling/optimization; do not lower thresholds or increase deadline to earn acceptance.

## Task4: Complete existing acceptance and release sequence

- [ ] Full appropriate backend regressions and local Fault/Load/Topology on final candidate with independently decoded receipts and cleanup.
- [ ] Freeze/review fresh Staging scope and obtain its explicit final approval before cloud mutations. Run remaining acceptance against current candidate, not old receipts relabeled as new.
- [ ] Complete reviewed production package,actual target/config/policy/account mappings,backup/window/rollback/operator and exact release approval,then production rollout/QA. Do not close#15 beforehand.

No cloud or production mutation is authorized by this performance plan. Local read-only experiments and reversible source/test changes continue under the existing task authorization.

## Checkpoint after query optimization (2026-11-14)

The one-query moderation restriction read is committed as `14759360` and its focused repository suite passed (`BUILD SUCCESSFUL`, 14 tasks). The controlled local DB-wire model was repeated with run `room-pilot-2e7dd081f094` against baseline `room-pilot-0d5a37af3f5d`: 1 ms upstream + 2 ms downstream delay, 20 rooms, 40 accounts, 600-second window, unchanged rate policy and resource caps. The candidate reached 26,317 selections / 517 matches before the 630-second timeout; baseline reached 24,139 / 473. Command p95 improved from 475.4 ms to 415.3 ms. Exact cleanup reported zero containers, networks, volumes and images.

This is measurable local improvement, not acceptance. The full Load gate remains open because the target is 60,000 selections / 1,200 matches within the bounded window. No cloud or production mutation was performed.

The same candidate also passed the no-delay local regression as run `room-pilot-603d6ce7c8b2`: 60,000 selections, 1,200 matches, 601.239 seconds, 99.794 selections/second, command p95 190.6 ms, zero retries. Load and latency exports were independently decoded against the run manifest/JAR, and exact cleanup reported zero containers, networks, volumes and images. The fixed RTT model remains the limiting case; this no-delay pass does not close the cloud Load/Topology gates.

The candidate also passed the local Fault profile as `room-pilot-20635a0af518`: all 8 cases passed, maximum recovery 14,767 ms, independently decoded fault/latency receipts, and exact cleanup was zero containers, networks, volumes and images. Fault acceptance is therefore locally green; cloud Load and Topology acceptance remain open.

The candidate completed the local Topology profile as `room-pilot-0e80fe531338`: topology preflight, presence/ingress, lanes, outages and drain all verified. Topology and latency exports were independently decoded against the run manifest/JAR; exact cleanup reported zero containers, networks, volumes and images. Local Fault, no-delay Load and Topology are green. The fixed RTT Load model still times out, and no cloud Load/Topology acceptance exists yet.

The no-delay Load backend metrics identify the next profiling target. Backend A processed 1,064,773 transaction bodies and 1,023,813 read transactions for 60,000 selections, about 17.75 transactions and 17.06 read transactions per selection; statement executions were 3.77 per selection. Pool saturation stayed below the gate. These are process-lifetime aggregate ratios that include setup, background reconciliation, presence and delivery; they do not measure the cost of an individual selection. Repeated routing/delivery reads remain a hypothesis to test with operation-level deltas. Statement-execute counters exclude first-statement and single-read stages, so 3.77 is not the total SQL statement count per selection. Any next change must retain fence validation, session-generation checks, lost-wakeup repair and event delivery semantics; no cache shortcut has been accepted yet.

A real PostgreSQL restriction-read regression was added in commit `2f782823`. It verifies one SELECT for the explicit-row path, explicit precedence over legacy status, fresh fallback after committed changes, and missing-user defaults. The focused suite compiled and passed with 0 failures; the current workstation had no `TEST_DATABASE_URL`, so 9 of 10 PostgreSQL tests were skipped and the real fixture evidence is still outstanding.

The PostgreSQL fixture was rerun after correcting the test fixture timestamp type. The moderation suite now executed all 10 tests against PostgreSQL 18 with 0 skipped, 0 failures and 0 errors; the exact-ID fixture cleanup completed successfully.

## Ownership regression checkpoint (2026-11-14)

The complete ownership test selection was executed with PostgreSQL 18 and forced task rerun: 273 discovered tests, 269 passed, 4 skipped, 0 failures/errors (6m27s). The four skips required Redis. A separate forced rerun of `RoomOwnershipProductionWebSocketTest` with both PostgreSQL 18 and Redis then passed all 13 cases with zero skips/failures/errors (1m). Together these runs exercised all 273 distinct ownership cases; the 9 WebSocket cases already executed in the first run were repeated. Every temporary container was removed by verified exact ID, including its anonymous volumes. Compact receipts are `.artifacts/room-query-regression-20261114/ownership-pg-results.json` and `websocket-pg-redis-results.json`.

No further application behavior changed in this checkpoint. The proposed snapshot-generation shortcut was rejected by automatic review and remains unapplied. Next performance work needs operation-level attribution while retaining a fresh generation check before each send; the aggregate counters alone do not justify removing an authorization query. Cloud acceptance and production rollout remain outstanding.

## Command-window query attribution (2026-11-14)

Added test-only prepared-statement execution profiling around the existing real Netty/PostgreSQL/Redis fixture. Closed labels select call sites from stack frames (including both Kotlin lambda classes and direct lambda methods); no SQL, bound values, account IDs, tokens or exception text are exported. Delegate SQLExceptions retain their original type. Existing application and authorization code is unchanged. The new test asserts both clients receive each acknowledged board and that fresh socket checks are still executed.

Verification: 14 `RoomOwnershipProductionWebSocketTest` cases plus 2 profiler tests passed, with zero skipped/failures/errors. The first attribution attempt exposed lambda-class-only classification and failed its explicit socket-check assertion; bytecode inspection and a regression for direct lambda methods corrected the classifier. Final logs and compact results are `.artifacts/room-query-regression-20261114/profile-verification.log`, `query-profile-results.json` and `query-profile-safe.txt`. All fixture containers and anonymous volumes were removed by verified exact ID.

The 10-selection window counted A: 80 socket-generation checks, 30 readEvents, 30 readState and 39 presence reads; B had 13 readState calls. Concurrent background work remains included. These are prepared JDBC execute invocations (a batched invocation can contain multiple SQL statements), not total SQL statements, wall-clock attribution or per-command causal costs. Plain Statement execution is outside this profiler. Stack collection also changes timing; use counts for call-site investigation only. The fixture uses one B-owned room and pool size 5, unlike the full 20-room/pool-20 acceptance. The large `other` bucket remains unclassified, including work already visible in the idle window. Do not infer that socket checks dominate total latency or remove them based on this result. Next work is to split the unclassified/background paths and preserve fresh authorization checks while testing a concrete round-trip reduction.

The repository-level attribution was refined with a final real-fixture run. In the 10-selection WebSocket window, backend A recorded 2,442 prepared executions from `PostgresPlayerProfileRepository.findByPlayerId`, 80 `isCurrentSocket` checks, 40 presence reads, 30 event reads and 30 room-state reads. The classifier itself passed the dedicated tests and the full 14-case WebSocket class. The profile counters include the idle 200 ms setup window, background callbacks and batched prepared statements, so they identify the dominant repository family while leaving the exact caller and wall-clock contribution unresolved. No runtime behavior was changed.

The profile fanout hypothesis was isolated to the in-memory moderation fixture. The diagnostic was rerun with `PostgresModerationRepository`, which exercises the production moderation path. In the 10-selection window, profile repository calls were zero. Backend A recorded 114 moderation executions, 80 current-socket checks, 39 presence reads, 30 event reads and 30 room-state reads. The previous 2,442 profile executions came from `GameEngine.publishMemoryTarget` while the test used `InMemoryModerationRepository`, so they do not describe the production path. This harness now selects PostgreSQL moderation whenever query profiling is enabled.

The production-like query profile was refined to label `JdbcModerationTransaction` executions. In the 10-selection window, backend A recorded 114 moderation row reads, 80 current-socket checks, 39 presence reads, 30 event reads and 30 room-state reads. The duplicate moderation check in `Application.kt` is visible in the call path, but automatic approval review rejected removing the inner production WebSocket check because it could weaken the account-block boundary. Runtime code remains unchanged. A focused test-only change therefore records the attribution while retaining both checks.

## Moderation gate deduplication (2026-11-14)

Production WebSocket dispatch now carries a local moderation gate token created by the outer message loop after `isBlocked` succeeds. `dispatchProduction` verifies the token belongs to the same account and reuses that result. The account-block enforcement remains in the outer loop and no blocked account reaches dispatch.

Verification passed the blocked-session regression and the ownership WebSocket class. In the production-like query profile, moderation executions fell from 114 to 104 across the 10-selection window. The reduction is one moderation read per owned command in this fixture. The fixture removed both PostgreSQL and Redis containers by exact ID.
