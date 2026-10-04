# Durable maintenance contract

Tasks 1–2 provide durable policy, protected admin HTTP, runtime admission, drain
and abort. Main selects the runtime and real repository when a database is
configured. Independent paid-receipt HTTP/client transport and admin/player UI
remain Tasks 3–4. Both /status and /api/status use the runtime provider.

## Types and service seam

Common protocol enums are `MaintenanceState` (`ACTIVE`, `ANNOUNCING`, `DRAINING`,
`MAINTENANCE`) and `MaintenanceMode` (`PLANNED`, `EMERGENCY`).

`MaintenanceAdminContext(token: String?, csrfToken: String? = null,
origin: String? = null)` carries the existing admin cookie/CSRF/Origin.
`MaintenanceService` exposes:

```kotlin
suspend fun read(context: MaintenanceAdminContext): MaintenanceAdminResponse
suspend fun update(context: MaintenanceAdminContext, request: MaintenanceUpdateRequest): MaintenanceAdminResponse
suspend fun refresh(activeMatches: Int, allowEmptyDrain: Boolean = true): MaintenancePolicy
fun effectivePolicy(): MaintenancePolicy
fun publicStatus(): ServiceStatusResponse
```

The constructor takes a `MaintenanceRepository`, optional `AdminService`, an
injectable UTC epoch-millisecond clock, `environmentOverride: Boolean = false`,
and optional `environmentMessageVi`/`environmentMessageEn`. Production wiring
must derive that Boolean from `FASTTOWIN_MAINTENANCE=true`. Environment override
always produces effective `MAINTENANCE`/`EMERGENCY`, source `ENVIRONMENT`, and
cannot be disabled through admin. It does not overwrite the saved DB policy.

`DatabaseRuntime.maintenanceRepository` is the connection-bound PostgreSQL
repository. `UnavailableMaintenanceRepository` always fails with safe 503;
there is no production in-memory maintenance persistence. The equivalent
in-memory repository exists only under test sources.

## Protected HTTP

Optional `gameModule(maintenanceService = service)` registers:

| Method and path | Behavior |
| --- | --- |
| `GET /admin/api/maintenance` | Owner/Operator/Support read and audited observation |
| `POST /admin/api/maintenance` | Owner mutation, CSRF and exact Origin, atomic audit/replay |

The normal default has no maintenance admin routes. Supplying an unavailable
durable repository returns 503, never a persisted or effective fake `ACTIVE`.
History uses existing `GET /admin/api/audit`, under its existing Owner access.

Use cookie `fasttowin_admin`, header `X-Admin-CSRF`, and exact configured `Origin`
for mutations. Reads accept absent Origin (same-origin navigation); any provided
Origin must match exactly. A player bearer token is not an admin credential.
Current active account, current role and current nonrevoked/unexpired session are
checked inside the maintenance transaction before replay lookup. Origin and CSRF
are also rechecked. Replay never restores permissions lost since the first call.

POST accepts strict `application/json` UTF-8, at most 16,384 bytes. Unknown fields,
malformed JSON, malformed UTF-8, missing required fields and wrong types return
400. All responses, including failures, carry `Cache-Control: no-store` and
`X-Content-Type-Options: nosniff`.

`MaintenanceUpdateRequest` is serialized with the following exact names:

| Field | Type/default | Validation/meaning |
| --- | --- | --- |
| `expectedVersion` | required Long | Nonnegative; matches locked saved version |
| `requestId` | required String | Exact case-sensitive `[A-Za-z0-9_-]{1,128}` |
| `reason` | required String | Nonblank, 1–500 UTF-16 characters; no control characters |
| `mode` | nullable enum, null | Required when enabled |
| `cause` | nullable String, null | Required when enabled; `UPDATE`, `INCIDENT`, `SECURITY`, `OVERLOAD`, `OTHER` |
| `messageVi`, `messageEn` | nullable String, null | Both required/nonblank when enabled, 1–1000 UTF-16 characters each |
| `warningMinutes` | Int, 5 | 0–60 inclusive |
| `drainMinutes` | Int, 15 | 1–60 inclusive |
| `expectedEndEpochMillis` | nullable Long, null | 0–253402300799999 inclusive, advisory only |
| `enabled` | required Boolean | False opens saved policy; true starts/edits/closes it |

