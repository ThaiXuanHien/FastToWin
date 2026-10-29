# Refresh response recovery implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Recover the exact refresh result after a lost response without resurrecting revoked sessions.

**Architecture:** The backend atomically rotates credentials and writes one encrypted receipt per session. Android durably prepares a secret in its encrypted session record before HTTP and reuses that attempt until the resulting credentials are atomically stored. Legacy native and Web cookie requests keep their existing behavior.

**Tech Stack:** Kotlin Multiplatform, Ktor, JDBC/PostgreSQL, Flyway, JCA AES-GCM/HMAC-SHA256, Android Keystore and SharedPreferences, coroutines and kotlin.test.

**Spec:** `docs/superpowers/specs/2026-10-29-refresh-response-recovery-design.md` (user approved 29 October 2026).

## Global Constraints

- Local implementation/testing only: no push, merge, production migration/deployment, new Internal release or device installation.
- Android first. Web cookie/Origin/CSRF and iOS remain legacy; no bearer credentials or attempt secrets in localStorage.
- Secret: secure random 32 bytes, base64url without padding. Treat it as a credential; never log token, secret, their hashes, request/response bodies or ciphertext.
- Exact predecessor token/secret retrieves the exact original response for 5 minutes, without another rotation or TTL extension. Revoke/account status/current generation always gate replay.
- HKDF-SHA256 RFC5869: IKM exact UTF-8 old refresh token, salt decoded secret, info `fasttowin/auth-refresh-recovery/v1`; AES-256-GCM, fresh 12-byte nonce, 128-bit tag, versioned bound AAD.
- Token rotation and encrypted receipt commit together. Parent users row locks precede session locks, consistent with moderation/login replacement.
- One receipt per session. After 5 minutes remove ciphertext, retain hash-only mapping solely for pending logout until successor expiry. Next rotation replaces/removes mapping; session/account deletion cascades.
- Pending logout revokes only its still-current successor, even after ciphertext cleanup/5-minute expiry. It cannot revoke a newer generation or replacement login.
- Android persists pending before HTTP and clears it with the new credentials atomically. Storage failure before preparation sends no HTTP. Logout/login change prevents late saves.
- Preserve single-flight ownership, temporary failure handling, timeout/backoff and existing QA scalar diagnostics. Never replay business commands.
- Tests must include actual local PostgreSQL migration and lost committed response, boundaries, concurrency, revocation and crypto tampering. Record RED/GREEN logs under `.artifacts/auth-refresh-recovery-20261029/`.
- Use one Gradle process at a time. Preserve the production DPAPI backup and all existing artifacts. Do not archive this worktree.

### Task 1: Backend protocol, durable receipt and safe revocation

**Files:**
- Modify `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/AuthProtocol.kt`.
- Modify `server/src/main/kotlin/com/hienthai/fastowin/server/Authentication.kt`, `PostgresAuthRepository.kt`, `InMemoryAuthRepository.kt`, `Application.kt`.
- Create `server/src/main/kotlin/com/hienthai/fastowin/server/RefreshRecovery.kt` for receipt model and authenticated codec; keep crypto out of HTTP handlers.
- Create next unused migration after V68 under `server/src/main/resources/db/migration/` for `auth_refresh_recoveries`.
- Create `server/src/test/kotlin/com/hienthai/fastowin/server/RefreshRecoveryTest.kt` and `RefreshRecoveryCryptoTest.kt`; extend existing authentication/route tests as needed.

**Interfaces:**
- Produce `RefreshTokenRequest(val refreshToken: String, val refreshAttemptSecret: String? = null)` and the equivalent optional field on `LogoutRequest`.
- Produce `AuthenticationService.refresh(refreshToken: String, refreshAttemptSecret: String? = null): AuthResult` and `logout(refreshToken: String, refreshAttemptSecret: String? = null)`.
- Keep `AuthSessionResponse` unchanged. Repository internal interfaces may be chosen to preserve transaction ownership and bind the real persisted session ID rather than the temporary ID on replacement tokens.
- Task 2 consumes only the optional protocol fields. Task 3 consumes these service/route contracts and the receipt schema.

