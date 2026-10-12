# Connected account activity — approved design

Authority: user priorities of 2026-10-12, after #31; user selected “Đăng nhập/kết nối game, lưu 90 ngày”. This extends #20/#24; Google Play/Apple provider activation and physical-device acceptance remain deferred. #10/#16 are cancelled.

## Definition and approach

Activity means an account whose verified registered-account connection has been accepted on `/game` and remains open, including lobby/idle connection time. Guests, failed authentication, rejected protocol/admission and HTTP-only login are excluded. This measures connected accounts, not human attention, concurrent players, match participation, or app foreground. Connection replacement/reconnect has its own random connection ID; an old socket closing cannot close its replacement.

Use server-observed connection intervals, rather than mutable session last_seen or client timestamps. Persist start and last-observed end for each connection. Observe every30 seconds and on connect/disconnect; only extend through actually observed time. Restart never extends abandoned sessions to now. Gaps exceeding60 seconds, collection failure or process restart create a new coverage window, never bridge missing history. An empty healthy window proves zero; no source/partial history does not. Keep data and coverage for90 days; read cutoff is enforced immediately, cleanup at most hourly, clipped long-running intervals remain valid.

Alternatives considered: completed-match facts miss lobby/disconnects; mutable sessions lose history; an external analytics SDK adds provider, privacy and client-version dependencies. Server intervals directly meet the selected definition without a new paid service.

## Storage and privacy

Migration V60 adds activity connection observations and coverage windows with indexes/range constraints. Activity rows use the existing private anonymous subject key, without names/email/access tokens/IP/raw messages or another identity map. Current recipients resolve through analytics_subject_links + live REGISTERED users. Existing account deletion erases that link, so deleted identities cannot receive future gifts; anonymous aggregate history within retention remains. Do not recreate a link for a deleted identity. Resolve new subjects under the existing identity-row transaction locking rules; sort multi-user locks deterministically. Each checkpoint writes all observations and coverage atomically. Failures roll back both and cannot assert healthy coverage. Database-backed collection only; memory/no database remains explicitly unavailable. Do not backfill history or mix production/test sources.

## Integration and boundaries

Add a focused activity package and database/bootstrap dependency, with a no-op default for existing test/application construction. Application hooks successful registered WebSocket connection and each socket's cleanup; a lifecycle coroutine checkpoints every30 seconds, includes idle sockets, and flushes safely on graceful shutdown. Analytics failure does not terminate gameplay or falsely acknowledge collection; log only a safe fixed message, propagate application cancellation. Collection is for the current single backend; multi-instance/global completeness stays #12/#15 and must not be claimed.

Expose a synchronous JDBC reader bound to the caller's existing connection. `coverage(from,until,now)` returns existing AnalyticsCoverage shape with SERVER/LIVE and COMPLETE/PARTIAL/UNAVAILABLE plus start/freshness. COMPLETE requires the entire [from,until) inside retention and the union of recorded healthy windows, with no gap. `activeSubjects`/bucket counts count distinct subjects whose recorded presence overlaps that interval (a zero-duration connect event belongs to its start bucket); never sum bucket distincts. `registeredRecipients` returns sorted distinct currently registered nondeleted user UUIDs only when that entire interval is COMPLETE, otherwise safe400 ACTIVITY_RANGE_INCOMPLETE/ACTIVITY_SOURCE_UNAVAILABLE.

## Consumers

Analytics adds ACTIVE_PLAYERS, Owner/Operator read-only, no currency/item/store/product filters. Existing DAY/WEEK/MONTH Bangkok buckets, whole-range distinct summary, prior-period comparison, exact decimal strings, RBAC/audit/no-store and consistent snapshot apply. Missing/partial windows retain truthful labels and suppress unproved zero/percentage. Existing monetary sources are unchanged and remain OFF until Console/provider setup; no revenue guessed from Gem grants/catalog.

Compensation ACTIVITY selection uses the incident's affected UTC interval. Draft records the source explicitly in optional backward-compatible round metadata; legacy rounds infer their original ALL_REGISTERED/USER_IDS source. Preview and publication re-query on the existing gift transaction/connection and bind source, interval, recipients and definition versions into the preview token. Never substitute ALL_REGISTERED when ACTIVITY lacks coverage. New identities or deletion/retention/coverage change invalidate preview as appropriate; publication freezes current selected recipients with existing campaign/claim/idempotency rules. Replays still use current authorization and retain committed results. No automatic compensation or live campaign is created.

## Admin UI and verification

Extend the existing white Material analytics metric selector and compensation audience selector, no new page/theme/layout system. Clearly label connected-account activity,90-day retention, coverage/gaps, and incident interval; render actual saved source in detail/preview. Keep48px labelled controls, keyboard/table chart alternative, current account/session/view fences. Apply UI UX Pro Max and inspect phone/desktop screenshots with supported and incomplete sources.

Tests: deterministic tracker connect/idle/disconnect/replacement/short sessions, failure/gap/restart/retention; real PostgreSQL atomic rollback, erasure/link race, distinct/bucket/half-open boundaries, zero vs unknown, cleanup and SQL correctness; real Application WebSocket guest/rejection vs accepted account; compensation role/current auth/replay/preview changes/frozen claim once; analytics cross-midnight/week/month and >2^53 exact existing guarantees; browser supported/incomplete activity and visual captures. No production load, payment, gift or maintenance exercise in this work.
