import { test, expect, tag, click, login } from '../support/game.mjs';
import { readFile } from 'node:fs/promises';

async function installIosKeyboardViewport(page) {
  await page.addInitScript(() => {
    const viewport = window.visualViewport;
    let height = window.innerHeight;
    Object.defineProperty(navigator, 'standalone', { configurable: true, value: true });
    Object.defineProperty(navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 26_3 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    });
    Object.defineProperty(window, 'visualViewport', {
      configurable: true,
      value: {
        get height() { return height; },
        get width() { return window.innerWidth; },
        offsetTop: 0, offsetLeft: 0, pageTop: 0, pageLeft: 0, scale: 1,
        addEventListener(...args) { viewport.addEventListener(...args); },
        removeEventListener(...args) { viewport.removeEventListener(...args); },
      },
    });
    window.__setKeyboardViewport = (nextHeight, notify = true) => {
      height = nextHeight;
      if (notify) viewport.dispatchEvent(new Event('resize'));
    };
  });
}

async function expectFullIosCanvas(page, hasBottomBar = true) {
  const { height } = page.viewportSize();
  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', `${height}px`);
  await expect(page.locator('canvas').first()).toHaveCSS('height', `${height}px`);
  if (!hasBottomBar) return;
  await expect.poll(async () => {
    const bounds = await tag(page, 'bottom_bar').boundingBox();
    return bounds ? bounds.y + bounds.height : 0;
  }).toBeCloseTo(height - 12, 0);
}

async function tapNativeInput(page, label, visibleHeight = page.viewportSize().height) {
  const input = page.locator('[data-fasttowin-native-input]').and(page.getByLabel(label, { exact: true }));
  await expect(input).toBeAttached();
  const viewport = page.viewportSize();
  for (let attempt = 0; attempt < 40; attempt++) {
    const bounds = await input.boundingBox();
    if (bounds && bounds.width > 0 && bounds.height > 0 && bounds.y >= 105 &&
        bounds.y + bounds.height <= visibleHeight - 12) {
      // Native editors expose their real rendered position; Compose semantics
      // may still expose old/clipped bounds after scrolling or font scaling.
      await input.tap();
      return input;
    }
    // Compose clips offscreen semantics to zero-sized boxes. Small wheel steps
    // cannot skip an entire field in the short landscape scroll container.
    await page.mouse.move(viewport.width / 2, Math.max(120, viewport.height / 2));
    const delta = bounds && bounds.width > 0
      ? (bounds.y < 105 ? bounds.y - 110 : bounds.y + bounds.height - (visibleHeight - 12))
      : 120;
    await page.mouse.wheel(0, delta);
    await page.waitForTimeout(100);
  }
  throw new Error(`Native input ${label} could not be brought into the viewport`);
}

test('iOS keyboard recovery receives focus events from a dialog shadow root', async ({ page }) => {
  const pwaScript = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url), 'utf8',
  );
  await installIosKeyboardViewport(page);
  await page.goto('/__e2e/ready');
  await page.setContent(`
    <style>#fastToWinRoot { height: var(--fast-to-win-viewport-height, 844px); }</style>
    <main id="fastToWinRoot"><div id="dialog-host"></div></main>
  `);
  await page.evaluate(() => {
    const shadow = document.getElementById('dialog-host').attachShadow({ mode: 'open' });
    const input = document.createElement('textarea');
    input.setAttribute('data-fasttowin-native-input', '');
    shadow.append(input);
  });
  await page.addScriptTag({ content: pwaScript });
  const input = page.locator('#dialog-host textarea');
  await input.focus();
  await page.evaluate(() => window.__setKeyboardViewport(500));
  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '500px');
  await input.evaluate(element => element.blur());
  await page.evaluate(() => window.__setKeyboardViewport(650));
  await page.waitForTimeout(300);
  await page.evaluate(() => window.__setKeyboardViewport(window.innerHeight, false));
  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', `${page.viewportSize().height}px`);
});

