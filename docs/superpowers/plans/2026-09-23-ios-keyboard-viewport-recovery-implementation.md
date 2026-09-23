# iOS Keyboard Viewport Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the full iOS PWA viewport and keep the bottom navigation visible after a native web input closes its software keyboard.

**Architecture:** Extend the existing `FASTTOWIN_PWA.viewport` bridge with one cancellable focus-driven stabilization loop. It records the full viewport immediately before keyboard focus, then samples `visualViewport.height + offsetTop` after blur until the pre-keyboard height is stable or a bounded deadline expires; each sample updates the CSS height and notifies Compose through the existing event.

**Tech Stack:** Browser JavaScript, Visual Viewport API, Compose Multiplatform viewport bridge, Playwright WebKit PWA tests.

**Spec:** `docs/superpowers/specs/2026-09-23-public-tournaments-and-pwa-input-design.md`

## Global Constraints

- The recovery applies only to elements carrying `data-fasttowin-native-input`.
- Android standalone remains excluded from the JavaScript viewport override.
- Existing iOS safe-area caps and edge-swipe suppression remain unchanged.
- A new focus or recovery cancels every timer/frame from the previous recovery.
- Recovery ends no later than 1.2 seconds after blur.

---

### Task 1: Reproduce the delayed iOS keyboard dismissal

**Files:**
- Modify: `e2e/tests/pwa-ios.spec.mjs`

**Interfaces:**
- Consumes: the existing `pwa.js` fixture pattern with a mocked `visualViewport`.
- Produces: a deterministic regression proving recovery works even when the final height change emits no resize event.

- [ ] **Step 1: Add the failing WebKit test**

Add a test beside the existing first-paint viewport test:

```javascript
test('iOS standalone restores the full viewport after keyboard dismissal settles late', async ({ page }) => {
  const pwaScript = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>
      #fastToWinRoot { height: var(--fast-to-win-viewport-height, 844px); }
    </style>
    <main id="fastToWinRoot"><input data-fasttowin-native-input></main>
  `);
  await page.evaluate(() => {
    let viewportHeight = 844;
    const listeners = new Map();
    Object.defineProperty(navigator, 'standalone', { configurable: true, value: true });
    Object.defineProperty(navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    });
    Object.defineProperty(window, 'visualViewport', {
      configurable: true,
      value: {
        get height() { return viewportHeight; },
        offsetTop: 0,
        addEventListener(type, listener) { listeners.set(type, listener); },
        removeEventListener(type) { listeners.delete(type); },
      },
    });
    window.__openKeyboard = () => {
      viewportHeight = 500;
      listeners.get('resize')?.(new Event('resize'));
    };
    window.__finishKeyboardDismissalWithoutResize = () => { viewportHeight = 844; };
  });
  await page.addScriptTag({ content: pwaScript });

  const input = page.locator('[data-fasttowin-native-input]');
  await input.focus();
  await page.evaluate(() => window.__openKeyboard());
  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '500px');
  await input.blur();
  await page.waitForTimeout(250);
  await page.evaluate(() => window.__finishKeyboardDismissalWithoutResize());

  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '844px');
});
```

- [ ] **Step 2: Run the focused test and verify RED**

From `e2e`:

```powershell
node .\node_modules\playwright\cli.js test --project=webkit-pwa-ios --grep "keyboard dismissal settles late"
```

Expected: FAIL because the current 160 ms settle timer finishes before the mocked viewport returns to 844 px and no later resize event triggers another sync.

- [ ] **Step 3: Commit the red regression test**

```powershell
git add e2e/tests/pwa-ios.spec.mjs
git commit -m "test: reproduce delayed iOS keyboard viewport"
```

---

### Task 2: Add bounded focus-driven stabilization

**Files:**
- Modify: `webApp/src/wasmJsMain/resources/pwa.js:1-61`
- Verify: `e2e/tests/pwa-ios.spec.mjs`

**Interfaces:**
- Extends: `FASTTOWIN_PWA.viewport.sync()` without changing its public signature.
- Consumes: `focusin` and `focusout` events from native web inputs.
- Produces: a final `fasttowin-viewport-change` after delayed keyboard dismissal.

- [ ] **Step 1: Add cancellable recovery state**

Keep the existing fast resize path and add state next to `pendingFrame`/`pendingSettle`:

```javascript
let recoveryTimer = 0;
let recoveryFrame = 0;
let preKeyboardViewportHeight = 0;
let recoveryStartedAt = 0;
let recoveryLastHeight = 0;
let recoveryLastSampleAt = 0;
```

Add `cancelViewportRecovery()` that cancels both IDs and zeroes all recovery-only sample state. Do not cancel the normal `pendingFrame`/`pendingSettle` sync work.

- [ ] **Step 2: Record the full pre-keyboard height**

Listen for bubbling `focusin`. Guard the target with `event.target instanceof Element`; when it matches `[data-fasttowin-native-input]`, cancel an older recovery and set:

```javascript
preKeyboardViewportHeight = currentViewportHeight();
```

This handler runs before the keyboard resize event in the browser and therefore preserves the full usable height.

- [ ] **Step 3: Stabilize after focusout**

Add a `focusout` listener for the same selector. Start at `performance.now()` and repeatedly sample through one animation frame followed by a 100 ms timer. At each sample:

1. call `publishViewportHeight()`;
2. compare the current height with `recoveryLastHeight` using a tolerance of `0.5` px;
3. require the current height to be at least `preKeyboardViewportHeight - 1` before accepting stability;
4. stop when two accepted samples are at least 100 ms apart, or when elapsed time reaches 1,200 ms;
5. on stop, call `publishViewportHeight()` once more and clear recovery state.

Use this concrete acceptance condition:

```javascript
const returnedToBaseline = !preKeyboardViewportHeight ||
    height >= preKeyboardViewportHeight - 1;
