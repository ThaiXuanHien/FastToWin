# Room Ownership Production Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add a production-safe, opt-in room ownership mode for two backend instances after the fixture-only candidate passes a separate rehearsal.

**Architecture:** Keep the existing fixture pilot unchanged for local and staging evidence. Add a separate production configuration and routing policy that never depends on a fixture marker or synthetic account allowlist. Gate production traffic by an explicit opt-in policy, use private peer transport, and preserve legacy handling for accounts and game modes outside the opt-in scope. Rollout proceeds through migration compatibility, a fresh cloud rehearsal, drain/rollback evidence, and a separately approved production window.

**Tech Stack:** Kotlin/JVM, Ktor, PostgreSQL 18 with Flyway, Redis presence/rate-limit stores, Railway private networking, Gradle tests.

**Spec:** `docs/superpowers/specs/2026-11-07-room-match-ownership-design.md`, `.artifacts/room-cloud-supervised-preflight/capability-v3/production-room-ownership-proposal.md`

## Execution checkpoint, 11 November 2026

This checkpoint supersedes historical signatures, counts and candidate hashes below. The old fixture JAR `8fff8ae…` is not the production candidate. Every new execution binds the packaged application JAR and complete runner dependencies to its own manifest.

- Production routing is now invoked by the real WebSocket handler. Readiness checks schema without requiring a fixture marker. Drain reads durable leases and pending settlements; global rollback additionally requires zero open rooms. The fresh affected PostgreSQL/WebSocket/activity/auth/moderation suite passed 164 tests with no failures, errors or skips and clean fixture removal.
- The isolated production seeder validates exact run/database identities and emptiness before migration, creates only synthetic accounts, refuses replay, and never deletes existing users. Docker seed smoke `room-prod-4aef64128564` verified 40 accounts, 40 profiles, zero marker rows and zero owned resources after cleanup.
- Reusable local execution is `scripts/room-production-rehearsal-local.ps1 -Profile Seed|Fault|Load`. Fault/Load use the production runtime and default rate-limit policies, alternating creators and distinct synthetic forwarded source addresses inside an internal Docker network. This does not verify public ingress header sanitization or shared-NAT capacity.
- Full production-like Fault passed eight cases in `room-pilot-c31dc6c3ae3e`, independently decoded and bound to JAR `f6a1799ef0a1c4653d46cf4ccb8e0d067a9414462c2ab54c44aa8d6c791fc9b6`. Maximum recovery was 15,055 ms; the PostgreSQL-pause case recovered in 3,170 ms. All owned containers, networks, volumes and images were zero after cleanup. Earlier failures exposed the fixture-marker latency probe, the runner's missing writable receipt directory and unbounded production authorization reads. Those remain failed receipts. The latter was reproduced by three real established-connection reply-loss tests before applying a 3-second JDBC network budget to session/moderation reads; all three then passed. This changes the candidate production code, not the running deployment.
- The 600-second Load `room-pilot-bd207760257d` passed on that same application JAR: 60,000 acknowledged selections, 1,200 completed matches, 601.187832 seconds elapsed, 99.802419 selections/second, zero retries, pools 20/20, and recoveries 14,961 / 12,853 / 16,986 ms. Independent receipt/context/image-manifest verification is `.artifacts/production-local-independent-20261111.json`. All owned resources were zero after cleanup. Command p95 was 197.398 ms; scheduled-to-ACK p95 was 15.531 seconds including injected outages. This is local correctness/load evidence, not a production latency promise.
- Before cloud/production rollout, also close the legacy/owned concurrency seam, define an enforceable backend topology, integrate owned-room presence state, and execute explicit cluster drain/rollback checks. A private second backend still starts legacy restore/timers today; hiding its ingress alone does not isolate that work.

Remaining sequence: implement and test the topology/drain gaps in [the closure plan](2026-11-11-room-ownership-rollout-closure.md); repeat acceptance on the resulting frozen candidate; freeze a concrete new cloud scope; run cloud capability/fault/load/drain/rollback and cleanup; review branch integration and release package; execute the authorized production rollout against the reviewed package. Keep this branch isolated until those gates are earned.

## Local acceptance checkpoint on 13 November 2026

