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

`NotificationPersistence` is also synchronous and connection-bound:

```kotlin
fun load(userId: String, limit: Int = 100, offset: Int = 0,
         includeDismissed: Boolean = false, announcementOnly: Boolean = false,
         notificationId: String? = null): List<NotificationSnapshot>
fun count(userId: String, includeDismissed: Boolean = false,
          announcementOnly: Boolean = false): Long
fun create(userId: String, notifications: List<NotificationSnapshot>)
fun markRead(userId: String, notificationId: String?, nowMillis: Long)
fun dismiss(userId: String, notificationId: String?, nowMillis: Long)
```

For player history, use `load(..., includeDismissed=true, announcementOnly=true)` with a bounded page and `count(..., includeDismissed=true, announcementOnly=true)` for its exact total. For detail, also pass `notificationId="admin-announcement:$id"` and `limit=1`. Keep the existing WebSocket inbox `loadNotifications` limit of 100 for legacy behavior. The HTTP task must authenticate the current registered player and convert internal `Long` counts to decimal strings in public DTOs. It owns response codes, pagination envelopes, request validation, no-store headers, and route authority.

## Internal records and invariants

`AnnouncementDefinition` has `id`, `version`, `status` (`DRAFT`, `PUBLISHED`, `CANCELLED`), bilingual title/body, `category`, `priority`, required half-open UTC bounds, `showBanner`, `pushEnabled`, typed `action`/`targetId`, `supersedesAnnouncementId`, `audience` (`ALL_REGISTERED`, `USER_IDS`), `selectedUserIds`, and creator/update/publication/cancellation facts. These are internal records. `publicContext(nowMillis)` derives `SCHEDULED`, `ACTIVE`, `EXPIRED`, or `CANCELLED`. Store validation enforces UUID identity, UTF-16 copy bounds, priority and timestamp bounds, typed target presence, audience list bounds, checked version increments, and published-copy immutability. The publication service must additionally check role/session/Origin/CSRF, linked target existence, audience eligibility, preview token, reason, and request fingerprint before mutation.

`registeredPlayers()` snapshots registered, nondeleted users, including blocked users, with their stored notification language. Memory fixtures default to English because `UserDetail` has no language field. `putRecipient` inserts a frozen `(announcementId,userId)` only in the same transaction that first publishes that definition; later calls update the existing recipient's checkpoint/read/dismiss/banner-hide/skip facts. The unique PostgreSQL key and monotonic receipt merge prevent duplicate materialization or lost read flags. `AnnouncementRecipientCounts` reports frozen, stored, read, dismissed, banner hidden, skipped and each push outcome as `Long`; these counters describe different facts and need not sum to the same total. Recipient and attempt identities have no user foreign key, so reports survive user hard deletion; user notifications and separate preferences cascade on deletion.

`AnnouncementReplay(actorId,requestId,fingerprint,responseType,responseJson)` has its own actor/request namespace and unique key. The service must serialize only a safe immutable typed result into `responseJson`. `AnnouncementPreference(userId,version,adminEnabled,eventEnabled)` defaults to version 1 and both consents false. It lives in a separate table; the old four-category `UpdatePushPreferences` cannot overwrite it. Same-value/version retries are idempotent; a different value requires the next version. `AnnouncementPushAttempt(announcementId,userId,attemptId,outcome,claimedAtEpochMillis,settledAtEpochMillis)` allows one committed attempt identity per recipient. Only an `ATTEMPTING` fact can settle; repeating the identical settled fact is idempotent. The caller owns the 20-second external deadline, 120-second stale-to-`UNKNOWN` recovery, conditional completion, no automatic resend, and atomic system audit for unknown outcomes.

V58 only adds schema and widens stored notification fallback title/message to 160/4000; the legacy client sync still enforces 120/300. Materialize a recipient checkpoint, the `user_notifications` row, replay and audit in a single `announcements.transaction` block. A thrown error, SQL failure, or cancellation rolls back the JDBC connection or copied memory state. Keep provider calls outside transactions and after durable in-app materialization.
