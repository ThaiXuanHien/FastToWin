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
  await selectLanguage(page, 'en');

  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await page.waitForLoadState('networkidle');
  await player.navigate(() => page.reload());
  await expect(page).toHaveURL(/\/settings$/);
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(tag(page, 'app_header').getByText('Settings', { exact: true })).toBeAttached();
  await page.waitForLoadState('networkidle');
});

for (const legacy of [
  { themeMode: 'LIGHT', width: 375, height: 812 },
  { themeMode: 'SYSTEM', width: 1280, height: 900 },
]) {
  test(`legacy ${legacy.themeMode} preferences keep other settings with Dark-only UI`, async ({ actors }, testInfo) => {
    const player = await actors('Dark migration');
    await player.context.addInitScript(preferences => {
      // Seed only the first visit, so reload tests the user's saved settings
      // instead of replacing them with the legacy fixture again.
      if (localStorage.getItem('fasttowin.preferences') === null) {
        localStorage.setItem('fasttowin.preferences', JSON.stringify(preferences));
      }
    }, {
      themeMode: legacy.themeMode, languageCode: 'vi', boardStyle: 'OCEAN',
      fontScale: 'LARGE', soundEnabled: false, vibrationEnabled: false,
    });
    const { page } = player;
    await page.setViewportSize({ width: legacy.width, height: legacy.height });
    await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' });
    await login(player);
    await click(page, tag(page, 'bottom_tab:account'));
    await click(page, tag(page, 'profile_settings'));
    await expect(tag(page, 'settings_screen')).toBeAttached();
    // Open the retained language control to bring the appearance section into
    // Compose's visible semantics tree before checking that its theme row is gone.
    await selectLanguage(page, 'en');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.getByText('App theme', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Light', { exact: true })).toHaveCount(0);
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('fasttowin.preferences')));
    expect(saved.themeMode).toBe('DARK');
    expect(saved.boardStyle).toBe('OCEAN');
    expect(saved.fontScale).toBe('LARGE');
    expect(saved.soundEnabled).toBe(false);
    expect(saved.vibrationEnabled).toBe(false);
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: testInfo.outputPath('dark-only-settings.png') });
    await player.navigate(() => page.reload());
    await expect(tag(page, 'settings_screen')).toBeAttached();
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await page.waitForLoadState('networkidle');
  });
}