The final local Fault, Load and Topology profiles bind candidate
7d55765c15f2e3435ea7e80ffb065df967b7f8bf4fe5441d39e14185a0fc68b0.
Load completed 60,000 selections and 1,200 matches in 601.667822 seconds.
All eight fault cases and five topology groups passed with cleanup.
See the [closure checkpoint](2026-11-11-room-ownership-rollout-closure.md)
for exact runs, latency, pool saturation, regression scope and remaining
Staging gates. Full server verification recorded 1,301 passing tests with
two expected opt-in capacity skips after correcting two fixture settings.
## Railway Staging checkpoint on 13 November 2026

The first approved scope, `0bbace01615fe77b28c70ba3411ee4bd6ca6b26feac9b5230752707a0e684ad5`, seeded successfully but stopped at the seed receipt gate. Railway added `level=info` metadata to structured application fields; the strict application proof rejected that envelope field. The controller cleaned its six journaled services and verified the original nine services and one volume.

The reviewed R2 scope, `eec6ac158fb8422eebc2e051b9294d9669b5de06c647f58b1484778efd4dd9af`, separated known provider metadata from exact application proof fields. Its offline suite passed 54 cases. The separately approved execution began at 14:20:22 UTC. It retained valid seed and occupied-database replay-refusal receipts, including unchanged database hash. When work resumed, its tool session and controller process were absent. The last provider startup logs for A/B contained only `Starting Container`; no application failure or completed workload is established by that evidence.

Recovery removed the six exact service IDs in the R2 journal. Independent read-only preflight at 14:29:14 UTC verified zero owned services, the original nine services and one volume, stopped prior compute, and HOBBY plan. See `.artifacts/room-cloud-production-rehearsal-20261113-r2/execution/interrupted.json`, `fault/interruption-observation.json`, `fault/cleanup.json`, and `post-cleanup-preflight.json`. R2 earned no Fault, Load, Topology, or production acceptance. Both executed scopes and their pinned inputs remain immutable. A fresh attempt requires a separately reviewed scope because each approved scope allowed one attempt per phase.

## Staging recovery checkpoint on 14 November 2026

R9 (scope `a629ad1190f767c4427b54cdf87a9412b624f9379209e1b5c7ccb7b2f9bb3582`) again failed deployment-stop verification. When work resumed, no controller was running and cleanup was incomplete. Recovery deleted only R9's acknowledged journaled service IDs; independently executed read-only preflight at 02:56:33.648 UTC confirmed zero owned services, the original nine services and one volume, no unacknowledged deletes, and HOBBY plan. Evidence is `.artifacts/room-cloud-production-rehearsal-20261113-r9/execution/fault/cleanup.json` and `execution/post-cleanup-preflight-recovered.json`. No cloud Fault/Load/Topology acceptance was earned.

Direct provider observation confirms B deployment `7a1f5a88-fce9-4c73-b00b-91eae7af9a3e` was REMOVED. The old controller searched only active/latest projections, which need not retain a stopped deployment. R10 queries the exact deployment ID and validates service, environment, terminal containers and independent active/latest compute. B replacement requires REMOVED; a naturally completed runner can remain SUCCESS only with an explicit stopped flag, terminal containers and no independent compute. R10 also performs three separate nonce/time/host-bound DNS measurements with stable A/B deployment/container snapshots, preserving disjoint B addresses, signed unchanged A, golden room and all workload gates.

R10 preparation is `.artifacts/room-cloud-production-rehearsal-20261114-r10`, scope `b31572986e216960e2a87b63448feabe43cd8d2f05bd2e639003b6731496d767`. The complete offline suite passed 87/87 tests with no skips; independent review reran 12/12 lifecycle cases and found no remaining blocker. The actual packaged probe class passed three pinned-JRE Docker smoke cases, including public-address refusal, and removed all test containers. Self-test verified all pinned upload contexts. Final read-only preflight at 03:13:16.886 UTC is bound to this scope hash and again confirmed the baseline without mutations. These are preparation receipts, not cloud workload results.