for (const { width, height, dismissal } of [
  { width: 375, height: 812, dismissal: 'done' },
  { width: 375, height: 812, dismissal: 'focused' },
  { width: 844, height: 390, dismissal: 'done' },
  { width: 844, height: 390, dismissal: 'focused' },
]) {
  test(`iOS tournament keyboard ${dismissal} restores the real canvas and bottom bar (${width}x${height})`, async ({ actors }, testInfo) => {
    const player = await actors(`iOS tournament ${dismissal}`, { iosStandalone: true });
    const { page } = player;
    await page.setViewportSize({ width, height });
    const keyboardHeight = Math.max(160, height - 332);
    const settlingHeight = Math.round((height + keyboardHeight) / 2);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await installIosKeyboardViewport(page);
    await login(player);
    // Enter the real screen without relying on Home's offscreen discovery tile.
    await player.navigate(() => page.goto('/tournament'));
    await page.locator('html').evaluate(element => {
      element.style.setProperty('--fast-to-win-safe-top', '47px');
      element.style.setProperty('--fast-to-win-raw-safe-bottom', '34px');
      window.dispatchEvent(new Event('resize'));
    });
    await expect.poll(async () => (await tag(page, 'app_header').boundingBox())?.y ?? -1)
      .toBeCloseTo(47, 0);
    // Keep the editor inside the upcoming keyboard viewport, so WebKit does
    // not hide/blur it merely because a desktop-mocked viewport clipped it.
    const name = await tapNativeInput(page, 'Tên giải đấu', keyboardHeight);
    await expect(name).toBeFocused();
    await name.fill('Kiểm tra bàn phím');
    // Desktop WebKit has no software keyboard. Change only visualViewport,
    // never window.resize, to reproduce iOS keyboard and late animation frames.
    await page.evaluate(value => window.__setKeyboardViewport(value), keyboardHeight);
    await expect(page.locator('canvas').first()).toHaveCSS('height', `${keyboardHeight}px`);
    if (dismissal === 'done') {
      await name.press('Enter');
      await expect(page.locator('[data-fasttowin-native-input]:focus')).toHaveCount(0);
    }
    await page.evaluate(value => window.__setKeyboardViewport(value), settlingHeight);
    await page.waitForTimeout(300);
    await page.evaluate(() => window.__setKeyboardViewport(window.innerHeight, false));
    await expectFullIosCanvas(page, false);
    if (dismissal === 'focused') await expect(name).toBeFocused();
    await expect(name).toHaveValue('Kiểm tra bàn phím');
    if (dismissal === 'focused') {
      // Reopen without another focusin, including an interrupted dismissal.
      await page.evaluate(value => window.__setKeyboardViewport(value), keyboardHeight);
      await expect(page.locator('canvas').first()).toHaveCSS('height', `${keyboardHeight}px`);
      await page.evaluate(value => window.__setKeyboardViewport(value), settlingHeight);
      await page.waitForTimeout(100);
      await page.evaluate(value => window.__setKeyboardViewport(value), keyboardHeight);
      await expect(page.locator('canvas').first()).toHaveCSS('height', `${keyboardHeight}px`);
      await page.evaluate(value => window.__setKeyboardViewport(value), settlingHeight);
      await page.waitForTimeout(300);
      await page.evaluate(() => window.__setKeyboardViewport(window.innerHeight, false));
      await expectFullIosCanvas(page, false);
      await expect(name).toBeFocused();
      await expect(name).toHaveValue('Kiểm tra bàn phím');
    }
    await page.screenshot({ path: testInfo.outputPath(`tournament-keyboard-${dismissal}.png`) });
    await click(page, page.getByRole('button', { name: 'Quay lại', exact: true }).first());
    await expect(tag(page, 'home_screen')).toBeAttached();
    await expectFullIosCanvas(page);
    await page.screenshot({ path: testInfo.outputPath(`home-after-keyboard-${dismissal}.png`) });
  });
}

