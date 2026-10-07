# Reward event authority and API

Issue #18, Task 1. This contract covers event configuration and the immutable start catalog. Engine capture, durable match snapshots, settlement, and player/admin screens are subsequent tasks. No campaign is automatically created or published at startup.

## Authority and lifecycle

Owner and Operator may list, inspect, create, edit and preview drafts. Owner alone may schedule or cancel. Support has no access to these admin endpoints. All admin checks use the existing `AdminService`, current account/session and CSRF records in the same transaction as the event and audit. Replay never bypasses current authorization.

An event starts as DRAFT, version 1. Updates increment the version. Scheduling freezes the definition and increments the version; effective SCHEDULED, ACTIVE and ENDED states derive from server time, with `startsAtEpochMillis <= now < endsAtEpochMillis`. A draft already in its live period can be scheduled while its end is future. Published definitions cannot be edited. Owner may cancel a draft, scheduled or active event; CANCELLED and ENDED are terminal/read-only. Only previously published events remain publicly inspectable after cancellation. A cancelled draft remains private.

Names require 1–200 Unicode code points, content 1–2000; whitespace-only values and unsupported control characters are rejected. Both languages are mandatory. Timezone is a member of the JVM IANA zone database (for example `Asia/Ho_Chi_Minh` or `UTC`); fixed offsets such as `+07:00` are rejected. UTC bounds are integers in `0..253402300799999`, with start strictly before end. `matchTypes` is a nonempty set of `CASUAL` and/or `RANKED`. Each multiplier independently accepts 1 or 2, including neutral 1/1 drafts/publications.

## Admin HTTP

Base `/admin/api/events`. Enabled when the existing admin service is configured. JSON responses use HTTP 200 on success. Authentication uses the `fasttowin_admin` cookie. Every POST/PUT, including preview, requires exact configured `Origin` and `X-Admin-CSRF`. GET permits absent Origin but rejects a supplied incorrect Origin. Every event path has `Cache-Control: no-store`, including CORS rejection and preflight. Normal route responses also have `X-Content-Type-Options: nosniff`.

| Method/path | Request | Response |
| --- | --- | --- |
| GET base | `page=0`, `pageSize=25`, optional `query`, optional `state` | `AdminPage<RewardEventAdminView>` |
| POST base | `RewardEventDraftRequest`, `expectedVersion=0` | `RewardEventAdminView` |
| GET `/{id}` | UUID | `RewardEventAdminView` |
| PUT `/{id}/draft` | `RewardEventDraftRequest` | `RewardEventAdminView` |
| POST `/{id}/preview` | No body | `RewardEventPreview` |
| POST `/{id}/schedule` | `RewardEventMutationRequest`, with `previewToken` | `RewardEventAdminView` |
| POST `/{id}/cancel` | `RewardEventMutationRequest` | `RewardEventAdminView` |

Pagination is zero-based; page size 1–100, default 25. Negative or overflowing page offsets fail 400. Admin query is a case-insensitive substring of either name, at most 128 UTF-16 code units and without controls. State is one exact enum spelling: DRAFT, SCHEDULED, ACTIVE, ENDED, CANCELLED. Admin lists sort by descending changed time then ascending ID. Page shape: `{items, page, pageSize, total}`.

`RewardEventDraftRequest` has required fields `{expectedVersion: Long, requestId: String, reason: String, draft: RewardEventDraft}`. `RewardEventMutationRequest` has required `{expectedVersion, requestId, reason}` and optional `previewToken: String? = null`. Version must be nonnegative. Request ID matches `[A-Za-z0-9_-]{1,128}`. Reason must be nonblank, no controls, at most 500 UTF-16 code units. Request IDs are deduplicated per actor across all event mutations; operation, target and full request payload are fingerprinted. Reusing an ID with different content or a stale expected version fails 409. Identical authorized replay returns the original response, including its original state/time. Preview tokens bind event ID, version and canonical definition. A draft edit invalidates the prior token. Tokens do not grant permission.

Request JSON is strict: unknown keys, invalid enum/types, malformed UTF-8 and bodies over 32,768 bytes fail 400; body-bearing mutations require `application/json`. Empty preview bodies are sufficient; preview does not mutate an event version or create a replay receipt, but does append an audit.

