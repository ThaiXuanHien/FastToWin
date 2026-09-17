import { test, expect, tag, click, login, openLanguageDialog, selectLanguage } from '../support/game.mjs';

async function expectHorizontalFit(page, locator, label) {
  await expect(locator, `${label} is rendered`).toBeAttached();
  const bounds = await locator.boundingBox();
  const viewport = page.viewportSize();
  expect(bounds, `${label} exposes layout bounds`).not.toBeNull();
  expect(bounds.x, `${label} does not overflow left`).toBeGreaterThanOrEqual(-1);
  expect(bounds.x + bounds.width, `${label} does not overflow right`).toBeLessThanOrEqual(viewport.width + 1);
}

async function renderedPixelAt(page, x, y) {
  const screenshot = await page.screenshot({
    clip: { x, y, width: 1, height: 1 },
  });
  return page.evaluate(async encodedPixel => {
    const image = new Image();
    image.src = `data:image/png;base64,${encodedPixel}`;
    await image.decode();
    const canvas = document.createElement('canvas');
    canvas.width = 1;
    canvas.height = 1;
    const context = canvas.getContext('2d');
    context.drawImage(image, 0, 0);
    return Array.from(context.getImageData(0, 0, 1, 1).data);
  }, screenshot.toString('base64'));
}

test('top-level navigation and room creation fit the configured viewport', async ({ actors }, testInfo) => {
  const player = await actors(`Responsive ${testInfo.project.name}`);
  const { page } = player;
  await login(player);

  const viewport = page.viewportSize();
  const canvas = page.locator('canvas').first();
  const canvasBounds = await canvas.boundingBox();
  expect(canvasBounds, 'Compose canvas is visible').not.toBeNull();
  expect(canvasBounds.width, 'Compose canvas covers the browser viewport').toBeLessThanOrEqual(viewport.width + 1);
  const appBounds = await tag(page, 'home_screen').boundingBox();
  expect(appBounds, 'Home content exposes layout bounds').not.toBeNull();
  expect(appBounds.width, 'Web app keeps the approved mobile content width').toBeLessThanOrEqual(Math.min(430, viewport.width) + 1);
  expect(Math.abs((appBounds.x + appBounds.width / 2) - viewport.width / 2), 'App content is horizontally centered').toBeLessThanOrEqual(2);

  const overflow = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    body: document.body.scrollWidth - document.body.clientWidth,
  }));
  expect(overflow.document, 'Document has no horizontal overflow').toBeLessThanOrEqual(0);
  expect(overflow.body, 'Body has no horizontal overflow').toBeLessThanOrEqual(0);

  await expectHorizontalFit(page, tag(page, 'bottom_bar'), 'Bottom navigation');
  for (const tabName of ['home', 'rooms', 'leaderboard', 'clan', 'account']) {
    await expectHorizontalFit(page, tag(page, `bottom_tab:${tabName}`), `Bottom tab ${tabName}`);
  }

  await click(page, tag(page, 'bottom_tab:account'));
  await expect(page).toHaveURL(/\/account$/);
  await expectHorizontalFit(page, tag(page, 'profile_identity_card'), 'Profile identity card');

  await click(page, tag(page, 'bottom_tab:rooms'));
  await expect(page).toHaveURL(/\/rooms$/);
  await expectHorizontalFit(page, tag(page, 'create_room_open'), 'Create room action');
  await click(page, tag(page, 'create_room_open'));
  await click(page, tag(page, 'match_type:CASUAL'));
  await click(page, tag(page, 'game_mode:ORDER'));
  await expectHorizontalFit(page, tag(page, 'create_room_name'), 'Create room name field');
  await expectHorizontalFit(page, tag(page, 'create_room_submit'), 'Create room submit action');
});

test('web shell stays dark outside the centered mobile canvas', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/');
  await expect(page.locator('#fastToWinRoot')).toBeAttached();

  await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(7, 24, 36)');
  const root = await page.locator('#fastToWinRoot').boundingBox();
  expect(root).not.toBeNull();
  expect(root.width).toBe(1440);
  await expect(page.locator('#fastToWinRoot')).toHaveCSS('position', 'fixed');
  expect(root.y, 'safe-area root starts inside the visual viewport').toBeGreaterThanOrEqual(0);
  expect(root.y + root.height, 'safe-area root ends inside the visual viewport').toBeLessThanOrEqual(900);

  // The CSS fallback prevents a white flash while Compose starts. Once the
  // canvas paints it uses Navy950, so either intentional dark shell colour is
  // valid at this boundary; a light/white regression still fails this test.
  const expectedShellPixels = [
    [7, 24, 36, 255], // styles.css: #071824
    [6, 19, 47, 255], // ArcadePalette.Navy950: #06132F
  ];
  expect(expectedShellPixels, 'left rendered gutter is navy')
    .toContainEqual(await renderedPixelAt(page, 10, 10));
  expect(expectedShellPixels, 'right rendered gutter is navy')
    .toContainEqual(await renderedPixelAt(page, 1430, 10));
});

test('dialog stays within ten-pixel insets on a narrow viewport', async ({ actors }) => {
  const player = await actors('Narrow dialog');
  const { page } = player;
  await page.setViewportSize({ width: 320, height: 568 });
  await login(player);
  await click(page, tag(page, 'bottom_tab:account'));
  await click(page, tag(page, 'profile_settings'));
  await openLanguageDialog(page);

  const dialog = await tag(page, 'arcade_dialog').boundingBox();
  expect(dialog).not.toBeNull();
  expect(dialog.x).toBeGreaterThanOrEqual(10);
  expect(dialog.x + dialog.width).toBeLessThanOrEqual(310);
});

test('language change keeps the active route and route history', async ({ actors }, testInfo) => {
  test.skip(testInfo.project.name === 'chromium-landscape', 'Compact landscape Settings scrolling is covered by the layout test.');
  const player = await actors(`Language route ${testInfo.project.name}`);
  const { page } = player;
  await login(player);

  await click(page, tag(page, 'bottom_tab:rooms'));
  await expect(page).toHaveURL(/\/rooms$/);
  await click(page, tag(page, 'bottom_tab:account'));
  await expect(page).toHaveURL(/\/account$/);
  await click(page, tag(page, 'profile_settings'));
  await expect(page).toHaveURL(/\/settings$/);

  await selectLanguage(page, 'en');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page).toHaveURL(/\/settings$/);

  await player.navigate(() => page.goBack());
  await expect(page).toHaveURL(/\/account$/);
  await player.navigate(() => page.goBack());
  await expect(page).toHaveURL(/\/rooms$/);
});

test('browser Back followed by immediate app navigation publishes the new route', async ({ actors }) => {
  const player = await actors('Back then navigate');
  const { page } = player;
  await login(player);

  await click(page, tag(page, 'bottom_tab:rooms'));
  await click(page, tag(page, 'bottom_tab:account'));
  await player.navigate(() => page.goBack());
  await click(page, tag(page, 'bottom_tab:leaderboard'));

  await expect(page).toHaveURL(/\/leaderboard$/);
  await expect(tag(page, 'leaderboard_screen')).toBeAttached();
});

test('invalid friend profile route is canonicalized without a stale address', async ({ actors }) => {
  const player = await actors('Invalid friend route');
  const { page } = player;
  await login(player);

  await page.evaluate(() => {
    history.pushState(
      { ...(history.state || {}), fastToWinDepth: 1 },
      '',
      '/friends/missing-player',
    );
    window.dispatchEvent(new PopStateEvent('popstate'));
  });

  await expect(tag(page, 'friends_screen')).toBeAttached();
  await expect(page).toHaveURL(/\/friends$/);
});
