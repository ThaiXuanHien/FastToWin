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
