# Task 4 — Public tournament discovery UI

## Outcome

Implemented discovery before invitations whenever there is no active tournament. The screen now has name, game-mode, size, and fee filters; refresh; host/mode/player-count/prize/entry-fee cards; free direct join; and an explicit paid-entry confirmation with cancellation. Removed or repriced listings invalidate a pending confirmation. Cached cards stay visible but joins are disabled while disconnected, refreshing, or joining. Loading, empty, and reconnect messages are distinct.

Creation defaults to PUBLIC using rememberSaveable, with equal-width Public/Private chips passed through the five-argument create callback. Host-owned, non-full lobbies allow offline friends to be invited and show add-friends guidance when nobody is eligible. The controller's filter, refresh, and public-join callbacks are wired in FastToWinApp.

Added 18 English/Vietnamese UI keys and five protocol recovery messages. Existing PLAYER_BUSY, NOT_ENOUGH_GOLD, and TOURNAMENT_CANCELLED mappings remain appropriate and are covered alongside the new mappings. The cancellation code is shared with the existing host-cancellation notice, so that localized notice is preserved.

## RED evidence

- Added Compose tests before production changes for paid confirmation/cancellation, free direct join, offline friend invitations, empty invitation guidance, public default/private selection, all filter callbacks plus refresh, and loading/reconnect/empty states.
- Added localization coverage before production changes for every new UI key and the eight relevant server-code mappings.
- Ran:
  `./gradlew.bat :protocol:jvmTest --tests '*SocialScreenLocalizationTest' :app:compileDevDebugAndroidTestKotlin --continue --no-daemon`
- After correcting two test-only API/import typos, the proper RED run failed with 11 localization tests, 2 failures (missing UI keys and missing specific server-code mappings). AndroidTest compilation failed on the missing public callbacks and five-argument create interface.
- No device was attached: the installed SDK's `adb devices` returned an empty device list. Therefore UI RED is compilation evidence, not runtime assertion evidence.

## GREEN evidence

Final command:

```powershell
./gradlew.bat :protocol:jvmTest :shared:testAndroidHostTest :app:compileDevDebugAndroidTestKotlin :webApp:compileKotlinJs :webApp:compileKotlinWasmJs --continue --no-daemon
```

- BUILD SUCCESSFUL in 35s, exit 0.
- Protocol JVM: 86 tests, 0 failures (includes 11 SocialScreenLocalizationTest cases).
- Shared Android host: 150 tests, 0 failures.
- Android devDebug app and AndroidTest Kotlin compilation passed.
- Web JS and Wasm compilation passed.
- TournamentScreenTest has 18 cases, including 8 new discovery/visibility/invitation cases; compiled only, not run on a device.
- `git diff --check` passed.
- Existing SDK XML-version, experimental screenshot-test, AndroidTest INVISIBLE_REFERENCE suppression, and Wasm interop opt-in warnings remain; no new compiler warnings were introduced in Task 4 files.

Commands used Android Studio JBR, the installed local Android SDK, workspace TEMP/TMP, and removed JAVA_TOOL_OPTIONS, following Task 3's environment workaround.

## Integration corrections

- Updated the old LocalizedMessageMapperTest expectation from “Created a private 8-player tournament.” to the neutral “Created a 8-player tournament.” This corrects a stale Task 2 expectation, not a new change to creation copy.
- Updated CriticalFlowsUiTest and LocalizedSocialShopUiTest for the extra visibility parameter. Creation/invitation tests now scroll to controls after discovery was inserted above them.
- Initial broad verification exposed the existing positional protocol translation table: adding five English keys changed its expected length from 70 to 75. Separated the new bilingual message extension from the legacy positional key list, preserving all existing translations and their size validation. The other ten languages use English fallback only for newly added discovery copy, as this task requests EN/VI.

## UI checklist

Code-reviewed:

- Filters wrap with FlowRow; labels can wrap rather than clip in a fixed row. The new Compose interaction fixture uses 375dp width and 1.4 font scale.
- Chips have at least 48dp height and native selected semantics; invitations have at least 48dp height; primary actions reuse existing 50dp arcade controls.
- Filter selection has no width-changing leading icon or layout animation. Added a bounds-stability assertion to the filter interaction test.
- All meaningful new controls have stable tags and visible localized labels. Decorative icons retain null descriptions.
- No emoji icons, raster assets, or new visual language added. Uses Material theme tokens and existing ArcadePanel/ArcadeActionButton/ArcadeDialog components.
- Focused composables accept immutable values and callbacks; network calls remain in the controller.
- Existing ResponsiveScreen and Scaffold insets are preserved.
- Useful loading, empty, reconnect, and server recovery copy is present.

