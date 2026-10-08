# Reward event authority and API

Issue #18, Tasks 1–2. This contract covers event configuration, immutable start capture, durable match snapshots and settlement. Player/admin screens are subsequent tasks. No campaign is automatically created or published at startup.

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

`PostgresRewardEventRepository` implements the source directly. `DatabaseRuntime.rewardEventRepository` exposes it; `Main` supplies it to both `RewardEventService` and `GameEngine`. `gameModule` has an optional trailing `rewardEventService` argument with an unavailable default. `GameEngine` has a trailing defaulted `eventRewardSource=NoRewardEventSource`; existing positional arguments retain their order and the single trailing clock-lambda call remains supported.

Authenticated PostgreSQL event transactions obtain global admin advisory lock `7421092187349`, then exclusive event advisory lock `7421092187350`, then check current authorization and replay, then write definition/replay/audit in one transaction. This order is deliberately preserved for all admin operations. Start source transactions obtain only the shared event advisory lock, read the catalog before callback, then retain that lock/connection until callback finishes and transaction commits. Callback throw/cancellation rolls back and closes; cancellation is rechecked after JDBC waits and before commit. JDBC lock waiting itself is blocking on the IO dispatcher. Public reads take neither advisory lock. Maintenance observation continues to use its separate existing transaction with no global admin lock.

The callback must not borrow any other connection. Acquire the source before the engine mutex. Task 2 must prepare quota/admission storage before the gate, use engine `tryLock` and release/retry outside the source callback on contention, then sample actual server time at successful start. Existing unrelated engine work can borrow JDBC while holding its mutex, so waiting for that mutex while holding the source connection is unsafe. Tests here prove pool-size-two source/writer ordering with a pool-free callback; the engine/quota/contended-mutex schedule belongs to Task 2.

The catalog copies published definitions; changes after its capture cannot mutate it. Snapshot resolves `[start,end)` and requested mode synchronously. GOLD/XP use independent maxima, never multiplication. Sources include every eligible definition contributing a factor above 1, ordered by ID. A 1/1 definition contributes no source. Real repository snapshots have capture time even with neutral rewards. `NoRewardEventSource` invokes the callback with a neutral catalog yielding the default snapshot (null capture time, 1/1, empty sources); use for legacy/test/tournament defaults only. `UnavailableRewardEventRepository` throws 503, including before a source callback, so configured durable failure cannot silently grant a neutral start.

In-memory storage uses a coroutine mutex around event transactions and source callbacks. It acquires the event mutex before entering `AdminRepository.transaction`, copies/mutates/audits inside that transaction and publishes its copy only after success. It never synchronously waits on a permit while holding the admin mutex. This stronger serial ordering is memory-only. Both implementations keep audit failure atomic with definition/replay changes.

Flyway V54 creates `reward_events` and `reward_event_requests`; V55 adds creator/publisher fields to stored JSON and cached admin responses. V54 remains unchanged. V55 recovers each identity only from a unique committed `EVENT_CREATE` or `EVENT_SCHEDULE` audit for that event. Missing or ambiguous audit history leaves null; it never guesses from the latest actor. Receipt fingerprints, original state/version/timestamps and deduplication keys are preserved. A cached response receives a publisher only if that response was already published, so replaying an old create/update after later publication still returns an unpublished result. Old JSON without the new fields decodes with null identities. No cron, identity targeting, annual repetition, gifts or production scheduling is included. Lunar/holiday presets in the admin UI must create editable drafts only.

## Engine capture and durable settlement (Task 2)

Final ready, casual rematch and matchmaking reserve quota before the event gate. The reservation uses the stable room/match identity (rematch UUID derives from the previous match ID). The event callback uses `Mutex.tryLock`, rechecks admission and connected participants, samples server time immediately before detached `Room.startMatch`, and resolves the immutable catalog synchronously. On contention the callback returns without engine acquisition; the source commits/closes before a 10ms retry, at most 20 attempts. No quota/profile/persistence connection is borrowed inside the callback and engine mutex is released before event commit/rollback/close. Tournament starts remain neutral and do not call the source.

The original room stays nonplayable behind an internal fence until the detached snapshot is saved outside the event transaction and engine mutex. Related mutations receive retryable `MATCH_START_UNAVAILABLE` using existing `ServerUnavailable` localization; negative ready/rematch replies cannot publish a candidate. Storage follows existing persistence serialization. A failed source releases its unstarted reservation; a failed or ambiguous active-room write retains the exact candidate and reservation for `retryMaintenanceSettlements` or a positive start retry, without querying a newer catalog. A successful durable save precedes `GameStarted`. Full maintenance publication suppresses gameplay delivery; reconciliation writes an aborted frozen match and cannot resurrect it after reopening. Disconnection before capture invalidates admission; after capture it is an ordinary disconnection from an already admitted match.

