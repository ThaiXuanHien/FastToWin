# Android AdMob rewarded ads

Issue: #51. Rewarded ads are opt-in and grant exactly **2 online matches** after Google-signed server verification. Base quota remains 10/day, resetting in Asia/Bangkok. No banner, interstitial, app-open, Web or iOS ad integration is included.

## Delivery status

Backend session/SSV/SQL code and Android integration are independently reviewed, including the presentation-feedback fix at `425682b`. Release builds default to ads disabled. Do not interpret an SDK earned callback, a Console probe, or a demo ad as a committed player reward.

Owned unpublished Android app: `ca-app-pub-1914037185148012~8507808229`. Rewarded unit: `ca-app-pub-1914037185148012/7821587525`, saved reward `2 online_matches`. These public IDs were supplied by the owner and the unit/reward verified in AdMob Console on 2026-10-20. A supported public store listing and live readiness are still separate gates.

## Backend configuration

| Variable | Default | Required when enabled |
|---|---|---|
| `FASTTOWIN_ADMOB_ENABLED` | `false` | Explicit `true` |
| `FASTTOWIN_ADMOB_ANDROID_AD_UNIT_ID` | Absent | Exact full owned unit ID |
| Existing `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Existing settings | PostgreSQL required in production |

Missing/invalid enabled configuration rejects startup. Disabling ads fails closed. The reward amount/item are server constants, never client inputs.

- `POST /api/rewarded-ads/sessions`: bearer authentication; request ID 1–64 ASCII-safe characters; owner/request idempotency.
- `GET /api/rewarded-ads/sessions/{id}`: owner-only status; GRANTED contains a real provider transaction and authoritative current quota.
- `GET /api/rewarded-ads/ssv`: public Google-signed callback. Missing/invalid signatures cannot grant.

Prepare limits: 6/owner/minute and 30/IP/minute; status 120/owner/minute and 300/IP/minute; callback 300/IP/minute. Maximum 3 outstanding sessions per account, 30-minute show window. Known sessions require exact unit, reward 2/online_matches and signed event timing, with 5-minute tolerance and maximum age 24 hours. Credit belongs to the processing day in Bangkok.

Callback acceptance, global `(provider, transaction)` uniqueness and quota increment share one PostgreSQL transaction. Replay, a second transaction on one session and cross-account reuse cannot add credit. Signature-valid, strictly parsed unknown-session Console probes acknowledge without writing/granting. SQL/key-fetch failures return non-2xx for retry.

Dev quota snapshots retain DEV_SIMULATED for Web/iOS compatibility. Android's explicit native mode uses authenticated prepare/status. Production enabled snapshots are MOBILE_PRODUCTION; Web/iOS do not advertise Android SDK ads.

## Android build modes

| Mode | Behavior |
|---|---|
| Omitted | Dev Debug retains simulation; other builds disable ads |
| `disabled` | No native ads |
| `demo` | Fixed Google sample app/unit only; SDK UI acceptance |
| `test` | Owned IDs plus registered physical test-device hashes; runtime SDK recognition required |
| `live` | Explicit owned IDs; only after all live gates below |

```powershell
# Add the project's normal bounded Gradle flags.
./gradlew.bat :app:assembleDevDebug -PFASTTOWIN_ADMOB_MODE=demo

# Owned TEST build; keep device hashes in private local config/environment.
# All values can be ORG_GRADLE_PROJECT_<property> environment variables.
-PFASTTOWIN_ADMOB_MODE=test
-PFASTTOWIN_ADMOB_APP_ID=ca-app-pub-1914037185148012~8507808229
-PFASTTOWIN_ADMOB_AD_UNIT_ID=ca-app-pub-1914037185148012/7821587525
-PFASTTOWIN_ADMOB_TEST_DEVICE_IDS=<EXACT_SDK_32_HEX_HASH>
-PFASTTOWIN_DEV_WS_URL=wss://staging-web-staging-b446.up.railway.app/game
```

Capture the exact test hash from Google's SDK `setTestDeviceIds` registration line after a genuine demo request. Do not derive it from serial/Android ID. TEST checks `RequestConfiguration.isTestDevice(context)` before initialization/loading an owned unit. Confirm the visible **Test Ad** label. Never click live ads during QA.

Version code is 2, above the previously uploaded Play internal version 1. Signing uses the owner's permanent keystore outside Git; no password belongs in Gradle source, logs or this runbook. An ads-disabled release bundle is not a live-ad activation.

## Privacy and recovery

Neutral age bands are local per authenticated account, without DOB or server age collection. UNKNOWN/under 13 cannot initialize/request ads. Ages 13–15 use restrictive under-consent handling; teens receive non-personalized handling/content restrictions. UMP must permit requests, and Settings exposes privacy options when UMP requires them. Consent forms are not repeatedly reopened on each retry. SDK/UMP versions are pinned at 1.5.0/4.0.0.

The SDK loads before allocating a server session. Immediately before show, prepare/persist the owner-bound session and attach its opaque `custom_data`; never attach email/access tokens. Completion is a UI signal only. Polling, including HTTP calls, is bounded to 60 seconds; pending verification remains retryable across dialog close, network loss and process restart. Once HTTP confirms GRANTED, the account controller retains the verified receipt until its WebSocket result arrives and replays the identical receipt after authenticated reconnect. The server returns the existing grant and current quota; the client never adds matches locally. Owner/generation fencing rejects stale callbacks. Backup excludes local age/pending preferences. No exact-age/legal declaration is submitted by this code.

## Staging and acceptance evidence

Temporary callback used for QA: `https://staging-web-staging-b446.up.railway.app/api/rewarded-ads/ssv`. Console verification succeeded using Google's signed probe. The owned registered test unit then delivered a real signed player-specific reward. Final cleanup is recorded in #51; never point a live build at short-lived staging.