Not verified on hardware:

- 375dp/large-text runtime interaction, landscape/tablet layout, largest system font, screen reader navigation, reduced-motion settings, and measured light/dark contrast. No attached Android device was available; compilation is not a visual or accessibility pass.

## Files changed

- shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TournamentScreen.kt
- shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt
- protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt
- protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/ProtocolMessageKeys.kt
- protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/SocialShopCatalogTexts.kt
- protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/ProtocolMessageCatalogTexts.kt
- protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization/SocialScreenLocalizationTest.kt
- shared/src/commonTest/kotlin/com/hienthai/fastowin/localization/LocalizedMessageMapperTest.kt
- app/src/androidTest/java/com/hienthai/fastowin/TournamentScreenTest.kt
- app/src/androidTest/java/com/hienthai/fastowin/CriticalFlowsUiTest.kt
- app/src/androidTest/java/com/hienthai/fastowin/LocalizedSocialShopUiTest.kt

## Self-review and remaining concerns

- The worktree was clean before this slice. Changes are limited to UI, localization, callback compatibility, tests, and this report.
- Server-side admission remains authoritative if a lobby fills or starts between discovery and confirmation; mapped errors explain recovery.
- The existing request protocol has no response query identifier (Task 3 limitation), so response ordering can still affect displayed results.
- Device execution and actual visual accessibility verification remain the main follow-up; there are no known failing host tests or compilation targets.

## Review round 1 — Current paid-join confirmation

Addressed both Important review findings:

1. Paid confirmation now includes tournament name, current entry fee, and current prize pool. The English and Vietnamese templates accept all three arguments. Tests assert the exact resolved sentences in both languages; the Compose paid-join case also asserts the displayed name/fee/prize copy.
2. Pending UI state stores only tournamentId. Each render resolves the matching latest PublicTournamentSummary, so same-ID name/fee/prize updates immediately change the dialog. Removal or transition to a free listing dismisses the dialog and clears pending selection without joining. Refreshing, joining, and disconnected states disable confirmation; the callback has the same explicit guard. The dialog can resume confirmation when the connection/loading state recovers.

The previous snapshot-retention/fee-change-invalidation behavior documented above is superseded by this round.

### RED/GREEN evidence

- Added four Compose regression cases before production changes: same-ID updates, refreshing/joining/disconnected disabled confirmation with no callback, listing removal, and transition to free. Extended the original paid-join case with exact displayed-content coverage. Mutable-state fixtures retain the 375dp / 1.4 font-scale configuration.
- Added a localization rendering test and updated the expected templates before production changes.
- RED command: `./gradlew.bat :protocol:jvmTest --tests '*SocialScreenLocalizationTest' :app:compileDevDebugAndroidTestKotlin --continue --no-daemon`. Twelve localization cases ran; two failed with ComparisonFailure because the old template omitted the prize pool. AndroidTest sources compiled.
- `adb devices` still listed no devices. Consequently the new state-transition Compose assertions have not been executed; only their compilation was verified.
- GREEN command: `./gradlew.bat :protocol:jvmTest :shared:testAndroidHostTest :app:compileDevDebugAndroidTestKotlin :webApp:compileKotlinJs :webApp:compileKotlinWasmJs --continue --no-daemon`.
- Result: BUILD SUCCESSFUL in 50s, exit 0. Protocol 87/87 tests passed; shared 150/150 passed. Android app and AndroidTest, web JS, and web Wasm compiled. TournamentScreenTest now has 22 compiled cases.
- `git diff --check` passed. Existing SDK/Wasm warnings remain.

### Round-1 self-review

- The fee and prize come from the same latest summary, not independent cached fields.
- Guards prevent confirmation during refresh/disconnection/join, while cancellation remains available.
- Invalidated selections do not reopen if the old listing later reappears.
- Existing arcade dialog/buttons, touch-target sizes, test tags, and compact layout are preserved; no new visual language or layout-shifting selection state was introduced.
- Remaining concern is unchanged: device interaction and visual/accessibility checks require an attached device. The localization assertions were executed, but compilation alone does not establish runtime Compose behavior.
