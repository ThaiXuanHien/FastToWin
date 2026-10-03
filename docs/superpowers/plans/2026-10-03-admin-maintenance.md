# Admin Maintenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Owner controls durable planned/emergency maintenance, with server enforcement, safe game draining and receipt completion, and responsive admin/player feedback.

**Architecture:** A maintenance module owns one durable versioned snapshot, audit and replay, with an effective environment override. Runtime applies the snapshot to GameEngine admission and lifecycle; existing auth/gift/store paths share explicit policy rather than a blanket HTTP block. Admin and shared client consume additive protocol fields.

**Tech Stack:** Kotlin/Ktor, PostgreSQL/Flyway/JDBC, kotlinx.serialization, native admin JavaScript/dialogs, shared Compose, existing Chromium/Gradle/screenshot tooling.

**Spec:** `docs/superpowers/specs/2026-10-03-admin-maintenance-design.md`; canonical approved GitHub Issue #23.

## Global Constraints

- States ACTIVE, ANNOUNCING, DRAINING, MAINTENANCE; modes PLANNED/EMERGENCY.
- Planned warning default 5 minutes, range 0–60; drain default 15 minutes, range 1–60. Expected end is advisory, never automatic reopening.
- Owner mutations, Operator/Support reads; current-role/CSRF/exact Origin, expectedVersion, request ID, atomic audit and replay.
- Runtime applies committed policy within 5 seconds; `FASTTOWIN_MAINTENANCE=true` overrides DB and cannot be disabled through admin.
- UTC persistence/Bangkok display; public status never contains internal reason/actor. Existing maintenance Boolean true only at MAINTENANCE.
- Finish already admitted economic transactions atomically; pending paid receipts may finish independently of game socket; no duplicate grants.
- Existing sessions cannot bypass policy. Drain allows existing started matches/resume, not new rooms/matches/rematches/tournament rounds.
- Deadline/emergency aborts incomplete matches without loss/Elo/unfinished rewards; already committed results remain. Persist abort before normal reconnect/cleanup can score it.
- Failed policy read blocks new intent; unavailable startup does not serve game. No-DB application cannot pretend to persist maintenance controls.
- Mandatory ui-ux-pro-max, white Material admin override; player Arcade master. No CDN/CSP relaxation, private caching or unlocalized UI copy.
- Work stays on existing non-main local preview checkout; no push/merge/deploy/production campaigns/issue closure. Preserve untracked/cache/secret artifacts.
- Tests use loopback disposable PostgreSQL settings and isolated schemas. Do not reset/drop existing databases or print credentials.

## File/module map and seams

- `protocol/.../protocol/MaintenanceProtocol.kt`: additive common enums/status and paid-receipt DTOs; `AuthProtocol.kt`: backward-compatible status fields.
- `server/.../maintenance/`: admin DTOs, repository, Postgres persistence, service, pure effective-state policy and routes. `V51__add_runtime_maintenance.sql`: state/replay storage.
- Existing `Database.kt`, `Main.kt`, `Application.kt`: dependency/runtime integration only; maintenance loops/HTTP guards extracted into module files.
- `GameEngine.kt`: policy synchronization with actual start transitions, drain/abort/resume and settlement boundaries. Do not restructure unrelated engine behavior.
- `StorePurchaseService.kt`: one shared verification/grant workflow extracted from GameEngine, with HTTP finishing route and shared client adapter.
- `admin-maintenance.js`: independent module using existing admin API/modal/channel adapter; existing admin assets/navigation/CSS only integrate it.
- `shared/.../ui/components/MaintenanceBanner.kt`: accessible banner; existing app shell/status/billing paths integrate without dropping live match state.

### Task 1: Durable maintenance policy and protected admin HTTP contract

**Files:**
- Create `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/MaintenanceProtocol.kt`.
- Modify `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/AuthProtocol.kt`.
- Create `server/src/main/resources/db/migration/V51__add_runtime_maintenance.sql`.
- Create `server/src/main/kotlin/com/hienthai/fastowin/server/maintenance/{MaintenanceModels,MaintenanceRepository,PostgresMaintenanceRepository,MaintenanceService,MaintenanceRoutes}.kt`.
- Modify `server/src/main/kotlin/com/hienthai/fastowin/server/{Database,Application}.kt` only for testable optional module injection; do not enable the incomplete control in Main yet.
- Create focused service/routes/Postgres tests under `server/src/test/kotlin/com/hienthai/fastowin/server/maintenance/` and protocol serialization tests.
- Create `docs/admin-maintenance.md` with the exact contract for later workers.

