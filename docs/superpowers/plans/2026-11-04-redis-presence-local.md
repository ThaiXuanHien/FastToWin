# Redis Presence Implementation Plan

> **For agentic workers:** Use `subagent-driven-development` or `executing-plans` to implement this plan task by task. Track each step with its checkbox.

**Goal** — Show the same friend and clan roster presence from two local FastToWin backends.

**Architecture** — A Redis store maintains expiring leases per authenticated socket. `GameEngine` projects presence onto authorized lists. A negotiated WebSocket message refreshes visible rosters without sending full profiles.

**Tech stack** — Kotlin, Ktor, Lettuce, Redis Lua, Compose Multiplatform, Gradle.

**Spec** — [Redis presence design](../specs/2026-11-04-redis-presence-local-design.md).

## Global constraints

- Use a dedicated Redis credential and namespace `ftw:<namespace>:presence:v1:*`. Presence is opt-in and defaults to existing local behavior. Use only local synthetic accounts and Redis fixtures.
- Renew every 20 seconds, expire after 60 seconds using Redis time, refresh visible rosters every 15 seconds, and read at most 50 distinct accounts per batch. Use Redis outside the engine mutex.
- Preserve protocol compatibility from version 38. Keep room ownership, match settlement and Redis rate limit behavior independent of presence.
- Use the existing isolated worktree on a dedicated `codex/redis-presence` branch from the verified integrated base. Carry the design and this plan forward when changing branches. Consult the repository UI/UX skill before changing screen lifecycle in shared UI.

## Local checkpoint

The branch `codex/redis-presence` contains the Redis store, socket lifecycle and negotiated client refresh. On 04 November 2026, Docker started after its stale runtime sockets were isolated. The local fixture runner at `.artifacts/redis-presence-local-20261104/run-fixture.ps1` passed the real Redis cases for two Netty backends, Friends and Clan snapshots, reconnect, ACL isolation and a lost reply. `-Lease` measured expiration after 60,046 ms when the first Redis client closed without revocation. It also force-killed a child Netty process while its WebSocket was still connected. The surviving backend reported the peer's clan member offline after 60,004 ms at the 15-second refresh interval. The member connected to the surviving backend stayed online throughout.

`-Load` passed three 64-second phases with 0, 100 and 500 simulated connections, each with 650 rate limit requests. At 500 connections Redis reached 2,018,912 bytes used, AOF increased by 935,025 bytes, and the six measured 50-player reads had p95 of 1.599 ms. The baseline AOF increase was 280,173 bytes. All three phases recorded zero evictions and zero new Redis error replies. This measures the Redis workload with two readers refreshing every 15 seconds. More concurrent viewers and longer AOF observation are useful inputs for sizing a deployment.

During the process crash run, an existing rate limit test failed to open its Redis client. All four rate limit acceptance tests passed on a fresh fixture in the isolated rerun. The crash and presence cases passed in the original run.

## Task 1 — Lease store and Redis fixture

Create `server/src/main/kotlin/com/hienthai/fastowin/server/presence/{PresenceStore,PresenceConfiguration,RedisPresenceStore}.kt` and `server/src/test/kotlin/com/hienthai/fastowin/server/presence/RedisPresenceStoreTest.kt`. Reuse the timeouts, URI redaction, disconnect behavior and fixture conventions in `server/src/main/kotlin/com/hienthai/fastowin/server/ratelimit/RedisRateLimiter.kt` and `server/src/test/kotlin/com/hienthai/fastowin/server/RedisNettyAcceptanceTest.kt`.

```kotlin
internal interface PresenceStore : AutoCloseable {
    suspend fun renew(playerId: String, connectionId: UUID, presence: FriendPresence): Boolean
    suspend fun revoke(playerId: String, connectionId: UUID): Boolean
    suspend fun read(playerIds: Collection<String>): Map<String, FriendPresence>?
}
```

