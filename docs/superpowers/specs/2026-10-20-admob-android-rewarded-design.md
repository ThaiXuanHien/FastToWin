# Android AdMob rewarded ads

Date: 2026-10-20. Scope approved directly in chat: Android rewarded +2 online matches, server verification, privacy for audience 13+, Samsung test ads. Existing economy design remains binding. No banner/interstitial/app-open ads or iOS/Web monetization in this change.

## Product behavior

Keep 10 basic online matches/day, Asia/Bangkok reset, and exactly 2 bonus matches per verified ad. Keep existing quota dialog and dark Arcade design. Watching is opt-in; no ads interrupt a match. Show loading, unavailable/no-fill, cancellation, and pending server verification clearly. Reward only follows signed SSV confirmation. Failed/unavailable/unfinished ads do not add matches. Delayed callbacks and app/network loss cannot lose or duplicate a committed reward.

Android first: Google Play internal exists; AdMob account approved, no app/units yet. Create Android Fast To Win as unpublished until a supported public store listing is available. Create one rewarded unit `online_matches`, amount 2. Live monetization remains gated on provider readiness, consent configuration, Play declarations and app-ads.txt; safely test using demo units for SDK UI and owned units with explicitly registered test devices for SSV. No live ads are clicked during QA.

## Boundaries

- Reuse `RewardedAdGateway`, `RewardedAdVerifier`, `PlayQuotaRules` and existing receipt uniqueness. Add a focused server `ads` package and shared HTTP API, with small platform adapters. Do not add Google SDK to common/iOS/Web.
- Android initializes stable GMA Next-Gen SDK (official current 1.5.0) on a background thread, only after privacy flags and UMP permit requests. UMP retains manifest App ID. Pin stable versions verified from Google's docs/Maven.
- Age audience 13+ is a target, not proof of a user's age. Before ad requests, use a neutral local age-band choice (under 13 / 13-15 / 16-17 / 18+; no preselection). Unknown/under 13: do not initialize/request ads. Under-16 choice is tagged under-age-of-consent in UMP and ad configuration; use suitable content rating and non-personalized handling. UMP governs regional consent; offer privacy options from Settings whenever required. No exact birthday is collected. No legal audience declaration is automatically submitted.
- User-facing new text lives in TextKey/catalogs with all 12 supported languages. UI follows ui-ux-pro-max and existing Arcade components; touch targets and large font stay usable. Preserve user-owned IDE changes.

## Authenticated session API

New protocol file `AdMobProtocol.kt` in `com.hienthai.fastowin.protocol`:

```kotlin
@Serializable data class RewardedAdSessionRequest(val requestId: String)
@Serializable enum class RewardedAdSessionStatus { PENDING, GRANTED, EXPIRED, FAILED }
@Serializable data class RewardedAdSessionView(
    val sessionId: String,
    val customData: String,
    val adUnitId: String,
    val expiresAtEpochMillis: Long,
    val status: RewardedAdSessionStatus,
    val providerTransactionId: String? = null,
    val quota: PlayQuotaSnapshot? = null
)
```

- `POST /api/rewarded-ads/sessions` authenticated with the normal access token, body RewardedAdSessionRequest. Request ID 1..64 ASCII-safe characters; repeat owner/request id returns the same session. Random opaque session/custom-data value binds account + configured unit + creation/expiry. 30 minute show validity. Per-account and per-IP limits; reject new session while maintenance admission denies new intent. Bound outstanding sessions, expire stale pending rows. Database is required when enabling production ads.
- `GET /api/rewarded-ads/sessions/{id}` authenticated owner only. Same view, GRANTED includes real providerTransactionId and authoritative current PlayQuotaSnapshot. No cross-account disclosure; no-store, bounded input, structured existing error keys. Client never specifies reward amount/item/user identity to the callback.
- SDK SSV `custom_data` is the opaque server nonce, never email/access token. `user_id` is unnecessary. SDK completion is only a UI signal, never proof of a reward.

## Google callback and durable grant

- Public `GET /api/rewarded-ads/ssv` uses the unmodified raw query bytes for Google's ECDSA SHA256 signature protocol. Strictly bound length/parameter count, reject duplicate security fields, malformed encodings and invalid/missing signatures/key ids. Fetch Google's public keys only from the fixed official endpoint; bounded time/size, cached <=24h with bounded unknown-key refresh and fail closed.
- Allow only the configured owned ad unit(s), reward_amount=2, reward_item=online_matches. Accept the documented numeric ad-unit callback form by exact canonical mapping to the configured full unit ID. Bind signed custom_data to the stored session; signed timestamp must fall within its show window (+5 min clock tolerance), no >5 min future or >24h old callbacks. Google test/probe events with no known session are acknowledged without granting after signature validation; never grant unknown accounts.
- PostgreSQL session acceptance and quota grant happen in ONE transaction, using the same global `(provider, provider_transaction_id)` uniqueness as `rewarded_ad_grants`. Credit date is processing time via existing PlayQuotaRules (Asia/Bangkok), and keep provider event time for audit. Replays after day change return the existing outcome without recredit. One session cannot earn twice with a different provider transaction. Concurrent callback duplicates add only 2 total. Callback persistence failure returns retryable non-2xx; never acknowledge a lost grant.
- Memory implementation is for local tests/dev only. Add a new Flyway migration; never edit an applied migration. Do not log raw queries, session nonce, auth tokens, or ad identifiers linked to account.
- A production RewardedAdVerifier may validate the existing WebSocket receipt only against an already granted owner/session/transaction. It returns VerifiedRewardedAd with the real transaction ID; existing grant call yields ALREADY_GRANTED and refreshes quota. This keeps old clients and dev simulation compatible. Configuration disabled/missing stays UNAVAILABLE and fail closed; Web/iOS never advertise Android ads.