test('iOS keyboard dismissal restores other shared text-input screens and dialogs', async ({ actors }, testInfo) => {
  const player = await actors('iOS keyboard audit', {
    iosStandalone: true, preferences: { languageCode: 'vi', fontScale: 'LARGE' },
  });
  const { page } = player;
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await installIosKeyboardViewport(page);
  await login(player);

  const cases = [
    { route: '/tournament', field: 'public_tournament_search', label: 'Tìm theo tên giải đấu' },
    { route: '/tournament', open: ['tournament_fee_custom'], field: 'tournament_custom_fee_input', label: 'Nhập số vàng lệ phí', text: '12' },
    { route: '/rooms', open: ['create_room_open', 'match_type:CASUAL', 'game_mode:ORDER'], field: 'create_room_name', label: 'Tên phòng' },
    { route: '/clan', field: 'clan_search_field', label: 'Tìm bang hội' },
    { route: '/clan', open: ['open_create_clan'], field: 'create_clan_name', label: 'Tên bang' },
    { route: '/clan', open: ['open_create_clan'], field: 'create_clan_description', label: 'Mô tả bang hội', multiline: true },
    { route: '/account', open: ['profile_edit'], field: 'profile_display_name', label: 'Biệt danh' },
    { route: '/friends', field: 'friend-code', label: 'Mã người chơi', text: 'FTW8X2Q' },
  ];
  for (const scenario of cases) {
    await test.step(scenario.field || 'friend code', async () => {
      await player.navigate(() => page.goto(scenario.route));
      await page.locator('html').evaluate(element => {
        element.style.setProperty('--fast-to-win-safe-top', '47px');
        element.style.setProperty('--fast-to-win-raw-safe-bottom', '34px');
        window.dispatchEvent(new Event('resize'));
      });
      await expect.poll(async () => (await tag(page, 'app_header').boundingBox())?.y ?? -1)
        .toBeGreaterThanOrEqual(47);
      for (const id of scenario.open || []) await click(page, tag(page, id));
      const input = await tapNativeInput(page, scenario.label);
      await expect(input).toBeFocused();
      const value = scenario.text || (scenario.multiline ? 'Kiểm tra\nbàn phím' : 'Kiểm tra');
      await input.fill(value);
      const hasBottomBar = await tag(page, 'bottom_bar').count() > 0;
      await page.evaluate(() => window.__setKeyboardViewport(500));
      await expect(page.locator('canvas').first()).toHaveCSS('height', '500px');
      await input.evaluate(element => element.blur());
      await page.evaluate(() => window.__setKeyboardViewport(650));
      await page.waitForTimeout(300);
      await page.evaluate(() => window.__setKeyboardViewport(window.innerHeight, false));
      await expectFullIosCanvas(page, hasBottomBar);
      await expect(input).toHaveValue(value);
      await page.screenshot({ path: testInfo.outputPath(`${scenario.field || 'friend-code'}-keyboard-restored.png`) });
    });
  }
});

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
  await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(7, 26, 59)');
  await expect(page.locator('#fastToWinSafeAreaProbe')).toHaveCSS('padding-top', '47px');
  await expect(page.locator('#fastToWinSafeAreaProbe')).toHaveCSS('padding-bottom', '34px');

  const viewport = page.viewportSize();
  const rootBounds = await root.boundingBox();
  expect(rootBounds.x).toBe(0);
  expect(rootBounds.y).toBe(0);
  expect(rootBounds.width).toBe(viewport.width);
  expect(rootBounds.height).toBeCloseTo(viewport.height, 0);
  await expect(page.locator('#composeScene')).toHaveCSS('transform', 'none');
});

test('iOS standalone refreshes a stale first-paint viewport on pageshow', async ({ page }) => {
  const pwaScript = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>
      #fastToWinRoot {
        width: 100%;
        height: var(--fast-to-win-viewport-height, 760px);
      }
    </style>
    <main id="fastToWinRoot" aria-label="Fast To Win"></main>
  `);
  await page.evaluate(() => {
    let viewportHeight = 760;
    const listeners = new Map();
    Object.defineProperty(window.navigator, 'standalone', {
      configurable: true,
      value: true,
    });
    Object.defineProperty(window.navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    });
    Object.defineProperty(window, 'visualViewport', {
      configurable: true,
      value: {
        get height() { return viewportHeight; },
        offsetTop: 0,
        addEventListener(type, listener) { listeners.set(type, listener); },
        removeEventListener(type) { listeners.delete(type); },
      },
    });
    window.__setFastToWinViewportHeight = height => {
      viewportHeight = height;
      listeners.get('resize')?.(new Event('resize'));
    };
  });
  await page.addScriptTag({ content: pwaScript });

  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '760px');
  await page.evaluate(() => {
    window.__setFastToWinViewportHeight(844);
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));
  });

  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '844px');
});

test('iOS standalone restores the full viewport after keyboard dismissal settles late', async ({ page }) => {
  const pwaScript = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>
      #fastToWinRoot { height: var(--fast-to-win-viewport-height, 844px); }
    </style>
    <main id="fastToWinRoot"><input data-fasttowin-native-input></main>
  `);
  await page.evaluate(() => {
    let viewportHeight = 844;
    const listeners = new Map();
    Object.defineProperty(navigator, 'standalone', { configurable: true, value: true });
    Object.defineProperty(navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    });
    Object.defineProperty(window, 'visualViewport', {
      configurable: true,
      value: {
        get height() { return viewportHeight; },
        offsetTop: 0,
        addEventListener(type, listener) { listeners.set(type, listener); },
        removeEventListener(type) { listeners.delete(type); },
      },
    });
    window.__openKeyboard = () => {
      viewportHeight = 500;
      listeners.get('resize')?.(new Event('resize'));
    };
    window.__finishKeyboardDismissalWithoutResize = () => { viewportHeight = 844; };
  });
  await page.addScriptTag({ content: pwaScript });

  const input = page.locator('[data-fasttowin-native-input]');
  await input.focus();
  await page.evaluate(() => window.__openKeyboard());
  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '500px');
  await input.blur();
  await page.waitForTimeout(250);
  await page.evaluate(() => window.__finishKeyboardDismissalWithoutResize());

  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '844px');
});