- [x] Write behavioral tests for lost-response retry, legacy behavior, exact fixed expiries, invalid secret, stale successor, pending logout before/after receipt expiry/cleanup, concurrent duplicate attempts and revoked accounts. For example:

```kotlin
val first = assertIs<AuthResult.Success>(service.refresh(oldToken, secret)).session
val retry = assertIs<AuthResult.Success>(service.refresh(oldToken, secret)).session
assertEquals(first, retry)
assertIs<AuthResult.Failure>(service.refresh(oldToken, otherSecret))
service.logout(oldToken, secret)
assertIs<AuthResult.Failure>(service.refresh(oldToken, secret))
```

- [x] Add minimal compatible signature scaffolding if compilation requires it, then run focused tests and capture actual failing assertions before behavior changes. Run `:server:test --tests '*RefreshRecovery*' --rerun-tasks` with the established JDK environment.
- [x] Implement strict canonical secret validation (wrong format/size = 400 before rotation), RFC5869 key derivation and AES-GCM receipt encryption. Test official RFC5869 vector, round trip, tag/AAD/codec alteration and metadata/session substitution. Clear temporary key arrays where practical.
- [x] Add receipt schema constraints/FK/index and transactional fresh/replay operations in both repositories. Replay must work when a concurrent first request already changed the old refresh hash. Serialize using parent row then real session row; recheck active account/session/generation after locks. On expired or malformed/tampered receipts return generic auth rejection. Do not extend expiry or rotate from a receipt.
- [x] Wire native refresh/logout fields through routes; Web ignores the new field and retains cookie rules. Invalidate mappings on legacy rotation and all revocation paths. Add bounded ciphertext/metadata cleanup to the existing lifecycle worker. Do not introduce a separate scheduler or provider.
- [x] Run focused new and existing authentication, moderation and Web cookie tests; record results and exact commands. Self-review all revocation/lock order paths, including login replacement/password reset/change/delete. Commit task-owned files only and write implementation report.

### Task 2: Android durable pending attempt and controller ownership

**Files:**
- Modify `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/SessionStores.kt`, `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/AuthApiClient.kt`, `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AuthController.kt`.
- Modify `shared/src/androidMain/kotlin/com/hienthai/fastowin/data/network/AndroidAuthSessionStore.kt`.
- Add/extend tests under `shared/src/commonTest/` and Android unit tests for the store as supported by the module; keep Web/iOS stores behavior unchanged.

**Interfaces:**
- Consume optional `refreshAttemptSecret` on refresh/logout requests from Task 1.
- Add `StoredAuthSession.refreshAttemptSecret: String? = null` for backwards-compatible decoding.
- Introduce an opt-in `RecoverableAuthSessionStore : AuthSessionStore` with `prepareRefresh(serverUrl: String, expected: StoredAuthSession): StoredAuthSession?` and `completeRefresh(serverUrl: String, expected: StoredAuthSession, refreshed: StoredAuthSession): Boolean`. A null/false result means ownership changed, and must not overwrite/logout the new owner. The Android implementation supplies secure randomness and synchronous encrypted commit under a process-wide shared lock. Legacy stores remain non-opt-in.
- Preserve the existing callback compatibility on `AuthApiClient.refresh`; add an optional named attempt argument, and pass it through logout too.

- [x] Write tests that transport consumes the server's first successful result but drops it; controller recreation reloads the same pending and retry receives exactly that result. Also test failure to persist pending => zero HTTP, before-commit transport failure => same pair retained, 408/429/5xx/timeouts remain temporary, final save clears pending, concurrent screen waiters share one refresh, old response after logout/new login cannot save.