Messages are plain runtime text, permit normal newlines/CR/tabs, and reject other
control characters. Never inject them as HTML. Optional cause/messages are still
validated when supplied on disable. Disable clears previous public messages,
mode, cause and deadlines while retaining the latest internal reason/actor.
UTC timestamps are persisted; UIs display Asia/Bangkok. Past advisory expected-end
values are allowed and never trigger reopening. Checked arithmetic rejects an
out-of-range planned deadline instead of wrapping.

After authorization, the request's typed JSON encoding is SHA-256 fingerprinted.
Explicit defaults and omitted defaults decode to the same identity. Replay is
scoped to `(actorId, requestId)`, exact same payload returns the originally saved
response even after subsequent automatic transitions; a different payload is
409. The original response is an historical acknowledgement: follow with GET to
observe current saved/runtime versions. Stale expectedVersion is 409 for fresh
requests. Audit failure rolls back snapshot, replay and automatic transitions.

Existing errors use `{ "code": "...", "message": "safe legacy fallback" }`:
400 `INVALID_REQUEST`, 401 `UNAUTHORIZED`, 403 `FORBIDDEN`, 409 `CONFLICT`,
409 `MODE_LOCKED`, 503 `STORAGE_UNAVAILABLE`. SQL text, schema names, secrets and
exception details are never returned. Coroutine cancellation propagates.

## Saved policy and observation

`MaintenanceAdminResponse` contains:

| Field | Meaning |
| --- | --- |
| `persisted` | Current committed `MaintenanceSnapshot` (or historical replay result) |
| `persistedSource` | `DATABASE` |
| `effective` | This process's observed `MaintenancePolicy`, with override applied |
| `runtime` | `MaintenanceRuntimeObservation`: version, observedAtEpochMillis, activeMatches, available |

The snapshot fields are `version: Long`, `state`, nullable `mode`, `cause`,
`messageVi`, `messageEn`, `reason`, `actorId`, `changedAtEpochMillis`,
`startedAtEpochMillis`, `transitionAtEpochMillis`, `drainDeadlineEpochMillis`,
`expectedEndEpochMillis`, plus `warningMinutes` and `drainMinutes`.
Initial durable version is 0/ACTIVE; this is seeded only by migration V51.
Missing/corrupt durable storage is unavailable. An admin write increments the
saved version but **does not** advance runtime observation or claim socket closure.

On initial planned enable, warning deadline is `now + warningMinutes`, and drain
deadline is that warning deadline plus `drainMinutes`. Warning 0 enters DRAINING
immediately. `transitionAtEpochMillis` means warning deadline in ANNOUNCING, drain
deadline in DRAINING, and null in ACTIVE/MAINTENANCE. `startedAtEpochMillis` is
initial enabling time, not automatic transition time. `changedAtEpochMillis`
changes on every committed transition; automatic transitions preserve the last
admin actor/reason and record audit action `MAINTENANCE_TRANSITION` with null
audit actor. Admin changes use `MAINTENANCE_UPDATE`; reads use `MAINTENANCE_READ`.
Audit summaries contain only state/mode/version, not bilingual message text.

Enabled-to-enabled edits preserve current stage, start time and deadlines.
Message/cause/internal reason/advisory end edits are allowed with a new version.
Changing warning/drain values while enabled is 409; disable then enable to
reschedule. PLANNED may escalate to EMERGENCY immediately. An enabled EMERGENCY
policy cannot change to PLANNED (409 `MODE_LOCKED`); disable then enable.
No edit or advisory timestamp implicitly opens service.

