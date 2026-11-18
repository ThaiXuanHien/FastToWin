# Owned-room reconnect investigation

Issue: #15. Production was recovered to legacy after the attended Android/Web pilot. This investigation changes local source only.

The operator command history confirms the Android action was `am force-stop` followed immediately by `am start`, without a LeaveRoom command. In the B-owned match, Web had first reloaded successfully at Android progress 5/50. The Owner then observed Web leaving the room and returning only after Android selected another number.

## Confirmed missing peer snapshot

The PostgreSQL/Netty reproduction creates a private ORDER room on worker B with both public sockets on ingress A. After five selections it reloads the host Web socket, closes the Android guest socket, waits for the committed disconnect deadline, and reconnects the guest. Web must retain the room and receive a PLAYING snapshot with progress 5/50 before selection 6.

Before the fix, Web received FriendsData and ClanInfoData but timed out waiting for a game snapshot. A smaller engine test confirmed the committed reconnect event addressed only the returning player. Both tests failed before the production-code change.

The fix addresses the reconnect snapshot to all active room members. Existing delivery checks still require current socket generation and current active membership. Expiry events retain their existing recipients, and an unchanged attachment remains a replay. The change does not increment game sequence or alter settlement.

Regression tests:

- `OwnedRoomEngineTest.reconnecting player restores the running snapshot for the idle opponent`: verifies the committed snapshot recipients, phase, selected numbers and unchanged game sequence.
- `RoomOwnershipProductionWebSocketTest.disconnect before reconnect keeps idle opponent in the running owned room`: verifies the two-backend socket flow, checks for an unexpected RoomClosed, and requires the peer snapshot before the next number selection.

Run with an isolated loopback PostgreSQL database using TEST_DATABASE_URL, TEST_DATABASE_USER and TEST_DATABASE_PASSWORD, with FASTTOWIN_TEST_ROOM_OWNERSHIP=true:

```powershell
./gradlew.bat :server:test --no-configuration-cache --tests 'com.hienthai.fastowin.server.ownership.OwnedRoomEngineTest' --tests 'com.hienthai.fastowin.server.ownership.RoomEventDeliveryTest' --tests 'com.hienthai.fastowin.server.ownership.RoomOwnershipProductionWebSocketTest'
```

Presence-specific tests additionally require an isolated FASTTOWIN_TEST_PRESENCE_REDIS_URL. Report skipped tests separately from passes.

Local verification on 15 November: 38 discovered tests across these three classes, 33 passed, 5 skipped because the isolated Redis fixture was absent, zero failures and zero errors. All 10 engine tests and all 13 event-delivery tests ran, including PostgreSQL-backed cases. The WebSocket class ran 10 tests and skipped its 5 Redis-dependent tests. The new engine and two-backend regressions both passed after failing before the fix. An independent read-only review found no actionable defects in the narrow patch. The local PostgreSQL fixture was stopped after verification.

## Remaining acceptance gap

The Owner clarified that Web returned to the room list, rather than showing a blank screen. The initial unexpected exit has not reproduced in the local socket tests, including either player role and Web reload before Android reconnect. Missing peer notification explains the absence of an immediate restoring snapshot, but it does not establish why Web initially left its screen. Do not mark the complete lifecycle incident fixed or close #15 from this patch alone.

## Real-client follow-up

An opt-in, temporary test bridge reused the production WebSocket fixture with real ingress A, worker B and isolated PostgreSQL. A Playwright driver rendered Compose Web, recorded sanitized frame metadata and navigation paths, and captured screens before disconnect, while disconnected, and after reconnect before another number input. Protocol-side clients used abrupt TCP termination. The retained production patch was unchanged during this follow-up.

| Client and viewport | Successful restart cycles | Result |
| --- | ---: | --- |
| Locally built Wasm Web, desktop 1280×900 | 1 | Room and 5/50 retained; peer snapshot arrived before number 6 |
| Locally built Wasm Web, phone viewport 390×844 | 5 | Same result across repeated reloads, alternating short/long reconnect gaps |
| Currently served public Web bundle, desktop | 3 | Same result with public static assets and local gameplay/authentication |
| Samsung SM-A366B Dev app and public Web bundle | 2 | Actual Android force-stop/start retained 5/50 on both clients; tapping 6 on Samsung updated both to 6/50 |

The public-runtime comparison fetched static assets only. The local proxy refused auth, API, admin and gameplay paths; all account and game operations used synthetic accounts in the isolated local database. Asset hashes are retained in the ignored diagnostic directory. This proves behavior for the bundle observed during this follow-up, not the browser cache or network state from the original production incident.

The physical-device run installed `com.hienthai.fastowin.dev` after verifying that package was absent. The APK was built from this checkout as version 1.0.11-dev; it does not establish QA on the Owner's installed production 1.0.12 (13) binary. The Dev app was uninstalled afterward. Its owned reverse-port and UI dump were removed, and the existing production app was brought back to the foreground. No production deployment, account or database was changed.

All 11 successful cycles retained the Web game screen and progress. Their Web traces contain no RoomClosed, server Error or pageerror. The initial return-to-room-list symptom remains unreproduced. One preliminary fixture launch returned ROOM_RECOVERING on the seed create before gameplay; the diagnostic was adjusted to wait for the asynchronous readiness heartbeat before seeding. No production behavior was changed for that setup condition.

Screens, sanitized event traces, public asset hashes and Android XML proofs remain under `.artifacts/room-sync-local/browser*`. The temporary JUnit process bridge was removed after the experiment; the two focused regression tests remain in source. The diagnostic script is explicitly marked as throwaway and stays in ignored artifacts.

Before another rollout, repeat the attended Web/Android sequence in an approved environment with a WebSocket frame and navigation trace. Check that Web stays in the match during Android restart and receives the restored snapshot without another number input. Retain progress, membership, settlement and reward checks. The full presence matrix, Grafana acceptance, load testing and HA limitations remain separate #15 gates.