## Client lifecycle

- `RewardedAdSessionGateway` shared HTTP seam: `suspend prepare(requestId: String): RewardedAdSessionView`, `suspend status(sessionId: String): RewardedAdSessionView`, `close()`. Use existing authenticated request configurator, refresh once on 401, finite HTTP timeouts and cancellation propagation.
- Evolve composable `rememberRewardedAdGateway(sessionGateway: RewardedAdSessionGateway? = null)` and actuals. The default/dev and Web/iOS behavior remains compatible. Construct the API client using current account's token provider in FastToWinApp, close on owner change/dispose, and pass through the existing gateway boundary.
- Native gateway has a narrow SDK driver seam so lifecycle, late callbacks, no-fill, cancel, expiry and double-click can be tested without real ads. Track generation/account/session to fence stale callbacks. Only one ad can be loading/showing/pending at once. Destroy/close drops activity/ad callbacks and cancels coroutines. Never cache an Activity across recreations.
- Prepare a session before show, attach its custom_data, then show the loaded rewarded ad. After earned/dismissed, poll session status with finite backoff for <=60 seconds. Do not block UI; pending state remains recoverable/retryable. Store pending session per account locally for process restart; backend callback grants even when app is closed. On granted status emit one RewardedAdReceipt with requestId=sessionId, provider=ADMOB_ANDROID, real transaction ID, proof=sessionId; existing controller refreshes authoritative quota. Do not re-emit across owner/session changes.
- Settings exposes privacy options when required. Age-band unknown/under-13 has a readable unavailable state; retry doesn't repeatedly open consent forms. Reset/debug consent and test-device flags are limited to test builds/configuration.

## Configuration

Explicit app mode disabled/demo/test/live; release defaults disabled unless the real app/unit configuration is supplied. Dev defaults existing simulation unless explicitly selecting AdMob test. Test mode must register a physical test-device hash before requesting an owned ad unit; do not assume an unregistered phone is safe. Public App ID/unit ID can be build properties/resources, never auth secrets. Production backend enabled only with allowed unit, reward item, database and callback verification configured. Document exact variables and URLs in the delivered runbook.

Keep the existing upload keystore in its owner-selected permanent directory outside the project, alias fasttowin-upload; never copy private files into the repo or logs. Google Play versionCode 1 is already uploaded, so next publishable AAB must use a higher version. Do not uninstall the existing production app/data without consent; use Dev QA package on Samsung where possible.

## Acceptance

Unit/host tests: cryptographic signed fixture + mutated raw query; unknown/rotated keys; wrong unit/reward/session/owner; timestamps/encoding/duplicates; config fail closed; HTTP auth/limits; atomic SQL replay/concurrency and rollback; Android gateway lifecycle/privacy/test-mode gate; API 401 retry and cancellation. Existing quota, maintenance, account isolation, receipt compatibility and 12-language scanner remain passing.

Samsung: test ad loads/show/cancel/completes; Google signed SSV grants exactly 2; repeated callback/status/WS claim does not duplicate; losing network and closing/reopening app recovers quota; no-fill and denied privacy remain usable. Inspect phone screenshots and large-font layout. Run host screenshot regression and Web compile/regression appropriate to common UI edits. No Android emulator tests: user explicitly requested skipping them after Pixel Launcher failures. CI emulator job becomes explicit opt-in via `FASTTOWIN_ANDROID_EMULATOR_TESTS=true`; do not enable it for this work.

Keep #28 open for actual Gem product/prices/provider tests. This change does not alter payout verification or bank details. Provider app readiness, public listing/app-ads.txt and real ad serving are separate acceptance gates, not claimed complete by seeing a demo ad.

## Official references (checked 2026-10-20)

- https://developers.google.com/admob/android/next-gen/quick-start
- https://developers.google.com/admob/android/next-gen/rewarded
- https://developers.google.com/admob/android/privacy
- https://developers.google.com/admob/android/next-gen/targeting
- https://developers.google.com/admob/android/ssv
- https://developers.google.com/admob/android/test-ads
- https://support.google.com/admob/answer/10564477