The R10 package contains no inherited execution, launch, or approval records. It uses new prefixes `room-pilot-a387f43fd390`, `room-pilot-cab80cd05d9f`, and `room-pilot-1cd324483b77`; the same six-service, 10.75-vCPU/3.39-GB caps and three-hour limit include 15 minutes reserved for cleanup. Start deadline is 14 November 10:00 UTC (17:00 Asia/Bangkok), cleanup deadline 13:00 UTC (20:00). Explicit new-scope execution approval is still pending. Production is unchanged.

Evidence correction: historical R7/R8 post-cleanup preflight files were manually authored, not independently executed observations; R7 pinned code was changed after execution. Do not use those files to certify historical input immutability or current baseline. R10 binds the actual R9 recovered cleanup and independently executed preflight instead. Earlier timeout-only and DNS-export-only diagnoses do not establish the complete root cause.

## R10 execution result

The user explicitly approved scope `b31572986e216960e2a87b63448feabe43cd8d2f05bd2e639003b6731496d767`. The frozen hidden launcher reserved one execution at 03:16:03 UTC on 14 November, with actual hard cleanup deadline 06:16:03 UTC (13:16 Asia/Bangkok). Controller and host identities are retained in the package's `launch/process.json`. Do not relaunch this scope or change pinned inputs.

Fault completed all eight cases. Maximum recovery was 14,039 ms; Redis process pause recovered in 762 ms and PostgreSQL pause in 3,885 ms. The revised stop gate observed the exact removed B deployment with no active/latest projection; B replacement changed both private address families while signed A process/deployment remained unchanged. Golden rooms verified both owners with all four public sockets on A and B public routes returning 404. The phase's accepted receipts retain the approved scope and frozen server JAR. Exact-ID cleanup confirmed zero owned services and the nine-service/one-volume baseline. R10 ended at 03:52:51 UTC with exit1 during the signed-status runner upload after Load initial capability. The upload error does not establish provider rejection or non-execution: the CLI wrapper discarded its underlying error details, and no new deployment ID was discovered. No Load workload or Topology acceptance was earned. Load exact-ID cleanup and independently executed preflight at 03:53:34.570 UTC again verified zero fixture services, nine baseline services and one volume. No retry was performed.

During the attended cloud run, the draft release initialization manifest was bound to the source-matching `postgres-test-99d0c77e71bb.json` receipt. Independent review confirmed all six source hashes, the frozen JAR/context files, 12 passing real PostgreSQL/runtime cases, STOP_NEW readiness and separate signed resume, and zero local resources. A local production backend image was also built from all 181 hash-matching frozen distribution files using the real `bin/server` entrypoint and a pinned JRE base. Content verification inside image `sha256:37cd14670fbf3c2c6a2402af24eccc8177418a8313397a13db81218e482db0d2` matched all files and removed its test container. This local image has not been pushed, deployed or health/readiness tested. Release status remains DRAFT_NOT_EXECUTABLE; production inputs and execution approval remain pending.

## R11 remaining-profile preparation

The next concrete scope is `e575ebc714e0dabf0e664e17626673cd86ca643445ae58653e72cdfc2626a8fc` in `.artifacts/room-cloud-production-rehearsal-20261114-r11`. It runs only Load and Topology once each, retaining R10 Fault under its original scope hash after revalidating source pins, candidate/context/baseline, approval, seed/replay/exports, replacement, signed ingress observations, final deployments and cleanup. Aggregate acceptance remains conditional on both new phases and their cleanup. No backend JAR, classpath, upload-context, probe, workload-gate, retry or timeout behavior changed.

The CLI repair preserves safe exit/signal/timing/output-size metadata and allowlisted error hints, without raw arguments, stdin, output, URLs, credentials or child Error objects. A harmless failing child through the actual R10 wrapper reproduced the missing diagnostic before the fix. This fixes the evidence loss; it does not identify or claim to fix the original upload cause. The original timeout/network/provider cause and failed-upload acceptance remain unknown.