- [ ] Run the lease tests using `FASTTOWIN_TEST_PRESENCE_REDIS_URL` against an isolated instance. Cover `PLAYING` over `ONLINE`, revocation of either connection, status transitions, and expiration while another connection stays alive. Add namespace isolation and verify that an unavailable read returns `null`. Use a short injected lease duration for deterministic expiration tests.
- [ ] Run `./gradlew.bat :server:test --tests '*RedisPresenceStoreTest'` with the fixture URL set. Confirm the new test fails for the missing store before implementing it.
- [ ] Create opt-in settings `FASTTOWIN_PRESENCE_STORE=redis`, `FASTTOWIN_PRESENCE_REDIS_URL` and `FASTTOWIN_PRESENCE_NAMESPACE`. Validate namespace with the existing Redis namespace constraints and reject missing config when enabled. Run mutations and reads through bounded async commands. Disable offline command queuing and retries. Build the three keys from server-provided canonical account IDs and send only UUID and state index as script arguments. For renew, compute milliseconds using `TIME`, remove the same connection from the other sets, prune expired entries in the target, `ZADD` with the new deadline and `PEXPIRE` the target. For revoke, `ZREM` the same ID from all three sets. For read, use one `TIME` and `ZCOUNT` with an exclusive lower bound for each key, ordered by priority. Return `null` for failed batches and return `OFFLINE` for successful misses. Validate the response length and codes before applying results. Preserve coroutine cancellation. Verify the dedicated fixture ACL denies rate-limit keys and unauthorized commands.
- [ ] Rerun the test with Redis running and inspect the JUnit report for executed cases. Commit the store and its tests together.

## Task 2 — Backend connection lifecycle and list projection

Modify `server/src/main/kotlin/com/hienthai/fastowin/server/{Application,Main,GameEngine}.kt`. Extend `server/src/test/kotlin/com/hienthai/fastowin/server/ClanPresenceGameEngineTest.kt` and add `RedisPresenceNettyTest.kt` next to `RedisNettyAcceptanceTest.kt`.

- [ ] Add a two-Netty-server fixture with shared test auth and social repositories, independent engines and one Redis namespace. Authenticate Bob at A and Alice at B. Check Bob's status in Alice's friend and clan snapshots. Move Bob through `ONLINE`, `IN_ROOM`, and `PLAYING` on A, then disconnect and verify B's next snapshot. Add a fake store that suspends to test that engine state can still be read and updated while Redis is waiting. Run `./gradlew.bat :server:test --tests '*ClanPresenceGameEngineTest' --tests '*RedisPresenceNettyTest'` before adding the wiring.
- [ ] Pass optional `PresenceStore` to `gameModule` and `GameEngine`. Build and close it from the application lifecycle. Bind presence ownership to the exact `SocketConnection` registered in `Application.kt`. Start its publisher after account authentication and registration. Schedule the initial value and signal changes from `presenceUpdates`, keeping heartbeats and state changes on one writer per socket. Compute the latest local value under the engine mutex, release it before I/O, and check the socket identity before scheduling the update. On close or replacement, stop the publisher before revoking that socket's lease, using a bounded cleanup even if the parent is cancelled. Use a single ordered write path so a delayed write cannot follow its revoke. If ordering cannot be guaranteed after a failed write, require a bounded expiry and assert that the old connection cannot affect the new connection's entry.
- [ ] Replace local-only projections in `loadFriends` and `clanInfo` with `read` over server-authorized, deduplicated roster IDs. Build a complete snapshot or fall back to current local projection on initial load if any batch fails. Keep the existing local recipients for immediate refreshes. Suppress background presence updates on unavailable reads so they cannot overwrite a client's last good result. Ensure any initial projection that succeeds uses a read after the local state change has been published, or overlays verified local sockets without hiding a higher remote state. Count read and write errors through fixed labels only.
- [ ] Rerun the task tests and `./gradlew.bat :server:test --tests '*RedisRateLimitRoutesTest'`. Verify ordinary local mode still projects presence and that guest sockets never write Redis. Commit the wiring and tests.

