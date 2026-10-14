import { randomUUID } from 'node:crypto';
import { test as base, expect } from 'playwright/test';
import { isExpectedNavigationAbort, createFirefoxNavigationErrorFence } from './navigation-errors.mjs';

export { expect };
export const test = base.extend({
  actors: async ({ browser, request, baseURL }, use, testInfo) => {
    const actors = [];
    const createActor = async (label = 'Player', options = {}) => {
      const suffix = randomUUID().replaceAll('-', '').slice(0, 12);
      const shortLabel = label.replace(/[^a-z0-9]/gi, '').slice(0, 8) || 'Player';
      const account = { email: `e2e-${suffix}@example.invalid`, password: `Test-${suffix}-9!`,
        displayName: options.displayName || `E2E ${shortLabel} ${suffix.slice(0, 4)}`,
        devicePlatform: 'web-e2e', gender: 'MALE' };
      const registered = await request.post(`${process.env.E2E_API_URL}/auth/register`, { data: account });
      expect(registered.status(), 'Create only this test account').toBe(201);
      const session = await registered.json();
      const verification = await request.post(
        `${process.env.E2E_API_URL}/auth/email-verification/request`,
        { data: { accessToken: session.accessToken } },
      );
      expect(verification.ok(), 'Request verification for the disposable account').toBeTruthy();
      const verificationCode = (await verification.json()).devEmailVerificationCode;
      expect(verificationCode, 'Development server exposes only the disposable verification code').toMatch(/^\d{6}$/);
      const verified = await request.post(
        `${process.env.E2E_API_URL}/auth/email-verification/confirm`,
        { data: { accessToken: session.accessToken, verificationCode } },
      );
      expect(verified.ok(), 'Verify the disposable account before UI login').toBeTruthy();
      const actor = {
        account,
        accessToken: session.accessToken,
        errors: [],
        errorDetails: [],
        navigationRequestFailures: [],
        closing: false,
        expectedNavigationAbortUntil: 0,
        correlatedWebKitAbortUntil: 0,
        cancelledWebKitRequest: null,
        blocked: false,
        sockets: new Set(),
      };
      actors.push(actor);
      const loopbackOrigins = [new URL(baseURL).origin, new URL(process.env.E2E_API_URL).origin];
      const recordBrowserError = detail => {
        actor.errors.push(detail.message);
        actor.errorDetails.push(detail);
      };
      const firefoxNavigationErrors = createFirefoxNavigationErrorFence({
        loopbackOrigins, onError: recordBrowserError,
      });
      actor.flushNavigationErrors = () => firefoxNavigationErrors.flush();
      const context = await browser.newContext({
        baseURL,
        viewport: testInfo.project.use.viewport,
        locale: 'vi-VN',
        serviceWorkers: 'block',
        hasTouch: testInfo.project.use.hasTouch ?? false,
      });
      actor.context = context;
      if (typeof testInfo.project.use.hasTouch === 'boolean') {
        // Chromium on a touchscreen Windows host can retain native
        // maxTouchPoints even when event emulation is disabled. Explicit input
        // projects own this fixture value; they are not real-device tests.
        await context.addInitScript(hasTouch => {
          Object.defineProperty(Navigator.prototype, 'maxTouchPoints', {
            configurable: true, get: () => hasTouch ? 1 : 0,
          });
        }, testInfo.project.use.hasTouch);
      }
      if (options.preferences) {
        await context.addInitScript(preferences => {
          window.localStorage.setItem('fasttowin.preferences', JSON.stringify(preferences));
        }, options.preferences);
      }
      if (options.iosStandalone) {
        await context.addInitScript(() => {
          // Standalone iOS has no desktop window chrome. Windows WebKit adds
          // 39px to outerHeight unless the standalone fixture models it.
          // Page/context init-script order is unspecified; preserve dimensions
          // owned by a page's keyboard/status-bar fixture if it ran first.
          if (!window.__fastToWinIosViewportMock) {
            Object.defineProperty(window, 'outerHeight', {
              configurable: true,
              value: window.innerHeight,
            });
          }
          Object.defineProperty(window.navigator, 'standalone', {
            configurable: true,
            get: () => true,
          });
          Object.defineProperty(window.navigator, 'userAgent', {
            configurable: true,
            get: () => 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
          });
        });
      }
      // Use only the chosen local test backend; disable push/PWA configuration.
      await context.route('**/config.js', route => route.fulfill({
        contentType: 'text/javascript',
        body: `globalThis.FASTTOWIN_CONFIG=${JSON.stringify({
          serverUrl: `${process.env.E2E_API_URL.replace('http:', 'ws:')}/game`,
        })};`,
      }));
      await context.routeWebSocket('**/game', route => {
        if (actor.blocked) { void route.close({ code: 1012, reason: 'E2E connection outage' }); return; }
        const upstream = route.connectToServer();
        const pair = { route, upstream };
        actor.sockets.add(pair);
        route.onClose(async (code, reason) => { actor.sockets.delete(pair); await upstream.close({ code, reason }); });
        upstream.onClose(async (code, reason) => { actor.sockets.delete(pair); await route.close({ code, reason }); });
      });
      actor.disconnect = async () => {
        actor.blocked = true;
        await Promise.all([...actor.sockets].flatMap(({ route, upstream }) => [
          route.close({ code: 1012, reason: 'E2E connection outage' }),
          upstream.close({ code: 1012, reason: 'E2E connection outage' }),
        ]));
        actor.sockets.clear();
      };
      actor.reconnect = () => { actor.blocked = false; };
      actor.page = await context.newPage();
      actor.page.on('requestfailed', request => {
        const at = Date.now();
        const browserName = testInfo.project.use.browserName;
        if (!['webkit', 'firefox'].includes(browserName) || at > actor.expectedNavigationAbortUntil) return;
        const url = new URL(request.url());
        const failure = {
          url: `${url.origin}${url.pathname}`, method: request.method(),
          failureText: request.failure()?.errorText, at,
        };
        // Diagnostic metadata only: never retain query strings, headers or bodies.
        // Generic Firefox wrappers require this exact local GET cancellation;
        // the error fence also handles requestfailed arriving after pageerror.
        actor.navigationRequestFailures.push(failure);
        if (actor.navigationRequestFailures.length > 8) actor.navigationRequestFailures.shift();
        if (browserName === 'webkit') actor.cancelledWebKitRequest = failure;
        if (browserName === 'firefox') firefoxNavigationErrors.recordCancellation(failure);
      });
      actor.page.on('pageerror', error => {
        // Browsers can report an in-flight fetch cancelled by an explicit
        // reload/history navigation as a page error. It can arrive just after
        // the navigation promise settles on slower CI runners, so keep a short
        // grace window and accept only browser-specific cancellation messages.
        const message = error.message;
        const browserName = testInfo.project.use.browserName;
        const expectedNavigationAbort = isExpectedNavigationAbort({
          browserName,
          message,
          now: Date.now(),
          expectedUntil: actor.expectedNavigationAbortUntil,
          correlatedWebKitAbortUntil: actor.correlatedWebKitAbortUntil,
          cancelledRequest: actor.cancelledWebKitRequest,
          loopbackOrigins,
        });
        if (expectedNavigationAbort) {
          if (browserName === 'webkit' && message.endsWith('due to access control checks.')) {
            // Confirm only the loopback cancellation already accepted above;
            // its Kotlin/Wasm wrapper follows within the same event burst.
            actor.correlatedWebKitAbortUntil = Math.min(actor.expectedNavigationAbortUntil, Date.now() + 250);
          } else if (message === 'Fail to fetch') {
            actor.correlatedWebKitAbortUntil = 0;
            actor.cancelledWebKitRequest = null;
          }
          return;
        }
        const detail = { message, stack: error.stack, at: Date.now(),
          url: actor.page.url(), closing: actor.closing,
          expectedNavigationAbortUntil: actor.expectedNavigationAbortUntil,
          navigationRequestFailures: [...actor.navigationRequestFailures] };
        if (browserName === 'firefox' &&
            firefoxNavigationErrors.capture(detail, actor.expectedNavigationAbortUntil)) return;
        recordBrowserError(detail);
      });
      actor.navigate = async action => {
        const tracksNavigationAbort = ['webkit', 'firefox'].includes(testInfo.project.use.browserName);
        if (tracksNavigationAbort) {
          await firefoxNavigationErrors.flush();
          firefoxNavigationErrors.resetCancellation();
          actor.navigationRequestFailures = [];
          actor.cancelledWebKitRequest = null;
          actor.expectedNavigationAbortUntil = Date.now() + 3_000;
        }
        try {
          return await action();
        } finally {
          // The pageerror event is delivered asynchronously after the load has
          // been cancelled; extend, rather than clear, the narrow grace window.
          if (tracksNavigationAbort) {
            actor.expectedNavigationAbortUntil = Date.now() + 1_500;
          }
        }
      };
      return actor;
    };
    await use(createActor);
    // Cleanup only UUID-named accounts created by this test. Never seed/reset shared data.
    const cleanupErrors = [];
    for (const [index, actor] of actors.entries()) {
      try {
        actor.reconnect?.();
        await actor.flushNavigationErrors?.();
        if (testInfo.status !== testInfo.expectedStatus || actor.errors.length) {
          await actor.page?.screenshot({ path: testInfo.outputPath(`player-${index}.png`) }).catch(() => {});
        }
        actor.closing = true;
        await actor.context?.close();
        await actor.flushNavigationErrors?.();
        if (actor.navigationRequestFailures.length || actor.errorDetails.length) {
          await testInfo.attach(`player-${index}-navigation-request-failures`, {
            body: Buffer.from(JSON.stringify(actor.navigationRequestFailures, null, 2)),
            contentType: 'application/json',
          });
        }
        if (actor.errorDetails.length) {
          await testInfo.attach(`player-${index}-browser-errors`, {
            body: Buffer.from(JSON.stringify(actor.errorDetails, null, 2)),
            contentType: 'application/json',
          });
        }
      } catch (error) { cleanupErrors.push(error); }
      try {
        const cleanupSessionResponse = await request.post(`${process.env.E2E_API_URL}/auth/login`, {
          data: {
            email: actor.account.email,
            password: actor.account.password,
            devicePlatform: 'web-e2e-cleanup',
          },
        });
        expect(cleanupSessionResponse.ok(), `Open cleanup session for ${actor.account.email}`).toBeTruthy();
        const cleanupSession = await cleanupSessionResponse.json();
        const deleted = await request.post(`${process.env.E2E_API_URL}/auth/delete-account`, {
          data: { accessToken: cleanupSession.accessToken, password: actor.account.password },
        });
        expect(deleted.ok(), `Clean up disposable account ${actor.account.email}`).toBeTruthy();
        expect(actor.errors, 'No uncaught browser errors').toEqual([]);
      } catch (error) { cleanupErrors.push(error); }
    }
    if (cleanupErrors.length) throw new AggregateError(cleanupErrors, 'E2E cleanup/browser errors');
  },
});