## Task 2 runtime responsibility

Call `refresh` before admitting any game at startup, then within 5 seconds of
committed policy changes. Failures must not allow normal startup admission.
`refresh` atomically reads/advances due warning and drain states and empty drain;
each automatic stage increments version conditionally against the locked row.
An overdue warning+drain may advance both stages in one atomic transaction.
Deadline calculation uses saved absolute UTC times, so restart never restarts a
timer. `activeMatches` must be nonnegative and come from the trusted runtime.

Before passing zero to refresh, the caller must apply admission/drain blocking
and sample active matches under the runtime admission boundary. An earlier
ACTIVE observation must not admit a match between that sample and closure.
Implement admission/transition synchronization in Task 2; this storage module
cannot infer trustworthy engine state. Periodic refreshes are serialized per
service instance, and publish the policy only after transaction commit.

The initial observation has nullable state/version, source `UNAVAILABLE`, and
`available=false`. A failed policy read marks the observation unavailable and
blocks new intent; it retains known state and existing-match permission for
already started matches. A successful refresh recovers availability. Override
still forces closure even during storage failure. `effectivePolicy()` is a
synchronous immutable snapshot, never a fresh DB query. `runtime.available`
describes successful DB observation; override can be effective while that value
is false. Observation does not promise runtime side effects have finished.

| Policy | blocksNewIntent | allowsExistingMatch | isFullyClosed |
| --- | --- | --- | --- |
| Available ACTIVE/ANNOUNCING | false | true | false |
| Available DRAINING | true | true | false |
| MAINTENANCE/override | true | false | true |
| Unavailable before any observation | true | false | true |
| Unavailable after known ACTIVE/ANNOUNCING/DRAINING | true | true | false |

See the runtime integration contract below for admission, resume, abort and
socket closure. Independent receipt HTTP/client transport remains Task 3.

## Public protocol

`publicStatus()` returns observed public fields only, or throws safe 503 when
unavailable without override. Task 2 must wrap that exception in its HTTP status
handler and add no-store. The common `ServiceStatusResponse` retains legacy
`maintenance`, `message`, `pollAfterSeconds`, and adds nullable `state`, `mode`,
`version`, `messageVi`, `messageEn`, `transitionAtEpochMillis`,
`expectedEndEpochMillis` with backward-compatible defaults. Legacy JSON true
defaults state to MAINTENANCE; false/omitted defaults to ACTIVE. Newly observed
states always have a nonnull state. Legacy `maintenance` is true only at
MAINTENANCE; DRAINING never forces old clients out of an admitted match.
The message fallback is Vietnamese then English. Active poll delay is 30 seconds;
other available states advertise 5 seconds. Public data never includes internal
reason, cause, actor or audit. The existing `/status` path stays available during
integration; adding the worker-safe `/api/status` alias is Task 2/4 work.

## Transaction rules

`MaintenanceRepository.transaction` supplies a single connection-bound
`MaintenanceTransaction` with `admin: AdminTransaction`, locking `snapshot()`,
conditional `putSnapshot(expectedVersion, value)`, `request(actor, requestId)` and
`putRequest(actor, requestId, record)`. PostgreSQL acquires shared admin advisory
lock `7421092187349` first, then locks the maintenance singleton. Account access
changes and session revocation use that same advisory lock; no separate auth
repository call may substitute for this bound authorization. V51 persists the
version/state/snapshot payload and separate `runtime_maintenance_requests` table.
Snapshot, authorized audit and replay commit or roll back together.

## Task 2 runtime integration

Main passes MaintenanceService and MaintenanceRuntime to gameModule. With a
configured database, the runtime uses durable policy and applies an initial
refresh before registering handlers/timers. Without a configured database, it
explicitly preserves environment-only operation; maintenance admin operations
use UnavailableMaintenanceRepository and return503. A failed configured policy
read never becomes ACTIVE.

