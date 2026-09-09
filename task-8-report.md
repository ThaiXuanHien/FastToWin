# Task 8 report

## Changes

- Added `phaseOneKeysAreTranslatedWithoutEnglishFallback`, covering the four reconnect and expiry keys in every non-English catalog.
- Added scanner fixtures that require a hard-coded Vietnamese reconnect overlay to be rejected and `localized(TextKey.ReconnectingMatch)` to be accepted.
- Added the exact six-step manual reconnect checklist to `README.md`.
- Left the approved specification status unchanged. Phase 1 is not marked implemented because the required local, instrumentation, E2E, manual-device, and CI evidence is incomplete.

## Verification attempted on 08/09/2026

- `:shared:checkLocalizedUiTextScannerFixtures --no-daemon` was attempted with the Android Studio JDK, local Android SDK, project-local Gradle home, and IPv4-only `JAVA_TOOL_OPTIONS`; Gradle stopped before task configuration with `java.io.IOException: Unable to establish loopback connection`.
- The complete Task 8 local Gradle command and `:app:connectedDevDebugAndroidTest --no-daemon` stopped at the same Gradle loopback failure before compiling or testing.
- `pnpm test` and `pnpm test:js-fallback` both stopped because the corresponding Web bundles were absent; they require the blocked Gradle Web builds first.
- `git diff --check` completed without whitespace errors.

## Remaining evidence

- A successful scanner/catalog RED-to-GREEN run remains blocked by Gradle's loopback failure.
- Complete local Gradle, connected Android instrumentation, Wasm E2E, Kotlin/JS fallback E2E, physical two-device reconnect testing, push, and the required CI jobs remain outstanding.

## Scope scan

- Match-event persistence, match detail, and rematch identifiers remain present.
- The raw `replay` scan still finds the retained practice/challenge `ReplaySameBoard` feature and replay-removal test names; it does not establish a clean no-match-replay result on its own.

## Round 1 scanner boundary repair

- `checkLocalizedUiText` now explicitly includes the shared root `FastToWinApp.kt`, where the reconnect overlay is rendered, while retaining the existing `*Main/kotlin` source boundary so test and generated sources are not added.
- The fixture task asserts that the reconnect overlay source remains selected. Before the selector implementation, that assertion would reject the existing segment-only selector; Gradle could not reach the task because of the same loopback failure.
- Production scanning and fixtures now both call `findLocalizedUiTextViolations`, including the narrow server-only `legacyFallback(...)` allowance. The fixture set covers a rejected Vietnamese reconnect `Text`, an accepted localized reconnect key, and an accepted server `legacyFallback` literal.
- A direct text scan of `FastToWinApp.kt` found no Vietnamese literals, so expanding the selector did not require unrelated source changes.
- Re-running `:shared:checkLocalizedUiText :protocol:jvmTest --tests "*LocalizationCatalogTest" --no-daemon` remained blocked before Gradle configuration with `java.io.IOException: Unable to establish loopback connection`.

## Round 2 integration-gate repair

- The controller's full gate reached compilation and found the invalid `androidx.compose.foundation.layout.weight` import in `FastToWinPullRefresh.kt`, missing `LaunchedEffect` imports in `ProfileScreen.kt`, and three configuration-cache serialization failures from the scanner tasks' build-script `doLast` closures.
- Removed only the invalid `weight` import and restored only `LaunchedEffect`; the composable remains inside a `ColumnScope`, where `Modifier.weight(1f)` is available without that import.
- Moved scanner execution into typed `buildSrc` tasks with declared file/directory inputs. The shared scanner owns source selection, lexical scanning, and the server-only `legacyFallback(...)` allowlist; fixtures use that same implementation. The build script now only wires task inputs and dependencies.
- The full gate and two configuration-cache-enabled scanner attempts were re-run in this sandbox with the prescribed JDK/SDK/IPv4 JVM environment, but each stopped before Gradle configuration with `java.io.IOException: Unable to establish loopback connection`. No GREEN output or configuration-cache reuse evidence is available from this environment.

## Round 3 buildSrc compilation repair

- The controller's full gate compiled `buildSrc` and reported `Unresolved reference DisableCachingByDefault` at the scanner task annotations. The annotation belongs to `org.gradle.work`, not `org.gradle.api.tasks` for this Gradle API.
- Corrected only that import; the task annotations and declared configuration-cache-safe inputs are unchanged.
- This sandbox still cannot reach Gradle compilation because its daemon bootstrap fails with `java.io.IOException: Unable to establish loopback connection`; the controller must re-run the gate for GREEN evidence.

## Round 4 Android-test compilation repair

- The controller's full gate passed the buildSrc/scanner configuration-cache stages and reached Android test compilation. It found that `ArcadeShellUiTest` accesses internal `HomeDashboard`, and that this Compose version has no importable `androidx.compose.ui.test.assertDoesNotExist` symbol.
- Added the same narrow file-level invisible-member suppression already used by `ReconnectOverlayUiTest`; `HomeDashboard` remains internal. Removed only the invalid explicit assertion import, leaving the existing node member calls unchanged.
- `:app:compileDevDebugAndroidTestKotlin --no-daemon` was attempted locally but this sandbox again stopped before Gradle configuration with `java.io.IOException: Unable to establish loopback connection`. Controller rerun remains needed for GREEN output.

## Round 5 Web dialog and refresh-state repair

- Reproduced all four responsive dialog failures: the language dialog content was visible, but Playwright timed out on `arcade_dialog` because the `Surface` semantics tag was not exported by the Compose Web accessibility tree. Moved the single tag to the bounded inner content container; the test continues to measure the rendered dialog rather than introducing a Web-only UI.
- Reproduced the desktop refresh failure: `pointer_refresh` stayed observable as enabled while the lobby was already searching. The refresh component now receives the combined local pending and external search state, so duplicate refresh requests stay blocked for both sources.
- Compose Web does not expose the tagged wrapper as a natively disabled DOM control. The E2E contract therefore uses mutually exclusive state tags (`pointer_refresh` while idle and `pointer_refresh_busy` while loading), while the real `ArcadeActionButton` remains disabled from the same `isRefreshing` value. The touch project verifies that neither pointer-only tag exists.
- RED evidence included four `arcade_dialog` locator timeouts, the previous `toBeDisabled()` failure, and a state-tag run that could not find `pointer_refresh_busy` before the production tag was added.
- GREEN: `:shared:testAndroidHostTest :webApp:wasmJsBrowserDevelopmentWebpack --no-daemon` completed successfully (64 tasks).
- GREEN: the focused desktop and touch refresh projects completed with `2 passed (10.1s)`.
- GREEN: the four focused dialog projects (`small-phone`, `large-phone`, `tablet`, and `landscape`) completed with `4 passed (23.4s)`.
- The approved specification status remains unchanged: Android instrumentation harness, Firefox startup, CI, and manual-device evidence remain outside this repair and Phase 1 is not declared deployed.
