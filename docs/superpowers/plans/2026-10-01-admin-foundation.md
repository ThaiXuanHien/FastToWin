# Admin Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Issue #21 phase A: a usable admin web interface with separate authentication, server authorization, player lookup and durable audit history.

**Architecture:** Serve a separate HTML/CSS/JavaScript application at `/admin/` from the existing Ktor server. Dedicated admin accounts and hashed, revocable sessions live in PostgreSQL; player information remains read-only and comes from existing game tables. Keep DTOs, security, repositories, HTTP routes and static UI in focused files, outside the large game engine.

**Tech Stack:** Existing Kotlin/JVM 17 toolchain, Ktor 3.5.2, Kotlin serialization, PostgreSQL/Flyway, PasswordHasher, native browser modules and CSS; existing Playwright package for browser tests. No additional production JS framework, backend service or external CDN.

**Spec:** https://github.com/ThaiXuanHien/FastToWin/issues/21 (approved 01/10/2026). Phase B and Issues #18–#20/#22 are separate work.

## Global Constraints

- API `/admin/*` có xác thực riêng cho phiên admin; token người chơi không tự có quyền quản trị.
- Owner: cấp/thu hồi quyền, công bố/hủy chiến dịch/thông báo, đọc audit.
- Operator: tra cứu và soạn/xem trước bản nháp; không công bố hoặc cấp quyền.
- Support: tra cứu người chơi; không sửa/phát tài sản hoặc xem danh sách admin.
- Phiên admin dùng cookie HttpOnly/Secure trong production, giới hạn thời gian và thu hồi server; bảo vệ CSRF cho thay đổi trạng thái, giới hạn origin và rate limit đăng nhập. Thu hồi quyền có hiệu lực cho request tiếp theo.
- Email đầy đủ chỉ Owner/Operator; Support dùng email che và tìm bằng ID/tên.
- Audit thay đổi và dữ liệu nghiệp vụ phải cùng transaction; thất bại không báo thành công.
- Không hiển thị password hash, session token, FCM token hoặc khóa xác thực.
- Không cập nhật ví/cấp/Elo trực tiếp.
- Nội dung quản trị dùng tiếng Việt ở MVP; UI dùng được trên desktop và điện thoại.
- Do not provision live accounts, deploy production, publish campaigns, merge or push as part of this work.

## Execution decisions

- Work on `codex/admin-foundation` in the existing checkout: tracked source was clean; leave existing `.tmp/` and skill caches untouched. No additional worktree is needed for these sequential tasks.
- Admin is disabled by default. `FASTTOWIN_ADMIN_ENABLED=true` explicitly enables it; `FASTTOWIN_ADMIN_ORIGIN` is an exact, validated origin, HTTPS in production and loopback HTTP permitted only in dev. Serve browser and API on that same origin; require that origin for admin mutations, including login.
- Production requires PostgreSQL. In dev, an in-memory security repository may support tests, but unavailable player storage returns an explicit unavailable response, not fake zero users.
- Sessions last eight hours, store only SHA-256 token/CSRF hashes, never use localStorage and never return the session cookie token in JSON. CSRF token is returned only to the authenticated session and held in browser memory.
- Bootstrap first Owner via a console tool, using masked password input or password-file secret, not command-line plaintext, fixed credentials or public HTTP registration. Bootstrap is idempotent for an existing owner and must not grant privileges to an existing player implicitly.
- Owner can provision additional dedicated admin credentials, change roles and revoke admin access with a reason and request ID; prevent removal of the last active Owner. Do not add player bans or player-session revocation.
- Read APIs use validated server-side pagination (default 25, maximum 100) and deterministic ordering. Support search never queries email; reject email-looking search for Support.
- Player gift history filters actual wallet sources ADMIN_GIFT/COMPENSATION; no mailbox or future campaign API is implemented here. Missing last activity is nullable and displayed as "Chưa có dữ liệu".
- Browser content renders using text nodes, never HTML from account names or audit reasons. Static resources have a restrictive CSP and protected responses use `Cache-Control: no-store`.

## Verification commands

The JVM's default Unix-domain socket directory fails with `Invalid argument: connect` in this Windows environment. A short workspace socket directory resolved it without project configuration changes:

```powershell
New-Item -ItemType Directory -Path '.tmp/jvm-sockets' -Force | Out-Null
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:\HienTX\Work\Android\FastToWin\.tmp\jvm-sockets'
.\gradlew.bat :server:test --no-daemon --console=plain
```