```kotlin
class MaintenanceRuntime(
    engine: GameEngine,
    service: MaintenanceService? = null,
    environmentStatus: () -> ServiceStatusResponse = { ServiceStatusResponse(false) },
    monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    suspend fun observe()
    suspend fun reconcile(): MaintenanceApplication
    suspend fun refresh(): MaintenanceApplication
    fun fenceStaleObservation()
    fun publicStatus(): ServiceStatusResponse
}
data class MaintenanceApplication(
    val deliveries: List<Delivery> = emptyList(),
    val abortedRoomIds: Set<String> = emptySet(),
    val closeSockets: Boolean = false
)
// GameEngine:
fun publishMaintenancePolicy(policy: MaintenancePolicy)
suspend fun reconcileMaintenancePolicy(): MaintenanceApplication
suspend fun applyMaintenancePolicy(policy: MaintenancePolicy): MaintenanceApplication
suspend fun activeMaintenanceMatchCount(): Int
suspend fun admitsNewIntent(): Boolean
fun newIntentAllowed(): Boolean
fun gameConnectionsClosed(): Boolean
suspend fun retryMaintenanceSettlements(): List<Delivery>
```

The Application owns one parent job with exactly three children: observation
every 1,000ms, reconciliation every 1,000ms, and a watchdog every 100ms. Observation
and reconciliation have independent IO workers; neither launches replacement
jobs while its previous operation is suspended. Application cancellation cancels
all three. `refresh()` remains the startup/test composition of observe then
reconcile; production polling uses the independent workers.

Observation publishes the volatile admission fence before waiting for room
persistence. First observation uses allowEmptyDrain=false, applies policy at the engine admission
boundary, then samples the count. The second refresh permits empty-drain
advancement only when the first applied policy blocked admission. This covers
policy changes between reads without trusting an ACTIVE zero count. Counts
include accepted completed results and staged next-round rooms awaiting durable
acknowledgment, never fabricated values.
Read failures block new intent, retain known closure, and return safe public503;
cancellation propagates. Observations do not promise sockets already closed.
If an observation has not completed for 4,000ms, the next watchdog tick marks
status unavailable (safe503) and blocks new intent, preserving known closure in
both database and environment-only modes. A successful observation recovers
availability. Under healthy scheduling/storage, committed policies are observed
on the next one-second cycle; stalled storage instead receives the four-second
staleness fence plus watchdog scheduling delay. No deadline promises durable abort
or socket closure while persistence is unavailable.

Room/ready/rematch/matchmaking/tournament transitions check policy under the
engine mutex after asynchronous profile/quota work. Short admission covers early
economic/profile/social handlers; accepted economic work may finish without
holding that mutex across provider calls. VerifyStorePurchase remains explicitly
allowed using the existing grant workflow. Task3 extracts that workflow into one
shared service and adds independent authenticated HTTP/client transport.

Login/register/guest upgrade reject blocked new intent with503/MAINTENANCE.
Refresh/logout, health, admin and status remain reachable. Both status paths use
one provider/error handler and no-store. Existing Caddy /api forwarding supports
the alias; Task4 owns worker cache migration.

Private persistence adds StoredActiveRoom.maintenanceAborted (Boolean,false),
pendingCompletedMatch (CompletedMatch?,null), and
StoredActivePlayer.accountResumeToken (String?,null). They never appear in public
protocol/status. Full closure marks incomplete rooms FINISHED/resultQueued,
clears forced winner/rematch state and durably saves before returning close
permission. Aborted snapshots never synthesize tournament winners; timers and
reconnect cleanup cannot settle them. Failed abort writes fence reopening until
retry succeeds. Room/refund I/O runs outside the engine mutex, serialized with
other room persistence.

