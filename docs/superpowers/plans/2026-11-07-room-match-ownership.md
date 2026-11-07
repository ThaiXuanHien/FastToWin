# Room and Match Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run a private classic 1v1 room through two backends, recover an interrupted match and record its result once.

**Architecture:** PostgreSQL serializes room ownership, membership, session generations and durable match changes. WebSocket backends route commands to the current owner and deliver committed room changes in revision order. Either backend can resume from the committed snapshot after the ownership lease ends.

**Tech Stack:** Kotlin/JVM 17, Ktor/Netty, PostgreSQL 18, HikariCP, Flyway, existing Redis presence and rate limits, Caddy, JUnit and Docker Compose.

**Spec:** [Approved room and match ownership design](../specs/2026-11-07-room-match-ownership-design.md) · [Issue #15](https://github.com/ThaiXuanHien/FastToWin/issues/15)

## Global Constraints

- The pilot serves authenticated test accounts in private 1v1 `CASUAL` and `ORDER` rooms. The default single backend path still handles other gameplay and existing snapshots.
- Run two independent processes with synthetic accounts and an isolated database locally before any staging rehearsal. Staging uses a dedicated database and two backends on the same build. Production rollout and replica changes have a separate approval.
- Renew the PostgreSQL lease every **3 seconds** with a **12-second** duration. Use PostgreSQL time. The initial staging recovery target is **25 seconds** with healthy PostgreSQL and a surviving backend.
- Keep a **30-second** reconnect window beginning at recovery. Persist the deadline so a second takeover during that window preserves it. An infrastructure outage alone does not award a forfeit within that window.
- Commit state and its events before acknowledging a mutation. Keep at most **256 event revisions per room for 2 minutes**. Reconcile gaps from a committed snapshot. Apply quota and settlement once per match.
- Keep `/internal/*` private. Use bounded timeouts and fixed labels for metrics. Put no authentication tokens, raw room passwords or player identifiers in logs or metrics.
- Use a dedicated worktree when executing. Check the highest Flyway version on the chosen base before reserving the next version. Use local tests during implementation. If the user asks to run CI, follow `AGENTS.md` and dispatch once with `run_android_ui=true` by default.

---

## File map

Paths below use `S = server/src/main/kotlin/com/hienthai/fastowin/server` and `T = server/src/test/kotlin/com/hienthai/fastowin/server`.

| Task | Files to create or modify | Responsibility |
| --- | --- | --- |
| 1 | `server/src/main/resources/db/migration/V70__room_ownership_pilot.sql`, `S/ownership/RoomOwnership.kt`, `S/ownership/PostgresRoomOwnershipRepository.kt`, `T/ownership/PostgresRoomOwnershipRepositoryTest.kt` | Authority, session fence, membership, event log and commit contract. `V70` follows current `V69`. |
| 2 | `server/src/main/resources/db/migration/V71__room_ownership_pilot_settlements.sql`, `S/ownership/OwnedMatchEconomy.kt`, `S/PostgresPlayQuotaRepository.kt`, `S/PostgresMatchResultRepository.kt`, `T/ownership/PostgresOwnedMatchEconomyTest.kt` | Reuse quota and result SQL on the fenced room transaction and keep a durable settlement digest. |
| 3 | `S/ownership/OwnedRoomEngine.kt`, `S/ownership/OwnedClassicMatch.kt`, `S/ownership/RoomOwnership.kt`, `S/ownership/PostgresRoomOwnershipRepository.kt`, `S/ownership/OwnedMatchEconomy.kt`, `S/GameEngine.kt`, `S/ActiveRoomRepository.kt`, `S/PasswordHash.kt`, `S/RoomSelectionMetrics.kt`, `T/ownership/OwnedRoomEngineTest.kt` | Private classic room commands, durable replay and owner recovery. |
| 4 | `S/ownership/RoomOwnershipPilot*.kt`, `S/ownership/OwnerCommandRoutes.kt`, `S/ownership/RoomEventDelivery.kt`, `S/ownership/PostgresRoomEventWakeups.kt`, `S/Application.kt`, `S/Database.kt`, `S/Main.kt`, `server/build.gradle.kts`, migration `V72` and ownership transport tests | Wire the normal WebSocket path, private routing and authorized delivery. Reuse the existing OkHttp engine and PostgreSQL driver. |
| 5 | `S/ownership/RoomOwnershipMetrics.kt`, `S/ServerMetrics.kt`, `S/ownership/RoomOwnershipPilot.kt`, `deploy/room-ownership/compose.local.yaml`, `deploy/room-ownership/Caddyfile`, `T/ownership/RoomOwnershipReadinessTest.kt` | Readiness, drain and an isolated two-process configuration with a test proxy. |
| 6 | `scripts/room-ownership-local.ps1`, `T/ownership/RoomOwnershipFaultTest.kt`, `T/ownership/RoomOwnershipLoadTest.kt`, `docs/room-ownership-pilot.md` | Repeatable fault and load acceptance and staging handoff. |

## Task 1 — Store room ownership in PostgreSQL

**Interfaces:** Define `OwnerFence(roomId: UUID, instanceId: UUID, epoch: Long)`, `SocketFence(playerId: UUID, generation: Long)` and `OwnedRoomState(fence: OwnerFence, revision: Long, snapshot: StoredActiveRoom, recoveryDeadline: Instant?)` in `RoomOwnership.kt`. `RoomOwnershipRepository` exposes `claim(roomId, instanceId): OwnedRoomState?`, `renew(fence): Boolean`, `release(fence): Boolean`, `lookupRoom(playerId): UUID?`, `read(roomId): OwnedRoomState?`, `bindSocket(playerId): SocketFence` and `readEvents(roomId, afterRevision, limit): RoomEventBatch`. `RoomEventBatch` distinguishes a contiguous ordered batch from a gap. Expose `withFencedRoom(fence, expectedRevision, socket, change): FencedResult` where `change: (Connection, OwnedRoomState) -> RoomMutation`. A `RoomMutation` is a new snapshot plus member changes and addressed protocol events, a read-only replay of an already recorded response, or a rejection that rolls back. `socket` is null for owner actions with no player initiator. `FencedResult` distinguishes committed state, replay, rejection, stale state and temporary failure. Create room, first membership, snapshot and first event together in `createRoom(instanceId, socket, initialSnapshot): FencedResult`.

- [x] Add the PostgreSQL fixture using the same Flyway/Hikari setup as `PostgresActiveRoomRepositoryTest`. Require `TEST_DATABASE_URL` for the explicit pilot suite. Give each test its own schema or its own database, use two independent pools, and require executed tests with zero skips in pilot receipts. Test a race to claim an expired room, stale writes and release, a concurrent duplicate membership, and replacement of the same player's socket.

  ```kotlin
  val old = repoA.claim(roomId, instanceA)!!
  fixture.expireLease(roomId) // SQL in the isolated fixture only
  val current = repoB.claim(roomId, instanceB)!!
  assertTrue(current.fence.epoch > old.fence.epoch)
  assertFalse(repoA.renew(old.fence))
  assertFalse(repoA.release(old.fence))
  val persisted = repoB.read(roomId)!!
  assertEquals(current.fence, persisted.fence)
  assertEquals(current.revision, persisted.revision)
  ```

- [x] Run `./gradlew.bat :server:test --tests '*PostgresRoomOwnershipRepositoryTest'`. Verify the new cases fail before the repository exists.
- [x] Create dedicated pilot tables `room_ownership_pilot_rooms`, `room_ownership_pilot_memberships`, `room_ownership_pilot_sessions` and `room_ownership_pilot_events`. Use one room row for snapshot, fence, revision, lease and reconnect deadline. Key membership by `player_id` and index `room_id`. Key events by `(room_id, revision)` and record `owner_epoch` and creation time. Mark closed rooms in the room row until the replay window ends. Route no legacy writes into these tables. In the following pattern check session generation under lock first for a player command, then lock the room row, then perform membership and other writes. Order multiple player/session locks by player ID. Read PostgreSQL time after acquiring the room lock and before mutating the room.

  ```sql
  SELECT instance_id, owner_epoch, revision, lease_until, snapshot
  FROM room_ownership_pilot_rooms WHERE room_id = ? FOR UPDATE
  ```

  In the transaction reject a stale generation, mismatched instance or epoch, expired lease, closed room, or mismatched revision before side effects. Renew and release match the full fence. On claim compare expiry with `clock_timestamp()`, increment the epoch once, and return the committed snapshot. Set finite per-transaction statement and lock timeouts. A timeout or ambiguous commit produces a temporary error and a fresh read at the caller. Commit events with state, notify after durable write via transactional `pg_notify` using room ID and revision, and trim committed events to 256 revisions and two minutes.
- [x] Rerun the targeted test and `./gradlew.bat :server:test --tests '*PostgresActiveRoomRepositoryTest'` with the isolated fixture. Verify lease claims have one winner and no stale operation modifies data. Commit only task files with `feat(server): persist fenced room ownership`.

Task 1 verification on 07/11/2026 used an isolated PostgreSQL 18.6 instance, separate Hikari pools and a clean database for final migration validation. The ownership suite ran 7 tests and the existing snapshot regression ran 1 test. All passed with zero skips. The normal game runtime is wired in later tasks.

## Task 2 — Fence quota and result transactions

**Interfaces:** Consume `withFencedRoom` from Task 1. Add connection-scoped helpers to the existing repositories, `reserveAndFinalizeMatch(connection, userIds, matchId, nowMillis): PlayQuotaConsumption` and `saveMatch(connection, completed): MatchWriteOutcome`, with `INSERTED`, `ALREADY_SAVED` and `CONFLICT`. `OwnedMatchEconomy` calls these helpers inside the room transaction for start and settlement. Provide `start(fence, revision, socket, candidate): FencedResult` and `settle(fence, revision): FencedResult`.

- [x] Add cases for repeated start, a committed settlement whose response was lost, settlement attempted by a stale owner, and a second payload for the same match ID. Assert actual `matches`, quota, player-stat and reward ledger deltas. Use two real JDBC transactions and reconcile a lost response by reading the committed room and result.

  ```kotlin
  fixture.startAndDropReply(ownerA, matchId)
  val ownerB = fixture.expireAndClaimOnB(roomId)
  assertEquals(matchId, fixture.readPlayingMatch(ownerB))
  assertEquals(1, fixture.quotaReceiptCount(matchId, hostId))
  fixture.finishAndRetryOnBoth(ownerA, ownerB, matchId)
  assertEquals(1, fixture.matchCount(matchId))
  assertEquals(fixture.expectedRewardOnce, fixture.rewardDelta(hostId))
  ```

- [x] Run `./gradlew.bat :server:test --tests '*PostgresOwnedMatchEconomyTest'` and observe the expected failures.
- [x] Extract connection-scoped SQL from the existing start and result helpers. Keep their existing wrappers for single-backend callers. For pilot start, reserve and finalize the existing quota on the same connection that records the stable `matchId` and playing snapshot. For settlement, lock the room first and use the pending completion stored in its committed snapshot as the input. Compare an existing result using a canonical digest of the accepted completion payload recorded by the pilot transaction. A mismatched digest returns `CONFLICT` before rewards and increments the integrity counter. Retry preserves the digest, receipt and `matchId`. Commit the settlement marker and cleared pending completion with the match result and reward side effects. Keep the legacy global `recoverPreparedMatches` sweep out of the pilot runtime.
- [x] Rerun `./gradlew.bat :server:test --tests '*PostgresOwnedMatchEconomyTest' --tests '*PostgresPlayQuotaRepositoryTest' --tests '*PostgresMatchResultRepositoryTest' --tests '*PostgresMatchRewardTest'`. Verify transaction rollback leaves no partial debit or reward. Commit `feat(server): fence match quota and settlement`.

Task 2 verification on 07/11/2026 used isolated PostgreSQL 18 and two separate pools. All 16 tests passed without skips, including the ownership and legacy snapshot suites. The economy suite verifies takeover, replay of an identical result, rejection of conflicting results and rollback after the economy writes. The playing snapshot clears both ready flags as in the existing game. The pilot engine will call these transactions starting in Task 3.

## Task 3 — Apply room commands to durable snapshots

**Interfaces:** `OwnedRoomEngine.handle(socket: SocketFence, message: ClientMessage): OwnedRoomReply`, `OwnedRoomEngine.reconnect(socket): OwnedRoomReply`, and `OwnedRoomEngine.disconnect(socket): Unit`. `OwnedRoomReply` carries a public protocol result or a typed retryable error and the committed `(epoch, revision)` for successful room changes. The engine uses `RoomOwnershipRepository` and `OwnedMatchEconomy` from Tasks 1–2.

- [x] Write tests for `CreateRoom`, `JoinRoom`, `SetReady`, `SelectNumber` and `LeaveRoom` against authenticated private `CASUAL/ORDER`. Assert the same snapshot rules as `GameEngineTest`, including invalid password, capacity, repeated `requestId`, latest snapshot after ambiguous commit, and denial of a superseded socket. Test that restoration after a takeover preserves numbers, selected numbers, points, sequence and pending result.

  ```kotlin
  val first = engine.handle(host, selectOne)
  val repeated = engine.handle(host, selectOne)
  assertEquals(first.publicMessage, repeated.publicMessage)
  assertEquals(first.revision, fixture.readRoom(roomId).revision)
  assertEquals(listOf(1), fixture.readRoom(roomId).snapshot.selectedNumbers)
  ```

- [x] Run `./gradlew.bat :server:test --tests '*OwnedRoomEngineTest'` and verify failure on the new behavior.
- [x] Extract only the classic mutation and projection logic needed to operate on a detached snapshot. Reuse existing protocol types and password validation. Store selection outcomes using `(matchId, playerId, requestId)` and reject reused IDs with different selection content. Replay a prior response only after membership and session checks. An idless retry after a new WebSocket reads current membership before resubmitting. Persist a canonical recovery deadline when a live match is taken over. Reattach current sockets, postpone infrastructure-related disconnect forfeits during the window, and evaluate ordinary expiry once under the current fence. Apply lifecycle commands through `withFencedRoom`, including cleanup and close. Scan a bounded batch of expired pilot rooms on the renewal tick, attempt claims and recover only the rooms this process acquires. Retry a durable pending settlement under the current fence.
- [x] Rerun `./gradlew.bat :server:test --tests '*OwnedRoomEngineTest' --tests '*GameEngineTest' --tests '*MaintenanceGameEngineTest'`. Commit `feat(server): recover owned classic rooms from snapshots`.

Task 3 verification on 07/11/2026 ran 180 tests without skips, including seven room engine cases using two pools on isolated PostgreSQL 18, legacy game tests, ownership and economy regressions, and the snapshot regression. The shared localization check passed. The engine exposes `renewalTick` for bounded claims and tombstone cleanup. Task 4 connects the pilot to WebSocket ingress and Task 5 schedules the tick.

## Task 4 — Route WebSockets and replay committed updates

**Interfaces:** `RoomOwnershipPilot.onConnected(account): SocketFence`, `onMessage(socket, command): OwnedRoomReply` and `onDisconnected(socket)`. `OwnerCommandRoutes` receives a private command envelope with ingress ID, authenticated sender and target instance IDs, room and ownership fence, socket generation and encoded supported command. `RoomEventDelivery` tracks the last delivered epoch and revision per subscribed room and socket generation.

- [x] Add a Netty test with two servers on distinct ephemeral ports. Connect one real WebSocket to each, authenticate with fixture accounts, create on A and join on B using `/game`, and assert both sockets receive the same committed revision. Then disconnect the old socket after binding a replacement. Drop an event notification and inject duplicate or older notifications. Verify delivery catches up once. Test spoofed internal requests, expired credentials, oversize payloads, non-test accounts and unsupported game modes.

  ```kotlin
  val room = fixture.createPrivateClassicOnA(host)
  fixture.joinOnB(guest, room.roomId)
  assertEquals(fixture.snapshotSeenBy(host).roomId, fixture.snapshotSeenBy(guest).roomId)
  assertEquals(fixture.committedRevision(room.roomId), fixture.revisionSeenBy(guest))
  ```

- [x] Run `./gradlew.bat :server:test --tests '*RoomOwnershipNettyTest'` and observe failure before the pilot path is wired.
- [x] Wire `FASTTOWIN_ROOM_OWNERSHIP_MODE=pilot` as explicit opt-in and default to the existing path. Validate a fixture database marker, a nonempty account allowlist, unique instance IDs and private peer addresses. Use the existing auth, origin checks and rate-limit gate on public ingress. Derive player identity from the authenticated WebSocket. Forward once on the private channel after rate-limit admission, with a 64 KiB body cap, 2-second request timeout, and authenticated peer identity bound to the envelope. Give the pilot peer service a separately scoped secret and verify integrity, peer IDs and an expiring nonce before decoding or dispatching the command. The owner checks the session fence, membership and lease in PostgreSQL. Never put public credentials in forwarded envelopes. A changed or unreachable owner yields the existing temporary/reconnect path and a fresh routing lookup before the next attempt.
- [x] Store recipient-safe events with each mutation, use PostgreSQL notifications as wake-ups and reconcile connected subscriptions at least once per second with bounded batches. Resolve the currently bound socket before sending each batch. Gate cross-epoch replay on a fresh snapshot. On retention gaps, send the latest committed snapshot and advance the cursor. Keep public traffic on the normal protocol and reuse `SessionReady.currentGame` on reconnect. Add capability negotiation and client tests before using any pilot-only public message shape.
- [x] Rerun `./gradlew.bat :server:test --tests '*RoomOwnershipNettyTest' --tests '*GameWebSocketTest' --tests '*RedisRateLimitRoutesTest'`. Confirm an internal hop does not consume quota twice. Commit `feat(server): route pilot room traffic across backends`.

The pilot requires `FASTTOWIN_ENV=dev`, a PostgreSQL fixture with a matching marker and verified allowlisted accounts. `FASTTOWIN_ROOM_OWNERSHIP_NODE_ID` and `FASTTOWIN_ROOM_OWNERSHIP_PEER_ID` are stable identities for signing. Each process boot generates a new owner instance ID for the PostgreSQL fence. The fixture also supplies `FASTTOWIN_ROOM_OWNERSHIP_PEER_URL`, `FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET` (or its `_FILE` form), `FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID` and `FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS`. Room discovery and reconnect reuse `RoomList` and `SessionReady.currentGame`. Task 5 wires the private topology, lease tick, readiness and drain.

Task 4 verification on 07/11/2026 ran 61 targeted tests without skips on local PostgreSQL 18. This includes two real Netty backends, peer request authentication and limits, PostgreSQL LISTEN, lost and duplicate wake-ups, owner epoch recovery, and the existing WebSocket and rate-limit regression suites. The shared localization check passed. The two-backend test checked that joining from the existing room list produced the same committed event on both sockets and consumed the join admission limit once.

## Task 5 — Configure local routing, readiness and drain

- [x] Add readiness tests for invalid schema or pilot configuration, PostgreSQL failure, recovery and planned drain. Exercise a racing create while drain is starting. Assert the shared gate closes new room admission and reports unavailable readiness, already admitted transactions resolve, and room leases are released only after their final snapshot is committed. Liveness stays `OK` while the process is available.

  ```kotlin
  fixture.beginDrain(backendA)
  assertEquals(HttpStatusCode.ServiceUnavailable, fixture.readiness(backendA))
  assertEquals(HttpStatusCode.OK, fixture.health(backendA))
  fixture.awaitReleasedRoom(roomId)
  assertEquals(fixture.lastCommittedSnapshot(roomId), fixture.claimOnB(roomId).snapshot)
  ```

- [x] Run `./gradlew.bat :server:test --tests '*RoomOwnershipReadinessTest'` and observe failure on the readiness/drain behavior.
- [x] Add readiness that checks PostgreSQL and an available pilot worker. Use one shared admission gate to reject new room work during drain. Finish or roll back admitted transactions, release each room with its fence, then close the corresponding sockets to allow reconnect. On process death the lease expires naturally. Export counters for takeovers, rejected fences, routing, resync and settlement failures, and a gauge/histogram for queued event age and recovery latency. Count outcomes at the point they occur and use fixed labels without IDs.
- [x] Add an isolated Compose topology for two Ktor processes, PostgreSQL 18, Redis and a Caddy test proxy. Health-check `/ready` on each private upstream. Keep only the game proxy and explicitly selected fixture ports on loopback. Verify public `/internal/*` is denied and direct private `/ready` is reachable only inside the fixture. Preserve the current production Caddy configuration by putting pilot routing in `deploy/room-ownership/Caddyfile`.
- [x] Rerun the targeted test and `docker compose -f deploy/room-ownership/compose.local.yaml config --quiet`. Commit `feat(server): add pilot readiness and local routing`.

Task 5 verification on 07/11/2026 ran 33 tests without skips, including five readiness/drain tests and three fixture seeding tests against isolated PostgreSQL 18. The localization check and installDist passed. The network-deadline case disabled the server statement timeout and confirmed the JDBC read stopped before a seven-second query completed. Drain retries fence release while the database resolves the timed-out transaction.

An isolated Docker Compose project then passed real readiness and proxy checks. Public `/health` returned 200 and `/ready`, `/internal`, and `/internal/metrics` returned 404. Both private readiness probes succeeded. Two synthetic accounts started a match and committed selection 1. A planned Docker stop of the owner released the room, and both accounts reconnected with the same match and selected number in 2,445 ms. The new epoch and two finalized quota receipts were verified in PostgreSQL. The surviving backend recorded takeover, resync and recovery histogram samples. Containers, networks, project images and volumes were removed after the smoke run.

Queued-event age covers persisted events for known current socket cursor positions. An uncertain first create/join appears after discovery resolves its room, so that narrow case can temporarily under-report. Task 6 expands this rehearsal to process kills, network partitions and sustained load.

## Task 6 — Prove recovery across independent processes

- [x] Build the acceptance runner around `./gradlew.bat :server:installDist`. Start resources using a unique Compose project name and synthetic credentials on the isolated network. Generate fixture-only data before each suite, reject external database or Redis targets, and clean up only the named fixture project. Save sanitized receipts under `.artifacts/room-ownership-local/<run-id>/` with image/build identity, durations, assertions and cleanup.
- [x] Add fault cases using real process and network control. Kill A after a committed selection. Block only A's path to PostgreSQL, let B claim, and reconnect A to prove stale writes are rejected. Separately interrupt before commit, after commit before ACK, between start and response, and after settlement before response. Drive both users through the real `/game` WebSocket. Interrupt Redis in a distinct case and assert no unsafe fallback or duplicate economy changes. Interrupt PostgreSQL and assert no optimistic acknowledgements.

  ```kotlin
  fixture.selectAndAwaitAck(host, number = 1)
  fixture.killOwner()
  val restored = fixture.awaitSameMatchOnSurvivor(timeoutSeconds = 25)
  assertEquals(listOf(1), restored.selectedNumbers)
  fixture.finishMatchAndRetrySettlement(restored.matchId)
  assertEquals(1, fixture.matchCount(restored.matchId))
  ```

- [x] Run the fault suite through `pwsh -File scripts/room-ownership-local.ps1 -Suite Fault`. Require both processes to have been reachable before each failure and save proof of the new epoch, previous committed revision and side-effect counts. A skipped or untriggered fault fails the acceptance run.
- [x] Add a seeded load scenario that runs the same script on one pilot backend and on two. Start with 20 concurrent private rooms, 5 selections per room per second aggregate, and a 10-minute run. Create the rooms with two authenticated synthetic clients per room, and close and create new rooms as matches finish. Measure p95 command latency, p95 recovery latency across repeated independent owner stops, events waiting to deliver and PostgreSQL pool saturation. Record the run configuration, compare the measurements and use them to select staging limits. The 25-second recovery goal remains the test threshold. Establish a staging threshold for command latency from the measured baseline using a documented margin.
- [x] Run `pwsh -File scripts/room-ownership-local.ps1 -Suite Load` and then `./gradlew.bat :server:test`. Inspect JUnit results for executed targeted cases, replay receipts and cleanup. Record outcomes in `docs/room-ownership-pilot.md`, along with the staging smoke steps, private routing, how to drain the pilot and criteria for stopping the rehearsal. Commit `test(server): exercise room owner failover across processes`.

## Staging handoff

Prepare a separately reviewable staging run using the exact tested build, isolated test data and one scripted case per row in the approved spec. Record both route choices, leases and epochs, pre/post snapshots, quota and reward counts, and recovery latency. Publish only bounded operational metrics through the existing review process. Report limitations such as matchmaking, tournaments and cluster-wide activity coverage as open portions of #15. Bring the measured staging result to the owner before considering any production replica change.