const stableForLongEnough = returnedToBaseline &&
    Math.abs(height - recoveryLastHeight) <= 0.5 &&
    now - recoveryLastSampleAt >= 100;
```

When the height changes, update both `recoveryLastHeight` and `recoveryLastSampleAt`. Schedule the next sample unless `stableForLongEnough` or the deadline is reached.

- [ ] **Step 4: Run the focused test and verify GREEN**

From `e2e`:

```powershell
node .\node_modules\playwright\cli.js test --project=webkit-pwa-ios --grep "keyboard dismissal settles late"
```

Expected: PASS.

- [ ] **Step 5: Run the complete PWA iOS regression project**

```powershell
node .\node_modules\playwright\cli.js test --project=webkit-pwa-ios
```

Expected: all iOS safe-area, viewport, bottom-bar, and navigation tests pass.

- [ ] **Step 6: Run browser-shell support tests**

From the repository root:

```powershell
node --test e2e\support\navigation-errors.test.mjs e2e\support\web-shell.test.mjs
```

Expected: all support tests pass.

- [ ] **Step 7: Commit the implementation**

```powershell
git add webApp/src/wasmJsMain/resources/pwa.js
git commit -m "fix: restore iOS viewport after keyboard dismissal"
```

---

### Task 3: Combined PWA verification

**Files:**
- Verify only; modify a source or test only if the combined run exposes an in-scope regression.

**Interfaces:**
- Consumes: native placeholder and iOS viewport plans.
- Produces: one verified native-input/PWA browser slice.

- [ ] **Step 1: Build the web and server test distributions**

From the repository root:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :webApp:wasmJsBrowserDevelopmentWebpack :server:installDist --no-daemon --no-configuration-cache
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run all affected Playwright projects**

From `e2e`:

```powershell
node .\node_modules\playwright\cli.js test --project=chromium-native-input --project=webkit-native-input --project=webkit-pwa-ios
```

Expected: every native-input and iOS PWA test passes.

- [ ] **Step 3: Review the final browser diff**

```powershell
git diff HEAD~4..HEAD -- shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/ui/components webApp/src/wasmJsMain/resources/pwa.js e2e/tests
git status --short
```

Confirm the existing unrelated `GameController.kt` change remains unstaged and untouched by these two PWA fixes.
