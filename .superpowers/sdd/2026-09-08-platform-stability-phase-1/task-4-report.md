# Task 4 report — authoritative snapshots and reconnect overlay

## RED

Before production edits, added `AuthoritativeSnapshotTest` and
`ReconnectOverlayUiTest`. The prescribed RED command failed for the expected
missing production APIs:

- `applyAuthoritativeSnapshot` was unresolved in `AuthoritativeSnapshotTest`.
- `ReconnectOverlay` was unresolved in `ReconnectOverlayUiTest`.

The first attempt also exposed a local Android SDK environment omission; after
setting `ANDROID_HOME`, the test compilation reached the expected RED failures.

## GREEN implementation

- `GameState.applyAuthoritativeSnapshot` now replaces every server-owned match
  value, including board numbers, player lists and selections, target, score,
  mistakes, timer, phase, winner, room/match metadata and sequence.
- Waiting and playing snapshot paths both delegate to this function. Timers are
  restarted only after the copied state has been applied; stale wrong-number UI
  feedback is reset for each authoritative sequence.
- Resume of a one-shot `FINISHED` snapshot sets `isGameOver`, preserving the
  authoritative winner and routing through the existing result screen. A
  recovered expiry result displays the localized official-loss explanation.
- The reconnect blocker is a production `ReconnectOverlay` layered above the
  unchanged route, with `reconnect_overlay`, `reconnect_blocker`,
  `reconnect_retry`, and `game_board` tags. It blocks pointer input and calls
  `GameController.retryConnection()` through the production app composition.
- Account-terminal expiry remains a direct socket-to-auth callback, outside
  Compose recomposition. Existing `GameSocketClientTest` asserts one expiry
  callback for terminal session failures.
- Added `ReconnectAction`, `ReconnectBlockingHint`, and
  `MatchExpiredOfficialLoss` in all twelve localization catalogs.

`ReconnectOverlay` is internal. The Android test suppresses only Kotlin's
cross-module visibility diagnostic so it invokes the real production composable
without making it a public application API or adding a test-only wrapper.

## Verification

- PASS: `:shared:testAndroidHostTest --tests "*AuthoritativeSnapshotTest"`
- PASS: `:protocol:jvmTest --tests "*LocalizationCatalogTest"`
- PASS: Task 4 verification command:
  `:shared:testAndroidHostTest --tests "*AuthoritativeSnapshotTest" --tests
  "*GameStateTest" :app:compileDevDebugAndroidTestKotlin
  :app:compileDevDebugKotlin :webApp:compileKotlinWasmJs
  :webApp:compileKotlinJs`
- Runtime UI test attempted on connected device `0B141FDD4000YQ`:
  `ReconnectOverlayUiTest` could not find a Compose hierarchy. The identical
  pre-existing `ArcadeGameUiTest` fails with the same harness error on this
  device, so this is an environment/harness limitation rather than an overlay
  assertion failure. Android UI-test compilation is green.
- PASS: `git diff --check`

## Residual risk

The focused instrumentation assertion has not passed on the currently attached
device because its Compose test harness fails for both new and existing tests.
Re-run `ReconnectOverlayUiTest` on a functioning Compose instrumentation
environment before Phase 1 is closed.

## Commit

Initial implementation: `7cd51b6f052d042bfc48238d6027ddd367aa142b`

## Fix round 1

- RED: the equal-sequence snapshot test did not compile until
  `authoritativeSnapshotRevision` existed.
- GREEN: every accepted authoritative snapshot increments that revision,
  including snapshots with an equal server sequence. `GameScreen` clears local
  wrong-number feedback from this revision rather than `latestGameSequence`, so
  repeated snapshots cannot retain stale red feedback.
- Restored `ReconnectOverlay` to `internal`; its test remains against the same
  production composable, not a duplicate layout or public test wrapper.
- The UI test performs a real touch on `game_number_1` while reconnecting and
  asserts the production number callback remains at zero. It also verifies the
  overlay, blocker, retry action, and one retry callback.
- PASS: snapshot/state tests and Android UI-test compilation. The focused
  instrumentation test still fails before hierarchy assertions with the same
  known device Compose-harness failure seen in `ArcadeGameUiTest`.