export const tag = (page, id) => page.getByTestId(id);

// Compose renders a canvas plus a transparent accessibility DOM in a shadow root.
// Click its real canvas position, not a JS controller or a synthetic server response.
// allowDisabled is reserved for tests asserting a real disabled canvas tap is inert.
export async function click(page, locator, { allowDisabled = false } = {}) {
  await expect(locator).toBeAttached();
  let searchDirection = 1;
  const visiblePositions = () => page.locator('[id], [role]').evaluateAll(nodes => JSON.stringify(nodes.map(node => {
    const box = node.getBoundingClientRect();
    return box.width > 0 && box.height > 0 ? [node.id || node.getAttribute('role'), Math.round(box.x), Math.round(box.y)] : null;
  }).filter(Boolean)));
  const wheelPoint = async viewport => {
    const dialogs = await tag(page, 'arcade_dialog').evaluateAll(nodes => nodes.map(node => {
      const box = node.getBoundingClientRect();
      return { x: box.x, y: box.y, width: box.width, height: box.height };
    }));
    const region = dialogs.findLast(box => box.width > 0 && box.height > 0 &&
      box.y < viewport.height && box.y + box.height > 0)
      || { x: 0, y: 0, width: viewport.width, height: viewport.height };
    const inputs = await page.locator('input, textarea').evaluateAll(nodes => nodes.map(node => {
      const box = node.getBoundingClientRect();
      return { x: box.x, y: box.y, width: box.width, height: box.height };
    }));
    const x = region.x + region.width / 2;
    for (const fraction of [0.5, 0.7, 0.3, 0.85, 0.15]) {
      const y = Math.max(1, Math.min(viewport.height - 1, region.y + region.height * fraction));
      if (!inputs.some(box => box.width > 0 && box.height > 0 &&
          x >= box.x && x <= box.x + box.width && y >= box.y && y <= box.y + box.height)) {
        return { x, y };
      }
    }
    throw new Error('No canvas scroll point outside native text inputs.');
  };
  for (let attempt = 0; attempt < 20; attempt++) {
    // Attachment alone can precede session readiness and a moving error row.
    if (!allowDisabled) await expect(locator).toBeEnabled();
    // Compose rebuilds its transparent semantics nodes while a LazyColumn is
    // settling. Resolve a short-lived handle for each attempt so a detached
    // WebKit node is retried instead of consuming the locator's full timeout.
    const handle = await locator.elementHandle({ timeout: 500 }).catch(() => null);
    const bounds = handle ? await handle.boundingBox().catch(() => null) : null;
    const viewport = page.viewportSize();
    let blockedDirection = null;
    if (bounds && bounds.width > 0 && bounds.height > 0) {
      const y = bounds.y + bounds.height / 2;
      if (y >= 0 && y < viewport.height) {
        // WebKit can briefly publish stale Compose semantics bounds while a
        // LazyColumn is settling after navigation, safe-area/font changes or
        // a wheel event. Check every click, not only previously scrolled items:
        // a transient position can otherwise target unrelated canvas content.
        await page.waitForTimeout(150);
        const stableHandle = await locator.elementHandle({ timeout: 500 }).catch(() => null);
        const stableBounds = stableHandle ? await stableHandle.boundingBox().catch(() => null) : null;
        const isStable = stableBounds &&
          stableBounds.width > 0 && stableBounds.height > 0 &&
          Math.abs(stableBounds.x - bounds.x) <= 1 &&
          Math.abs(stableBounds.y - bounds.y) <= 1 &&
          Math.abs(stableBounds.width - bounds.width) <= 1 &&
          Math.abs(stableBounds.height - bounds.height) <= 1;
        if (!isStable) continue;
        const dialog = await tag(page, 'arcade_dialog').evaluateAll(nodes => nodes.map(node => {
          const box = node.getBoundingClientRect();
          return { x: box.x, y: box.y, width: box.width, height: box.height };
        }).findLast(box => box.width > 0 && box.height > 0) || null);
        const chrome = dialog ? [] : await page.locator('#app_header, #bottom_bar').evaluateAll(nodes =>
          nodes.map(node => {
            const box = node.getBoundingClientRect();
            return { id: node.id, x: box.x, y: box.y, width: box.width, height: box.height };
          }));
        const exposure = await stableHandle.evaluate((node, { viewport, dialog, chrome, stableBounds }) => {
          const box = node.getBoundingClientRect();
          const x = box.x + box.width / 2;
          const y = box.y + box.height / 2;
          const current = {
            point: { x, y },
            moved: ['x', 'y', 'width', 'height'].some(key => Math.abs(box[key] - stableBounds[key]) > 1),
            enabled: !node.matches(':disabled') && !node.closest('[aria-disabled="true"]'),
          };
          let top = dialog?.y ?? 0;
          let bottom = dialog ? dialog.y + dialog.height : viewport.height;
          let left = dialog?.x ?? 0;
          let right = dialog ? dialog.x + dialog.width : viewport.width;
          // New Compose semantics retain offscreen bounds. Respect actual DOM
          // clipping ancestors and modal bounds before clicking the canvas.
          for (let parent = node.parentElement; parent; parent = parent.parentElement) {
            const style = getComputedStyle(parent);
            const clip = parent.getBoundingClientRect();
            if (/hidden|clip|scroll|auto/.test(style.overflowY)) {
              top = Math.max(top, clip.top);
              bottom = Math.min(bottom, clip.bottom);
            }
            if (/hidden|clip|scroll|auto/.test(style.overflowX)) {
              left = Math.max(left, clip.left);
              right = Math.min(right, clip.right);
            }
          }
          for (const region of chrome) {
            if (node.closest(`#${region.id}`) || !region.width || !region.height) continue;
            if (x >= region.x && x < region.x + region.width &&
                y >= region.y && y < region.y + region.height) {
              return { ...current, exposed: false, direction: region.id === 'app_header' ? -1 : 1 };
            }
          }
          const native = node.matches('[data-fasttowin-native-input]');
          return {
            ...current,
            exposed: x >= left && x < right && y >= top && y < bottom &&
              (!native || document.elementFromPoint(x, y) === node),
            direction: y < top ? -1 : 1,
          };
        }, { viewport, dialog, chrome, stableBounds });
        // The final exposure read may observe a layout change after both
        // protocol samples. Resample instead of clicking discarded coordinates.
        if (exposure.moved || (!allowDisabled && !exposure.enabled)) continue;
        if (!exposure.exposed) {
          blockedDirection = exposure.direction;
        } else {
          await page.mouse.click(exposure.point.x, exposure.point.y);
          return;
        }
      }
    }
    // Offscreen Compose semantics can have zero bounds or retain clipped bounds.
    // Follow the clipping direction when known; otherwise sweep in small steps
    // and reverse at a scroll boundary instead of skipping a field above us.
    if (blockedDirection !== null) searchDirection = blockedDirection;
    else if (bounds && bounds.height > 0) searchDirection = bounds.y < 0 ? -1 : 1;
    const beforeScroll = await visiblePositions();
    // Native HTML input overlays receive wheel events themselves. Choose a
    // point on the real canvas inside the dialog, outside those input bounds.
    const point = await wheelPoint(viewport);
    await page.mouse.move(point.x, point.y);
    await page.mouse.wheel(0, searchDirection * Math.min(240, viewport.height / 3));
    // Wait through both scroll animation and debounced semantics. An unchanged
    // first frame alone does not prove a boundary, especially after reversing.
    let unchanged = true;
    await expect.poll(visiblePositions, { timeout: 650, intervals: [100, 150, 200] })
      .not.toBe(beforeScroll).then(() => { unchanged = false; }).catch(() => {});
    await page.waitForTimeout(150);
    if (unchanged) searchDirection *= -1;
  }
  throw new Error('UI element could not be brought into the viewport.');
}

