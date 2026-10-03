# Admin gift campaigns and player mailbox

Issue #19 backend contract. Dates are UTC epoch milliseconds (`Long`); admin UI inputs and labels use Asia/Bangkok. Player text has separate Vietnamese/English fields. No websocket enum or push notification is required.

## Admin authentication and routes

Use existing `fasttowin_admin` cookie (`Path=/admin`). Every mutation, including preview, requires exact configured `Origin` and the session-bound `X-Admin-CSRF` header. Every request checks the current session/account within the admin transaction. Owner can create/edit/preview/publish/cancel. Operator can create/edit/preview. Support can read list/detail only. Player bearer tokens never authorize admin requests. All responses use `Cache-Control: no-store`.

Base: `/admin/api/gift-campaigns`.

| Method/path | Body | Success |
| --- | --- | --- |
| GET base `?page=0&pageSize=25` | None | `{items:[GiftCampaignDetail],page,pageSize,total}` |
| POST base | GiftDraftRequest below | GiftCampaignDetail |
| GET `/{id}` | None | GiftCampaignDetail |
| PUT `/{id}` | GiftDraftRequest plus expectedVersion | GiftCampaignDetail |
| POST `/{id}/preview` | `{"expectedVersion":1}` | GiftPreview |
| POST `/{id}/publish` | `{"expectedVersion":1,"previewToken":"opaque-sha256","requestId":"publish-123","reason":"Event launch"}` | GiftCampaignDetail |
| POST `/{id}/cancel` | `{"expectedVersion":2,"requestId":"cancel-123","reason":"Stop campaign"}` | GiftCampaignDetail |

Successful operations return HTTP200. List pagination uses page>=0 and pageSize1..100. Create starts version1, update/publish/cancel increment by1. Preview does not increment. Versions are optimistic concurrency checks. After publication content/audience/schedule are immutable; corrections require a new campaign. Cancellation may also abandon a draft and never reverses already claimed gifts.

### Draft request

```json
{
  "titleVi": "Quà sự kiện",
  "titleEn": "Event gift",
  "bodyVi": "Cảm ơn bạn đã tham gia!",
  "bodyEn": "Thank you for joining!",
  "reason": "October event",
  "gold": 100,
  "xp": 30,
  "audience": "ALL_REGISTERED",
  "userIds": [],
  "registeredFromEpochMillis": null,
  "registeredUntilEpochMillis": null,
  "availableAtEpochMillis": 1790899200000,
  "expiresAtEpochMillis": null,
  "requestId": "create-123",
  "expectedVersion": null
}
```

`expectedVersion` is absent/null for create and required for update. `expiresAtEpochMillis` absent/null defaults to exactly30 days after availableAt, then is persisted as a concrete timestamp. Schedule must be nonnegative, expire strictly after availableAt and by253402300799999. Gold/XP are Int0..2147483647 and at least one positive. Titles1..160 characters; bodies1..4000; whitespace-only content is invalid. Reason is nonblank, <=500 chars, without control characters. RequestId matches `[A-Za-z0-9_-]{1,128}`. JSON bodies are limited to512KiB; unknown fields and invalid JSON are rejected.

Audience options:

* `ALL_REGISTERED`: all currently registered, non-deleted accounts; userIds empty and date bounds null.
* `USER_IDS`: nonempty list, max10000 canonical UUIDs. Duplicates are normalized/deduplicated and reported. Missing/deleted/guest IDs appear in preview invalidUserIds and prevent publication.
* `REGISTERED_BETWEEN`: registered accounts selected by users.created_at in `[registeredFromEpochMillis,registeredUntilEpochMillis)`; both bounds required, nonnegative start, end>start; userIds empty.

There is no activity audience. UI must disable that option and explain that no official last-activity source is available. Do not substitute registration date for activity.

### Campaign detail and preview

```json
{
  "campaign": {
    "id": "8e161351-1c86-46bc-b1cc-dff408658d73",
    "version": 2,
    "state": "PUBLISHED",
    "draft": {
      "titleVi": "Quà sự kiện", "titleEn": "Event gift",
      "bodyVi": "Cảm ơn bạn!", "bodyEn": "Thank you!",
      "reason": "October event", "gold": 100, "xp": 30,
      "audience": "ALL_REGISTERED", "userIds": [],
      "registeredFromEpochMillis": null, "registeredUntilEpochMillis": null,
      "availableAtEpochMillis": 1790899200000,
      "expiresAtEpochMillis": 1793491200000,
      "requestId": "create-123", "expectedVersion": null
    },
    "createdAtEpochMillis": 1790800000000,
    "publishedAtEpochMillis": 1790800100000,
    "cancelledAtEpochMillis": null,
    "cancellationReason": null
  },
  "counters": {
    "eligible": 10, "pending": 0, "delivered": 10,
    "claimed": 3, "expired": 0, "cancelled": 0, "skipped": 0,
    "claimedGold": 300, "claimedXp": 90
  }
}
```