test('iOS standalone preserves the full viewport baseline across native input focus transfer', async ({ page }) => {
  const pwaScript = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>
      #fastToWinRoot { height: var(--fast-to-win-viewport-height, 844px); }
    </style>
    <main id="fastToWinRoot">
      <input data-fasttowin-native-input aria-label="First native input">
      <input data-fasttowin-native-input aria-label="Second native input">
    </main>
  `);
  await page.evaluate(() => {
    let viewportHeight = 844;
    const listeners = new Map();
    Object.defineProperty(navigator, 'standalone', { configurable: true, value: true });
    Object.defineProperty(navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    });
    Object.defineProperty(window, 'visualViewport', {
      configurable: true,
      value: {
        get height() { return viewportHeight; },
        offsetTop: 0,
        addEventListener(type, listener) { listeners.set(type, listener); },
        removeEventListener(type) { listeners.delete(type); },
      },
    });
    window.__openKeyboard = () => {
      viewportHeight = 500;
      listeners.get('resize')?.(new Event('resize'));
    };
    window.__finishKeyboardDismissalWithoutResize = () => { viewportHeight = 844; };
  });
  await page.addScriptTag({ content: pwaScript });

  await page.getByRole('textbox', { name: 'First native input' }).focus();
  await page.evaluate(() => window.__openKeyboard());
  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '500px');
  const secondInput = page.getByRole('textbox', { name: 'Second native input' });
  await secondInput.focus();
  await secondInput.blur();
  await page.waitForTimeout(250);
  await page.evaluate(() => window.__finishKeyboardDismissalWithoutResize());

  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '844px');
});

test('iOS standalone cancels an old recovery deadline and finishes the next one without an animation frame', async ({ page }) => {
  const pwaScript = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>
      #fastToWinRoot { height: var(--fast-to-win-viewport-height, 844px); }
    </style>
    <main id="fastToWinRoot">
      <input data-fasttowin-native-input aria-label="First native input">
      <input data-fasttowin-native-input aria-label="Second native input">
    </main>
  `);
  await page.evaluate(() => {
    let viewportHeight = 844;
    const listeners = new Map();
    Object.defineProperty(navigator, 'standalone', { configurable: true, value: true });
    Object.defineProperty(navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    });
    Object.defineProperty(window, 'visualViewport', {
      configurable: true,
      value: {
        get height() { return viewportHeight; },
        offsetTop: 0,
        addEventListener(type, listener) { listeners.set(type, listener); },
        removeEventListener(type) { listeners.delete(type); },
      },
    });
    window.requestAnimationFrame = () => 1;
    window.cancelAnimationFrame = () => {};
    window.__setViewportWithoutResize = height => { viewportHeight = height; };
    window.__openKeyboard = () => {
      viewportHeight = 500;
      listeners.get('resize')?.(new Event('resize'));
    };
  });
  await page.addScriptTag({ content: pwaScript });

  const root = page.locator('#fastToWinRoot');
  const firstInput = page.getByRole('textbox', { name: 'First native input' });
  const secondInput = page.getByRole('textbox', { name: 'Second native input' });
  await firstInput.focus();
  await page.evaluate(() => window.__openKeyboard());
  await expect(root).toHaveCSS('height', '500px');
  await page.waitForTimeout(200);
  await firstInput.blur();
  await page.waitForTimeout(100);
  await secondInput.focus();
  await page.evaluate(() => window.__setViewportWithoutResize(700));
  await page.waitForTimeout(1150);
  await expect(root).toHaveCSS('height', '500px');

  await secondInput.blur();
  await page.waitForTimeout(250);
  await page.evaluate(() => window.__setViewportWithoutResize(844));
  await expect(root).toHaveCSS('height', '844px', { timeout: 1500 });
});