All97 offline tests passed with no skips; independent review reran10 new process/retained-proof tests and found no blocker. Self-test verified pinned contexts and retained Fault. The independently executed read-only preflight at04:05:17.084 UTC binds the final scope hash and confirms9baseline services/1volume, zero fixture services and zero mutations. R11 has no execution, launch or approval record. Prefixes are `room-pilot-40135b460ad9` and `room-pilot-b8157b59f4c8`; caps remain six concurrent temporary services,10.75vCPU/3.39GB RAM and at most three hours including15minutes cleanup. Start by14November17:00 and cleanup by20:00 Asia/Bangkok, subject to the earlier actual launcher deadline. Explicit new-scope execution approval is pending.

## R11 attended execution

The user approved R11 explicitly and asked how long it would run and whether skipping affects production readiness. The timing estimate was60-90minutes, with a hard three-hour maximum; skipping postpones the remaining cloud acceptance and release readiness, while the currently deployed production app is unchanged. The frozen hidden launcher started at04:07:40 UTC on14November with actual hard cleanup deadline07:07:40 UTC (14:07Asia/Bangkok). Scope is `e575ebc714e0dabf0e664e17626673cd86ca643445ae58653e72cdfc2626a8fc`; controller PID19200 and host PID16768 identities are journaled in `launch/process.json`. R10 Fault was revalidated before cloud operations. The execution finished at04:35:53 UTC with exit1 after the actual Load runner reported protocol-rejected. All setup, private DNS, signed ingress, B replacement and golden-room gates passed first. Do not rerun or modify the approved frozen package. No production mutation is authorized by this Staging execution.

## R11 Load failure investigation

The checksum/scope-bound failure receipt reports221.010seconds elapsed,383completed matches and19,697command attempts before protocol-rejected. The receipt does not preserve the exact rejection code. Topology never started; no Load or aggregate cloud acceptance was earned. Exact service-ID cleanup and independently executed preflight at04:37:00.360 UTC confirmed zero fixture services,9baseline services and1volume, without unacknowledged deletes. Provider logs and decoded failure/latency exports are retained under R11's `execution/two-backend`. Backend I/O errors around cleanup are not established as the original cause.

Investigation found that `runRehearsal` wrote the existing safe, allowlisted protocol code and phase to `failure-sites.json` inside the disposable runner, but exported only the broader category. A test-only repair now also prints that diagnostic as `FTW_RUNNER_DIAGNOSTIC`. No exception message, identity, raw stack or secret is added. The first prefix conflicted with the production receipt reader's deliberate staging-namespace refusal; review caught it, a real-R11-export regression reproduced it, and a separate neutral prefix fixed it without changing the reader. Final focused Kotlin tests passed11/11; the repository receipt suite passed20/20 including the actual-printer-prefix compatibility regression. Independent review found no remaining blocker in the output path or privacy boundaries. Runtime/server JAR remains unchanged.

A full600-second local Load reproduction is running in `.artifacts/room-production-local/room-pilot-9c504ede72fd`, bound to the same candidate/JAR. Its frozen runner source contains the earlier diagnostic prefix, before the compatibility fix; retain that fact rather than relabel its inputs. No new cloud attempt is prepared or approved. The actual R11 rejection code/root cause remains unknown until stronger evidence exists.

The local release image's actual `bin/server` entrypoint was additionally verified against isolated PostgreSQL/Redis using12real SQL/runtime cases. `runtime-smoke/postgres-test-019a2b5da049.json` binds the runner sources and local image metadata, records STOP_NEW health/readiness [200,200,503,503], separately signed resume to generation3, and zero owned local containers/networks/images/volumes after cleanup. This evidence is linked in the draft release manifest; image remains local, unpushed and undeployed.

## Global Constraints

- The current `pilot` mode remains restricted to `dev` and fixture accounts.
- Production room ownership must not use `FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE=true`.
- Production auth timeout remains unchanged until a separate timeout change is designed and tested.
- Redis namespace `prod_main` and the existing rate-limit behavior remain unchanged.
- No production mutation occurs during implementation or rehearsal preparation.
- Every cloud rehearsal uses synthetic accounts, private networking, bounded resources, one attempt per phase, and cleanup by journaled service IDs.
- Rollback must stop new opt-in room creation, drain active work, and verify no pending owned settlement before removing the route.

---

### Task 1: Freeze migration and schema compatibility evidence