Run Gradle with escalation when Windows denies the JBR executable or socket/cache access. PostgreSQL integration tests use a disposable local database through `TEST_DATABASE_URL`; report explicitly if those tests are skipped. Do not point tests at production.

### Task 1: Backend security, user directory and audit

**Files:**
- Create under `server/src/main/kotlin/com/hienthai/fastowin/server/admin/`: `AdminModels.kt`, `AdminConfiguration.kt`, `AdminRepository.kt`, `InMemoryAdminRepository.kt`, `PostgresAdminRepository.kt`, `AdminService.kt`, `AdminRoutes.kt`, `AdminBootstrap.kt`.
- Create: `server/src/main/resources/db/migration/V49__add_admin_accounts_sessions_and_audit.sql`.
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/Database.kt`, `Main.kt`, `Application.kt`; `server/build.gradle.kts` for the bootstrap task.
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/admin/AdminServiceTest.kt`, `AdminRoutesTest.kt`, `PostgresAdminRepositoryTest.kt`.

**Interfaces:**
- Produce `AdminRole { OWNER, OPERATOR, SUPPORT }` and serializable safe DTOs; dedicated credentials are internal and never serialized.
- `AdminConfiguration.fromEnvironment(environment: String, values: Map<String,String>): AdminConfiguration?` returns null when disabled; rejects invalid enabled configuration.
- `Application.gameModule(..., adminService: AdminService? = null)` installs admin routes without changing existing client behavior.
- `DatabaseRuntime.adminRepository` exposes `PostgresAdminRepository`; service gets environment/origin configuration and existing PasswordHasher, injectable clock and rate limiter for tests.
- HTTP contract for Task 2:

```text
POST /admin/api/login             {email,password} -> {admin:{id,email,role},csrfToken}; Set-Cookie
GET  /admin/api/session           -> {admin:{id,email,role},csrfToken}
POST /admin/api/logout            -> {ok:true}; revoke session, expire cookie
GET  /admin/api/users?q=&page=0&pageSize=25 -> {items:[UserSummary],page,pageSize,total}
GET  /admin/api/users/{id}        -> UserDetail
GET  /admin/api/users/{id}/matches?page=0&pageSize=25 -> {items:[MatchEntry],page,pageSize,total}
GET  /admin/api/users/{id}/wallet?page=0&pageSize=25 -> {items:[WalletEntry],page,pageSize,total}
GET  /admin/api/users/{id}/gifts?page=0&pageSize=25 -> same WalletEntry page filtered by actual sources
GET  /admin/api/accounts          -> owner-only paginated safe admin identities
POST /admin/api/accounts          {email,password,role,reason,requestId} -> safe admin identity
POST /admin/api/accounts/{id}/access {role,active,reason,requestId} -> safe identity; revoke old sessions
GET  /admin/api/audit?page=0&pageSize=25 -> owner-only {items:[AuditEntry],page,pageSize,total}
```

`UserSummary` includes id, displayName, email (masked for Support), accountType, status, createdAtEpochMillis. `UserDetail` adds level, xp, gold, gems, elo, clanId/clanName and nullable lastActivityAtEpochMillis. `MatchEntry` includes id, matchType, outcome, score and finishedAtEpochMillis; `WalletEntry` includes id, sourceType, sourceId, goldDelta, gemsDelta, xpDelta, createdAtEpochMillis. `AuditEntry` includes id, actorId, action, targetId, requestId, reason, safe before/after summaries and createdAtEpochMillis. Error JSON is `{code,message}` with Vietnamese safe messages; 400 invalid request, 401 missing/revoked session, 403 forbidden origin/CSRF/role, 404 missing record, 409 stale/conflicting mutation, 429 rate limit, 503 unavailable player storage.

Every protected request re-reads session/account/role from storage. Sensitive reads and admin mutations append audit before returning; PostgreSQL audit and mutation share a transaction. Repository rechecks privileges within mutations, serializes last-owner changes and deduplicates request IDs; reusing an ID with another payload is a conflict. Never audit plaintext password, token, CSRF, email search input or arbitrary request bodies.

- [x] **Step 1: Write failing HTTP/service tests before production code.** Name the breaks: missing authorization, CSRF bypass, email disclosure, missing transactional audit, duplicated grant/revocation and expired-session acceptance. Use real in-memory repository and Ktor test host; no mocked authorization checks.

```kotlin
@Test fun `player bearer token cannot access admin directory`() = testApplication {
    application { gameModule() }
    val response = client.get("/admin/api/users") {
        header(HttpHeaders.Authorization, "Bearer player-token")
    }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
}
```

