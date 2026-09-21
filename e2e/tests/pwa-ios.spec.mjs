import { test, expect, tag, click, login } from '../support/game.mjs';
import { readFile } from 'node:fs/promises';

test('iOS standalone keeps the Compose viewport edge to edge', async ({ page }) => {
  const styles = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/styles.css', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>${styles}</style>
    <div id="fastToWinSafeAreaProbe" aria-hidden="true"></div>
    <main id="fastToWinRoot" aria-label="Fast To Win">
      <div id="composeScene" style="position: relative; width: 100%; height: 847.5px"></div>
    </main>
  `);
  const root = page.locator('#fastToWinRoot');
  await expect(root).toBeAttached();

  await page.locator('html').evaluate(element => {
    element.style.setProperty('--fast-to-win-safe-top', '47px');
    element.style.setProperty('--fast-to-win-safe-right', '0px');
    element.style.setProperty('--fast-to-win-safe-bottom', '34px');
    element.style.setProperty('--fast-to-win-safe-left', '0px');
    window.dispatchEvent(new Event('resize'));
  });

  await expect(root).toHaveCSS('background-color', 'rgb(6, 19, 47)');
  await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(6, 19, 47)');
  await expect(page.locator('#fastToWinSafeAreaProbe')).toHaveCSS('padding-top', '47px');
  await expect(page.locator('#fastToWinSafeAreaProbe')).toHaveCSS('padding-bottom', '34px');

  const viewport = page.viewportSize();
  const rootBounds = await root.boundingBox();
  expect(rootBounds).toEqual({
    x: 0,
    y: 0,
    width: viewport.width,
    height: viewport.height,
  });
  await expect(page.locator('#composeScene')).toHaveCSS('transform', 'none');
});

test('iOS standalone keeps real header and bottom bar inside the safe area', async ({ actors }) => {
  const player = await actors('iOS standalone controls', { iosStandalone: true });
  const { page } = player;
  await login(player);

  await page.locator('html').evaluate(element => {
    element.style.setProperty('--fast-to-win-safe-top', '47px');
    element.style.setProperty('--fast-to-win-safe-right', '0px');
    element.style.setProperty('--fast-to-win-safe-bottom', '34px');
    element.style.setProperty('--fast-to-win-safe-left', '0px');
    window.dispatchEvent(new Event('resize'));
  });

  await expect(tag(page, 'app_header')).toBeAttached();
  await expect(tag(page, 'bottom_bar')).toBeAttached();
  const viewport = page.viewportSize();
  const rootBounds = await page.locator('#fastToWinRoot').boundingBox();
  const headerBounds = await tag(page, 'app_header').boundingBox();
  const bottomBarBounds = await tag(page, 'bottom_bar').boundingBox();

  expect(rootBounds.y).toBe(0);
  expect(rootBounds.height).toBe(viewport.height);
  expect(headerBounds.y, 'header stays below the iOS status area').toBeGreaterThanOrEqual(47);
  expect(
    bottomBarBounds.y + bottomBarBounds.height,
    'bottom bar stays above the iOS home-indicator area',
  ).toBeLessThanOrEqual(viewport.height - 34);
});

test('iOS standalone uses only in-app Back and does not create swipe history entries', async ({ actors }) => {
  const player = await actors('iOS standalone history', { iosStandalone: true });
  const { page } = player;
  await login(player);

  const initialHistory = await page.evaluate(() => ({
    length: history.length,
    depth: history.state?.fastToWinDepth ?? 0,
  }));

  await click(page, tag(page, 'bottom_tab:account'));
  await expect(page).toHaveURL(/\/account$/);
  await click(page, tag(page, 'profile_settings'));
  await expect(tag(page, 'settings_screen')).toBeAttached();
  await expect(page).toHaveURL(/\/settings$/);

  const nestedHistory = await page.evaluate(() => ({
    length: history.length,
    depth: history.state?.fastToWinDepth ?? 0,
  }));
  expect(nestedHistory).toEqual(initialHistory);

  await click(page, page.getByRole('button', { name: 'Quay lại', exact: true }));
  await expect(tag(page, 'profile_identity_card')).toBeAttached();
  await expect(page).toHaveURL(/\/account$/);
  expect(await page.evaluate(() => history.length)).toBe(initialHistory.length);
});
