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