Evidence collected on 2026-10-20:

- Final integrated gate at `477805b`: protocol 109, full shared 320 and server 54 tests, plus 17 screenshot comparisons passed with zero failures/errors/skips. Production bundle/localization/JS/Wasm compilation passed; lint reported 0 errors and 87 warnings. Final handoff-recovery covering results are recorded separately, without adding overlapping counts.
- Actual isolated PostgreSQL: signature/replay/concurrency/rollback tests ran, including 12 simultaneous duplicate callbacks, global transaction collisions and day-change/restart outcomes.
- Production bundle/localization/JS/Wasm compilation passed; lint 0 errors, 87 disclosed existing/tooling warnings. No emulator QA.
- HTTPS staging health 200; unsigned SSV 400; anonymous prepare/status 401; application schema V62.
- Samsung Dev QA: neutral age dialog, under-13 blocked fixture, separate adult fixture, Google demo Test Ad display/completion/dismissal. Without SSV, quota stayed 10 consumed/0 bonus. Pending session survived Activity recreation; pending dialog at 160% font remained readable with Close visible, then font restored to 100%.
- Owned TEST APK at `425682b`: SDK-recognized Samsung hash, actual owned app/unit and staging WSS. Ad displayed **Test Ad**; after it completed the Dev process was stopped. SQL recorded 10 consumed, **2 bonus, 1 granted session, 1 global receipt**. Reopening the app recovered **2/12** remaining without another ad.
- Three authenticated HTTP status reads remained GRANTED; three WebSocket receipt replays all returned ALREADY_GRANTED with bonus 2 and remaining 2. No second receipt/bonus was created. This exercises an actual Google-signed session; duplicate raw Google query replay is separately covered by signed-fixture SQL/route tests, since raw callbacks are deliberately not logged/captured.
- Brief physical Wi-Fi/mobile-data loss showed the offline screen; both original network toggles were restored in `finally`. Reconnected app retained 2/12 and SQL retained one receipt/bonus 2. A separate HTTP/WebSocket replay login replaced the phone QA session under the existing account policy; the phone was deliberately logged in again before the network test.
- For a subsequent loading-cancel check, only the synthetic fixture's consumed count was advanced 10 to 12. Starting a load then pressing Back returned to Home; no delayed ad appeared, no new server session was allocated and SQL stayed at 12 consumed, 2 bonus, 1 granted session, 1 receipt. This was test-data preparation, not a real played match or a second reward.
- Presentation-feedback review fix: 16 scoped behavioral tests and 13 screenshot comparisons passed, including cancellation/unavailable plus pending/timeout/retry states. Owned TEST APK built successfully after this fix. Earlier overlapping host counts are not summed.

Backup timing correction: the initial custom dump targeted Postgres's default `staging_only` database, while the backend used the earlier restore-drill database `staging_restore`. The actual application archive was verified **after** additive V62 migration; no current-app pre-migration backup is claimed. Future backup commands must derive the database from server `DATABASE_URL` and verify the private staging host. Production was not deployed/migrated by this QA.

Remaining provider/device gates before live activation include regional UMP denial/required-options forms and concrete provider no-fill/early-dismissal behavior; these branches have host/fake-driver coverage and are not claimed as region/provider device acceptance. Demo display and fake-driver tests do not replace those gates. iOS execution remains unavailable on this Windows host.

## Live activation and cleanup

Before live activation: supported public store listing, AdMob app readiness, app-ads.txt, actual privacy policy, published regional consent configuration, Play Contains ads/Advertising ID/Data safety declarations, reviewed explicit live configuration and owned signed reward acceptance. Keep #28 Gem products/pricing/purchase tests open separately; payout verification/bank details are unchanged.

Do not log callback queries, nonces, auth tokens or account-linked ad IDs. Application access logs exclude `/api/rewarded-ads`; the current Caddy proxy has no access logger. Reassess this if proxy/edge logging changes.

After QA, remove the temporary staging callback unless an intentionally running acceptance endpoint is retained; stop staging compute while retaining its volumes/backups, revoke only the session SSH key and verify production deployment IDs unchanged. Stop the owned loopback Java/PG runtime, remove only its USB reverse, restore font 1.0 and remove only the Dev QA app/artifacts created for this acceptance. Never uninstall/clear the production package.

Device/local cleanup completed for this acceptance: the Dev QA package was removed (it was absent before this QA), the production package remained installed, font was 1.0, USB reverse list was empty and loopback backend/PG were stopped, including the local PostgreSQL runtime used by the final integrated gate. The temporary Railway SSH key was revoked. Cleanup evidence in `staging-cleanup.json` records all four staging services STOPPED, retained volumes/backups and unchanged production deployment IDs; no SSH keys remain. The owner confirmed manually removing the temporary staging callback and saving the change. Browser tooling was unavailable, so callback removal was not independently reverified. Retained staging storage can remain billable.

## Primary references

- https://developers.google.com/admob/android/next-gen/quick-start
- https://developers.google.com/admob/android/next-gen/rewarded
- https://developers.google.com/admob/android/next-gen/privacy
- https://developers.google.com/admob/android/next-gen/targeting
- https://developers.google.com/admob/android/next-gen/test-ads
- https://developers.google.com/admob/android/ssv
- https://support.google.com/admob/answer/10564477