`StoredActiveRoom`, private Room and `CompletedMatch` carry default-neutral `eventRewards`. The stored room also preserves intentional-leave IDs so restored result breakdowns remain zero for leavers. Source failure cannot prevent already frozen matches from finishing. Legacy missing context decodes to factors 1/1 with no sources. Startup quota recovery runs before admission: PostgreSQL checks active snapshots, pending completed payloads and committed matches before refunding RESERVED receipts. Refund uses each receipt's original `quota_date`; FINALIZED and legacy receipts cannot be refunded. The reservation methods on `PlayQuotaRepository` are mandatory for custom implementations; silently consuming with no rollback support is unsupported. Existing `consumeMatch` callers retain finalized consumption semantics.

V56 adds nullable JSONB `matches.event_rewards` and `match_players.reward_breakdown`, plus quota receipt state defaulting existing rows to FINALIZED. V54/V55 are unchanged. Settlement writes frozen context, each player's exact breakdown, stats and a single total MATCH wallet credit in the existing transaction. Intentional leave gives zero. The V47 MATCH wallet trigger counts the total toward lifetime earned assets once. No EVENT_BONUS row, Gem multiplier, Elo multiplier or mission/achievement multiplier exists.

Additive protocol fields:

- `GameSnapshot.eventRewards: MatchRewardEventSnapshot? = null`: null before a match starts; authoritative frozen configuration after start, including neutral tournament/legacy context.
- `GameSnapshot.rewardBreakdowns: Map<String, MatchRewardBreakdown> = emptyMap()`: keyed by player ID for a completed non-aborted game. Base, bonus, total and contributing sources are server calculated. **This is the accepted result amount, not a paid/credited flag.** Game completion can be delivered while durable result settlement is pending; Task 3 must use neutral reward labels and must not claim successful wallet credit from this map alone.
- `MatchHistorySnapshot.rewardBreakdown: MatchRewardBreakdown? = null`: read from the committed player-result row. Match detail uses this same persisted summary. Null means historical data has no recorded breakdown; clients must not infer historical x2 from the current catalog or multiply guessed constants.

No new WebSocket discriminator or notification kind was added. Offline/client logic never becomes a reward authority. Player UI must consume these fields, tolerate absent/default fields, and continue to obtain wallet/profile balances from the server.

Operational limits: this continues the single authoritative engine process model. A crash after quota reservation but before a durable start refunds on startup; a durable active/pending/completed record instead finalizes the receipt. An uncertain write remains fenced until successful retry or maintenance abort. If refund/finalization storage also fails, its receipt remains reserved for later startup reconciliation; it is never guessed safe to refund a potentially started match. Global active-room persistence remains serialized as before, so slow storage can delay other persistence operations while the engine mutex stays free.

## Admin console (Task 4)

Owner/Operator use **Sự kiện thưởng** for paginated name search and exact state/history filters, bilingual detail, editable draft and server preview. Owner confirms scheduling or cancellation separately with a reason. Published definitions are read-only; ENDED/CANCELLED history has no edit/cancel action. Stable creator/publisher IDs and latest actor are distinct facts; missing legacy identities are explicitly unknown, and a missing publisher with no publication timestamp means never published.

The lazy `admin-events.js` receives the existing UI/API adapter. Shared navigation invalidates the outgoing view before import; session/view/request fences cover reads, mutations, failures and cleanup. API paths are relative `/events`; preview is authenticated POST with `{}` and CSRF, and scheduling sends the exact returned preview token and preview version. Mutations keep a request ID for an identical payload retry, disable fields/dismissal while pending, and require reload after conflict. A network error never claims rollback. Admin-authored text is rendered as text only.

Date fields always explicitly enter **UTC**, including the full year and optional milliseconds. The separate **IANA display timezone** changes rendering only; it does not reinterpret entered UTC. Preview/detail show both the exact ISO UTC interval and the IANA-zone display interval, with inclusive start/exclusive end. Browser timezone conversion is intentionally not offered. Server time and definition validation remain authoritative.

Eight holiday suggestions populate editable drafts only: lunar New Year, January 1, March 8, October 20, October 31, November 19, November 20 and December 24–25. Applying a suggestion requires an explicit year. Lunar New Year requires manually entered Gregorian dates for that year; no lunar-date calculation, automatic repetition, publication, gender or occupation targeting exists. Fixed-date suggestions use UTC calendar days (Christmas ends December 26 at 00:00 UTC); admins review/edit dates and display timezone before saving and previewing. Separate coin/star factors have accessible Vietnamese currency names; form labels and confirmation actions remain readable.

Local acceptance runner: `e2e/support/admin-events-live.mjs`, from `e2e` with `ADMIN_LIVE_SETTINGS` pointing at the retained owned loopback settings. It checks source/served asset hashes and no-store, creates labelled isolated events/accounts, exercises real browser draft/preview/schedule plus actual WebSocket CASUAL capture, cancels one source and waits for the other to expire, verifies persisted single MATCH wallet/history/detail and duplicate-input safety, then proves a new rematch is neutral. It preserves existing data and leaves its own campaigns terminal. Sensitive traces are off; settings/passwords/tokens are never printed. Repository settlement replay/rollback is separately covered by the real PostgreSQL event settlement suite; duplicate WebSocket input is not described as a forced storage outage.