**Interfaces:**
- Common enums `MaintenanceState`, `MaintenanceMode`; additive ServiceStatusResponse fields state/mode/version/messageVi/messageEn/transitionAtEpochMillis/expectedEndEpochMillis, with defaults so legacy JSON decodes.
- `MaintenanceAdminContext(token: String?, csrfToken: String?, origin: String?)`.
- `MaintenanceUpdateRequest(expectedVersion: Long, requestId: String, reason: String, mode: MaintenanceMode?, cause: String?, messageVi: String?, messageEn: String?, warningMinutes: Int = 5, drainMinutes: Int = 15, expectedEndEpochMillis: Long? = null, enabled: Boolean)`; all fields serialized/documented consistently.
- `MaintenanceRepository.transaction` supplies one connection-bound maintenance/admin transaction like GiftRepository, reusing the admin advisory lock for authorization/revocation ordering.
- `MaintenanceService.read(context)`, `update(context,request)`, `refresh(activeMatches: Int)`, `publicStatus()`, `effectivePolicy()`; document precise return types in contract/report. Clock injectable. `refresh` advances due states and empty drain atomically, conditional on current version.
- `GET /admin/api/maintenance`; `POST /admin/api/maintenance`; history via existing audit. Read response distinguishes persisted and effective state/source plus runtime observation. No write when environment override prevents reopening.
- A pure `MaintenancePolicy` exposes `blocksNewIntent`, `allowsExistingMatch`, `isFullyClosed`; environment override takes precedence. Unavailable policy is a separate safe state, not ACTIVE.

- [ ] Write service/repository/HTTP behavioral tests before implementation: role/Origin/CSRF; same request/payload replay vs409; stale version; concurrent disable/automatic transition; audit failure rolls back; restart/due time; real auth SQL failure503; override; public data masking. Run focused test command and preserve actual failing output.

  Exact pure-policy cases for the suite:
  ```kotlin
  @Test fun drainingKeepsCurrentMatchOnly() {
      val policy = MaintenancePolicy(MaintenanceState.DRAINING)
      assertTrue(policy.blocksNewIntent)
      assertTrue(policy.allowsExistingMatch)
      assertFalse(policy.isFullyClosed)
  }
  @Test fun emergencyClosesGame() {
      val policy = MaintenancePolicy(MaintenanceState.MAINTENANCE)
      assertTrue(policy.blocksNewIntent)
      assertFalse(policy.allowsExistingMatch)
      assertTrue(policy.isFullyClosed)
  }
  ```
- [ ] Implement the snapshot transaction and service. Use validation: reason 1–500 chars, messages 1–1000 chars when enabled, safe causes enumerated above, integer ranges as Global Constraints, canonical request identity/fingerprint, checked timestamp arithmetic. Exclude control characters from reason; allow normal message newlines as text. Store replay result in its own request table; preserve actor fields for automatic transitions.
  ```kotlin
  // Inside the same JDBC transaction, after the shared admin lock:
  val actor = adminService.authorize(tx.admin, context.token,
      context.csrfToken, mutation = true, ownerOnly = true)
  // Current authorization precedes replay; a replay cannot bypass revocation.
  // Lock current state, reject expectedVersion mismatch, persist snapshot,
  // append safe audit, persist replay, then commit or roll all writes back.
  ```
- [ ] Register optional admin routes for tests only; validate JSON/body size and catch storage errors without swallowing CancellationException. Add no-store and safe503 to status/reads. No-DB Main retains existing environment operation until complete runtime Task2; expose persistence unavailable when durable control is requested without DB.
- [ ] Run `:protocol:test :server:test --tests '*maintenance*'` with real local TEST_DATABASE settings, inspect XML failures/errors/skips, run localized text scanner and diff check. Document DTO/paths/failure source/override semantics and the exact test evidence.
- [ ] Stage only task files, commit `feat: add durable admin maintenance policy`, write task-1-report.md. Do not expose control as production-ready before Task2/3/4 integration.

### Task 2: Runtime enforcement, safe match draining and abort

**Files:**
- Modify `server/src/main/kotlin/com/hienthai/fastowin/server/{Main,Application,GameEngine}.kt`.
- Create runtime/policy helpers under `server/.../maintenance/`; modify GiftService/GiftRoutes only for explicit admission and receipt replay checks.
- Modify tournament/active-room persistence only when required for durable abort; keep existing gameplay and schema unrelated to maintenance unchanged.
- Add `MaintenanceGameEngineTest.kt`, `MaintenanceRuntimeTest.kt` and gift-policy race tests.