`RewardEventDraft` (all fields required):

```json
{
  "nameVi": "Cuối tuần nhân đôi", "nameEn": "Double rewards weekend",
  "contentVi": "Thưởng GOLD và XP cho trận trực tuyến.", "contentEn": "GOLD and XP bonuses for online matches.",
  "timezone": "Asia/Ho_Chi_Minh",
  "startsAtEpochMillis": 1790989200000, "endsAtEpochMillis": 1791162000000,
  "goldMultiplier": 2, "xpMultiplier": 2, "matchTypes": ["CASUAL", "RANKED"]
}
```

`RewardEventAdminView`: `{id: String, version: Long, state: RewardEventState, draft: RewardEventDraft, actorId: String, reason: String, changedAtEpochMillis: Long, publishedAtEpochMillis: Long? = null, creatorId: String? = null, publisherId: String? = null}`. Actor/reason identify the latest successful mutation. `creatorId` is assigned at creation and remains stable through edits, publication and cancellation. `publisherId` is assigned only by successful publication and survives cancellation. Both are admin account IDs. Failed publication rolls back the identity together with the definition, audit and replay receipt.

Nullable identities support historical data: `creatorId=null` means the legacy creator is unknown, never the latest actor. For `publisherId=null`, use `publishedAtEpochMillis`: absent/null means never published (including cancelled drafts); a nonnull publication timestamp means the legacy publisher is unknown. Default null properties may be omitted from JSON. New creations always have a creator, and new successful publications always have a publisher. `RewardEventPreview`: `{event: RewardEventAdminView, previewToken: String, overlaps: List<RewardEventPublicView>, policy: "MAX_PER_ASSET", serverNowEpochMillis: Long}`. Overlaps include other published, noncancelled definitions whose intervals and eligible modes intersect, sorted by ID. They are informational; overlaps are allowed and do not stack factors.

Errors use existing `AdminError`: `{code: String, message: String}`. Status/code pairs: 400/INVALID_REQUEST, 401/UNAUTHORIZED, 403/FORBIDDEN, 404/NOT_FOUND, 409/CONFLICT, 503/STORAGE_UNAVAILABLE. CORS can return its own body for disallowed origins before routing, while retaining no-store. Storage errors expose no SQL/driver details. No configured database means explicitly unavailable event storage, never an empty success.

## Public HTTP and protocol

`GET /api/events?page=0&pageSize=25` returns `RewardEventPublicPage`:

```text
{ items: List<RewardEventPublicView>, serverNowEpochMillis: Long,
  page: Int, pageSize: Int, total: Long }
```

Only published SCHEDULED/ACTIVE events appear, sorted by start then ID. `GET /api/events/{id}` returns `RewardEventPublicDetail = {event: RewardEventPublicView, serverNowEpochMillis: Long}`. Published ENDED/CANCELLED history remains available. Private draft IDs return 404. Public endpoints require no bearer token or admin cookie, take no global admin authorization lock, and accept no mutations.

`RewardEventPublicView` is a flat safe DTO with required fields: `id`, `version`, `state`, `nameVi`, `nameEn`, `contentVi`, `contentEn`, `timezone`, `startsAtEpochMillis`, `endsAtEpochMillis`, `goldMultiplier`, `xpMultiplier`, `matchTypes`. Public views and reward source snapshots include no creator, publisher, latest actor, internal reason, request ID, preview token or secret. Public DTOs reside in `protocol/RewardEventProtocol.kt` for shared-client decoding. Timezone is for display; epoch timestamps remain authoritative. Consumers must use server time and server-calculated rewards, not multiply guessed rewards in the client.

The same protocol file defines:

