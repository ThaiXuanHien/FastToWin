import { test, expect, tag, click, login, selectLanguage } from '../support/game.mjs';

async function expectHorizontalFit(page, locator, label) {
  await expect(locator, `${label} is rendered`).toBeAttached();
  const bounds = await locator.boundingBox();
  const viewport = page.viewportSize();
  expect(bounds, `${label} exposes layout bounds`).not.toBeNull();
  expect(bounds.x, `${label} does not overflow left`).toBeGreaterThanOrEqual(-1);
  expect(bounds.x + bounds.width, `${label} does not overflow right`).toBeLessThanOrEqual(viewport.width + 1);
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

test('language change keeps the active route and route history', async ({ actors }, testInfo) => {
  test.skip(testInfo.project.name === 'chromium-landscape', 'Compact landscape Settings scrolling is covered by the layout test.');
  const player = await actors(`Language route ${testInfo.project.name}`);
  const { page } = player;
  await login(player);

  await click(page, tag(page, 'bottom_tab:rooms'));
  await expect(page).toHaveURL(/\/rooms$/);
  await click(page, tag(page, 'bottom_tab:account'));
  await click(page, tag(page, 'profile_settings'));
  await expect(page).toHaveURL(/\/settings$/);

  await selectLanguage(page, 'de');
  await expect(page.locator('html')).toHaveAttribute('lang', 'de');
  await expect(page).toHaveURL(/\/settings$/);

  await player.navigate(() => page.goBack());
  await expect(page).toHaveURL(/\/account$/);
  await player.navigate(() => page.goBack());
  await expect(page).toHaveURL(/\/rooms$/);
});
