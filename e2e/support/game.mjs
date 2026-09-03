import { randomUUID } from 'node:crypto';
import { test as base, expect } from 'playwright/test';

export { expect };
export const test = base.extend({
  actors: async ({ browser, request, baseURL }, use, testInfo) => {
    const actors = [];
    const createActor = async (label = 'Player') => {
      const suffix = randomUUID().replaceAll('-', '').slice(0, 12);
      const shortLabel = label.replace(/[^a-z0-9]/gi, '').slice(0, 8) || 'Player';
      const account = { email: `e2e-${suffix}@example.invalid`, password: `Test-${suffix}-9!`,
        displayName: `E2E ${shortLabel} ${suffix.slice(0, 4)}`, devicePlatform: 'web-e2e', gender: 'MALE' };
      const registered = await request.post(`${process.env.E2E_API_URL}/auth/register`, { data: account });
      expect(registered.status(), 'Create only this test account').toBe(201);
      const session = await registered.json();
      const actor = {
        account,
        accessToken: session.accessToken,
        errors: [],
        expectedNavigationAbort: false,
        blocked: false,
        sockets: new Set(),
      };
      actors.push(actor);
      const context = await browser.newContext({
        baseURL, viewport: testInfo.project.use.viewport, locale: 'vi-VN', serviceWorkers: 'block',
      });
      actor.context = context;
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
        // WebKit reports an in-flight fetch cancelled by an explicit reload/
        // history navigation as a generic page error. Ignore it only while the
        // test is performing that navigation; real fetch errors still fail.
        if (actor.expectedNavigationAbort && error.message === 'Load failed') return;
        actor.errors.push(error.message);
      });
      actor.navigate = async action => {
        actor.expectedNavigationAbort = testInfo.project.use.browserName === 'webkit';
        try {
          return await action();
        } finally {
          await actor.page.waitForTimeout(250);
          actor.expectedNavigationAbort = false;
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
        const deleted = await request.post(`${process.env.E2E_API_URL}/auth/delete-account`, {
          data: { accessToken: actor.accessToken, password: actor.account.password },
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
  for (let attempt = 0; attempt < 12; attempt++) {
    const bounds = await locator.boundingBox();
    const viewport = page.viewportSize();
    if (bounds && bounds.width > 0 && bounds.height > 0) {
      const y = bounds.y + bounds.height / 2;
      if (y >= 0 && y < viewport.height) {
        await page.mouse.click(bounds.x + bounds.width / 2, y);
        return;
      }
    }
    // Offscreen Compose semantics can have a zero-sized box until the canvas scrolls.
    await page.mouse.move(viewport.width / 2, viewport.height / 2);
    await page.mouse.wheel(0, bounds && bounds.y < 0 ? -450 : 450);
    await page.waitForTimeout(150); // Allow Compose's debounced semantics/layout sync after scroll.
  }
  throw new Error('UI element could not be brought into the viewport.');
}

export async function fill(page, locator, text) {
  await click(page, locator);
  // Focusing Compose replaces the semantics div with a backing native input.
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.insertText(text);
  await page.keyboard.press('Tab');
  // Compose replaces the backing input after blur and rebuilds its accessibility
  // nodes on a debounce. Wait before using the next node's canvas coordinates.
  await page.waitForTimeout(250);
}

export async function login(actor) {
  const { page, account } = actor;
  await page.goto('/');
  await click(page, tag(page, 'auth_open_login'));
  await fill(page, tag(page, 'auth_email'), account.email);
  await fill(page, tag(page, 'auth_password'), account.password);
  const loginResponsePromise = page.waitForResponse(response =>
    response.url() === `${process.env.E2E_API_URL}/auth/login` && response.request().method() === 'POST'
  );
  await click(page, tag(page, 'auth_login_submit'));
  const loginResponse = await loginResponsePromise;
  expect(loginResponse.ok(), 'Login through the visible Web form').toBeTruthy();
  actor.accessToken = (await loginResponse.json()).accessToken;
  await click(page, page.getByRole('button', { name: 'Bỏ qua', exact: true }));
  await expect(tag(page, 'home_screen')).toBeAttached();
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