**Files:**
- Create: `.artifacts/room-cloud-supervised-preflight/capability-v3/production-migration-check.json`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/ownership/PostgresRoomOwnershipRepositoryTest.kt`
- Check: `server/src/main/resources/db/migration/V70__room_ownership_pilot.sql`
- Check: `server/src/main/resources/db/migration/V71__room_ownership_pilot_settlements.sql`
- Check: `server/src/main/resources/db/migration/V72__room_ownership_pilot_fixture.sql`

**Interfaces:**
- Produces a receipt proving V70–V72 apply after V69 and that the ownership repository tests run against the resulting schema.
- Does not alter migration files.

- [ ] **Step 1: Run the isolated PostgreSQL integration suite**

Run from the worktree:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
pwsh -NoProfile -File .artifacts/room-cloud-supervised-preflight/capability-v3/gradle-test-local.ps1
```

Expected: 120 tests, 0 failures, 0 errors, 0 skipped, and cleanup receipt with `containers=0`, `networks=0`, `volumesUnchanged=true`.

- [ ] **Step 2: Record the migration evidence**

Write `production-migration-check.json` with the test-count hash, server JAR SHA-256 `8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818`, migration names V70–V72, and `productionRestoreVerified=false`.

- [ ] **Step 3: Review the gap**

Confirm the receipt does not claim a production restore. A real production backup restore remains a separate approval-gated operation.

---

### Task 2: Add a production policy boundary

**Files:**
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipPilotConfig.kt`
- Create: `server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipProductionPolicy.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipAcceptanceSettingsTest.kt`

**Interfaces:**
- `RoomOwnershipProductionPolicy.fromEnvironment(values: Map<String,String>): RoomOwnershipProductionPolicy?`
- `RoomOwnershipProductionPolicy.allows(account: AuthenticatedAccount, mode: ProtocolGameMode): Boolean`
- `RoomOwnershipProductionPolicy.routeKey(accountId: UUID): String`

- [ ] **Step 1: Write failing tests**

Cover these exact cases:

```kotlin
assertFailsWith<IllegalArgumentException> {
    RoomOwnershipPilotConfig.fromEnvironment("production", fixtureValues)
}
assertNull(RoomOwnershipProductionPolicy.fromEnvironment(emptyMap()))
assertFailsWith<IllegalArgumentException> {
    RoomOwnershipProductionPolicy.fromEnvironment(mapOf("FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production"))
}
```

- [ ] **Step 2: Run the focused test**

Run:

```powershell
./gradlew.bat :server:test --tests '*RoomOwnershipAcceptanceSettingsTest' --offline --no-daemon --console=plain
```

Expected: the new production-policy assertions fail before implementation.

- [ ] **Step 3: Implement the boundary**

Require an explicit production policy identifier, a signed or provider-managed opt-in source, a private peer URL for each backend, and a separate secret from Redis credentials. Reject fixture-only variables, synthetic account lists, `FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE`, and public peer URLs. Keep `pilot` behavior unchanged.

- [ ] **Step 4: Run focused tests again**

Expected: all policy tests pass and the existing fixture rejection tests remain green.

Current checkpoint: the standalone `RoomOwnershipProductionPolicy` parser and focused tests are green. It is intentionally not wired into `Main` yet because `RoomOwnershipPilot` still requires fixture schema and fixture account verification.

---

### Task 3: Route only the approved production slice

**Files:**
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipPilot.kt`
- Create: `server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipRouting.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipRoutingTest.kt`

**Interfaces:**
- `RoomOwnershipRouting.route(accountId: UUID, gameMode: ProtocolGameMode): RouteDecision`
- `RouteDecision.Legacy`
- `RouteDecision.OwnedRoom(routeKey: String)`
- `RouteDecision.Reject(reason: String)`

- [ ] **Step 1: Write failing routing tests**

Cover opt-in CASUAL/ORDER accounts, non-opt-in accounts, unsupported modes, reconnects, and a mixed room where one account is outside the approved policy.

- [ ] **Step 2: Implement routing**

Keep legacy routing for non-opt-in accounts and unsupported modes. Require every member of an owned room to satisfy the same policy. Reconnect must reuse the same route key. Never expose peer control endpoints publicly.

