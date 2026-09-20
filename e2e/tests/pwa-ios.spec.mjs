import { test, expect, tag, click, login } from '../support/game.mjs';
import { readFile } from 'node:fs/promises';

test('iOS standalone safe areas reserve content space without exposing a black frame', async ({ page }) => {
  const styles = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/styles.css', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>${styles}</style>
    <main id="fastToWinRoot" aria-label="Fast To Win">
      <canvas style="width: 100%; height: 100%"></canvas>
    </main>
  `);
  const root = page.locator('#fastToWinRoot');
  await expect(root).toBeAttached();

  await root.evaluate(element => {
    element.style.setProperty('--fast-to-win-safe-top', '47px');
    element.style.setProperty('--fast-to-win-safe-right', '0px');
    element.style.setProperty('--fast-to-win-safe-bottom', '34px');
    element.style.setProperty('--fast-to-win-safe-left', '0px');
  });

  await expect(root).toHaveCSS('padding-top', '47px');
  await expect(root).toHaveCSS('padding-bottom', '34px');
  await expect(root).toHaveCSS('background-color', 'rgb(7, 26, 68)');

  const viewport = page.viewportSize();
  const rootBounds = await root.boundingBox();
  const canvasBounds = await page.locator('canvas').first().boundingBox();
  expect(rootBounds).toEqual({ x: 0, y: 0, width: viewport.width, height: viewport.height });
  expect(canvasBounds.y).toBeGreaterThanOrEqual(47);
  expect(canvasBounds.y + canvasBounds.height).toBeLessThanOrEqual(viewport.height - 34);
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