Before admin is enabled the contract is 404, so enabled-route tests must construct an AdminService with a seeded test-only repository. Bootstrap fixtures belong in tests, never hard-coded production defaults. Add tests for valid Owner login, generic wrong-password failure, bounded rate limiting, exact-origin mismatch, missing CSRF, 8-hour expiry, immediate revocation, Support masking/email-query rejection, paginated directory and histories, last Owner protection, idempotent role requests, forbidden Operator mutation, audit sanitization and unavailable user storage.

- [x] **Step 2: Run focused tests to record red.** Use `:server:test --tests '*admin.AdminRoutesTest' --tests '*admin.AdminServiceTest'`; unresolved new types count as the initial missing-feature failure, then use real assertions after interfaces compile.
- [x] **Step 3: Implement the smallest complete backend to satisfy those tests.** Keep SQL prepared and column selections explicit; reuse PasswordHasher and progression calculation. Introduce migration tables for dedicated admin accounts, sessions, request deduplication and audit without modifying old migrations. Add bound query validation and escaped substring matching; a UUID query matches by identity. Query existing user/profile/stats/clan/session/match/wallet tables; do not load all players into memory.

```kotlin
val cookie = call.request.cookies["fasttowin_admin"]
// Authorization is performed by AdminService against current repository state.
val principal = service.authenticate(cookie) ?: return unauthorized(call)
// Mutating calls require exact Origin and a session-bound X-Admin-CSRF value.
```

The cookie is HttpOnly, SameSite=Strict, path `/admin`, Secure outside dev. Login always requires exact origin; logout and account mutations also require the CSRF header. Configuration validates origin scheme/authority, path/query/fragment and uses no request Host trust. Unknown email verifies against a dummy password hash before generic failure. Bound login bodies/password lengths and hash off the event loop.

- [x] **Step 4: Add PostgreSQL behavior tests.** Register disposable player fixtures using existing auth repository; assert actual SQL pagination, Support masking, restart persistence, audit/mutation rollback (controlled DB failure), deduplication conflict and concurrent last Owner revocation. Missing `TEST_DATABASE_URL` must be reported as skipped coverage, not verified SQL.
- [x] **Step 5: Run focused tests and full backend suite, self-review and commit only task files.** Preserve user scratch files; no server deployment or live credential bootstrap. Report test commands/results and exact DTO signatures for Task 2 in the task report.

### Task 2: Admin web interface, browser verification and operations docs

