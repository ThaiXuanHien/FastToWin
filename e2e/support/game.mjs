import { randomUUID } from 'node:crypto';
import { test as base, expect } from 'playwright/test';
import { isExpectedNavigationAbort } from './navigation-errors.mjs';

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
        expectedNavigationAbortUntil: 0,
        blocked: false,
        sockets: new Set(),
      };
      actors.push(actor);
      const context = await browser.newContext({
        baseURL,
        viewport: testInfo.project.use.viewport,
        locale: 'vi-VN',
        serviceWorkers: 'block',
        hasTouch: testInfo.project.use.hasTouch ?? false,
      });
      actor.context = context;
      if (options.preferences) {
        await context.addInitScript(preferences => {
          window.localStorage.setItem('fasttowin.preferences', JSON.stringify(preferences));
        }, options.preferences);
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
        });
        if (expectedNavigationAbort) return;
        actor.errors.push(message);
      });
      actor.navigate = async action => {
        const tracksNavigationAbort = ['webkit', 'firefox'].includes(testInfo.project.use.browserName);
        if (tracksNavigationAbort) {
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
        if (testInfo.status !== testInfo.expectedStatus || actor.errors.length) {
          await actor.page?.screenshot({ path: testInfo.outputPath(`player-${index}.png`) }).catch(() => {});
        }
        await actor.context?.close();
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
export async function click(page, locator) {
  await expect(locator).toBeAttached();
  let scrolled = false;
  for (let attempt = 0; attempt < 12; attempt++) {
    // Compose rebuilds its transparent semantics nodes while a LazyColumn is
    // settling. Resolve a short-lived handle for each attempt so a detached
    // WebKit node is retried instead of consuming the locator's full timeout.
    const handle = await locator.elementHandle({ timeout: 500 }).catch(() => null);
    const bounds = handle ? await handle.boundingBox().catch(() => null) : null;
    const viewport = page.viewportSize();
    if (bounds && bounds.width > 0 && bounds.height > 0) {
      const y = bounds.y + bounds.height / 2;
      if (y >= 0 && y < viewport.height) {
        // WebKit can briefly publish stale Compose semantics bounds while a
        // LazyColumn is still settling after a wheel event. Clicking that
        // transient position targets unrelated canvas content. Require the
        // offscreen item to keep the same bounds across two layout frames.
        if (scrolled) {
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
        }
        await page.mouse.click(bounds.x + bounds.width / 2, y);
        return;
      }
    }
    // Offscreen Compose semantics can have a zero-sized box until the canvas scrolls.
    await page.mouse.move(viewport.width / 2, viewport.height / 2);
    await page.mouse.wheel(0, bounds && bounds.y < 0 ? -450 : 450);
    scrolled = true;
    await page.waitForTimeout(150); // Allow Compose's debounced semantics/layout sync after scroll.
  }
  throw new Error('UI element could not be brought into the viewport.');
}

export async function fill(page, locator, text) {
  await click(page, locator);
  // Focusing Compose replaces the semantics div with a backing native input.
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.insertText(text);
  // Clicking the next control performs the blur. Sending Tab here makes
  // Firefox apply Compose's pending edit a second time and duplicates text.
  await page.waitForTimeout(250);
}

export async function login(actor) {
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
  await click(page, page.getByRole('button', { name: 'Bỏ qua', exact: true }));
  await expect(tag(page, 'home_screen')).toBeAttached();
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
  // WebKit exposes only the currently materialized portion of Compose's
  // accessibility tree. The language row sits below optional install/push
  // sections, so reveal it before asking the generic click helper for bounds.
  for (let attempt = 0; attempt < 12 && await setting.count() === 0; attempt++) {
    const viewport = page.viewportSize();
    await page.mouse.move(viewport.width / 2, viewport.height / 2);
    await page.mouse.wheel(0, 450);
    await page.waitForTimeout(150);
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
  await click(page, page.getByRole('button', { name: 'Phòng', exact: true }));
  await expect(page).toHaveURL(/\/rooms$/);
  await click(page, tag(page, 'create_room_open'));
  await click(page, tag(page, 'match_type:CASUAL'));
  await click(page, tag(page, 'game_mode:ORDER'));
  const name = `E2E-${randomUUID().slice(0, 8)}`;
  await fill(page, tag(page, 'create_room_name'), name);
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