Campaign state is `DRAFT|PUBLISHED|CANCELLED`. The draft object retains the last edit's requestId/expectedVersion; consumers must generate a new requestId and use campaign.version for a new edit. Counters: eligible=frozen recipients; pending=undelivered while campaign remains active; delivered=ever delivered, including later claims/expiry/cancellation; claimed=paid; expired=delivered, unclaimed and expired unless cancelled; cancelled=all unclaimed, non-skipped frozen recipients after cancellation; skipped=deleted before delivery. These counts overlap (claimed/expired are subsets of delivered). ClaimedGold/XP are actual paid totals, retained with the frozen receipt even if an account is later deleted. Draft counters are zero; use preview for its estimated audience.

```json
{
  "campaignId": "8e161351-1c86-46bc-b1cc-dff408658d73",
  "version": 1,
  "recipientCount": 10,
  "duplicateCount": 2,
  "invalidUserIds": [],
  "totalGold": 1000,
  "totalXp": 300,
  "previewToken": "opaque-sha256"
}
```

Publish recalculates eligibility and totals and rejects409 if version/token changed. Registration or deletion since preview can invalidate it. Publication atomically freezes the audience with campaign state and audit. Same actor+requestId+exact request payload replays the original mutation response even after later state/version changes; changed payload/operation/target with the same gift requestId returns409. Replays still require current authorization. Keep the same ID and body for network retries; use a new ID for a new intent.

## Registered player HTTP API

All player routes require `Authorization: Bearer <registered-account-access-token>`; admin cookies and guest resume tokens do not authenticate. No request body is needed for a single claim. Player errors never disclose another account's entitlement. The service is injected into `gameModule`; there are no client websocket changes.

The shipped shared client uses `/api/gifts/mailbox`. The equivalent `/gifts/mailbox` prefix remains supported for HTTP compatibility; both prefixes share the same authenticated handler, service, storage and claim receipt. `/gifts` itself remains the player Compose SPA navigation, separate from the mailbox HTTP subtree.

GET `/api/gifts/mailbox?page=0&pageSize=25` (or `/gifts/mailbox?page=0&pageSize=25`) returns:

```json
{
  "items": [{
    "campaignId": "8e161351-1c86-46bc-b1cc-dff408658d73",
    "titleVi": "Quà sự kiện", "titleEn": "Event gift",
    "bodyVi": "Cảm ơn bạn!", "bodyEn": "Thank you!",
    "gold": 100, "xp": 30,
    "availableAtEpochMillis": 1790899200000,
    "expiresAtEpochMillis": 1793491200000,
    "state": "AVAILABLE",
    "claimedAtEpochMillis": null
  }],
  "page": 0, "pageSize": 25, "total": 1
}
```

Mailbox uses availableAt descending, campaignId descending tie-break; includes only delivered gifts after their availability time. States are `AVAILABLE|CLAIMED|EXPIRED|CANCELLED`. Claim takes precedence over cancellation/expiry; cancellation takes precedence over expiry. Drafts, pending recipients and skipped recipients are never exposed. Page/pageSize have the same limits as admin.

POST `/api/gifts/mailbox/{campaignId}/claim` (or `/gifts/mailbox/{campaignId}/claim`) returns:

```json
{
  "gift": {
    "campaignId": "8e161351-1c86-46bc-b1cc-dff408658d73",
    "titleVi": "Quà sự kiện", "titleEn": "Event gift",
    "bodyVi": "Cảm ơn bạn!", "bodyEn": "Thank you!",
    "gold": 100, "xp": 30,
    "availableAtEpochMillis": 1790899200000,
    "expiresAtEpochMillis": 1793491200000,
    "state": "CLAIMED", "claimedAtEpochMillis": 1790900000000
  },
  "awardedGold": 100, "awardedXp": 30,
  "gold": 450, "xp": 230
}
```

`gold`/`xp` are current total wallet/experience balances. `awardedGold`/`awardedXp` describe the original receipt, including on retry; clients must replace displayed balances or refresh the profile, never add these values locally. Retry returns the already claimed receipt without paying again, including after expiry/cancellation. Existing PostgreSQL player profile refresh sees the same player_stats balances and derives level as usual. Lifetime earned gold/gems are unaffected.

Claim validity uses server request-received time in `[availableAt,expiresAt)`, captured before bearer authentication/database waits. A valid request waiting for locks does not expire while queued. Single-claim expired/cancelled responses are409; refresh mailbox for final state. Undelivered or somebody else's gift returns404.

POST `/api/gifts/mailbox/claim-all` (or `/gifts/mailbox/claim-all`) accepts `{"campaignIds":["id-one","id-two"]}` (1..100 entries), and always processes each item in its own transaction:

```json
{
  "items": [
    {"campaignId":"id-one","result":null,"error":{"code":"CONFLICT","message":"Yêu cầu xung đột với dữ liệu hiện tại."}},
    {"campaignId":"id-two","result":null,"error":{"code":"NOT_FOUND","message":"Không tìm thấy dữ liệu."}}
  ]
}
```

Successful entries carry `result:GiftClaimResult` and `error:null`. The batch HTTP status is200 for per-item outcomes; invalid batch length/body fails400. Entries preserve input order and may repeat an ID safely. The same received-at time applies to all entries; failed items do not roll back successful items. UI can batch pages and retry individual errors.

Shared serializable DTOs live in `protocol/GiftProtocol.kt`: `GiftState`, `MailboxGift`, `GiftMailboxPage`, `GiftClaimResult`, `GiftClaimAllRequest`, `GiftError`, `GiftClaimItem`, `GiftClaimAllResult`. They are independent of existing websocket message types.

### Player mailbox UI

The Home discovery section opens a separate `/gifts` route in the shared Compose app. Guests can see sign-in guidance; only registered accounts request mailbox pages. Opening the route fetches page0 and never claims gifts. The screen keeps server states (`AVAILABLE`, `CLAIMED`, `EXPIRED`, `CANCELLED`), displays the stored Vietnamese/English content using the current app language, and offers explicit **Nhận** / **Nhận tất cả** actions. Load more fetches the next server page; Claim all reads every server page and sends available campaign IDs in batches of at most100. Per-item failures remain visible and retryable, while successful receipts trigger the existing authoritative GameController profile refresh. The client never adds awarded amounts to its local wallet.

Mailbox state belongs to the registered account's app lifecycle. Leaving the screen clears pending UI state; opening it again fetches server truth. Account changes dispose the client and fence late responses. Access-token refresh within the same account retries one unauthorized HTTP request with a fresh bearer token. Notification records, taps, and deletion do not affect gift visibility or claims.

### PWA and reverse proxy routing

Both checked-in Caddy templates forward `/admin`, `/admin/*`, `/gifts/mailbox`, `/gifts/mailbox/*` and the existing `/api/*` subtree to Ktor. Exact `/gifts` stays on the game SPA. Backend admin CSP and `no-store` headers are retained; the game CSP is not added to admin responses. Admin availability still depends on the existing backend enable switch.

The game worker never caches either mailbox HTTP subtree, and v7 activation removes same-origin mailbox and admin entries from every CacheStorage cache, including older and unrelated cache names. The game offline shell and `/gifts` SPA navigation remain available offline; mailbox reads fail on network loss instead of returning stored private data. The `/api/gifts` client path also bypasses still-active legacy workers, which already exclude `/api/*`, before v7 activation. A previously opened application running the old client still needs reload to receive the new client or worker activation; an already running legacy client cannot be retroactively changed by this release. The admin worker policy handshake remains unchanged.

## Errors, durability, and local runtime

Errors use `{code,message}`:400 `INVALID_REQUEST`,401 `UNAUTHORIZED`,403 `FORBIDDEN`,404 `NOT_FOUND`,409 `CONFLICT`,503 `STORAGE_UNAVAILABLE`. Database failures return a safe503 without SQL or credentials. Reward/balance overflow returns409 and rolls back all effects. Admin mutation validation and access checks occur inside the transaction; Origin is also checked before body parsing.

Migration V50 creates campaigns, frozen-recipient/entitlement rows and gift request replay records. `(campaign_id,user_id)` is the entitlement key; recipient rows intentionally do not cascade-delete with users. Publication, recipient snapshot, audit and request replay record commit together. Claims lock campaign→recipient→user→player_stats and atomically update wallet, receipt and state; wallet source is `ADMIN_GIFT`, sourceId=campaignId. Cancellation locks the same campaign. All admin gift operations share the existing admin authorization advisory lock, preventing revocation from interleaving with mutation/audit.

`GiftService.deliverDueBatch(limit=100)` is explicitly callable; accepted limits1..1000. Each batch commits entitlements and persisted PENDING→DELIVERED/SKIPPED checkpoints together. Restart/retry reads those rows and never needs an in-process queue. Deleted accounts become SKIPPED before delivery. Late delivery retains the original expiry, and cancellation stops new delivery. Main starts a5-second application-lifetime loop with cancellation propagation and safe retry logging. Notification records, reads and deletion do not determine entitlements; this backend sends no pushes.

PostgreSQL is the functional runtime. Existing no-DB Main uses NoOpPlayerProfileRepository, so gift routes explicitly return503 rather than granting into a disconnected balance map; unauthenticated requests still return401. `InMemoryGiftRepository` remains equivalent for isolated fixtures and shares `InMemoryAdminRepository` transaction snapshots for admin identity/audit, seeded player balances and wallet history. It is not connected as a fake economy in no-DB Main. Controller explicitly approved this limitation for Task1.