**Files:**
- Create: `server/src/main/resources/admin/index.html`, `admin.css`, `admin.js` (split `api.js` or view modules only when responsibility warrants it).
- Create/modify: `server/src/main/kotlin/com/hienthai/fastowin/server/admin/AdminAssets.kt` and route integration.
- Create: `e2e/admin.playwright.config.mjs`, `e2e/tests/admin.spec.mjs`, `e2e/support/admin-fixture.mjs`, `docs/admin-management.md`.
- Modify: `e2e/package.json` (dedicated admin test script), `README.md` (admin documentation link).
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/admin/AdminAssetsTest.kt`; route security coverage for static resources.

**Interfaces:**
- Consume Task 1 endpoints/DTOs exactly; read its implementation report before building requests.
- Produce a Vietnamese responsive console at `/admin/`: login, player search/details/history, Owner-only admin access management and Owner-only audit.
- Use same-origin `fetch` with credentials, memory-held CSRF header on mutation, structured safe errors and AbortController/request sequence guards to prevent stale searches from replacing current data.

- [x] **Step 1: Write failing asset/browser tests.** Missing admin assets should fail first. Browser tests must exercise the actual rendered static UI; use a local HTTP fixture with the documented API contract for loading/error/expiry responsiveness tests, and at least one real local backend/DB flow for auth/search/audit when available.

```javascript
test('expired session clears visible player details', async ({ page }) => {
  await page.goto('/admin/');
  // Login through visible labelled fields and view a seeded player.
  // Fixture makes next API response 401.
  await page.getByRole('button', { name: 'Làm mới' }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeVisible();
  await expect(page.getByText('seeded-player@example.com')).not.toBeVisible();
});
```

The fixture is test-only and never delivered by production assets. Test search/pagination, empty/error states, user histories, malicious displayName as text, role-hidden owner controls, keyboard focus, mobile 360px and desktop 1440px overflow, duplicate-submit prevention, stale response protection and session expiry. Assert behavior rather than exact CSS/source strings.

- [x] **Step 2: Run focused tests to record red.** Use `:server:test --tests '*admin.AdminAssetsTest'` and `node e2e/node_modules/playwright/cli.js test --config=e2e/admin.playwright.config.mjs`; adapt executable path to the existing Node runtime.
- [x] **Step 3: Implement UI and asset delivery.** Serve only named resources (`index.html`, `admin.css`, modules), no arbitrary filesystem paths. Use restrictive same-origin CSP, no inline script/event handlers, no CDN fonts, visible labels/focus, 44px controls, 16px body typography, semantic tokens, sufficient contrast, live loading/error messages and no fake future-event controls.

```javascript
const response = await fetch('/admin/api/users?q=' + encodeURIComponent(query) + '&page=' + page,
  { credentials: 'same-origin', signal });
if (response.status === 401) { clearAdminAndPlayerState(); showLogin(); }
// Render all database-provided text with textContent/createTextNode.
```

Player detail includes summary metrics, nullable activity, clan and paginated match/wallet/gift source history. Owner access actions require reason and a request ID, disable while pending and retain request ID only for retry of the same payload. Support sees masked email and cannot enter email search. On any refresh failure clear stale displayed data or explicitly label it stale; on 401 clear all sensitive state immediately. Never store credentials, player snapshots or tokens in browser persistent storage.

- [x] **Step 4: Document enablement and bootstrap.** Explain dev/prod origin, explicit enable switch, no default credentials, console password-file input, same-origin reverse proxy, cookie/security behavior, Owner/Operator/Support, role changes/revocation, read-only players, audit, and features reserved for phase B. Never publish a seeded password in docs or start production.
- [x] **Step 5: Run browser checks, inspect screenshots at phone/desktop sizes, run full backend tests and commit only task files.** Record whether PostgreSQL/live auth browser flow was exercised. Update plan/ledger coverage and keep Issue #21 open because phase B remains.

## Completion gate

- Both tasks have passing focused/full tests and independent spec/quality review.
- Full-branch review addresses authentication, transaction/audit consistency, SQL privacy/pagination and browser stale-state handling.
- Verify branch diff contains only planned files and user scratch files remain untouched.
- Report phase A completion and any unverified local DB/browser coverage precisely; never close Issue #21 until phase B is done.

## Execution evidence (02/10/2026)

Task 1: `474076b` and security amendment `8ae63d6`; independent backend review and scoped amendment review passed. Creation replay stores no fast password verifier; it uses the account's existing salted PBKDF2 credential.

Task 2: `c02420a` and UI amendment `6409804`; implementation and verification complete, independent UI and scoped amendment reviews passed; whole-branch review pending. The issue remains open for phase B.

- Backend: 56 suites / 274 tests / zero failures, errors or skips. All six admin PostgreSQL tests ran on disposable loopback PostgreSQL, including persistence, transaction rollback and concurrent last-Owner protection.
- Browser: 23 passing Chromium tests, including a real loopback Ktor/PostgreSQL Owner login, player search/details, audit, logout and reload. Phone/desktop screenshots inspected at 360px and 1440px. Firefox/WebKit and deployed production checks were not run.
- Bootstrap CLI executed twice against the disposable UI database and returned the same Owner identity. Test credentials stayed in ignored secret files, with live traces disabled.
- No production accounts, deployment, push or merge. Work remains on `codex/admin-foundation`.

Decisions made during execution, in order:

1. Use the existing clean checkout on a feature branch for sequential work and preserve user scratch. If concurrent edits require isolation, a separate worktree will be needed.
2. Adapt SDD workspace/brief/review scripts to native PowerShell. A faulty adaptation could omit review context; reviewers read complete packaged diffs, recovering any truncated tool output.
3. Serve native HTML/CSS/browser modules from Ktor on the API origin, with no production JS dependency or CDN. Phase B may require revisiting the UI structure or framework as complexity grows.
4. Repair two pre-existing tournament test fixtures by inserting missing player_stats rows before their existing balance setup. Both failures were reproduced on baseline `960afce` in a separate database; production and original assertions are unchanged. The risk is masking a fixture lifecycle assumption, so the independent backend reviewer inspected both changes.
The UI amendment blocks editor replacement/cancel/refresh while an access mutation awaits its result and establishes Support as the account form's native reset default. Four rendered regressions were RED before the amendment and GREEN afterward. The amended assets were rebuilt and both asset tests passed; the prior 274-test backend run remains the full backend evidence because Kotlin logic is unchanged.
