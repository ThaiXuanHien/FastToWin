import { test, expect, tag, click, fill, login } from '../support/game.mjs';

const largeTextPreferences = {
  soundEnabled: false,
  vibrationEnabled: false,
  visualEffectsEnabled: false,
  themeMode: 'DARK',
  boardStyle: 'CLASSIC',
  fontScale: 'LARGE',
  hasCompletedTutorial: false,
};

async function expectHorizontalFit(page, locator, label) {
  await expect(locator, `${label} is rendered`).toBeAttached();
  const bounds = await locator.boundingBox();
  const viewport = page.viewportSize();
  expect(bounds, `${label} exposes layout bounds`).not.toBeNull();
  expect(bounds.x, `${label} does not overflow left`).toBeGreaterThanOrEqual(-1);
  expect(bounds.x + bounds.width, `${label} does not overflow right`).toBeLessThanOrEqual(viewport.width + 1);
}

async function expectNoDocumentOverflow(page) {
  const overflow = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    body: document.body.scrollWidth - document.body.clientWidth,
  }));
  expect(overflow.document, 'Document has no horizontal overflow').toBeLessThanOrEqual(0);
  expect(overflow.body, 'Body has no horizontal overflow').toBeLessThanOrEqual(0);
}

test('large text, long content and a compact keyboard viewport remain usable', async ({ actors }) => {
  const displayName = 'Người chơi có biệt danh rất dài';
  const roomName = 'Phòng thử thách với tên rất dài';
  const player = await actors('Adaptive input', { displayName, preferences: largeTextPreferences });
  const { page } = player;
  await login(player);

  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('fasttowin.preferences')).fontScale))
    .toBe('LARGE');
  await expectHorizontalFit(page, tag(page, 'app_header'), 'Large-text header');
  await expectNoDocumentOverflow(page);

  await click(page, tag(page, 'bottom_tab:account'));
  await expect(page).toHaveURL(/\/account$/);
  await expect(page.getByText(displayName, { exact: true })).toBeAttached();
  await expectHorizontalFit(page, tag(page, 'profile_identity_card'), 'Long-name identity card');

  await click(page, tag(page, 'bottom_tab:rooms'));
  await click(page, tag(page, 'create_room_open'));
  await click(page, tag(page, 'match_type:CASUAL'));
  await click(page, tag(page, 'game_mode:ORDER'));

  // Mobile browsers reduce the visual viewport while the software keyboard is
  // open. Resize after opening the dialog, then prove the field and action can
  // still be reached and used without horizontal overflow.
  await page.setViewportSize({ width: 320, height: 360 });
  await fill(page, tag(page, 'create_room_name'), roomName);
  await expect(page.locator('input:focus')).toHaveValue(roomName);
  await expectHorizontalFit(page, tag(page, 'create_room_name'), 'Focused room-name field');
  await expectNoDocumentOverflow(page);
  await click(page, tag(page, 'create_room_submit'));
  await expect(page).toHaveURL(/\/room\/[\w-]+$/);
  await expect(page.getByText(roomName, { exact: true })).toBeAttached();
});
