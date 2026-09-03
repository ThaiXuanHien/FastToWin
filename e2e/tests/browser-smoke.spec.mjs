import { test, expect, tag, click, login } from '../support/game.mjs';

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
