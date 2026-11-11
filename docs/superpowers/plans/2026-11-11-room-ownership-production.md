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