## Task 3 — Negotiated refresh on visible screens

Modify `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt`, `server/src/main/kotlin/com/hienthai/fastowin/server/{Application,GameEngine}.kt`, and `shared/src/commonMain/kotlin/com/hienthai/fastowin/{FastToWinApp,state/GameController}.kt`. Add `PresenceRefreshProtocolTest.kt` in the protocol tests and `PresenceRefreshTest.kt` under shared common tests in the state package.

```kotlin
// Add to SessionReady: val presenceRefreshSupported: Boolean = false
// Add within ClientMessage and ServerMessage, respectively.
@Serializable data class GetPresence(val requestId: String, val clanId: String? = null) : ClientMessage()
@Serializable data class PresenceData(
    val requestId: String,
    val clanId: String?,
    val states: Map<String, FriendPresence>
) : ServerMessage()
```

- [ ] Test legacy `SessionReady` JSON decoding as capability false, the new messages round-tripping, and client merge behavior for a response with a stale request ID, changed clan, or different socket generation. Test server authorization with a forged clan selection and repeated requests, and verify an outage never emits an authoritative empty roster. Run `./gradlew.bat :protocol:jvmTest :server:test :shared:testAndroidHostTest` to see the missing behavior fail.
- [ ] Keep version 44 and the compatibility floor 38. Advertise the optional capability only with the Redis feature enabled. Verify old client decoders ignore the new field and new clients do not send the new frame to servers whose `SessionReady` lacks it. For `GetPresence`, allow authenticated accounts only, bound request identifiers and enforce a per-socket cooldown of at least 10 seconds. Resolve the authorized roster through the existing friend and clan repository paths. Match visibility to the corresponding initial list. Send only to the requesting socket after a connection identity check. If the store is unavailable, omit the update.
- [ ] Start one `LaunchedEffect` keyed by the actual visible friend list or clan roster and authenticated socket generation. Tick every 15 seconds while visible. Send a new bounded request ID each time and retain at most one acceptable response for the current scope. Cancel on navigation, logout or socket replacement. Apply only to IDs still in the displayed list. Preserve other roster fields and keep the previous state if no response arrives.
- [ ] Rerun the task tests and `./gradlew.bat :shared:compileKotlinJs :shared:compileKotlinWasmJs`. Verify two roster scopes and both directions of client compatibility. Commit the protocol and refresh behavior together.

## Task 4 — Local failure and load acceptance

- [ ] Extend the local fixture with a Redis outage and recovery using the existing fault proxy in the rate limit tests. Verify stale replies and error suppression, abrupt process death and natural expiration, replacement closure ordering, another live backend retaining its lease, and updates after recovery. Use real 20/60/15 second intervals for the timing acceptance, separate from short unit-test timing.
- [ ] Start an isolated fixture from the pinned Redis image with `maxmemory 64mb`, `maxmemory-policy noeviction`, `appendonly yes` and `appendfsync everysec`. Benchmark two independent backend connections with 100 and then 500 simulated authenticated sockets, up to 50 users per roster and 20 seconds between renewals. Use identical independent rate-limit traffic in a baseline and in the presence run. Capture p95 batch read latency, read/write counts, peak used memory, AOF current size growth, eviction and rejected command deltas, plus protected-route errors. Use `INFO memory`, `INFO persistence`, `INFO stats` and the finite application counters before and after. Keep the duration, request rates and sample counts with the numbers. Treat missed freshness bounds, eviction, OOM or new rate-limit failures as failed acceptance.
- [ ] Run `./gradlew.bat :server:test :protocol:jvmTest :shared:testAndroidHostTest` with the fixture. Confirm Redis tests ran and report any platform-specific tests omitted. Save a sanitized local result under `.artifacts/redis-presence-local-20261104/`, remove only the fixture created for this run and record any risk that affects a later rollout. No production mutation is part of local acceptance.
