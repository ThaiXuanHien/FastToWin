import { test, expect, click, tag, login } from '../support/game.mjs';

// These checks use only dummy credentials on the loopback backend, never create
// an account, and exercise
// visible DOM inputs, not Compose's invisible, focus-created backing textarea.
test('login exposes directly tappable native email and password inputs', async ({ page }) => {
  await page.goto('/');
  await click(page, tag(page, 'auth_open_login'));
  const email = page.locator('input[data-fasttowin-native-input][type=email]');
  const password = page.locator('input[data-fasttowin-native-input][type=password]');
  await expect(email).toBeVisible();
  await expect(password).toBeVisible();
  await expect(email).toHaveAttribute('inputmode', 'email');
  await email.tap();
  await expect(email).toBeFocused();
  await email.fill('keyboard-test@example.invalid');
  await password.tap();
  await expect(password).toBeFocused();
  await password.fill('unsubmitted-test');
  await expect(email).toHaveValue('keyboard-test@example.invalid');
  await click(page, page.getByRole('button', { name: 'Hiện mật khẩu', exact: true }));
  await expect(page.locator('input[data-fasttowin-native-input]').nth(1)).toHaveAttribute('type', 'text');
  await expect(page.locator('input[data-fasttowin-native-input]').nth(1)).toHaveValue('unsubmitted-test');
  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith('/auth/login') && response.request().method() === 'POST');
  await click(page, tag(page, 'auth_login_submit'));
  const response = await responsePromise;
  expect(response.status()).toBe(401);
  expect(response.request().postDataJSON()).toMatchObject({
    email: 'keyboard-test@example.invalid', password: 'unsubmitted-test',
  });
});

test('focused login input survives a mobile safe-area recomposition', async ({ page }) => {
  await page.goto('/');
  await click(page, tag(page, 'auth_open_login'));
  const email = page.locator('input[data-fasttowin-native-input][type=email]');
  await email.tap();
  await expect(email).toBeFocused();

  await page.locator('html').evaluate(element => {
    element.style.setProperty('--fast-to-win-safe-top', '1px');
    window.dispatchEvent(new Event('resize'));
  });

  await expect(email).toBeFocused();
  await expect(page.locator('input[data-fasttowin-native-input]')).toHaveCount(2);
});

test('registration preserves IME composition and removes native fields on back', async ({ page }) => {
  await page.goto('/');
  await click(page, tag(page, 'auth_open_register'));
  const name = page.locator('input[data-fasttowin-native-input]').first();
  await expect(name).toBeVisible();
  await name.tap();
  await name.dispatchEvent('compositionstart', { data: '' });
  await name.fill('中文');
  await expect(name).toBeFocused();
  await name.dispatchEvent('compositionend', { data: '中文' });
  await expect(name).toHaveValue('中文');
  await name.press('Home');
  await name.press('ArrowRight');
  await name.press('a');
  await expect(name).toHaveValue('中a文');
  await click(page, page.getByRole('button', { name: 'Quay lại', exact: true }).first());
  await expect(tag(page, 'auth_open_login')).toBeAttached();
  await expect(page.locator('[data-fasttowin-native-input]')).toHaveCount(0);
});

test('rejecting an overlength middle edit preserves the password caret', async ({ page }) => {
  await page.goto('/');
  await click(page, tag(page, 'auth_open_login'));
  const password = page.locator('input[data-fasttowin-native-input][type=password]');
  await expect(password).toBeVisible();
  await password.fill('a'.repeat(128));
  await password.press('Home');
  await password.press('ArrowRight');
  await password.press('b');
  await expect(password).toHaveValue('a'.repeat(128));
  await expect.poll(() => password.evaluate(input => input.selectionStart)).toBe(1);
});

test('rapid and middle password edits preserve browser order and selection', async ({ page }) => {
  await page.goto('/');
  await click(page, tag(page, 'auth_open_login'));
  const password = page.locator('input[data-fasttowin-native-input][type=password]');
  await expect(password).toBeVisible();
  await password.focus();
  await password.pressSequentially('123', { delay: 0 });
  await expect(password).toHaveValue('123');
  await expect.poll(() => password.evaluate(input => input.selectionStart)).toBe(3);
  await password.press('Home');
  await password.press('ArrowRight');
  await password.press('9');
  await expect(password).toHaveValue('1923');
  await expect.poll(() => password.evaluate(input => input.selectionStart)).toBe(2);
  await expect(password).toHaveAttribute('autocorrect', 'off');
  await expect(password).toHaveAttribute('spellcheck', 'false');
  await expect(password).toHaveAttribute('autocapitalize', 'none');
});

test('numeric tournament fee keeps typed digits in browser order', async ({ actors }) => {
  const player = await actors('Numeric input');
  await login(player);
  await click(player.page, tag(player.page, 'home_tournament'));
  await expect(tag(player.page, 'tournament_screen')).toBeAttached();
  await click(player.page, tag(player.page, 'open_create_tournament'));
  await click(player.page, tag(player.page, 'tournament_fee_custom'));

  // The fixed three-column preset grid is taller than the old FlowRow on
  // compact viewports. Scroll the newly revealed field into the Compose
  // viewport before asserting its native HTML overlay; offscreen interop
  // elements are intentionally hidden by Compose.
  await click(player.page, tag(player.page, 'tournament_custom_fee_input'));

  const numeric = player.page.locator(
    'input[data-fasttowin-native-input][inputmode=numeric]',
  );
  await expect(numeric).toBeVisible();
  await numeric.focus();
  await numeric.pressSequentially('12345', { delay: 0 });

  await expect(numeric).toHaveValue('12345');
  await expect.poll(() => numeric.evaluate(input => input.selectionStart)).toBe(5);
  await expect(numeric).toHaveAttribute('autocapitalize', 'none');
});