- [ ] **Step 3: Run routing and ownership tests**

Run:

```powershell
./gradlew.bat :server:test --tests '*RoomOwnershipRoutingTest' --tests '*PostgresRoomOwnershipRepositoryTest' --offline --no-daemon --console=plain
```

Expected: all focused tests pass.

---

### Task 4: Add drain and rollback guards

**Files:**
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomAdmissionGate.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipPilot.kt`
- Create: `server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipRollbackGuard.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipRollbackGuardTest.kt`

**Interfaces:**
- `beginDrain(): DrainToken`
- `awaitSettled(deadline: Instant): DrainReceipt`
- `canRollback(receipt: DrainReceipt): Boolean`

- [ ] **Step 1: Write failing drain tests**

Require new room creation to be rejected after drain begins, admitted transactions to finish, active sockets to reconnect through the legacy path, and rollback to fail when owned settlements remain.

- [ ] **Step 2: Implement the guard**

Close admission, await committed transactions, require zero pending settlements and zero owned leases, then permit route removal. Keep liveness available while readiness is false.

- [ ] **Step 3: Run drain tests**

Run:

```powershell
./gradlew.bat :server:test --tests '*RoomOwnershipRollbackGuardTest' --tests '*RoomOwnershipReadinessTest' --offline --no-daemon --console=plain
```

Expected: all drain and readiness tests pass.

---

### Task 5: Build and run the production-like cloud rehearsal

**Files:**
- Create: `.artifacts/room-cloud-production-rehearsal/<run-id>/scope.json`
- Create: `.artifacts/room-cloud-production-rehearsal/<run-id>/runbook.md`
- Reuse: `.artifacts/room-cloud-supervised-preflight/capability-v3/run-staging.mjs`
- Reuse: `.artifacts/room-cloud-supervised-preflight/capability-v3/contexts.json`

- [ ] **Step 1: Create a fresh scope**

Pin the production candidate JAR, image digests, project/environment IDs, resource limits, private domains, synthetic account count, fault cases, load duration, deadline, and cleanup journal. Set `approved=false`.

- [ ] **Step 2: Run local self-test and read-only preflight**

Expected: scope hashes, contexts, existing service identities, volumes, attachments, and workspace plan match.

- [ ] **Step 3: Obtain a separate execution approval**

Do not reuse the previous Staging approval. The approval must name the new prefix, deadline, resource caps, and cleanup rule.

- [ ] **Step 4: Execute capability, fault, load, drain, and rollback checks**

Stop at the first failed gate. Preserve receipts and clean all journaled IDs. Do not create public ingress or persistent volumes.

---

### Task 6: Prepare the production rollout package

**Files:**
- Create: `.artifacts/room-production-release/<release-id>/manifest.json`
- Create: `.artifacts/room-production-release/<release-id>/rollback.md`
- Create: `.artifacts/room-production-release/<release-id>/qa-checklist.md`
- Check: `docs/redis-production-operations.md`

- [ ] **Step 1: Pin candidate artifacts**

Record JAR, server distribution, Redis image, backend image, migration list, and configuration key names. Never record secret values.

- [ ] **Step 2: Write rollback procedure**

Drain opt-in rooms, verify no pending settlement, disable production policy, restore one-backend routing, and retain the old deployment until QA passes.

- [ ] **Step 3: Write QA checklist**

Verify presence online/offline, rate-limit behavior, room creation, reconnect, owner failover, settlement exactly-once behavior, health/readiness, and monitoring counters.

- [ ] **Step 4: Request production approval**

Production approval must name the exact release manifest, maintenance window, QA accounts, backup receipt, rollback deadline, and operator responsible for observing the window.

---

## Verification checklist

- [x] Fixture local suite is green with cleanup evidence.
- [ ] Production policy rejects fixture-only settings.
- [ ] Routing tests cover mixed membership and reconnect.
- [ ] Drain and rollback guards are green.
- [ ] Fresh cloud rehearsal passes capability, fault, load, drain, and cleanup gates.
- [ ] Production release manifest and rollback package are reviewed.
- [ ] No production room-ownership mutation has occurred before the final approval.