**Interfaces:** Consumes Task1 exact public/admin contract; produces `GameEngine.applyMaintenancePolicy(policy)` and `activeMaintenanceMatchCount()` plus a pure/injected admission seam used by auth/gifts. Engine policy/start decisions share its mutex; define concrete return deliveries/aborted room IDs in report before client Task4.

- [ ] Write failing tests with real engine/player fixtures: existing match completes/settles once during drain; CreateRoom/JoinRoom/SetReady/rematch/matchmaking/tournament round starts blocked across the transition race; old socket and direct HTTP cannot bypass; only matching live resume admitted; deadline/emergency abort persists and reconnect/cleanup do not generate loss/rewards/Elo; completed results are retained. Test restored rooms and tournament pending/entry-refund behavior without scoring maintenance as a winner.
- [ ] Add the engine policy state and check start transitions under the existing mutex, including automatic matchmaking and tournament progression. Early-return economic/social/profile handlers require explicit new-intent admission. Already admitted wallet work may finish; a later call is refused. Timer/cleanup paths share abort markers and cannot score an aborted room.
  ```kotlin
  // The check must be inside each actual start critical section, not just
  // a transport pre-check before asynchronous quota/profile work.
  if (maintenancePolicy.blocksNewIntent) {
      return@withLock HandleResult(listOf(maintenanceError(playerId)))
  }
  // Only then mutate WAITING -> PLAYING; drain may not race this transition.
  ```
- [ ] Start owned runtime polling at <=5 seconds, synchronize due transitions with observed active count, apply effective override, send state/close sockets as appropriate. Stop coroutine cleanly; read failure blocks new intent and retains known closure. Main uses real repository when DB present; unavailable durable controls do not disable existing no-DB environment mode.
- [ ] HTTP policy explicitly blocks login/register/upgrade and new game mutation in DRAINING/MAINTENANCE; allow logout/refresh needed to finish paid receipts, health/status/admin. Gift claim replay of an already committed receipt remains readable without admitting a new payout; claim-all returns per-item safe outcomes. Authorization still precedes disclosure/replay.
- [ ] Ensure policy close doesn't silently roll back committed economy or turn normal disconnect cleanup into penalties. Engine abort handles all restored rooms and tournament persistence before socket closure.
- [ ] Run focused engine/runtime/gift tests plus relevant existing engine/match/tournament suites and full server tests once at this integration gate, inspect XML. Compile/installDist, scanner, diff check. Update docs/report, explicit commit `feat: enforce maintenance and drain active matches`.

### Task 3: Finish paid store receipts independently of the game socket

**Files:**
- Create `server/.../StorePurchaseService.kt` and HTTP route helper under `server/.../maintenance/` or store module.
- Modify `GameEngine.kt`, `Main.kt`, `Application.kt` for the same injected verification/grant service.
- Add common receipt request/response DTOs in MaintenanceProtocol.kt or dedicated StorePurchaseProtocol.kt; exact fields reuse store/productId/purchaseToken/requestId semantics.
- Create `shared/.../data/network/StorePurchaseApiClient.kt`; modify GameController/FastToWinApp/StoreBilling integration to use common HTTP finalization and authoritative profile refresh.
- Add server service/route and shared HTTP/controller tests; update `docs/admin-maintenance.md`.

**Interfaces:** `POST /api/store-purchases/verify`, registered bearer authentication; optional existing web-session configuration. Returns the existing typed StorePurchaseStatus/result, never raw receipt/token. `StorePurchaseService.verify(playerId,request)` shared by legacy WS and HTTP; no local grant arithmetic. Same underlying store verification and fingerprint/grant transaction as existing engine.

- [ ] Write failing HTTP tests that finalize while socket closed/maintenance active, reject missing/other-account bearer, reject fake/sandbox in production, replay once, handle real auth/storage failure503, preserve cancellation, retain current verifier status mapping. Assert one STORE_PURCHASE wallet row and receipt after race/retry.
- [ ] Extract existing verify/grant logic into one service without changing receipt identity or two competing grant paths. Keep legacy WS message compatible. Bound body/token lengths and rate-limit per player/IP using existing verify policies; API namespace bypasses old worker caching and both proxies already forward `/api/*`.
  ```kotlin
  // Both transports call the same service; the HTTP route authenticates
  // a registered user independently of any in-memory game session.
  val result = storePurchaseService.verify(authenticatedUserId, request)
  call.response.header(HttpHeaders.CacheControl, "no-store")
  call.respond(result)
  ```