test('Android browser uses the visible viewport instead of the hidden browser chrome area', async ({ page }) => {
  const pwaScript = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>
      :root { --fast-to-win-viewport-height: 844px; }
      #fastToWinRoot {
        width: 100%;
        height: var(--fast-to-win-viewport-height);
      }
    </style>
    <main id="fastToWinRoot" aria-label="Fast To Win"></main>
  `);
  await page.evaluate(() => {
    Object.defineProperty(window.navigator, 'standalone', {
      configurable: true,
      value: false,
    });
    Object.defineProperty(window.navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36',
    });
    Object.defineProperty(window, 'visualViewport', {
      configurable: true,
      value: {
        height: 743,
        offsetTop: 0,
        addEventListener() {},
        removeEventListener() {},
      },
    });
  });
  await page.addScriptTag({ content: pwaScript });

  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '743px');
});

test('iOS first tutorial completion remeasures the viewport before showing Home', async ({ page }) => {
  const pwaScript = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>
      #fastToWinRoot {
        width: 100%;
        height: var(--fast-to-win-viewport-height, 760px);
      }
    </style>
    <main id="fastToWinRoot" aria-label="Fast To Win"></main>
  `);
  await page.evaluate(() => {
    let viewportHeight = 760;
    Object.defineProperty(window.navigator, 'standalone', {
      configurable: true,
      value: true,
    });
    Object.defineProperty(window.navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    });
    Object.defineProperty(window, 'visualViewport', {
      configurable: true,
      value: {
        get height() { return viewportHeight; },
        offsetTop: 0,
        addEventListener() {},
        removeEventListener() {},
      },
    });
    window.__settleFastToWinIosViewport = height => {
      viewportHeight = height;
    };
  });
  await page.addScriptTag({ content: pwaScript });

  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '760px');
  await page.evaluate(() => {
    window.FASTTOWIN_PWA.navigation.replace('#home');
    window.setTimeout(() => window.__settleFastToWinIosViewport(844), 80);
  });

  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '844px');
});

for (const size of [
  { width: 390, height: 844, fontScale: 'STANDARD' },
  { width: 375, height: 812, fontScale: 'LARGE' },
  { width: 844, height: 390, fontScale: 'STANDARD' },
]) {
  test(`iOS tutorial to Home resizes the real Compose canvas (${size.width}x${size.height}, ${size.fontScale})`, async ({ page }, testInfo) => {
    await page.setViewportSize(size);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.addInitScript(({ fontScale }) => {
      const viewport = window.visualViewport;
      let height = window.innerHeight - 84;
      localStorage.setItem('fasttowin.preferences', JSON.stringify({ languageCode: 'vi', fontScale }));
      Object.defineProperty(navigator, 'standalone', { configurable: true, value: true });
      Object.defineProperty(navigator, 'userAgent', {
        configurable: true,
        value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 26_3 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
      });
      Object.defineProperty(window, 'visualViewport', {
        configurable: true,
        value: {
          get height() { return height; },
          get width() { return window.innerWidth; },
          offsetTop: 0, offsetLeft: 0, pageTop: 0, pageLeft: 0, scale: 1,
          addEventListener(...args) { viewport.addEventListener(...args); },
          removeEventListener(...args) { viewport.removeEventListener(...args); },
        },
      });
      window.__settleTutorialViewport = () => { height = window.innerHeight; };
    }, size);
    await page.goto('/');
    await click(page, page.getByRole('button', { name: 'Chơi với tư cách khách', exact: true }));
    await expect(tag(page, 'tutorial_continue')).toBeAttached();

    await page.locator('html').evaluate(element => {
      element.style.setProperty('--fast-to-win-safe-top', '47px');
      element.style.setProperty('--fast-to-win-raw-safe-bottom', '34px');
      window.dispatchEvent(new Event('resize'));
    });
    await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', `${size.height - 84}px`);
    await expect(page.locator('canvas').first()).toHaveCSS('height', `${size.height - 84}px`);
    await expect.poll(async () => (await page.getByRole('button', { name: 'Bỏ qua', exact: true })
      .boundingBox())?.y ?? -1).toBeGreaterThanOrEqual(47);
    await click(page, page.getByRole('button', { name: 'Bỏ qua', exact: true }));
    await expect(tag(page, 'home_screen')).toBeAttached();

    // iOS can settle after navigation without emitting window.resize. Reproduce
    // the existing viewport bridge's CSS update, with no synthetic native resize.
    await page.evaluate(() => {
      window.__settleTutorialViewport();
      window.FASTTOWIN_PWA.viewport.sync();
    });
    await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', `${size.height}px`);
    await expect(page.locator('canvas').first()).toHaveCSS('height', `${size.height}px`);
    await expect.poll(async () => {
      const bounds = await tag(page, 'bottom_bar').boundingBox();
      return bounds ? bounds.y + bounds.height : 0;
    }).toBeCloseTo(size.height - 12, 0);
    await expect.poll(async () => (await tag(page, 'app_header').boundingBox())?.y ?? -1)
      .toBeCloseTo(47, 0);
    await page.screenshot({ path: testInfo.outputPath('home-after-viewport-settle.png') });
  });
}

