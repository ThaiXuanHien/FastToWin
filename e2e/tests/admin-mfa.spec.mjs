import { test, expect, pageOf } from '../support/admin-fixture.mjs';

const owner = { id: 'owner-1', email: 'owner@example.com', role: 'OWNER', active: true, mfaEnabled: true };
const challengeToken = 'c'.repeat(43);
const recoveryCodes = ['abcde-fghjk', 'mnpqr-stuvw', 'xyz23-45678', 'abcd2-efgh3', 'jkmn4-pqrs5', 'tuvw6-xyz78', 'a2b3c-d4e5f', 'g6h7j-k8m9n', 'p2q3r-s4t5u', 'v6w7x-y8z9a'];
const shots = '../.artifacts/admin-mfa/';

async function signIn(page, fixture) {
  await page.goto(fixture.url + '/admin/');
  await page.getByLabel('Email quản trị', { exact: true }).fill('owner@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('FixturePassword123!');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
}

test('first sign-in enrolls an authenticator and shows recovery codes once', async ({ page, fixture }) => {
  let attempts = 0;
  fixture.handlers.set('/admin/api/login', (call, send) => {
    send(200, { mfa: 'ENROLL', challengeToken, secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/FastToWin%20Admin:owner%40example.com?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP' });
    return true;
  });
  fixture.handlers.set('/admin/api/login/mfa', (call, send, state) => {
    attempts++;
    expect(call.body.challengeToken).toBe(challengeToken);
    if (attempts === 1) { send(401, { code: 'MFA_INVALID_CODE', message: 'Mã xác thực không đúng.' }); return true; }
    expect(call.body.code).toBe('123456');
    state.signedIn = true;
    send(200, { admin: owner, csrfToken: 'fixture-csrf', recoveryCodes });
    return true;
  });

  for (const width of [360, 1440]) {
    await page.setViewportSize({ width, height: width === 360 ? 800 : 1000 });
    attempts = 0;
    await signIn(page, fixture);
    await expect(page.getByRole('heading', { name: 'Bật xác thực hai lớp' })).toBeVisible();
    await expect(page.getByLabel(/Khóa thiết lập/)).toHaveText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');
    const code = page.getByLabel('Mã xác thực 6 số');
    await expect(code).toBeFocused();
    await expect(code).toHaveAttribute('autocomplete', 'one-time-code');
    await page.screenshot({ path: `${shots}enroll-${width}.png`, fullPage: true });

    await code.fill('000000');
    await page.getByRole('button', { name: 'Xác nhận và bật' }).click();
    await expect(page.getByRole('alert')).toHaveText('Mã xác thực không đúng.');
    await expect(code).toHaveValue('');

    await code.fill('123 456');
    await page.getByRole('button', { name: 'Xác nhận và bật' }).click();
    await expect(page.getByRole('heading', { name: 'Lưu mã khôi phục' })).toBeVisible();
    await expect(page.getByRole('list', { name: 'Mã khôi phục' }).getByRole('listitem')).toHaveCount(10);
    const next = page.getByRole('button', { name: 'Tiếp tục' });
    await expect(next).toBeDisabled();
    await page.screenshot({ path: `${shots}recovery-${width}.png`, fullPage: true });
    await page.getByLabel('Tôi đã lưu các mã này ở nơi an toàn').check();
    await next.click();
    await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
    await expect(page.getByText('abcde-fghjk')).toHaveCount(0);
    await page.getByRole('button', { name: 'Đăng xuất' }).click();
    await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeVisible();
  }
  const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
  expect(scrollWidth).toBeLessThanOrEqual(1440);
});

test('enrolled admins verify with a code or switch to a recovery code', async ({ page, fixture }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  const bodies = [];
  fixture.handlers.set('/admin/api/login', (call, send) => { send(200, { mfa: 'VERIFY', challengeToken }); return true; });
  fixture.handlers.set('/admin/api/login/mfa', (call, send, state) => {
    bodies.push(call.body);
    if (bodies.length === 1) { send(401, { code: 'MFA_CHALLENGE_EXPIRED', message: 'Phiên xác thực đã hết hạn. Vui lòng đăng nhập lại.' }); return true; }
    state.signedIn = true; send(200, { admin: owner, csrfToken: 'fixture-csrf', recoveryCodes: [] }); return true;
  });

  await signIn(page, fixture);
  await expect(page.getByRole('heading', { name: 'Nhập mã xác thực' })).toBeVisible();
  await expect(page.getByText(/Khóa thiết lập/)).toHaveCount(0);
  await page.screenshot({ path: `${shots}verify-360.png`, fullPage: true });
  await page.getByLabel('Mã xác thực 6 số').fill('654321');
  await page.getByRole('button', { name: 'Xác nhận', exact: true }).click();
  // An expired challenge returns to the password step with the reason.
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveText('Phiên xác thực đã hết hạn. Vui lòng đăng nhập lại.');

  await page.getByLabel('Email quản trị', { exact: true }).fill('owner@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('FixturePassword123!');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Dùng mã khôi phục' }).click();
  await expect(page.getByLabel('Mã xác thực 6 số')).toBeHidden();
  const recovery = page.getByLabel('Mã khôi phục');
  await expect(recovery).toBeFocused();
  await recovery.fill('ABCDE-FGHJK');
  await page.getByRole('button', { name: 'Xác nhận', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
  expect(bodies[0]).toEqual({ challengeToken, code: '654321' });
  expect(bodies[1]).toEqual({ challengeToken, recoveryCode: 'ABCDE-FGHJK' });
});

test('owners reset another admin second factor with a reason', async ({ page, fixture }) => {
  const support = { id: 'support-2', email: 'support@example.com', role: 'SUPPORT', active: true, mfaEnabled: true };
  let resets = [];
  fixture.handlers.set('/admin/api/login', (call, send, state) => { state.signedIn = true; send(200, { admin: owner, csrfToken: 'fixture-csrf' }); return true; });
  fixture.handlers.set('/admin/api/accounts', (call, send) => {
    if (call.method !== 'GET') return false;
    send(200, pageOf([owner, { ...support, mfaEnabled: resets.length === 0 }])); return true;
  });
  fixture.handlers.set('/admin/api/accounts/support-2/mfa/reset', (call, send) => {
    resets.push(call); send(200, { ...support, mfaEnabled: false }); return true;
  });
  await signIn(page, fixture);
  await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await expect(page.getByText('Đã bật 2 lớp')).toHaveCount(2);
  await expect(page.getByRole('button', { name: 'Đặt lại xác thực hai lớp cho owner@example.com' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Đặt lại xác thực hai lớp cho support@example.com' }).click();
  const dialog = page.getByRole('dialog', { name: 'Đặt lại xác thực hai lớp' });
  await expect(dialog.getByLabel('Lý do đặt lại')).toBeFocused();
  await page.screenshot({ path: `${shots}reset-1440.png` });
  await dialog.getByLabel('Lý do đặt lại').fill('Mất điện thoại');
  await dialog.getByRole('button', { name: 'Đặt lại', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã đặt lại xác thực hai lớp.' })).toBeVisible();
  expect(resets).toHaveLength(1);
  expect(resets[0].body.reason).toBe('Mất điện thoại');
  expect(resets[0].body.requestId).toMatch(/^[0-9a-f-]{36}$/);
  expect(resets[0].headers['x-admin-csrf']).toBe('fixture-csrf');
  await expect(page.getByText('Chưa bật 2 lớp')).toHaveCount(1);
});