- [ ] Shared adapter uses current token provider with one forced-refresh retry, existing web markers/credentials, bounded exact request and safe localized errors. Do not acknowledge/finish platform purchase until GRANTED/ALREADY_GRANTED; preserve pending receipt for retry after failure/maintenance/reconnect. Account replacement fences profile/UI effects; refresh authoritative balances, queue behind earlier profile requests.
- [ ] Test MockEngine URLs/method/body/auth/configurator, late account response, blocked game connection, profile refresh and finalization status; run focused server/shared plus existing store tests. Compile Android/JS/Wasm, scanner/diff check; commit `feat: finalize paid receipts during maintenance`, full report/evidence.

### Task 4: Material admin control and localized player status

**Files:**
- Create `server/src/main/resources/admin/admin-maintenance.js`; modify admin.js/CSS/AdminAssets.kt and design-system admin override.
- Create `shared/.../ui/components/MaintenanceBanner.kt`; modify FastToWinApp.kt, MaintenanceScreen.kt and status/billing integration; additive localized TextKeys/catalog mappings VI/EN with existing fallback rules.
- Add `e2e/tests/admin-maintenance.spec.mjs` and fixtures, shared status/banner state tests and phone/desktop screenshot previews.

**Interfaces:** Consume exact Task1–3 DTO/error contracts, existing admin module adapter, and runtime observations; do not implement a parallel network/auth/modal layer. Expose status/banners through existing serviceStatusClient; preserve live game composition in DRAINING.

- [ ] Write browser cases for Owner vs read-only roles, planned/emergency/disable, field ranges, environment override, unknown mutation retry identity,409 stale version, modal pending/keyboard/error/session fences. Tests assert real controls and sent exact DTOs; screenshot captures360/768/1024/1440 list/form/confirm as needed.
- [ ] Build admin module using textContent and reusable native modal/API/channel, add assets explicitly and role navigation. Show source/effective/persisted state, expected end advisory, server-observed count and timestamp; avoid showing mutation commit as proof all sockets closed. Root engine observed count must be current runtime state, not a fabricated DB value.
  ```javascript
  const payload = { ...validatedFields, expectedVersion: snapshot.version,
    requestId: crypto.randomUUID(), reason: reason.value.trim() };
  // Keep this exact payload/requestId for an uncertain identical retry;
  // current view/session/channel fences still apply to response handling.
  await api('/maintenance', { method: 'POST', body: payload });
  ```
- [ ] Shared banner selects VI/EN runtime message, presents preparation/countdown without replacing a live match, announces maintenance appropriately and auto-rechecks service status. Disable initiating purchase/new match controls for drain; backend remains authoritative. Existing offline/retry and guest guidance remain separate. Keep pending paid-receipt completion reachable while MaintenanceScreen is displayed.
- [ ] Run complete admin browser suite from e2e cwd, focused shared/protocol/status tests and screenshot validation, scanner/APK/JS/Wasm. Inspect actual phone/desktop screenshots; report landscape/largest-text/assistive/iOS limitations honestly.
- [ ] Rebuild/installDist and safely restart only the verified loopback preview. Live acceptance on test DB: Ownerplanned→drain→closed→off, existingmatch vs new room, emergency oldsocket,paid receipt exactly once, unauth/role/override; restore ACTIVE when done and retain preview. Never toggle production.
- [ ] Explicit commit `feat: add maintenance controls and player feedback`, docs and report. Task/final reviewers check all acceptance rows and compatibility/caches/proxies; preserve final evidence and local branch.

## Plan self-review and acceptance map

- Task1: persistence/replay/audit/version/authorization/status/environment override/no-DB/read failure contract.
- Task2: actual engine start race, existing match/tournament drain/abort/restore/settlement, old sockets, auth/gift admission, runtime propagation and lifecycle.
- Task3: paid receipts independent of socket, same verifier/ledger, refresh/retry/account fences and source notification safety (no new callback source introduced).
- Task4: admin material/modal/read-only roles/unknown results, localized banners/status/offline/live-match behavior, screenshots and real loopback acceptance.
- Planning example snippets name only interfaces produced here; implementers must supply executable fixtures and actual RED/GREEN evidence, not a test that only asserts its fixture.
- New multi-instance realtime coordination, automated load-based maintenance and actual deployments remain explicitly outside approved MVP.
