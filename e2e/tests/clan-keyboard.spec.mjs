import { test, expect, tag, click, login } from '../support/game.mjs';

for (const label of ['Tên bang', 'Mô tả bang hội']) {
  test(`clan creation keeps ${label} focused while the PWA keyboard opens`, async ({ actors }, testInfo) => {
    const player = await actors('Clan keyboard', { iosStandalone: true });
    const { page } = player;
    const initialHeight = page.viewportSize().height;
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.addInitScript(() => {
      const viewport = window.visualViewport;
      let height = window.innerHeight;
      Object.defineProperty(window, 'outerHeight', { configurable: true, get: () => window.innerHeight });
      Object.defineProperty(window, 'visualViewport', { configurable: true, value: {
        get height() { return height; }, get width() { return window.innerWidth; },
        offsetTop: 0, offsetLeft: 0, pageTop: 0, pageLeft: 0, scale: 1,
        addEventListener(...args) { viewport.addEventListener(...args); },
        removeEventListener(...args) { viewport.removeEventListener(...args); },
      } });
      window.__setClanKeyboardHeight = next => {
        height = next;
        viewport.dispatchEvent(new Event('resize'));
      };
    });
    await login(player);
    await click(page, tag(page, 'bottom_tab:clan'));
    const search = page.locator('[data-fasttowin-native-input][aria-label="Tìm bang hội"]');
    await search.fill('Giữ truy vấn');
    await click(page, tag(page, 'open_create_clan'));
    const input = page.locator(`[data-fasttowin-native-input][aria-label="${label}"]`);
    await expect(input).toBeVisible();
    await expect(search, "Inactive background editor must not overlay the modal").not.toBeAttached();
    await expect(page.locator("[data-fasttowin-dialog-inputs]")).toHaveCount(1);
    await input.tap();
    await expect(input).toBeFocused();
    const original = await input.elementHandle();
    await original.evaluate(element => {
      window.__clanKeyboardBlurCount = 0;
      element.addEventListener('blur', () => window.__clanKeyboardBlurCount++);
    });
    await page.screenshot({ path: testInfo.outputPath('clan-before-keyboard-phone.png') });

    // Desktop browsers do not display a software keyboard. Exercise its real
    // PWA visualViewport -> CSS -> Compose resize path and assert editor focus,
    // identity and continued typing, rather than only checking canvas dimensions.
    for (const height of [700, 600, 500, 600, 500]) {
      await page.evaluate(height => window.__setClanKeyboardHeight(height), height);
      await expect(page.locator('canvas').first()).toHaveCSS('height', `${height}px`);
      await expect(input).toBeFocused();
    }
    await page.waitForTimeout(1000);
    expect(await original.evaluate(element => element.isConnected), 'Original editor remains attached').toBe(true);
    await expect(input).toBeFocused();
    expect(await page.evaluate(() => window.__clanKeyboardBlurCount), 'Keyboard opening must not blur the editor').toBe(0);
    await input.pressSequentially('Bang thử nghiệm');
    await expect(input).toHaveValue('Bang thử nghiệm');
    await page.screenshot({ path: testInfo.outputPath('clan-keyboard-open-phone.png') });

    // Explicit dismissal must remain possible; the fix must not steal focus back.
    await input.evaluate(element => element.blur());
    await page.evaluate(() => window.__setClanKeyboardHeight(window.innerHeight));
    await expect(input).not.toBeFocused();
    await expect(page.locator('canvas').first()).toHaveCSS('height', `${initialHeight}px`);
    await page.waitForTimeout(1000);
    await expect(input).not.toBeFocused();
    await expect(input).toHaveValue('Bang thử nghiệm');

    // Window changes must keep both dialog state and an intentionally focused
    // editor stable. Capture the large-screen presentation for visual review.
    await input.tap();
    await page.setViewportSize({ width: 812, height: 375 });
    await page.evaluate(() => window.__setClanKeyboardHeight(window.innerHeight));
    await expect(input).toBeFocused();
    await page.screenshot({ path: testInfo.outputPath('clan-landscape.png') });
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.evaluate(() => window.__setClanKeyboardHeight(window.innerHeight));
    await expect(input).toBeFocused();
    await page.screenshot({ path: testInfo.outputPath('clan-desktop.png') });
    await click(page, page.getByRole('button', { name: /^hủy$/i }).last());
    await expect(input).not.toBeAttached();
    await expect(page.locator("[data-fasttowin-dialog-inputs]")).toHaveCount(0);
    await expect(search).toHaveValue('Giữ truy vấn');
    await click(page, tag(page, 'open_create_clan'));
    await expect(input).toHaveValue('');
    await page.keyboard.press('Escape');
    await expect(input).not.toBeAttached();
    await expect(page.locator("[data-fasttowin-dialog-inputs]")).toHaveCount(0);
    await expect(search).toHaveValue('Giữ truy vấn');
    await click(page, tag(page, 'open_create_clan'));
    await page.setViewportSize({ width: 812, height: 375 });
    await page.evaluate(() => window.__setClanKeyboardHeight(260));
    await click(page, page.getByRole('button', { name: /^hủy$/i }).last());
    await expect(input).not.toBeAttached();
    await expect(page.locator('[data-fasttowin-dialog-inputs]')).toHaveCount(0);
    await expect(search).toHaveValue('Giữ truy vấn');
  });
}
