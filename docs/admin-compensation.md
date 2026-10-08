# Compensation foundation (Issue 20, Task 1)

This delivery implements incidents, source capabilities, the transaction-bound store, and the shared player claim/delivery foundation. Production round creation, preview, publication, cancellation and reporting belong to Task 2. The fixture round campaigns in tests do not expose that authority through HTTP.

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

## Remaining authority

Task 2 owns all round operation HTTP request/response DTOs and must document them before client work. It also owns bilingual body generation including incident description, exact UTC interval and receiving reason, explicit supplemental rounds, publication recipient freezing, default expiry, ACTIVITY rejection, previous-claim preview binding and decimal-string report counters. Player label/rendering and admin UI remain later tasks. No provider, production compensation, push, deployment or GitHub change is part of this foundation.