test('Android standalone keeps the focused input stable while the keyboard opens', async ({ page }) => {
  const pwaScript = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>
      #fastToWinRoot {
        width: 100%;
        height: var(--fast-to-win-viewport-height, 844px);
      }
    </style>
    <main id="fastToWinRoot" aria-label="Fast To Win">
      <input type="password" data-fasttowin-native-input>
    </main>
  `);
  await page.evaluate(() => {
    let viewportHeight = 844;
    const listeners = new Map();
    Object.defineProperty(window.navigator, 'standalone', {
      configurable: true,
      value: true,
    });
    Object.defineProperty(window.navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36',
    });
    Object.defineProperty(window, 'visualViewport', {
      configurable: true,
      value: {
        get height() { return viewportHeight; },
        offsetTop: 0,
        addEventListener(type, listener) { listeners.set(type, listener); },
        removeEventListener(type) { listeners.delete(type); },
      },
    });
    window.__openFastToWinAndroidKeyboard = () => {
      viewportHeight = 500;
      listeners.get('resize')?.(new Event('resize'));
    };
  });
  await page.addScriptTag({ content: pwaScript });

  const password = page.locator('input[type=password]');
  await password.focus();
  await page.evaluate(() => window.__openFastToWinAndroidKeyboard());

  await expect(password).toBeFocused();
  await expect(page.locator('#fastToWinRoot')).toHaveCSS('height', '844px');
});

test('iOS standalone keeps only a compact bottom breathing space', async ({ page }) => {
  const styles = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/styles.css', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>${styles}</style>
    <div id="fastToWinSafeAreaProbe" aria-hidden="true"></div>
    <main id="fastToWinRoot" aria-label="Fast To Win"></main>
  `);

  await page.locator('html').evaluate(element => {
    element.style.setProperty('--fast-to-win-raw-safe-bottom', '34px');
  });

  await expect(page.locator('#fastToWinSafeAreaProbe')).toHaveCSS('padding-bottom', '12px');
});

test('iOS home-indicator fallback continues the arcade backdrop', async ({ page }) => {
  const styles = await readFile(
    new URL('../../webApp/src/wasmJsMain/resources/styles.css', import.meta.url),
    'utf8',
  );
  await page.setContent(`
    <style>${styles}</style>
    <main id="fastToWinRoot" aria-label="Fast To Win"></main>
  `);

  // iOS can composite the home-indicator region below the CSS viewport. Leave
  // an equivalent strip outside Compose and verify the document fallback joins
  // the terminal color of ArcadeBackdrop instead of showing a black frame.
  await page.locator('#fastToWinRoot').evaluate(element => {
    element.style.height = 'calc(var(--fast-to-win-viewport-height) - 34px)';
  });

  const viewport = page.viewportSize();
  const fallback = await page.evaluate(({ x, y }) => {
    const element = document.elementFromPoint(x, y);
    return {
      tagName: element?.tagName,
      backgroundColor: getComputedStyle(document.body).backgroundColor,
    };
  }, { x: Math.floor(viewport.width / 2), y: viewport.height - 1 });

  expect(fallback).toEqual({
    tagName: 'BODY',
    backgroundColor: 'rgb(7, 26, 59)',
  });
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
  await expect.poll(async () => (await tag(page, 'app_header').boundingBox())?.y ?? -1, {
    message: 'header stays below the iOS status area after Compose applies safe-area insets',
  }).toBeGreaterThanOrEqual(47);
  await expect.poll(async () => {
    const bounds = await tag(page, 'bottom_bar').boundingBox();
    return bounds ? bounds.y + bounds.height : Number.POSITIVE_INFINITY;
  }, {
    message: 'bottom bar stays above the iOS home-indicator area after Compose applies safe-area insets',
  }).toBeLessThanOrEqual(page.viewportSize().height - 34);
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