```kotlin
assertEquals(1, requests.size)
assertNotNull(store.load(url)?.refreshAttemptSecret)
assertEquals(firstAttempt, retryRequest.refreshAttemptSecret)
assertNull(store.load(url)?.refreshAttemptSecret) // only after new credentials persist
```

- [x] Run focused tests with actual failing assertions before behavior changes; retain RED logs. Existing test doubles may implement the opt-in interface with deterministic secrets to exercise this contract; production randomness stays Android-only and cryptographically secure.
- [x] Implement atomic prepare/CAS completion. Use SharedPreferences `commit()` for durable writes; encrypted JSON contains pending and tokens together. Multiple store instances must share the same lock. Compare generation and pending before save. Do not clear a newer session on an old failure.
- [x] Integrate into controller-owned refresh. Create/reuse pending before HTTP, reuse after process/controller recreation, and clear only with successfully stored new credentials. Logout snapshots the current stored pending after prepare, clears UI immediately, then sends the old token plus pending. Preserve final revocation vs temporary failure rules and QA trace privacy.
- [x] Verify old JSON decoding and legacy stores never opt in (especially Web and iOS). Run `:shared:testAndroidHostTest` (this repository has no desktop JVM target) plus Android compile tasks available in this repository, record results, self-review, commit task-owned files and write report.

### Task 3: PostgreSQL lost-response integration and local release gates

**Files:**
- Create `server/src/test/kotlin/com/hienthai/fastowin/server/PostgresRefreshRecoveryTest.kt` and extend route tests if needed.
- Update `docs/account-api.md` with optional fields, fixed recovery window, logout mapping lifetime and backend-before-Android rollout.
- Update `.artifacts/auth-refresh-recovery-20261029/handoff.md` with local evidence and remaining deployment gates (artifact not tracked unless repository conventions require it).

**Interfaces:**
- Consume Task 1 service and schema, Task 2 pending API. PostgreSQL test environment uses TEST_DATABASE_URL/USER/PASSWORD on an isolated local test DB only. Tests must fail or explicitly report skipped integration when that DB is unavailable; never present skip as PostgreSQL evidence.

- [x] Create an isolated local PostgreSQL cluster using installed local binaries or bundled test tooling; apply real migrations with Flyway. Never use Railway/production connection strings.
- [x] Add a test whose transport receives a real committed native route response, deliberately withholds it from the client, then retries the same pair. Reconstruct repository/service before retry to model process restart. Assert exact response equality and exactly one successor generation; independent DB connections test same/different secret contention.

```kotlin
val committed = postRefresh(oldToken, secret)
// Deliberately drop this response at the test transport boundary.
val recovered = postRefreshAfterServiceRestart(oldToken, secret)
assertEquals(committed, recovered)
assertEquals(1, countReceiptsForSession(sessionId))
```

- [x] Verify just-before/exact/after fixed TTL, pending logout after ciphertext scrub, wrong secret, ciphertext/tag/AAD tampering, next legacy/new generation, all revocation paths and lock races (logout, revoke-one/all, login replacement, block, password change/reset, delete). Verify persisted receipt columns contain no plaintext credential; inspect relevant log statements for privacy. Add only meaningful missing tests; fix runtime defects revealed by these tests in the owning code and report changes.
- [x] Run focused PostgreSQL tests, full server tests and `:shared:testAndroidHostTest`, protocol tests, Android compile/unit tests, Web JS/Wasm compile. Inspect iOS source compatibility; actual native iOS compiler requires macOS and must be reported as unavailable on Windows rather than claimed passing.
- [ ] Document deployment ordering and local-only status; request independent security/whole-branch review through the controller. Address all important findings with focused regression tests and re-review. Commit validated source/test/docs changes; leave the implementation branch local with a clear next action requiring separate deployment/publication approval.

Local Task 3 evidence is recorded in `.superpowers/sdd/2026-10-29-refresh-response-recovery/task-3-report.md`. Independent Task 3/whole-branch review and any resulting fixes remain pending; local checks do not authorize deployment or Android distribution.
