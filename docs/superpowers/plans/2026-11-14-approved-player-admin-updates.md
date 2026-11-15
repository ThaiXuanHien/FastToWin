# Fast To Win implementation plan

The approved preview covers rankings, community rules, persistent play credits, clan competition, and admin tools.

- [x] Rankings and community. Apply Top 30 to the server reads while retaining the current player's and clan's standings. Open public player details and reuse the existing friend action and Arcade button. Open the selected clan and sort its members by Elo. Give the rules checkbox and label a 16 dp gap. Verify Android and shared UI paths.
- [x] Play credits and rewards. Add a persistent credit balance to the existing play quota contract. Reserve and refund the original source across day boundaries, spending daily quota first. Exchange gems atomically using server offers. Add play credits to admin gifts and code rewards. Verify duplicate requests, concurrent claims, and rollover.
- [x] Gift codes. Reuse admin authorization and audit controls. Make redemption atomic, limited per account, and bounded by expiry and optional total uses. Add the dedicated Profile destination and return navigation. Verify admin publish, redemption, and failure states.
- [x] Clan competition. Assign groups at the start of future weeks and settle each group independently. Preserve history and entitlements. Apply a capped gold bonus for eligible wins based on clan level. Verify boundary times, retries, and settlement.
- [x] Admin players. Replace the requested indicators with verified IAP and rewarded ad facts. Add permissioned account creation with audit and an unverified email.
- [x] Android release build. Enable R8 and resource shrinking. Build the production bundle and confirm generated mapping and DEX output. Verify optimized Dev-release startup and interactive offline practice on the connected physical device.

## Implemented rules

- Gem offers: 20 gems for 5 plays; 50 gems for 15 plays. Purchased and gifted plays persist in the bank across daily resets. Daily quota is consumed first; cancellation refunds its original source.
- Gift codes: Owner-defined rewards (gold, XP, gems, banked plays), expiry and optional redemption cap. One redemption per account; publishing and stopping require audit reasons. Wallet awards and redemption records are atomic.
- Clan pools: future published configurations use groups of at most 20 clans, ordered by contribution in the previous four completed weeks, with separate starter pools. Membership is frozen for settlement. Historical published races keep their original behavior.
- Clan win bonus: 5% of base gold at levels 5–9, 10% at level 10 or above, on eligible casual/ranked wins. Capture clan level on match admission and preserve it through retries/restarts.
- Support-created accounts can play without an email code while email remains unverified. The Owner operation is audited and idempotent; other accounts retain normal verification requirements.

## Local verification

- Full server suite: 1,004 passed, 28 Redis opt-in tests skipped; PostgreSQL integration tests used a real isolated local database. Subsequent admin/support regression run: 35 passed.
- Protocol: 129 passed. Shared Android host: 519 passed, including refreshed authentication metadata.
- Admin browser: 323 passed, 2 optional live-backend tests skipped. Admin clan-race Node checks: 4 passed.
- Changed Compose screenshot set: 57 passed across phone, tablet/desktop and large text; 3 additional leaderboard popup screenshots passed for the same device classes.
- Android instrumentation sources and JS/Wasm web targets compile; localization scanner passes. Production R8 bundle build passes.
- UI gallery: `.artifacts/ui-preview-20261114/implemented-ui.html`. Logs live beside it. No production migration, deployment or CI run was requested.

## Physical device acceptance — 2026-11-15

- Samsung SM-A366B, Android 17: 37 UI tests passed across seven separate class runs. Coverage includes leaderboard identity/popup, centered labels/values, friend callbacks, clan member dialogs, clan race, community consent, quota dialogs, Gem exchange confirmation/retry, and gift-code input/pending/rewards.
- UI tests use isolated protocol snapshots and callbacks; they do not spend live gems, send real friend requests or redeem production codes. Connection interruptions affected long combined runs, so successful results are recorded per class in `.artifacts/ui-preview-20261114/physical-device/results.json` with the corresponding runner logs.
- Physical R8 smoke testing found a real startup crash: Room 2.2.5 uses `Class.newInstance()` for WorkManager's generated database, while its bundled rule kept only the class. R8 full mode removed `WorkDatabase_Impl()` (confirmed in `usage.txt`). Added one constructor member rule in `app/proguard-rules.pro`.
- Rebuilt both Dev release APK and production AAB. Confirmed the constructor in both generated seed reports. Installed a locally debug-signed Dev release APK; startup succeeded, offline Classic rendered, tapping 1 advanced the target to 2 with 10 points and 100% accuracy. Its process crash log remained empty. Closed the test game afterward.
- Native screenshots are included in the UI gallery. APK and production AAB builds passed after the fix. Online production transactions and Play Console metrics still require the eventual deployment/upload.
