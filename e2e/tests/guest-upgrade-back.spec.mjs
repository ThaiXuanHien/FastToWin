import { test, expect, tag, click, login } from '../support/game.mjs';

async function logOutToWelcome(page) {
  await click(page, tag(page, 'profile_logout'));
  await click(page, tag(page, 'confirm_logout'));
  await click(page, page.getByRole('button', { name: 'Quay lại', exact: true }));
  await expect(tag(page, 'auth_open_login')).toBeAttached();
}

async function expectUpgrade(page) {
  await expect(page.getByRole('button', { name: 'LƯU VÀ TẠO TÀI KHOẢN', exact: true })).toBeAttached();
}

test('guest upgrade cancellation escapes the account route without adding history', async ({ actors }, testInfo) => {
  const player = await actors('Cancel upgrade');
  const { page } = player;
  await login(player);
  // Recreate the app on this path: the old bridge kept this initial snapshot
  // even after logout and later URL replacements.
  await player.navigate(() => page.goto('/account'));
  await expect(tag(page, 'profile_settings')).toBeAttached();
  await logOutToWelcome(page);
  await click(page, page.getByRole('button', { name: 'Chơi với tư cách khách', exact: true }));
  await expectUpgrade(page);
  const historyLength = await page.evaluate(() => history.length);

  await click(page, page.getByRole('button', { name: 'Quay lại', exact: true }));

  await expect(page).toHaveURL(/\/$/);
  await expect(tag(page, 'home_screen')).toBeAttached();
  await expect(page.getByRole('button', { name: 'LƯU VÀ TẠO TÀI KHOẢN', exact: true })).not.toBeAttached();
  expect(await page.evaluate(() => history.length)).toBe(historyLength);
  await page.screenshot({ path: testInfo.outputPath('cancel-upgrade-home.png') });

  await player.navigate(() => page.reload());
  await click(page, page.getByRole('button', { name: 'Chơi với tư cách khách', exact: true }));
  await expect(tag(page, 'home_screen')).toBeAttached();
  await expect(page).toHaveURL(/\/$/);
});

test('guest upgrade cancellation preserves Rooms when opened from Rooms', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto('/rooms');
  await click(page, page.getByRole('button', { name: 'Chơi với tư cách khách', exact: true }));
  await click(page, tag(page, 'ugc_terms_checkbox'));
  await click(page, tag(page, 'ugc_terms_accept'));
  await expect(tag(page, 'room_list')).toBeAttached();
  await click(page, page.getByRole('button', { name: 'LƯU TIẾN TRÌNH BẰNG EMAIL', exact: true }));
  await expectUpgrade(page);
  const historyLength = await page.evaluate(() => history.length);

  await click(page, page.getByRole('button', { name: 'Quay lại', exact: true }));

  await expect(tag(page, 'room_list')).toBeAttached();
  await expect(page).toHaveURL(/\/rooms$/);
  expect(await page.evaluate(() => history.length)).toBe(historyLength);
  await page.screenshot({ path: testInfo.outputPath('cancel-upgrade-rooms-desktop.png') });
});

test('guest upgrade cancellation respects browser Back while the form is open', async ({ actors }) => {
  const player = await actors('Latest upgrade route');
  const { page } = player;
  await login(player);
  await player.navigate(() => page.goto('/account'));
  await expect(tag(page, 'profile_settings')).toBeAttached();
  await click(page, tag(page, 'bottom_tab:rooms'));
  await expect(tag(page, 'room_list')).toBeAttached();
  await click(page, tag(page, 'bottom_tab:account'));
  await expect(tag(page, 'profile_settings')).toBeAttached();
  await logOutToWelcome(page);
  await click(page, page.getByRole('button', { name: 'Chơi với tư cách khách', exact: true }));
  await expectUpgrade(page);

  await player.navigate(() => page.goBack());
  await expect(page).toHaveURL(/\/rooms$/);
  const historyLength = await page.evaluate(() => history.length);
  await click(page, page.getByRole('button', { name: 'Quay lại', exact: true }));

  await expect(tag(page, 'room_list')).toBeAttached();
  await expect(page.getByRole('button', { name: 'LƯU VÀ TẠO TÀI KHOẢN', exact: true })).not.toBeAttached();
  await expect(page).toHaveURL(/\/rooms$/);
  expect(await page.evaluate(() => history.length)).toBe(historyLength);
});
