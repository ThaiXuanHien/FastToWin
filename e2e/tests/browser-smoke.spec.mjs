import { test, expect, tag, click, login, selectLanguage } from '../support/game.mjs';

test('login, reload and top-level browser navigation work', async ({ actors }) => {
  const player = await actors('Browser smoke');
  await login(player);
  await player.page.waitForLoadState('networkidle');
  await player.navigate(() => player.page.reload());
  await expect(tag(player.page, 'home_screen')).toBeAttached();
  await player.page.waitForLoadState('networkidle');
  await click(player.page, tag(player.page, 'bottom_tab:rooms'));
  await expect(player.page).toHaveURL(/\/rooms$/);
  await expect(tag(player.page, 'create_room_open')).toBeAttached();
  await player.page.waitForLoadState('networkidle');
  await player.navigate(() => player.page.goBack());
  await expect(tag(player.page, 'home_screen')).toBeAttached();
  await player.page.waitForLoadState('networkidle');
  await player.navigate(() => player.page.goForward());
  await expect(player.page).toHaveURL(/\/rooms$/);
});

test('language selection persists across reload and updates html lang', async ({ actors }) => {
  const player = await actors('Language persistence');
  const { page } = player;
  await login(player);

  await click(page, tag(page, 'bottom_tab:account'));
  await click(page, tag(page, 'profile_settings'));
  await expect(page).toHaveURL(/\/settings$/);
  await selectLanguage(page, 'ja');

  await expect(page.locator('html')).toHaveAttribute('lang', 'ja');
  await player.navigate(() => page.reload());
  await expect(page).toHaveURL(/\/settings$/);
  await expect(page.locator('html')).toHaveAttribute('lang', 'ja');
  await expect(tag(page, 'app_header').getByText('設定', { exact: true })).toBeAttached();
});