export async function fill(page, locator, text, nativeLabel = null) {
  const input = nativeLabel ? page.getByLabel(nativeLabel, { exact: true })
    .and(page.locator('[data-fasttowin-native-input]')) : null;
  // HtmlElementView hides a clipped editor but retains its last visible bounds.
  // Reveal the field through Compose geometry first, then focus the real input.
  await click(page, locator);
  if (input) await click(page, input);
  if (input) await expect(input).toBeFocused();
  // Use the native overlay explicitly when its label is supplied; other callers
  // focus through Compose semantics before sending real keyboard input.
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.insertText(text);
  if (input) await expect(input).toHaveValue(text);
  // Clicking the next control performs the blur. Sending Tab here makes
  // Firefox apply Compose's pending edit a second time and duplicates text.
  await page.waitForTimeout(250);
}

export async function login(actor, { onboarding = true } = {}) {
  const { page, account } = actor;
  await page.goto('/');
  await click(page, tag(page, 'auth_open_login'));
  await fill(page, tag(page, 'auth_email'), account.email);
  await fill(page, tag(page, 'auth_password'), account.password);
  // Commit the pending Compose text edit on an inert canvas edge. Refocusing
  // the email field can replay its edit in Firefox and duplicate the address.
  await page.mouse.click(4, 4);
  await expect(page.locator('input:focus')).toHaveCount(0);
  await page.waitForTimeout(250);
  const loginResponsePromise = page.waitForResponse(response =>
    response.url() === `${process.env.E2E_API_URL}/auth/login` && response.request().method() === 'POST'
  );
  await click(page, tag(page, 'auth_login_submit'));
  const loginResponse = await loginResponsePromise;
  expect(loginResponse.ok(), 'Login through the visible Web form').toBeTruthy();
  if (onboarding) await click(page, page.getByRole('button', { name: 'Bỏ qua', exact: true }));
  await expect(tag(page, 'home_screen')).toBeAttached();
  // The shell reaches Home before the authoritative profile snapshot is
  // applied. That snapshot inserts the daily check-in card and moves every
  // discovery action below it. Wait for the profile-backed layout so callers
  // never click a transient accessibility position from the pre-profile UI.
  await expect(tag(page, 'daily_check_in_card')).toBeAttached();
}

