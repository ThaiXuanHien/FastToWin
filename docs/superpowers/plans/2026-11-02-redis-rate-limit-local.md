# Redis Rate Limit Local Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Share selected entry-point token buckets across two local backends with real Redis and explicit failure handling, without changing production.

**Architecture:** Preserve the existing `RateLimiter.consume` interface. An opt-in runtime dispatches the spec's fixed 18 entry buckets to one atomic Redis implementation and all remaining buckets to the existing memory limiter. HTTP/WebSocket/admin adapters translate unavailable outcomes separately from real rate limiting; running-game traffic remains local.

**Tech Stack:** Kotlin/JVM 17, Ktor, coroutines, Lettuce 7.9.0.RELEASE if dependency compatibility checks pass, Redis 8.10.3 isolated local fixture, JUnit.

**Spec:** `docs/superpowers/specs/2026-11-02-redis-rate-limit-local-design.md` (Owner approved).

## Global Constraints

- Local implementation/testing only: no push, CI, cloud provisioning, staging, deploy, Samsung/emulator, presence or additional production replicas.
- Default store is `memory`; explicitly empty/unknown store is invalid. Redis requires secret URL or `_FILE`, and namespace `[a-z0-9][a-z0-9_-]{0,47}`.
- Command timeout 200 ms; connect/startup bounded by 2 seconds; at most 128 in-flight commands per backend; no unbounded backlog, consume replay or fallback after Redis errors.
- Preserve all capacities/refill windows and sequential short-circuit semantics. Redis validates capacity 1..1,000,000 and window 1..86,400,000 ms. Key TTL is max(900,000 ms, window).
- Use the spec's exact 18 bucket names; all other names stay memory. Never create buckets from client-controlled names.
- Raw identifiers/secrets/URI must not reach keys, logs, metrics or exception cause chains. Metrics label values are fixed as in the spec.
- Keep client payloads/protocol/version/VI-EN keys unchanged. Use existing `ServerUnavailable` for temporary errors.
- Redis integration evidence must use a real isolated instance. Skip/mock is not evidence. Parent provides the fixture URL/container identity after verifying the official image.
- Run focused tests during edits. Run the complete server suite once after integration, with the project's existing isolated PostgreSQL setup when available; report any pre-existing infrastructure gaps honestly.

## File responsibilities and contracts

- `server/.../ratelimit/RateLimitConfiguration.kt`: strict environment/secret parsing; redacted failures.
- `server/.../ratelimit/RedisRateLimiter.kt`: atomic token consumption, transport timeouts/cancellation, bounded concurrency, validated replies.
- `server/.../ratelimit/RateLimitRuntime.kt`: opt-in creation, fixed bucket dispatch, safe counters, client lifecycle.
- `server/src/main/resources/redis/rate_limit.lua`: Redis TIME + validated hash token bucket + TTL; no unrelated keys.
- `server/.../RateLimitFailures.kt`: shared safe failure contract, usable by HTTP/WS/admin wiring.
- Existing `Main.kt`, `Application.kt`, `admin/AdminService.kt`, `admin/AdminRoutes.kt`: minimal runtime injection/error translation. No unrelated refactor.
- Gradle catalog/server build: pinned client dependency only after checking resolved Netty compatibility.
- Focused tests below, `scripts/tests/` fixture runner if useful, and `docs/redis-rate-limit.md`: repeatable local acceptance, supported configuration and limits.

Both tasks agree on this internal interface (package `com.hienthai.fastowin.server.ratelimit`):

```kotlin
internal fun openRateLimitRuntime(
    values: Map<String, String> = System.getenv(),
    fileReader: (String) -> String = { java.nio.file.Files.readString(java.nio.file.Path.of(it)) }
): RateLimitRuntime

internal interface RateLimitRuntime : com.hienthai.fastowin.server.RateLimiter, AutoCloseable {
    fun renderMetrics(): String
}
```

Safe failure contract in package `com.hienthai.fastowin.server`:

```kotlin
internal enum class RateLimitFailure {
    TIMEOUT, DISCONNECTED, POLICY_MISMATCH, INVALID_RESPONSE, OVERLOADED, OTHER
}
internal class RateLimitUnavailableException(val reason: RateLimitFailure) :
    RuntimeException("Rate limit service temporarily unavailable.")
```

### Task 1: Redis runtime, configuration, atomic algorithm and real-Redis contract tests

**Files:** Create the ratelimit module, Lua resource and failure contract above; modify `gradle/libs.versions.toml` and `server/build.gradle.kts`. Tests: `RateLimitConfigurationTest.kt`, `RedisRateLimiterTest.kt`, `RateLimitRuntimeTest.kt` under `server/src/test/kotlin/com/hienthai/fastowin/server/ratelimit/`. Keep fixture/process control in test utilities or a local test runner, never in production classes.

**Consumes:** Existing `RateLimiter`, `InMemoryRateLimiter`, `RateLimitPolicy`, fixed bucket strings, `readEnvironmentSecret` conventions and the spec.

**Produces:** Exact runtime/failure interface above, default-memory compatibility, selected Redis behavior and bounded metrics.

- [x] Write the initial failing configuration/default behavior tests and watch red. An example that catches accidental opt-in:

```kotlin
@Test fun `memory default does not read a Redis credential`() = runTest {
    openRateLimitRuntime(emptyMap()) { error("must not read a credential") }.use { runtime ->
        val policy = RateLimitPolicy(1, 60_000)
        assertTrue(runtime.consume("login-ip", "local", policy).allowed)
        assertFalse(runtime.consume("login-ip", "local", policy).allowed)
    }
}
```

