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