export async function selectLanguage(page, languageCode) {
  const dialog = await openLanguageDialog(page);
  const option = tag(page, `language_option_${languageCode}`);
  for (let attempt = 0; attempt < 12 && await option.count() === 0; attempt++) {
    const bounds = await dialog.boundingBox();
    if (!bounds) break;
    await page.mouse.move(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    await page.mouse.wheel(0, 320);
    await page.waitForTimeout(150);
  }
  await click(page, option);
}

export async function openLanguageDialog(page) {
  // The URL changes before WebKit finishes publishing the Compose semantics
  // tree on slower CI runners, so wait for the destination screen itself.
  await expect(tag(page, 'settings_screen')).toBeAttached({ timeout: 30_000 });
  const setting = tag(page, 'language_setting');
  // This row starts below optional install/push sections. Chromium can attach
  // it with zero bounds while only fixed header nodes have measurable geometry.
  // Scroll the known downward path until the row itself has bounds: fixed
  // header positions cannot prove that the canvas reached a scroll boundary.
  for (let attempt = 0; attempt < 12; attempt++) {
    const viewport = page.viewportSize();
    const handle = await setting.elementHandle({ timeout: 250 }).catch(() => null);
    const bounds = handle ? await handle.boundingBox().catch(() => null) : null;
    if (bounds && bounds.width > 0 && bounds.height > 0 &&
        bounds.y + bounds.height > 0 && bounds.y < viewport.height) break;
    await page.mouse.move(viewport.width / 2, viewport.height / 2);
    await page.mouse.wheel(0, Math.min(240, viewport.height / 3));
    await page.waitForTimeout(250);
  }
  await expect(setting).toBeAttached();
  const dialog = tag(page, 'language_dialog');
  // A successful canvas click opens the dialog before WebKit publishes the
  // dialog semantics node. Retrying the click during that gap targets the
  // now-covered setting row and can scroll the modal instead. Click once and
  // wait for the resulting state transition.
  await click(page, setting);
  await expect(dialog).toBeAttached({ timeout: 5_000 });
  return dialog;
}

export async function createRoom(actor) {
  const page = actor.page;
  await click(page, page.getByRole('tab', { name: 'Phòng', exact: true }));
  await expect(page).toHaveURL(/\/rooms$/);
  await click(page, tag(page, 'create_room_open'));
  await click(page, tag(page, 'match_type:CASUAL'));
  await click(page, tag(page, 'game_mode:ORDER'));
  const name = `E2E-${randomUUID().slice(0, 8)}`;
  await fill(page, tag(page, 'create_room_name'), name, 'Tên phòng');
  await click(page, tag(page, 'create_room_submit'));
  await expect(page).toHaveURL(/\/room\/[\w-]+$/);
  const roomURL = page.url();
  // Also exercise F5 with an existing waiting room (no duplicate room creation).
  await page.reload();
  await expect(page.getByText(name, { exact: true })).toBeAttached();
  await expect(page.getByRole('button', { name: 'SẴN SÀNG', exact: true })).toBeAttached();
  return { name, url: roomURL };
}

export async function joinAndStart(host, guest, room) {
  await guest.page.goto(room.url);
  await expect(guest.page.getByText(room.name, { exact: true })).toBeAttached();
  await click(guest.page, guest.page.getByRole('button', { name: 'SẴN SÀNG', exact: true }));
  await click(host.page, host.page.getByRole('button', { name: 'SẴN SÀNG', exact: true }));
  for (const actor of [host, guest]) {
    await expect(tag(actor.page, 'game_screen')).toBeAttached();
    await expect(tag(actor.page, 'game_number_1')).toBeAttached();
  }
}

export async function selectNumber(actor, opponent, number) {
  await click(actor.page, tag(actor.page, `game_number_${number}`));
  if (number < 50) {
    for (const player of [actor, opponent]) {
      await expect(player.page.getByText(`${number}/50`, { exact: true })).toBeAttached();
    }
  } else {
    for (const player of [actor, opponent]) await expect(tag(player.page, 'result_screen')).toBeAttached();
  }
}