test('labeled tournament example appears only while focused and empty', async ({ actors }, testInfo) => {
  const player = await actors('Tournament placeholder');
  await login(player);
  await click(player.page, tag(player.page, 'home_tournament'));
  await expect(tag(player.page, 'tournament_screen')).toBeAttached();
  await click(player.page, tag(player.page, 'open_create_tournament'));

  const tournamentName = player.page.locator(
    'input[data-fasttowin-native-input][aria-label="Tên giải đấu"]',
  );
  for (let attempt = 0; attempt < 12 && !(await tournamentName.isVisible()); attempt++) {
    const viewport = player.page.viewportSize();
    await player.page.mouse.move(viewport.width / 2, viewport.height / 2);
    await player.page.mouse.wheel(0, 450);
    await player.page.waitForTimeout(150);
  }
  await expect(tournamentName).toBeVisible();
  await expect(tournamentName).not.toBeFocused();
  const beforeFocus = await tournamentName.evaluate(input => ({
    placeholder: input.getAttribute('placeholder'),
    placeholderShown: input.matches(':placeholder-shown'),
  }));
  expect(beforeFocus).toEqual({ placeholder: null, placeholderShown: false });

  await tournamentName.focus();
  await expect(tournamentName).toHaveAttribute('placeholder', 'VD: Cúp Chiến Thần');
  await expect.poll(() => tournamentName.evaluate(input => input.matches(':placeholder-shown'))).toBe(true);
  await tournamentName.fill('Cúp cuối tuần');
  const afterFill = await tournamentName.evaluate(input => ({
    value: input.value,
    placeholderShown: input.matches(':placeholder-shown'),
  }));
  expect(afterFill).toEqual({ value: 'Cúp cuối tuần', placeholderShown: false });
  await player.page.screenshot({ path: testInfo.outputPath('tournament-create-phone.png') });
  await player.page.setViewportSize({ width: 1280, height: 800 });
  await expect(player.page.locator('canvas').first()).toHaveCSS('width', '1280px');
  await expect(tag(player.page, 'arcade_dialog_close')).toBeAttached();
  await player.page.screenshot({ path: testInfo.outputPath('tournament-create-desktop.png') });
  await click(player.page, tag(player.page, 'arcade_dialog_close'));
  await expect(tournamentName).not.toBeAttached();
});


// Exercise the shared pointer helper independently of server/Compose timing.
test('canvas click resamples a target that moves after stable bounds were sampled', async ({ page }) => {
  await page.setContent(`
    <style>
      button { position: absolute; left: 20px; top: 160px; width: 200px; height: 44px; }
      #moving-target { z-index: 2; }
    </style>
    <button id="old-position">Other action</button>
    <button id="moving-target">Intended action</button>
  `);
  await page.evaluate(() => {
    window.clickedAction = null;
    document.querySelector('#old-position').addEventListener('click', () => { window.clickedAction = 'other'; });
    const target = document.querySelector('#moving-target');
    target.addEventListener('click', () => { window.clickedAction = 'intended'; });
    const readBounds = target.getBoundingClientRect.bind(target);
    let moved = false;
    // Protocol bounding-box reads remain stable. The final in-page exposure
    // read observes the same late layout movement captured in the CI trace.
    target.getBoundingClientRect = () => {
      if (!moved) { target.style.top = '105px'; moved = true; }
      return readBounds();
    };
  });
  await click(page, tag(page, 'moving-target'));
  expect(await page.evaluate(() => window.clickedAction)).toBe('intended');
});

test('canvas click waits until an attached control becomes enabled', async ({ page }) => {
  await page.setContent('<button id="ready-target" disabled style="position:absolute;left:20px;top:160px;width:200px;height:44px">Ready action</button>');
  await page.evaluate(() => {
    window.clickedAction = null;
    const target = document.querySelector('#ready-target');
    target.addEventListener('click', () => { window.clickedAction = 'ready'; });
    setTimeout(() => { target.disabled = false; }, 1000);
  });
  await click(page, tag(page, 'ready-target'));
  expect(await page.evaluate(() => window.clickedAction)).toBe('ready');
});


test('canvas click deliberately targets an inert control while retaining geometry checks', async ({ page }) => {
  await page.setContent(`
    <style>
      [role=button] { position: absolute; left: 20px; top: 160px; width: 200px; height: 44px; }
      #inert-target { z-index: 2; }
    </style>
    <div id="old-position" role="button">Other action</div>
    <div id="inert-target" role="button" aria-disabled="true">Inert action</div>
  `);
  await page.evaluate(() => {
    window.clickedAction = null;
    document.querySelector('#old-position').addEventListener('click', () => { window.clickedAction = 'other'; });
    const target = document.querySelector('#inert-target');
    target.addEventListener('click', () => { window.clickedAction = 'inert'; });
    const readBounds = target.getBoundingClientRect.bind(target);
    let moved = false;
    target.getBoundingClientRect = () => {
      if (!moved) { target.style.top = '105px'; moved = true; }
      return readBounds();
    };
  });
  await click(page, tag(page, 'inert-target'), { allowDisabled: true });
  expect(await page.evaluate(() => window.clickedAction)).toBe('inert');
});