```kotlin
RewardEventSourceSnapshot(
    id: String, version: Long, nameVi: String, nameEn: String,
    startsAtEpochMillis: Long, endsAtEpochMillis: Long,
    goldMultiplier: Int, xpMultiplier: Int
)
MatchRewardEventSnapshot(
    capturedAtEpochMillis: Long? = null, goldMultiplier: Int = 1,
    xpMultiplier: Int = 1, sources: List<RewardEventSourceSnapshot> = emptyList()
)
MatchRewardBreakdown(
    baseGold: Int = 0, bonusGold: Int = 0, totalGold: Int = 0,
    baseXp: Int = 0, bonusXp: Int = 0, totalXp: Int = 0,
    sources: List<RewardEventSourceSnapshot> = emptyList()
)
```

These are additive DTOs; absent fields decode neutrally. No unsolicited WebSocket message or enum was added. Existing game/history DTO integration belongs to Task 2. JSON consumers must tolerate omitted default properties and unknown additive properties as appropriate.

## Start catalog seam and storage ordering

```kotlin
interface EventRewardSource {
    suspend fun <T> withStartCatalog(block: suspend (RewardEventCatalog) -> T): T
}
// Immutable catalog; only public operation:
fun RewardEventCatalog.snapshot(atEpochMillis: Long, matchType: MatchType): MatchRewardEventSnapshot
```

`PostgresRewardEventRepository` implements the source directly. `DatabaseRuntime.rewardEventRepository` exposes it; `Main` supplies this repository to `RewardEventService`. `gameModule` adds an optional trailing `rewardEventService` argument with an unavailable default. This task does not change `GameEngine` or wire its start consumer.

Authenticated PostgreSQL event transactions obtain global admin advisory lock `7421092187349`, then exclusive event advisory lock `7421092187350`, then check current authorization and replay, then write definition/replay/audit in one transaction. This order is deliberately preserved for all admin operations. Start source transactions obtain only the shared event advisory lock, read the catalog before callback, then retain that lock/connection until callback finishes and transaction commits. Callback throw/cancellation rolls back and closes; cancellation is rechecked after JDBC waits and before commit. JDBC lock waiting itself is blocking on the IO dispatcher. Public reads take neither advisory lock. Maintenance observation continues to use its separate existing transaction with no global admin lock.

The callback must not borrow any other connection. Acquire the source before the engine mutex. Task 2 must prepare quota/admission storage before the gate, use engine `tryLock` and release/retry outside the source callback on contention, then sample actual server time at successful start. Existing unrelated engine work can borrow JDBC while holding its mutex, so waiting for that mutex while holding the source connection is unsafe. Tests here prove pool-size-two source/writer ordering with a pool-free callback; the engine/quota/contended-mutex schedule belongs to Task 2.

The catalog copies published definitions; changes after its capture cannot mutate it. Snapshot resolves `[start,end)` and requested mode synchronously. GOLD/XP use independent maxima, never multiplication. Sources include every eligible definition contributing a factor above 1, ordered by ID. A 1/1 definition contributes no source. Real repository snapshots have capture time even with neutral rewards. `NoRewardEventSource` invokes the callback with a neutral catalog yielding the default snapshot (null capture time, 1/1, empty sources); use for legacy/test/tournament defaults only. `UnavailableRewardEventRepository` throws 503, including before a source callback, so configured durable failure cannot silently grant a neutral start.

In-memory storage uses a coroutine mutex around event transactions and source callbacks. It acquires the event mutex before entering `AdminRepository.transaction`, copies/mutates/audits inside that transaction and publishes its copy only after success. It never synchronously waits on a permit while holding the admin mutex. This stronger serial ordering is memory-only. Both implementations keep audit failure atomic with definition/replay changes.

Flyway V54 creates `reward_events` and `reward_event_requests`; V55 adds creator/publisher fields to stored JSON and cached admin responses. V54 remains unchanged. V55 recovers each identity only from a unique committed `EVENT_CREATE` or `EVENT_SCHEDULE` audit for that event. Missing or ambiguous audit history leaves null; it never guesses from the latest actor. Receipt fingerprints, original state/version/timestamps and deduplication keys are preserved. A cached response receives a publisher only if that response was already published, so replaying an old create/update after later publication still returns an unpublished result. Old JSON without the new fields decodes with null identities. No cron, identity targeting, annual repetition, gifts or production scheduling is included. Lunar/holiday presets in the admin UI must create editable drafts only.