- [x] Implement strict parsing/default memory and safe errors, then run `:server:test --tests '*RateLimitConfigurationTest' --tests '*RateLimitRuntimeTest'`. Explicit Redis startup must fail on missing/invalid credentials/namespace and refuse credential leakage through `toString`, message or cause.
- [x] Add the real-Redis failing shared-capacity test before implementing the Lua/client. Two independently opened runtimes consume the same key concurrently; with capacity 3 and a very long refill window, exactly 3 succeed total:

```kotlin
val policy = RateLimitPolicy(3, 86_400_000)
val results = coroutineScope {
    (0 until 24).map { n -> async {
        (if (n % 2 == 0) first else second).consume("login-ip", "shared", policy).allowed
    } }.awaitAll()
}
assertEquals(3, results.count { it })
```

- [x] Implement the single-key EVAL script and pinned client; inspect `:server:dependencyInsight --dependency netty --configuration runtimeClasspath` and dependency resolution before retaining it. Do not force unrelated versions. Use Redis TIME, preserve token/refill semantics, validate policy/state before mutation and set TTL. Missing/corrupt state must follow the exact spec rules.
- [x] Add and observe red, then implement: independent namespaces/keys; continuous refill and retry; TTL; policy mismatch; malformed state/reply; connection loss; dropped response after write; finite timeout/in-flight cap; cancellation; startup/shutdown lifecycle; no replay; secret-safe counters. Local game-message buckets must continue when Redis fails, while a selected entry bucket refuses.
- [x] Run the focused contract suite with the real fixture, record exact counts and red/green evidence. Fixture-related test helpers may use a loopback TCP proxy to drop/hold responses, not a fake grant/deny implementation. Record Redis reset semantics honestly.
- [x] Self-review and commit only Task 1 files. Write the task report with command/output, dependency graph evidence and limitations.

### Task 2: Wire runtime into real HTTP/WebSocket/admin flows and complete local acceptance

**Files:** Modify `Main.kt`, `Application.kt`, `admin/AdminService.kt`, `admin/AdminRoutes.kt` only where necessary; add `RedisRateLimitRoutesTest.kt` and test utilities; extend existing startup/metrics tests where behavior changes. Create `docs/redis-rate-limit.md` and a safe fixture runner under `scripts/tests/` if not already delivered by Task 1.

**Consumes:** Task 1's exact runtime/failure contract. Existing public `gameModule(rateLimiter=...)` test injections and `createModerationRuntime` calls must remain source-compatible through defaults.

**Produces:** Optional runtime injection shared with AdminService, safe 503/WS error mapping and real two-application local evidence.

- [x] Add a failing endpoint test using a limiter that throws the safe unavailable exception. It must expose the current missing mapping, not merely assert that a test stub was called. HTTP outcome example:

```kotlin
val response = client.post("/auth/login") {
    contentType(ContentType.Application.Json)
    setBody("""{"email":"qa@example.com","password":"valid-test-password"}""")
}
assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
assertContains(response.bodyAsText(), "RATE_LIMIT_UNAVAILABLE")
```

- [x] Pass one runtime from startup to gameModule and AdminService; dispose it on shutdown and startup errors. Keep default memory construction for existing tests. Wire the runtime's safe metric text into the existing private endpoint, preserving its current access boundary.
- [x] Map real denial to unchanged 429/Retry-After, unavailable to HTTP/admin 503. WS connect unavailable refuses a new game identity with a temporary close; Create/Join unavailable sends the structured error and keeps the authenticated socket alive. Catch only the safe failure type; propagate application cancellation. Do not catch broad engine errors or re-run an operation.
- [x] Add two application instances using independent Redis runtimes: shared player/admin login quotas, create/join/connection entry quotas, namespace separation and missing-Redis fail-safe. Keep room ownership isolated and do not claim a multi-instance game engine.
- [x] Add a real Redis outage acceptance test: ongoing match selections still work through local guards; create/join on the same authenticated connection reports temporary failure without losing the connection; recovering Redis allows a subsequent new attempt. Add metrics output checks with no raw identifier/secret.
- [x] Run focused application/auth/admin/WebSocket tests during edits. Then run full `:server:test` once with available PostgreSQL fixtures; run required localization/coverage checks if the changed paths affect their gates. No Android/Web bundle rebuild is needed for server-only changes with unchanged wire/localization data.
- [x] Document opt-in environment fields, fixed shared/local scopes, 200 ms/2 s/128 bounds, 64 MB noeviction fixture, cancellation and unknown-write behavior, Redis reset/persistence caveat, safe cleanup of only this run's container/namespace, and remaining #12/#15 work. Keep production instructions marked not approved.
- [x] Self-review, commit Task 2 files and report verified results. Request the final whole-branch review after both tasks are accepted; do not merge/push/deploy automatically.

## Local execution result — 02/11/2026

Implementation and scoped/final reviews complete through `fba4d65`; no Critical/Important findings remain. Redis runtime18 focused cases passed; real-Netty/routes17-case fixture runner passed end-to-end including removal of its own temporary container; final startup/integration-focused checks passed7/7. Coverage and localization gates passed during integration.

The complete server invocation ran990 tests with27 failures from existing PostgreSQL URL guards. After supplying a compatible isolated database alias/name, all five affected classes passed30/30. These are separate runs, not a claim of a clean single990-case invocation. Reports preserve the initial XML and subsequent evidence.

Temporary proxy and owned Redis/PostgreSQL containers/volumes were removed after verifying identity. Existing user containers/images were preserved. Non-secret reports are in `.artifacts/redis-rate-limit-20261102/`. No push, CI, merge or deployment was performed. Production use, TLS acceptance, persistence/HA, presence and distributed room ownership remain outside this stage.