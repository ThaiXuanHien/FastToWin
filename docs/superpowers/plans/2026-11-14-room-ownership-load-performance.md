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

The no-delay Load backend metrics identify the next profiling target. Backend A processed 1,064,773 transaction bodies and 1,023,813 read transactions for 60,000 selections, about 17.75 transactions and 17.06 read transactions per selection; statement executions were 3.77 per selection. Pool saturation stayed below the gate. This points to repeated routing/delivery reads as the RTT multiplier. Any next change must retain fence validation, session-generation checks, lost-wakeup repair and event delivery semantics; no cache shortcut has been accepted yet.

A real PostgreSQL restriction-read regression was added in commit `2f782823`. It verifies one SELECT for the explicit-row path, explicit precedence over legacy status, fresh fallback after committed changes, and missing-user defaults. The focused suite compiled and passed with 0 failures; the current workstation had no `TEST_DATABASE_URL`, so 9 of 10 PostgreSQL tests were skipped and the real fixture evidence is still outstanding.
