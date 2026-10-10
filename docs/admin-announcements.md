# Admin announcements: Task 1 persistence contract

This document fixes the model and store names for the publication, player API, and UI tasks implementing [Issue 22](https://github.com/ThaiXuanHien/FastToWin/issues/22). The foundation stores facts and exposes a compatible notification projection. It does not publish, schedule, send push, or authorize a route by itself.

## Protocol and notification compatibility

`NotificationSnapshot.announcement: AdminAnnouncementContext? = null` is the only new field on the existing `NotificationsData` payload. `NotificationKind`, `NotificationDestination`, and the WebSocket message discriminator retain their existing values. The context contains public `id`, `titleVi`, `titleEn`, `bodyVi`, `bodyEn`, `category`, `priority`, `startsAtEpochMillis`, `endsAtEpochMillis`, derived `state`, typed `action`, nullable `targetId`, and nullable `supersedesAnnouncementId`. No audit reason, audience, recipient, preference, provider, or token field belongs in it. Old readers using unknown-key tolerance still decode `COSMETIC`/`PROFILE` with `actionData=null` and safe plain-text title/message fallback. A missing context decodes `null`.

Use `AnnouncementDefinition.notification(language, nowMillis)` for the initial fallback text. It selects Vietnamese when the stored notification language starts with `vi`, English otherwise. Its ID is `admin-announcement:<UUID>`. `GameEngine.syncNotifications` rejects either this reserved prefix or any client-authored non-null context, even on a legacy allowed kind/template. Persisted context state is projected from the current definition on read, so expiry and cancellation remain explicit in old inbox reads without changing the wire shape. No new destination or auto-action is implied.

## Construction and transaction ownership

`NotificationRepository.announcements: AnnouncementRepository` exposes the authority from the same notification store. `NoOpNotificationRepository` returns `UnavailableAnnouncementRepository`, whose transaction raises the existing safe storage-unavailable error. `PostgresNotificationRepository(dataSource)` constructs `PostgresAnnouncementRepository` from that **same** data source. `InMemoryNotificationRepository(adminRepository: InMemoryAdminRepository = InMemoryAdminRepository())` preserves the no-argument constructor; pass the same admin repository used by a test/dev application when coordinated admin state is required. It exposes the same notification map as its legacy methods, not a second inbox. Memory lock order is notification mutex then admin repository mutex. `InMemoryAdminRepository.coordinatedTransaction` checks cancellation and publishes the copied announcement/notification state and admin state before releasing either lock. Independent readers therefore wait through the complete publication boundary. The callback is internal and performs only reference swaps; ordinary `InMemoryAdminRepository.transaction` keeps its existing constructor and API.

`AnnouncementRepository.transaction(admin: Boolean = false, block: AnnouncementTransaction.() -> T): T` is suspending; the block and all transaction operations are synchronous. Use `admin=true` for admin mutations to acquire advisory lock `7421092187349` before announcement rows. PostgreSQL creates one connection, runs `JdbcAdminTransaction`, `JdbcNotificationPersistence`, and announcement operations on it, checks the coroutine job immediately before commit, and commits or rolls back together. Never call a suspending legacy notification method from inside this block. Definition writes lock the announcement row; recipient and attempt writes lock announcement before recipient/notification. An existing recipient is locked with `FOR UPDATE` before its stored receipt is read and merged, so a concurrent legacy read/dismiss cannot be overwritten. Legacy read/dismiss locks matching recipients in UUID order before changing notification rows, without acquiring the admin advisory lock. The existing invitation methods remain independent.

`AnnouncementTransaction` exposes `val admin: AdminTransaction`, `val notifications: NotificationPersistence`, and these exact methods:

```kotlin
fun definition(id: String): AnnouncementDefinition?
fun putDefinition(value: AnnouncementDefinition)
fun definitions(status: AnnouncementStatus?, limit: Int, offset: Int): List<AnnouncementDefinition>
fun definitionCount(status: AnnouncementStatus?): Long
fun registeredPlayers(): List<AnnouncementPlayer>
fun recipient(announcementId: String, userId: String): AnnouncementRecipient?
fun recipients(announcementId: String, limit: Int, offset: Int): List<AnnouncementRecipient>
fun recipientCounts(announcementId: String): AnnouncementRecipientCounts
fun putRecipient(value: AnnouncementRecipient)
fun replay(actorId: String, requestId: String): AnnouncementReplay?
fun putReplay(value: AnnouncementReplay)
fun preference(userId: String): AnnouncementPreference
fun putPreference(value: AnnouncementPreference)
fun pushAttempt(announcementId: String, userId: String): AnnouncementPushAttempt?
fun putPushAttempt(value: AnnouncementPushAttempt)
```

`NotificationPersistence` is also synchronous and connection-bound. HTTP read-all/dismiss-all invokes one null-ID bulk mutation with `announcementOnly=true`; legacy null-ID mutations retain all-notification scope by default. Both use the same JDBC operation, which acquires the entire matching recipient set in ascending announcement UUID order before notification writes. Single-ID mutations retain recipient-before-notification ordering. A caller must not implement bulk mutation by iterating inbox chronology and holding each receipt lock until commit.

```kotlin
fun load(userId: String, limit: Int = 100, offset: Int = 0,
         includeDismissed: Boolean = false, announcementOnly: Boolean = false,
         notificationId: String? = null): List<NotificationSnapshot>
fun count(userId: String, includeDismissed: Boolean = false,
          announcementOnly: Boolean = false): Long
fun create(userId: String, notifications: List<NotificationSnapshot>)
fun markRead(userId: String, notificationId: String?, nowMillis: Long, announcementOnly: Boolean = false)
fun dismiss(userId: String, notificationId: String?, nowMillis: Long, announcementOnly: Boolean = false)
```

For player history, use `load(..., includeDismissed=true, announcementOnly=true)` with a bounded page and `count(..., includeDismissed=true, announcementOnly=true)` for its exact total. For detail, also pass `notificationId="admin-announcement:$id"` and `limit=1`. Keep the existing WebSocket inbox `loadNotifications` limit of 100 for legacy behavior. The HTTP task must authenticate the current registered player and convert internal `Long` counts to decimal strings in public DTOs. It owns response codes, pagination envelopes, request validation, no-store headers, and route authority.

## Internal records and invariants

`AnnouncementDefinition` has `id`, `version`, `status` (`DRAFT`, `PUBLISHED`, `CANCELLED`), bilingual title/body, `category`, `priority`, required half-open UTC bounds, `showBanner`, `pushEnabled`, typed `action`/`targetId`, `supersedesAnnouncementId`, `audience` (`ALL_REGISTERED`, `USER_IDS`), `selectedUserIds`, and creator/update/publication/cancellation facts. These are internal records. `publicContext(nowMillis)` derives `SCHEDULED`, `ACTIVE`, `EXPIRED`, or `CANCELLED`. Store validation enforces UUID identity, UTF-16 copy bounds, priority and timestamp bounds, typed target presence, audience list bounds, checked version increments, and published-copy immutability. The publication service must additionally check role/session/Origin/CSRF, linked target existence, audience eligibility, preview token, reason, and request fingerprint before mutation.

`registeredPlayers()` snapshots registered, nondeleted users, including blocked users, with their stored notification language. Memory fixtures default to English because `UserDetail` has no language field. `putRecipient` inserts a frozen `(announcementId,userId)` only in the same transaction that first publishes that definition; later calls update the existing recipient's checkpoint/read/dismiss/banner-hide/skip facts. The unique PostgreSQL key and monotonic receipt merge prevent duplicate materialization or lost read flags. `AnnouncementRecipientCounts` reports frozen, stored, read, dismissed, banner hidden, skipped and each push outcome as `Long`; these counters describe different facts and need not sum to the same total. Recipient and attempt identities have no user foreign key, so reports survive user hard deletion; user notifications and separate preferences cascade on deletion.

`AnnouncementReplay(actorId,requestId,fingerprint,responseType,responseJson)` has its own actor/request namespace and unique key. The service must serialize only a safe immutable typed result into `responseJson`. `AnnouncementPreference(userId,version,adminEnabled,eventEnabled)` defaults to version 1 and both consents false. It lives in a separate table; the old four-category `UpdatePushPreferences` cannot overwrite it. Same-value/version retries are idempotent; a different value requires the next version. `AnnouncementPushAttempt(announcementId,userId,attemptId,outcome,claimedAtEpochMillis,settledAtEpochMillis)` allows one committed attempt identity per recipient. Only an `ATTEMPTING` fact can settle; repeating the identical settled fact is idempotent. The caller owns the 20-second external deadline, 120-second stale-to-`UNKNOWN` recovery, conditional completion, no automatic resend, and atomic system audit for unknown outcomes.

V58 only adds schema and widens stored notification fallback title/message to 160/4000; the legacy client sync still enforces 120/300. Materialize a recipient checkpoint, the `user_notifications` row, replay and audit in a single `announcements.transaction` block. A thrown error, SQL failure, or cancellation rolls back the JDBC connection or copied memory state. Keep provider calls outside transactions and after durable in-app materialization.

## Task 2 HTTP and delivery contract

All announcement HTTP responses send `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. The early application setup hook applies both headers before CORS and admin-off responses. With admin disabled, every `/admin/api/announcements` path, including preflight, returns 404. Request JSON is strict UTF-8, disallows unknown keys, and is bounded to 2,300,000 bytes for draft create/update and 32,768 bytes for actions/preferences. The draft bound covers 10,000 raw UUID strings even with every character encoded as a six-byte JSON Unicode escape (2,190,001 bytes for the array), both maximum 160-unit titles and 4,000-unit bodies (49,920 escaped bytes), the 500-unit reason (3,000), and under 8,192 bytes for escaped keys and remaining metadata. Extra whitespace counts toward the same limit; overflow returns 400. Errors are `{code,message}` with safe 400/401/403/404/409/503 status as applicable. `page` is zero based, `pageSize` defaults to 25 and is bounded to 1..100.

Admin routes require the `fasttowin_admin` cookie and current admin session. Mutations also require exact configured Origin and `X-Admin-CSRF`. Owner and Operator can create/update/preview drafts; only Owner can publish/cancel; Support can list/detail/capabilities. Each mutation rechecks current role and credentials inside the advisory-locked transaction before replay. No admin announcement routes are mounted when admin is off.

| Method/path | Request | Response |
| --- | --- | --- |
| `GET /admin/api/announcements?page=&pageSize=&status=` | Optional `status=DRAFT\|PUBLISHED\|CANCELLED` | `AnnouncementAdminPage` |
| `POST /admin/api/announcements` | `AnnouncementDraftRequest` | `AnnouncementAdminView`, HTTP 201 |
| `GET /admin/api/announcements/capabilities` | — | `AnnouncementCapabilities` |
| `GET /admin/api/announcements/{id}` | — | `AnnouncementAdminView` |
| `PUT /admin/api/announcements/{id}` | `AnnouncementDraftRequest` with `expectedVersion` | `AnnouncementAdminView` |
| `POST /admin/api/announcements/{id}/preview` | Empty body | `AnnouncementPreview` |
| `POST /admin/api/announcements/{id}/publish` | `AnnouncementMutationRequest` with `previewToken` | `AnnouncementAdminView` |
| `POST /admin/api/announcements/{id}/cancel` | `AnnouncementMutationRequest` without `previewToken` | `AnnouncementAdminView` |

`AnnouncementDraftRequest` has `titleVi,titleEn,bodyVi,bodyEn,category,priority,startsAtEpochMillis,endsAtEpochMillis,showBanner,pushEnabled,action,targetId,supersedesAnnouncementId,audience,selectedUserIds,expectedVersion,requestId,reason`. For create, omit nullable `expectedVersion`; for update, supply the current positive version. Boolean channels default false and in-app is always on. `AnnouncementMutationRequest` has `expectedVersion,requestId,reason,previewToken?`. `AnnouncementAdminView` is `{announcement,counts,serverNowEpochMillis}`; its announcement is the admin-only stored definition. `AnnouncementCounts` has decimal strings `frozen,eligible,pending,stored,read,dismissed,bannerHidden,skipped,pushAccepted,pushFailed,pushUnknown,pushInFlight,pushDisabled,pushOptedOut,pushNoToken,pushUnavailable,pushExpired`. `AnnouncementAdminPage` adds `items,page,pageSize,total` with `total` a decimal string. `AnnouncementPreview` has `announcement,recipientCount,duplicateCount,invalidUserIds,previewToken,pushConfigured,pushReminderBodyLimit=240,serverNowEpochMillis`. `recipientCount` and `duplicateCount` are decimal strings. `AnnouncementCapabilities` has `pushConfigured,serverNowEpochMillis`. Draft USER_IDS may retain duplicate canonical UUIDs (bounded to 10,000); preview reports them and binds the sorted draft plus deduplicated verified recipient IDs. Publication freezes each selected ID once and stores a unique list. Missing/guest/deleted IDs block publication, as do an ended interval or changed audience/token.

Counts describe durable observations. `frozen` is the immutable publication audience size; `eligible = frozen - skipped` is the remaining audience at the last durable evaluation, including already stored recipients, not a fresh account/provider check. `pending = frozen - stored - skipped` while the definition is PUBLISHED, and zero for DRAFT/CANCELLED. Expired catch-up remains pending until history is stored or deletion is recorded as skipped. Cancellation preserves frozen/eligible/stored history while stopping pending work. `stored` counts durable in-app creation, `read` counts receipt reads, and `pushAccepted` counts only provider acceptance; none proves display. The receipt and push counters overlap and are not additive.

Player routes require a current Bearer access token and a registered nondeleted account; guest/deleted accounts have no announcement authority. They never expose internal definition, audience, reason, token, provider, or audit facts.

| Method/path | Response |
| --- | --- |
| `GET /api/announcements?page=&pageSize=` | `AnnouncementPlayerPage` including dismissed history and exact decimal `total` |
| `GET /api/announcements/{id}` | `AnnouncementPlayerItem` only for a stored recipient notification |
| `GET /api/announcements/banners` | `AnnouncementBanners`, at most one active unhidden item |
| `POST /api/announcements/{id}/read`, `POST /api/announcements/read-all` | `AnnouncementMutationResult` |
| `POST /api/announcements/{id}/dismiss`, `POST /api/announcements/dismiss-all` | `AnnouncementMutationResult` |
| `POST /api/announcements/{id}/hide-banner` | `AnnouncementMutationResult` |
| `GET /api/announcements/push-preferences` | `AnnouncementPreferenceView` |
| `PUT /api/announcements/push-preferences` | `AnnouncementPreferenceRequest` → `AnnouncementPreferenceView` |

`AnnouncementPlayerItem` is `{notification,announcement,dismissed,bannerHidden}`. The `announcement` object is the public protocol context with derived `SCHEDULED/ACTIVE/EXPIRED/CANCELLED` state; the stored fallback notification keeps the old `COSMETIC/PROFILE` shape. `AnnouncementPlayerPage` is `{items,page,pageSize,total,serverNowEpochMillis}` and `AnnouncementBanners` is `{items,serverNowEpochMillis}`. `AnnouncementMutationResult` is `{affected,serverNowEpochMillis}` with `affected` a decimal string. `AnnouncementPreferenceRequest` is `{expectedVersion,adminEnabled,eventEnabled}` and the view is `{version,adminEnabled,eventEnabled,serverNowEpochMillis}`. Both consents default false, live separately from the legacy four preferences, and a same-value retry with the preceding version returns the saved view.

`AnnouncementService.deliverDueBatch(limit=100)` materializes due published recipients in bounded checkpointed transactions. Late catch-up stores expired history without an active banner or push; cancellation stops pending delivery. `AnnouncementPushDelivery.deliverDueBatch(limit=100)` processes at most `limit` newly stored outcomes, including disabled/opted-out/no-token/expired/unavailable results, and returns that processed count. It claims only the next attempt under announcement/current-account/recipient locks immediately before external I/O, never a local queue of ATTEMPTING claims. Each send rechecks its matching durable ATTEMPTING identity before invoking the provider outside the transaction with a 20-second deadline. Materialization, stale recovery, and push have independent five-second loops, so slow push does not defer in-app history. Hide-banner also locks announcement before current user and receipt. Firebase announcement sends await an asynchronous future through cancellable suspension; a deadline cancels the future and settles UNKNOWN without waiting for provider completion. `recoverStale(limit=100)` marks claims older than 120 seconds `UNKNOWN` with a system audit entry. Ambiguous/timeout/interrupted sends remain `UNKNOWN` and never auto-retry. The `PushNotificationService` announcement capability and raw bilingual method leave legacy localized social/reminder calls unchanged; reminder body is truncated safely to 240 UTF-16 units, while in-app copy remains full.

The Task 2 store extension adds `definition(id,lock=false)`, `currentPlayer(userId)`, and `publishedTarget(action,targetId)` to `AnnouncementTransaction`. `currentPlayer` reads current registered/nondeleted account language/token with a user row lock in PostgreSQL. `publishedTarget` checks published reward events and gift campaigns on the same JDBC connection. The memory test fixture can seed a current announcement token/language with `InMemoryAdminRepository.seedAnnouncementPushTarget`; this never configures a production provider.


## Bounded pending push selection (V59)

`AnnouncementTransaction.pendingPushCandidates(limit: Int): List<AnnouncementPushCandidate>` returns at most 1..1000 internal `(announcementId,userId)` keys in stable UUID order, without row locks or granting send authority. V59 adds `admin_announcement_pending_push` with a composite primary-key index and recipient foreign key (cascade only when the durable recipient is removed). It backfills only PUBLISHED, stored recipients without any committed attempt; ATTEMPTING and every terminal outcome, including UNKNOWN, remain excluded. Applied V58 is unchanged.

The queue is maintained on the announcement transaction's existing connection/copied memory state. A stored checkpoint enqueues a published unattempted recipient together with its notification; the first committed attempt removes that key; cancellation removes that announcement's keys. Receipt updates cannot requeue attempted or cancelled work. Any failed checkpoint, claim, cancellation or audit transaction rolls queue changes back with the authoritative facts. Hard account deletion preserves the key long enough for current-account validation to persist a skipped outcome; it never restores an attempt.

## Admin console

`/admin/` lazily loads the explicitly allowlisted `admin-announcements.js` through the shared navigation/session fence. All current roles read list/history and the separate report dialog; Owner/Operator author and preview drafts; only Owner confirms publish/cancel. Draft mutations send the exact request DTO above. Same-content uncertain retries retain the same complete payload and request UUID; a 409 disables the stale payload and requires reload/preview. Late dialog, import, view and account responses are discarded.

Aggregate and pagination totals are decimal strings formatted through BigInt, including values beyond JavaScript's safe integer range. Counts remain independent overlapping observations. Push is off by default; capability text distinguishes configured, unavailable and unknown. Full in-app and shortened push text are separately previewed. Published content is immutable; **Tạo bản sửa mới** copies into a new draft referencing the old announcement and resets push consent selection. No provider configuration or bulk unknown retry is exposed.

The focused synthetic fixture is `e2e/tests/admin-announcements.spec.mjs`. The owned local runner is `e2e/support/admin-announcements-live.mjs`; it requires separate `ADMIN_ANNOUNCEMENT_LIVE_SETTINGS` with a loopback target and `ann_task4_<UUID>` schema. It never uses the original admin settings or production push credentials. Runtime/schema, source-served hashes, SQL/public JSON evidence and settled images are retained under `.artifacts/admin-announcements-delivery/task4/` and documented in the Task 4 report. This is local integration evidence only.

A push batch reads one bounded page from the queue, then revalidates each candidate under announcement → current user/recipient authority immediately before claiming and sending. Each terminal skip consumes processed capacity. The batch examines at most its requested candidate limit and never refills stale candidates within that call; concurrent claims/cancellation can therefore return fewer processed outcomes, with remaining work picked up on the next poll. No ATTEMPTING queue is preclaimed. Selection neither scans completed recipient prefixes nor locks exhausted announcement definitions. Existing matching-attempt completion, recovered-UNKNOWN checks, deadlines and independent in-app scheduling retain their authority.
