# Redis production handoff — 12/11/2026

## Release

- Source commit `ba59bf0ed6fcd2f1d4c2955dbdd38e25dd17581c`
- Android Internal Testing `1.0.11 (12)`, signed AAB SHA-256 `f686aabff0247bafce9a4d1abcab8e5daad532a121714fe812382c11e922b8a6`
- Backend deployment `94abd101-efb8-4277-b494-ae6c08cebe69`, source JAR SHA-256 `43d604c5addf75e8fea6e75db8c384f9ad3ff2bc88b42d3a90687dbbcec4cd66`
- Web deployment `ba977e7e-d592-4933-b142-d409c9943401`, package SHA-256 `cf44b199f4239a766dfc86ef22764f2e101024b532940f144dee5670cab4ea52`
- Production status after reopen was `ACTIVE` v33 and `/health` returned HTTP 200.

## Verification

- 31 backend Redis integration tests passed with no skips.
- 7 client host tests passed.
- Web retry tests passed on Wasm desktop and JavaScript phone fallback.
- Production smoke ran with `FASTTOWIN_ENV=prod`, PostgreSQL V69 and separate Redis roles.
- Android Galaxy A36 installed versionCode 12 from Google Play Internal Testing.
- Maintenance entered with zero matches and zero WebSocket connections.
- Public Web runtime hashes matched the tested package.
- The saved post-reopen observation contains 7 samples from 09:06:16 to 09:10:56 (UTC+7), spanning 4 minutes 40 seconds. The policy was `ACTIVE` v33. WebSocket connections, Redis rate-limit unavailable, and Redis presence read/write failures were all zero in those samples.

## Recovery

- Previous backend and Web deployments retained `canRollback=true` before rollout.
- PostgreSQL backup and isolated restore receipt reached schema V69 with matching encrypted hash.
- The temporary SSH key was revoked and its absence was verified after observation.

## Live Android and Web QA

- Android production build `1.0.11 (12)` ran on Galaxy A36 with a user-provided session. Web ran with the Play review test account. Both joined the same private classic casual room and started the match.
- Web reloaded in the match and resumed at `1/50` with the same opponent. Android was closed and reopened and resumed at `1/50`. Android played the next number and both clients advanced to `2/50`.
- The Web player chose to leave the match. Web showed a loss but described a room expiry after disconnect. Android continued to display `2/50` and showed the win only after reopening the app. Both clients then agreed on the winner. The production WebSocket handler failed at 12:47:03 (UTC+7) in `GameEngine.persistCompletedMatch`. Formatting a mission completion notification threw `IllegalArgumentException` after saving the result and before delivery to the clients. The Web client then reconnected and used the expired-loss message for the finished snapshot without checking how the match ended.
- A temporary friendship between the two accounts enabled presence checks. Web showed the Android player online, offline after the Android app stopped, and online after reopening it. Android displayed the Web player as in a room when Web hosted a second private room. The second room was closed and removal of the temporary friendship was verified on both clients.
- Clan screens loaded on both clients. The Web roster showed the active test account online and another member offline. The Android account is in a different clan. Shared clan member transitions and the in-match indicator in the Friends screen remain unverified.

## Post-match monitoring

- Public health and policy returned HTTP 200 and `ACTIVE` v33 on all 16 samples from 12:47:35 to 13:02:46 (UTC+7), a span of 15 minutes 11 seconds after the private match. Samples are in `.artifacts/redis-rollout/post-match-health-20261112-054735.jsonl`.
- Grafana Explore returned 60 samples for Redis primary from 12:47:46 to 13:02:46 (UTC+7). The minimum collector and scrape `up` were both 1. AOF remained enabled, and the minimum last-write and last-rewrite status were both 1 (`OK`). Evictions and delayed fsync increased by 0, and peak pending fsync was 0. Peak Redis memory was 1,870,680 bytes (1.78 MiB). Peak AOF size was 2,168,787 bytes (2.07 MiB). [Redis Cloud dashboard](https://orangetruck1313.grafana.net/d/fasttowin-redis-cloud/fast-to-win-c2b7-redis-cloud).
- In the same post-match health window, Railway recorded 61 successful Redis collections and no failures. The 178 backend log records contained none of the checked Redis failure markers. Grafana had no series for the backend presence and rate-limit counters. Their deltas during the window remain unverified.

## Match result fix

- The server uses the rendered mission title when the legacy snapshot lacks the localization arguments. The WebSocket regression now delivers the same final result to both reconnected clients when a player leaves and a mission completes.
- A finished snapshot identifies a voluntary leave or disconnect timeout when the server knows the cause. The client shows that reason only to the affected player. Recovered results with no reason use the ordinary win or loss description.
- Local verification with disposable PostgreSQL passed 986 backend tests, 514 Android host tests and 129 protocol tests. The 27 Redis fixture tests were skipped. `:webApp:composeCompatibilityBrowserDistribution` succeeded, and the packaged JS fallback and Wasm loader hashes matched their individual production outputs.

## Production retest

- Planned maintenance reached `MAINTENANCE` v36. The Owner console observed zero active work, Web showed the maintenance screen, and a new WebSocket closed immediately. The aggregate WebSocket gauge was unavailable for this window. After both services passed Railway health checks, production reopened to `ACTIVE` v37 at 18:58 (UTC+7).
- The first private match confirmed that Android v1.0.11 (12) displayed the win as soon as Web left. Web displayed a generic loss. The browser asset inventory showed it had loaded `5bab2b0e9a04c624fceb.wasm` from the previous release. The compatibility distribution now versions the bootstrap and both runtime URLs by build content. Chrome with a warm cache passed the ordinary reload check for Wasm and simulated iPhone JS fallback. All 65 packaged Web files matched the successful Gradle build with production config, and the public runtime SHA-256 values matched the package.
- The same production browser loaded `f3ae02487340cb815328.wasm` after a regular reload. In a new private casual match, Web recovered at `1/50` after reload and both clients reached `2/50` after Android played. Web left and displayed “Bạn đã chủ động rời trận và bị xử thua.” Galaxy A36 displayed `CHIẾN THẮNG!` without restarting the app. The live test did not verify that a new mission-completion push was triggered. That path was exercised by the local WebSocket regression test.
- Between 18:58:11 and 19:13:16 (UTC+7), Railway logged 61 successful Redis collector events and zero failures. At 19:21, `/health` returned 200 and policy was `ACTIVE` v37. The 42 backend log entries inspected after the final Web deployment contained no checked Redis failure markers or missing localization argument errors. Internal backend counter deltas were unavailable.
- Final Railway deployments were `1774934a-45c6-43b2-af30-0463acd9130e` for backend and `e7ee8ca3-13d6-4c81-a894-ed687b4944ee` for Web. The previous backend was `94abd101-efb8-4277-b494-ae6c08cebe69`. The Web deployment immediately before the cache fix was `2681a445-bb65-44e8-8b5c-69386b700d00`.

## Remaining scope

Issue #15 still owns distributed room, match ownership and settlement. This release keeps one backend replica. Play Console reported two non-blocking Android warnings for missing deobfuscation mapping and native debug symbols.
