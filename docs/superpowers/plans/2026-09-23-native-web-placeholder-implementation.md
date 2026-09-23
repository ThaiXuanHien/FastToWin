# Native Web Placeholder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make native web placeholders disappear synchronously when Android PWA users type while retaining the Material floating label.

**Architecture:** The HTML input becomes the sole placeholder renderer on web. Compose continues to own the Material container and label but passes no placeholder layer to `DecorationBox`; Android/iOS native targets keep the existing Compose placeholder path.

**Tech Stack:** Compose Multiplatform HTML interop, Kotlin/Wasm, HTML input attributes, Playwright Chromium/WebKit E2E.

**Spec:** `docs/superpowers/specs/2026-09-23-public-tournaments-and-pwa-input-design.md`

## Global Constraints

- The tournament-name label remains accessible and floats normally.
- Only the example placeholder disappears when DOM input becomes non-empty.
- Password selection, numeric ordering, and IME composition regressions must remain covered.
- The fix applies to every `AppOutlinedTextField` on web without changing native Android/iOS rendering.

---

### Task 1: Reproduce placeholder ownership in Web E2E

**Files:**
- Modify: `e2e/tests/native-input.spec.mjs`

**Interfaces:**
- Consumes: existing `actors`, `login`, `click`, and `tag` E2E helpers.
- Produces: regression test for `placeholder` and `:placeholder-shown` on the tournament name input.

- [ ] **Step 1: Add the failing Android-PWA-style test**

Add:

```javascript
test('tournament example disappears as soon as native name input has text', async ({ actors }) => {
  const player = await actors('Tournament placeholder');
  await login(player);
  await click(player.page, tag(player.page, 'home_tournament'));
  await click(player.page, tag(player.page, 'tournament_name'));

  const tournamentName = player.page.locator('input[data-fasttowin-native-input][aria-label="Tên giải đấu"]');
  await expect(tournamentName).toHaveAttribute('placeholder', 'VD: Cúp Chiến Thần');
  await expect.poll(() => tournamentName.evaluate(input => input.matches(':placeholder-shown'))).toBe(true);
  await tournamentName.fill('Cúp cuối tuần');
  await expect(tournamentName).toHaveValue('Cúp cuối tuần');
  await expect.poll(() => tournamentName.evaluate(input => input.matches(':placeholder-shown'))).toBe(false);
});
```

- [ ] **Step 2: Run the test and verify RED**

From `e2e`:

```powershell
node .\node_modules\playwright\cli.js test --project=chromium-native-input --grep "tournament example disappears"
```

Expected: FAIL because the native input has no `placeholder` attribute or Compose still owns the visible placeholder.

- [ ] **Step 3: Commit the red test only**

```powershell
git add e2e/tests/native-input.spec.mjs
git commit -m "test: reproduce native web placeholder overlap"
```

---

### Task 2: Transfer placeholder rendering to the DOM input

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/AppOutlinedTextField.kt:56-118`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/AppOutlinedTextField.kt:128-134`
- Modify: `shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/ui/components/NativeWebTextInput.web.kt:36-160`
- Modify: platform actuals matching `NativeWebTextInput` under `shared/src/*Main/kotlin/com/hienthai/fastowin/ui/components/`

**Interfaces:**
- Consumes: optional `placeholder: String?` from `AppOutlinedTextField`.
- Produces: `NativeWebTextInput(..., placeholder: String, ...)` with native HTML placeholder rendering.

- [ ] **Step 1: Extend the expect/actual signature**

Add `placeholder: String` immediately after `label: String` in the expect and every actual signature. Pass `placeholder.orEmpty()` from `AppOutlinedTextField`.

- [ ] **Step 2: Suppress only the Compose placeholder on web**

Keep `placeholderContent` for non-web `OutlinedTextField`. In the native branch pass `placeholder = null` to `OutlinedTextFieldDefaults.DecorationBox`:

```kotlin
placeholder = null,
```

Keep `label = labelContent`; focus and non-empty controlled values therefore retain Material label behavior.

- [ ] **Step 3: Set the HTML placeholder attribute**

In the Wasm `update` block:

```kotlin
if (placeholder.isEmpty()) {
    element.removeAttribute("placeholder")
} else {
    element.setAttribute("placeholder", placeholder)
}
```

Do not copy placeholder text into the input value. Do not alter reconciliation or selection logic.

- [ ] **Step 4: Compile all affected targets**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :shared:compileAndroidHostTest :webApp:compileKotlinWasmJs --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run native-input regression projects**

From `e2e`:

```powershell
node .\node_modules\playwright\cli.js test --project=chromium-native-input --project=webkit-native-input
```

Expected: all tests pass, including placeholder, selection, composition, password, and numeric ordering.

- [ ] **Step 6: Commit the implementation**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/AppOutlinedTextField.kt shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/ui/components/NativeWebTextInput.web.kt shared/src/androidMain/kotlin/com/hienthai/fastowin/ui/components/NativeWebTextInput.android.kt shared/src/iosMain/kotlin/com/hienthai/fastowin/ui/components/NativeWebTextInput.ios.kt
git commit -m "fix: hide native web placeholder while typing"
```
