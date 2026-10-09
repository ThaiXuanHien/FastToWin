# Compensation incidents and rounds (Issue 20)

This delivery implements incidents, source capabilities, the transaction-bound store, round creation/preview/publication/cancellation/reporting, shared player claim/delivery, compatible player presentation and the native admin dialogs.

## Incident HTTP contract

Routes exist only with an enabled `AdminService`. The `fasttowin_admin` cookie authenticates the current session/account. Mutations require exact configured `Origin` and session-bound `X-Admin-CSRF`. Owner/Operator create and edit; Support reads history/capabilities. Authorization occurs inside the gift transaction after advisory lock `7421092187349` has been obtained, before disclosure or replay. Reads reject a supplied wrong Origin. Responses use `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.

| Method/path | Request | HTTP 200 response |
| --- | --- | --- |
| GET `/admin/api/incidents?page=0&pageSize=25` | none | `CompensationPage<CompensationIncident>` |
| POST `/admin/api/incidents` | `CompensationIncidentRequest`, expectedVersion absent/null | `CompensationIncident` |
| GET `/admin/api/incidents/{id}` | none | `CompensationIncident` |
| PUT `/admin/api/incidents/{id}` | `CompensationIncidentRequest`, positive expectedVersion | `CompensationIncident` |
| GET `/admin/api/incidents/capabilities` | none | `CompensationCapabilities` |

Page is zero-based, default size 25, maximum 100; offset must fit Int. The page DTO is `{items, page: Int, pageSize: Int, total: String}`. Its total is a decimal string, unlike the existing gift/admin numeric page totals. Lists order incident UUID descending. There is no delete endpoint: identity/history is retained.

```json
{
  "incidentKey": "OUTAGE_20261008",
  "titleVi": "Sự cố kết nối", "titleEn": "Connection incident",
  "descriptionVi": "Bạn có thể đã bị gián đoạn kết nối.",
  "descriptionEn": "Your connection may have been interrupted.",
  "affectedFromEpochMillis": 1791417600000,
  "affectedUntilEpochMillis": 1791421200000,
  "causeCode": "INTERNAL_DIAGNOSTIC",
  "evidence": "Internal investigation references",
  "responsibleAdminId": "8e161351-1c86-46bc-b1cc-dff408658658",
  "requestId": "incident-create-1", "reason": "Record confirmed incident",
  "expectedVersion": null
}
```

`CompensationIncidentRequest` has exactly the properties above. Key is trimmed, uppercased with `Locale.ROOT`, then must match ASCII `[A-Z0-9][A-Z0-9_-]{0,127}`. Canonical key is unique and immutable. Bilingual titles are nonblank and at most 160 UTF-16 code units; descriptions at most 4000; causeCode at most 128; evidence at most 10000. All are nonblank. Affected UTC interval is half-open `[from, until)`, within `0..253402300799999`, strictly increasing. Responsible admin UUID is canonicalized to lowercase and must identify an existing active account when assigned. Later deactivation preserves historical ownership; an edit retaining that account does not reassign it.

RequestId matches `[A-Za-z0-9_-]{1,128}`. Reason is nonblank, at most 500 UTF-16 units, with no ISO control characters. Create rejects any supplied expectedVersion. Update requires a positive Int, matching the current version, and rejects Int overflow. First published round freezes every incident field; cancellation of a published round does not unfreeze it. Exact identical committed update/create retries still return their original receipt after later edits or publication, after current authorization and request-shape checks.

`CompensationIncident` returns the definition without requestId/reason, plus `id: String`, canonical `incidentKey`, `version: Int`, `createdByAdminId`, `updatedByAdminId`, `createdAtEpochMillis: Long`, `updatedAtEpochMillis: Long`. Creator/timestamp stay stable; successful edits update editor/timestamp/version. Diagnostic and admin fields are admin-only. Mutation and audit/replay commit atomically. Replay does not append another mutation audit. Audit summaries contain versions, not copied evidence.

Bodies require application/json and are bounded to 512 KiB. Unknown fields, invalid JSON, invalid values return 400 `INVALID_REQUEST`; 401 `UNAUTHORIZED`, 403 `FORBIDDEN`, 404 `NOT_FOUND`, 409 `CONFLICT`, and 503 `STORAGE_UNAVAILABLE` use `{code,message}` without SQL diagnostics. No-DB storage is unavailable, not a disconnected compensation ledger.

## Capabilities

`CompensationCapabilities` is `{sources: [CompensationCapability]}`. Each item is `{source: CompensationAudience, available: Boolean, explanation: String}`. Enum values are `ALL_REGISTERED`, `USER_IDS`, `ACTIVITY`. Registered account snapshots and verified explicit UUIDs are supported; ACTIVITY is unavailable with an honest coverage/retention explanation. No session last-seen, registration date, platform or app-version inference is made. Task 2 must reject forged ACTIVITY round targeting with 400 `ACTIVITY_SOURCE_UNAVAILABLE`; Task 1 has no targeting mutation endpoint.

## Transaction/store contract for Task 2

Every `GiftTransaction` now exposes `val compensation: CompensationStore`. JDBC binds it to the exact gift/admin Connection; memory binds it to the exact copied gift state and recipients/campaigns, with the existing copied admin state for wallet/audit. Store methods are synchronous: they cannot open connections, suspend, commit, or call another gift repository/service transaction. Admin callers use `repository.transaction(true)` and authorize current actor/session/Origin/CSRF inside it before replay. Task 2 must perform all round checks, gift writes, frozen entitlements, audits and replay writes within that one transaction.

```kotlin
interface CompensationStore {
    fun incident(id: String, lock: Boolean = false): CompensationIncident?
    fun incidentByKey(key: String): CompensationIncident?
    fun incidents(page: PageRequest): CompensationPage<CompensationIncident>
    fun putIncident(incident: CompensationIncident)
    fun round(campaignId: String): CompensationRound?
    fun rounds(incidentId: String, page: PageRequest): CompensationPage<CompensationRound>
    fun putRound(round: CompensationRound)
    fun maximumRoundNumber(incidentId: String): Int
    fun hasDraftRound(incidentId: String): Boolean
    fun hasPublishedRound(incidentId: String): Boolean
    fun replay(actorId: String, requestId: String): CompensationReplay?
    fun putReplay(actorId: String, requestId: String, replay: CompensationReplay)
    fun previousClaims(incidentId: String, beforeRoundNumber: Int,
        selectedUserIds: List<String>): CompensationPreviousClaims
}
```

`CompensationRound(campaignId: String, incidentId: String, roundNumber: Int, supplementalReason: String?, receivingReasonVi: String, receivingReasonEn: String)` is the internal mapping. The campaign UUID is the round UUID and wallet source ID; campaign owns reward/audience/schedule. Put campaign with server-generated compensation context before putRound. Store rejects ordinary campaigns and context incident/number mismatch, conflicting campaign/incident/number identity, and a second draft. The incident must exist; positive number and `(incident_id,round_number)` uniqueness are protected in PostgreSQL. Number allocation, supplemental reason validation, optimistic guards, preview binding and publication authorization remain Task 2 service responsibilities. `maximumRoundNumber` returns 0 for no rounds; Task 2 must use explicit expected max+1 with overflow protection. Cancelled numbers remain used. Mapping pagination is descending round number. There is intentionally no hard delete method.

`hasPublishedRound` reads persistent first-publication evidence (`publishedAtEpochMillis`), not just current campaign state. `hasDraftRound` reads current campaign state. Task 2 must retain publishedAt when cancelling. Definition changes require service version/state checks; store is a persistence primitive, not a second workflow authority.

`CompensationReplay(fingerprint: String, responseType: String, internal responseJson: String)` is internal, non-serializable as a DTO. Its immutable response JSON has the typed committed result. `(actorId,requestId)` is one compensation namespace spanning incident and future round operations. Fingerprint must bind operation, target and complete request; incident operations hash the complete original request JSON, with operation/target prefixes. Incident responseType is `INCIDENT`. Task 2 supplies distinct stable responseType values for its DTOs and verifies type/fingerprint before decoding. Put replay is insert-only; reuse conflicts. Never disclose the private receipt through HTTP.

`CompensationPreviousClaims(distinctUsers: String, entitlements: String, selectedOverlap: String)` comes from one receipt query over mappings with `roundNumber < beforeRoundNumber` and non-null claimedAt. Distinct users differs from the number of paid entitlements across rounds; selectedOverlap counts distinct previous recipients in the selected UUID set. All three are decimal strings. Counts include retained receipts after account deletion; they do not join users. Empty selection has overlap zero. Task 2 previews must bind this summary and selected audience to their token.

## Player/wallet source and compatibility

`GiftCampaign.compensation` and `MailboxGift.compensation` are optional default-null `CompensationMailboxContext`. It has exactly incidentId, incidentKey, roundNumber, affectedFromEpochMillis, affectedUntilEpochMillis, receivingReasonVi, receivingReasonEn. No evidence, cause/error code, responsible identity or admin audit reason is projected. Legacy stored JSON missing context decodes null; old readers configured to ignore unknown fields accept new metadata.

Wallet source derives from persisted campaign context only: null → `ADMIN_GIFT`, present → `COMPENSATION`. Generic gift requests cannot select source/context. Generic admin list filters compensation and detail/update/preview/publish/cancel return 404 before disclosure or replay. Shared authenticated player mailbox, delivery checkpoints and claim endpoints process both sources. Claims use existing campaign→entitlement→user→stats locking, receipt retry, maintenance gate, received-at expiry semantics and overflow rollback. No event factors or extra delivery loop are read/created. V47 earned gold/gems allowlist remains unchanged and excludes COMPENSATION. XP is credited exactly per campaign; there is no lifetime-earned-XP column.

Migration V57 is additive after V56. It adds indexed campaign source with legacy ADMIN_GIFT default, incident/round/replay tables and unique identities. Applied V47/V50 migrations are untouched. Repository writes derive the indexed source from persisted context. Real PG tests use only fresh UUID schemas and the existing isolated-schema cleanup policy.

## Round HTTP contract

The same enabled-admin, cookie, current authorization, Origin, CSRF, strict JSON (512 KiB), no-store and safe error rules apply to round routes. Owner/Operator create/update/preview; Owner alone publishes/cancels; all authenticated admin roles read list/detail. Path IDs are canonical UUIDs (case-insensitive input). Every successful response is HTTP 200.

| Method/path under `/admin/api/incidents/{incidentId}/rounds` | Request | Response |
| --- | --- | --- |
| GET base path, `?page=0&pageSize=25` | none | `CompensationPage<CompensationRoundDetail>` |
| POST base path | `CompensationRoundRequest` (create) | `CompensationRoundDetail` |
| GET `/{roundId}` | none | `CompensationRoundDetail` |
| PUT `/{roundId}` | `CompensationRoundRequest` (update) | `CompensationRoundDetail` |
| POST `/{roundId}/preview` | `CompensationRoundPreviewRequest` | `CompensationPreview` |
| POST `/{roundId}/publish` | `CompensationRoundPublishRequest` | `CompensationRoundDetail` |
| POST `/{roundId}/cancel` | `CompensationRoundCancelRequest` | `CompensationRoundDetail` |

Lists use the incident pagination limits, decimal-string `total`, and descending round number. A round from another incident is 404. There is no delete endpoint. `roundId`, `campaign.id`, `round.campaignId` and the COMPENSATION wallet `source_id` are the same UUID.

### Round definition requests

`CompensationRoundRequest` has exactly the following properties. Nullable/default fields may be omitted; other fields are required. Counts of UTF-16 code units match Kotlin String length.

| Property | Type/default | Meaning/validation |
| --- | --- | --- |
| `incidentVersion` | Int | Positive current incident version. |
| `receivingReasonVi`, `receivingReasonEn` | String each | Player-safe nonblank explanation, each at most 1000. |
| `gold`, `xp` | Int each | Per-recipient reward, each 0..2147483647, at least one positive. |
| `availableAtEpochMillis` | Long | Nonnegative UTC availability; before expiry. |
| `requestId` | String | `[A-Za-z0-9_-]{1,128}`, shared actor-scoped compensation replay namespace. |
| `reason` | String | Internal nonblank audit reason, at most 500, no ISO controls. |
| `audience` | CompensationAudience, `ALL_REGISTERED` | `ALL_REGISTERED` or `USER_IDS`; `ACTIVITY` returns 400 `ACTIVITY_SOURCE_UNAVAILABLE`. |
| `userIds` | List<String>, `[]` | USER_IDS requires 1..10000 canonical UUID inputs; uppercase accepted and normalized lowercase. Other sources require empty. Duplicates remain in the definition for preview diagnostics. |
| `expiresAtEpochMillis` | Long?, null | Null becomes availability + exactly 2592000000 ms (30 days); explicit/default expiry must be greater than availability and at most 253402300799999. |
| `expectedNextRoundNumber` | Int?, null | Create requires exactly current maximum + 1. Update requires absent/null. |
| `supplementalReason` | String?, null | Round 1 requires absent/null. Every round >1 requires nonblank internal reason, at most 500, no ISO controls. |
| `expectedVersion` | Int?, null | Create requires absent/null. Update requires positive current campaign version. |

Example create body:

```json
{
  "incidentVersion": 1,
  "receivingReasonVi": "Bạn nằm trong nhóm được đền bù cho sự cố này.",
  "receivingReasonEn": "You are included in compensation for this incident.",
  "gold": 100, "xp": 30, "availableAtEpochMillis": 1791417600000,
  "requestId": "round1-create", "reason": "Prepare confirmed compensation",
  "audience": "USER_IDS", "userIds": ["8e161351-1c86-46bc-b1cc-dff408658658"],
  "expectedNextRoundNumber": 1
}
```

Round 1 cannot be recreated with a new requestId. At most one draft exists per incident; publish or cancel it before an explicit supplemental round. Cancelled numbers remain used. No retry allocates a number. Supplemental rounds may intentionally award the same person again with a new round/source UUID.

### Preview, publish and cancel requests

These DTOs have exactly the properties listed; all are required:

```kotlin
CompensationRoundPreviewRequest(incidentVersion: Int, expectedVersion: Int, requestId: String, reason: String)
CompensationRoundPublishRequest(incidentVersion: Int, expectedVersion: Int, previewToken: String, requestId: String, reason: String)
CompensationRoundCancelRequest(incidentVersion: Int, expectedVersion: Int, requestId: String, reason: String)
```

Versions must be positive. `requestId`/`reason` use the definition rules. `previewToken` must be 64 lowercase hex characters. Preview/publish require a draft; cancel accepts draft or published. Successful update/publish/cancel increments campaign version; overflow is 409. Exact committed retries return the original typed response after current role/session/Origin/CSRF and request-shape checks, even after claims, edits or cancellation. Changed operation/target/body with the same actor/requestId is 409. Preview retries also retain their original token; use a new requestId to obtain a fresh preview.

`CompensationPreview` has exactly:

```kotlin
incidentId: String
incidentVersion: Int
campaignId: String
version: Int                     // current campaign version (publish expectedVersion)
roundNumber: Int
source: CompensationAudience     // ALL_REGISTERED or USER_IDS
userIds: List<String>             // normalized explicit selection conditions, empty for ALL_REGISTERED
sampleUserIds: List<String>       // at most 10 selected UUIDs, sorted
invalidUserIds: List<String>      // sorted distinct missing/guest/deleted explicit UUIDs
duplicateUserIds: List<String>    // sorted distinct explicit UUIDs occurring more than once
duplicateCount: String           // extra input entries after distinct, not duplicateUserIds.size
recipientCount: String
totalGold: String
totalXp: String
previousClaimedUserCount: String
previousClaimedEntitlementCount: String
selectedPreviouslyClaimedUserCount: String
previewToken: String
```

All counts/totals are decimal strings. `totalGold`/`totalXp` are selected count × per-recipient reward. Previous counts cover paid receipts of strictly earlier rounds of this incident; users are distinct, entitlements count each earlier paid round, selected overlap is distinct users among the current audience. Receipts survive account deletion. These are three facts from one query, not sums of per-round distinct users.

ALL_REGISTERED selects current registered/nondeleted accounts, including banned accounts; USER_IDS verifies against the same set. The source and normalized `userIds` are the complete selection conditions; there are no activity/date/platform/version predicates. Invalid explicit IDs block publication with 400. Token binds the full incident and round/campaign definitions, versions, sorted selected/invalid IDs and the coherent prior paid summary. Changed incident, round, registered selection or previous claims yields 409 and requires a new preview. Publication freezes the selected entitlements atomically; later registrations receive nothing from that round.

### Round detail and receipt report

`CompensationRoundDetail` has exactly `incidentId: String`, `incidentKey: String`, `incidentVersion: Int`, `round: CompensationRound`, `campaign: GiftCampaign`, `report: CompensationReport`.

`CompensationRound` has exactly `campaignId: String`, `incidentId: String`, `roundNumber: Int`, `supplementalReason: String?`, `receivingReasonVi: String`, `receivingReasonEn: String`, and default-null audit fields `createdByAdminId: String?`, `updatedByAdminId: String?`, `createdAtEpochMillis: Long?`, `updatedAtEpochMillis: Long?`, `publishedByAdminId: String?`, `cancelledByAdminId: String?`. Service-created mappings populate creation/editor fields, then publisher/canceller on those transitions. They remain admin-only.

`GiftCampaign` has exactly `id: String`, `version: Int`, `state: CampaignState` (`DRAFT`, `PUBLISHED`, `CANCELLED`), `draft: GiftDraftRequest`, `createdAtEpochMillis: Long`, default-null `publishedAtEpochMillis: Long?`, `cancelledAtEpochMillis: Long?`, `cancellationReason: String?`, `compensation: CompensationMailboxContext?`. The nested `GiftDraftRequest` fields are `titleVi/titleEn/bodyVi/bodyEn/reason: String`, `gold/xp: Int`, `audience: GiftAudience` (default ALL_REGISTERED), `userIds: List<String>` (default []), `registeredFromEpochMillis/registeredUntilEpochMillis: Long?` (always null for compensation), `availableAtEpochMillis: Long`, `expiresAtEpochMillis: Long?` (normalized nonnull), `requestId: String`, `expectedVersion: Int?` (default null). Compensation uses only ALL_REGISTERED/USER_IDS in this nested audience. No client-provided public title/body/context/source is accepted by the round request.

Draft projections reflect current editable incident copy. Publication freezes bilingual title/body, reasons, exact ISO-8601 UTC half-open interval, GOLD/XP, schedule and compensation context. Body includes the entire description (up to 4000) and receiving reason (up to 1000); only this server-generated envelope has a 5200 UTF-16 bound, while ordinary gifts remain bounded to 4000. Internal diagnostic/evidence, supplemental/audit reason and admin identity are excluded from the player projection. First publication permanently prevents incident edits, including after cancellation. Published campaign definition and original mutation replay responses remain immutable.

`CompensationReport` has exactly nine String fields: `eligible`, `pending`, `delivered`, `claimed`, `expired`, `cancelled`, `skipped`, `claimedGold`, `claimedXp`. Each is a decimal string. Eligible is frozen entitlement count; pending/delivered/skipped use durable delivery checkpoints. Pending becomes zero on cancellation. Claimed counts paid receipts and actual paid totals are claimed × immutable per-recipient GOLD/XP. Expired counts delivered unclaimed entitlements after expiry unless cancelled; cancelled counts all unclaimed non-skipped entitlements after cancellation. Delivered includes claimed/expired/cancelled delivered entitlements, so counters overlap and must not be added as a partition. Delivery is not payout. A cancelled round never reclaims paid assets or deletes its receipts. Reading detail recomputes report from current receipts; a replay deliberately returns its original report snapshot.

## Admin console

All current admin roles see **Đền bù**. Support reads incidents, independently paginated round history and receipt reports. Owner/Operator create/edit unpublished incidents and round drafts and request previews; Owner confirms publication/cancellation in a separate native dialog. A supplemental draft names the exact next round number and requires an internal supplemental reason. Preview and confirmation display that number/reason and distinguish previous paid users, paid entitlements and selected overlap.

Player-safe VI/EN explanation, receiving reason, UTC affected interval and exact rewards stay separate from labelled internal evidence, cause, responsible UUID and audit reasons. UTC forms include the full year and interpret entries as UTC regardless of the browser timezone. Empty expiry uses the server's exact 30-day default. ACTIVITY is visibly unavailable with the capability explanation; only supported sources can be selected. No activity inference, automatic incident/round/yearly allocation or push is added.

The lazy `admin-compensation.js` module uses existing session/navigation/request/dialog fences. Pending native forms block dismissal and fields. An uncertain retry with identical content keeps the exact request ID and payload; a 409 locks that mutation until reload and renewed preview. First-publication evidence is checked across round history pages before enabling incident edits, including cancelled published history. Published/cancelled definitions are read-only. All compensation count/total formatting and pagination boundaries use BigInt directly from decimal-string transport, including values above 2^53.

Admin assets remain explicitly allowlisted, disabled with admin, no-store and protected by existing CSP. Feature CSS keeps the white Material modal header separate from its scrolling form body; utilities retain accessible 48px targets. Mission-style filled gold coin and primary star summarize exact reward numbers with currency names in accessible labels.

`e2e/tests/admin-compensation.spec.mjs` uses the real module after authenticated fixture login. `e2e/support/admin-compensation-live.mjs` requires a separate owned loopback configuration with a UUID PostgreSQL schema and fresh accounts; it verifies browser incident→round1 ALL_REGISTERED→preview/publication→delivery/claim→identical replay→explicit round2 with previous paid counts→claim→cancel unclaimed, late-registration exclusion, public private-field exclusion and source/served asset hashes. Live traces are disabled. Durable SQL evidence verifies one COMPENSATION wallet source per paid round/player, no ADMIN_GIFT/EVENT_BONUS and unchanged lifetime-earned values. Repository tests remain evidence for rollback, concurrency and restart invariants.

Task4 logs, XML, browser scenario/count maps, original 360/768/1024/1440 and 200% settled images/geometry, source/served hashes and SQL facts live under `.artifacts/admin-compensation-delivery/task4/`. No physical-device, iOS, provider, production compensation, cash/refund/quota, push, merge or deployment claim is made.