Accepted completed room-backed results persist as exact pending payloads before
result-repository work. Settlement survives socket cancellation; one in-flight
attempt per match and existing repository idempotency prevent duplicate grants.
Pending results count as active drain work and prevent premature tournament
cancellation. Failed result saves remain pending for runtime/restart retry.
Cleanup, disconnect-forfeit and both result-screen exits retain the source room
until acknowledgment; a pending result cannot be overwritten by a rematch. Lookup
uses matchId rather than assuming rematches share their roomId. Attempts have a
4,000ms coroutine timeout inside cancellation shielding; a timed-out attempt
retains its payload and releases the in-flight claim so the owned worker retries.
Blocking JDBC calls must return before coroutine cancellation can complete; they
do not hold up the independent policy fence/watchdog. Abort/refund reconciliation
does not add a NonCancellable wrapper around storage.

Drain resume is scoped to an existing PLAYING participant. New snapshots persist
an authoritative account resume token. Older snapshots without one allow a fresh
verified account identity to resume only its live participant room, issuing a
new bound token; stale pre-restart supplied tokens are ignored only in that
compatibility case. Once authoritative, mismatching non-null tokens reject.
Unrelated/new/waiting identities and spectator-to-player bypasses are blocked;
final checks occur under mutex after appearance lookup.

Drain pauses subsequent tournament rounds; reopening starts eligible pending
rounds. Closure cancels incomplete tournaments with existing CANCELLED, keeps
completed rounds/results and never manufactures a champion.
Ordinary completion and reopening share staged pairing logic. An immutable batch
remains pending until `TournamentRepository.commitProgression` acknowledges it:

```kotlin
data class TournamentProgression(
    val tournament: TournamentSnapshot,
    val newRooms: List<StoredActiveRoom>,
    val completedRoomId: String? = null,
    val removedRoomIds: List<String> = emptyList()
)
suspend fun commitProgression(
    batch: TournamentProgression,
    rooms: ActiveRoomRepository,
    profiles: PlayerProfileRepository
)
```

PostgreSQL uses the tournament repository's single DataSource/connection for
bracket, all generated room snapshots, champion wallet grant and source-room
deletion. No nested profile commit occurs. Tournament and wallet row locks plus
the canonical `(user_id, TOURNAMENT_PRIZE, tournamentId)` source prevent duplicate
grants; replay validates the original recipient and amount. In-memory storage is
explicitly process-local, restores snapshot preimages on failure, and relies on
wallet source idempotency for retries. No schema migration is added.

Rooms and brackets become visible only after acknowledgment. If policy closes
during the write, a second acknowledged batch returns the unexposed round to
PENDING and removes its generated rooms before publication. Failed batches retain
their source result/count and retry in process; PostgreSQL rollback leaves a
restart-recoverable source result or paused bracket. Restoring a paused bracket
reconstructs disconnected roster accounts so pairing can serialize participants;
this does not authenticate or connect them and the existing verified-account
admission rules still apply. Pending progression must commit before count can
drop to zero and socket-close permission can be issued.
TournamentRepository.cancelForMaintenance(cancelled, profiles) atomically saves
cancellation and full entry refunds in PostgreSQL, including paid entrants who
left the lobby. Wallet source TOURNAMENT_MAINTENANCE_REFUND uses tournamentId as
sourceId, per player, and is retry-safe. Uncertain commit outcomes fence writes
until restoration, matching existing paid admission.

GiftService accepts newIntentAllowed: () -> Boolean; Main binds engine admission.
Claim authorizes before disclosure, returns committed receipt replay before
new-payout admission, and checks the gate inside its transaction before award.
Claim-all retains per-item outcomes; new payouts return503/MAINTENANCE while
committed replay remains readable.

Returned deliveries use existing RoomClosed with code MAINTENANCE and
messageKey MaintenanceDefaultMessage. No new socket state message: Task4 polls
status for bilingual warning/draining text. Application delivers closures and
closes authenticated sockets with GOING_AWAY after durable abort success.
Task4 still owns live preview/browser acceptance; Task2 did not restart preview
or production.
