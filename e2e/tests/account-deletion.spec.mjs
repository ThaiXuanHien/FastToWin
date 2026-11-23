import { test, expect, tag, click, login } from '../support/game.mjs';

// App Store guideline 5.1.1(v): an account must be deletable from inside the app with no
// extra step such as re-entering a password. One visible row plus one confirmation.
test('account deletion is one visible row and one confirmation, with no password', async ({ actors }, testInfo) => {
  const actor = await actors('Delete');
  const { page, account } = actor;
  await login(actor);

  await click(page, tag(page, 'bottom_tab:account'));
  await expect(tag(page, 'profile_section_gift_code')).not.toBeAttached();
  await click(page, tag(page, 'profile_delete_account'));
  const dialog = tag(page, 'delete_account_dialog');
  await expect(dialog).toContainText('XÓA TÀI KHOẢN?');
  await expect(dialog).toContainText('xóa vĩnh viễn');
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('delete-account-dialog.png') });

  const deleted = page.waitForResponse(response =>
    response.url() === `${process.env.E2E_API_URL}/auth/delete-account` && response.request().method() === 'POST');
  await click(page, tag(page, 'confirm_delete_account'));
  expect((await deleted).ok()).toBeTruthy();
  actor.accountDeleted = true;
  await expect(tag(page, 'auth_open_login')).toBeAttached({ timeout: 20_000 });
  await page.screenshot({ path: testInfo.outputPath('after-delete.png') });

  const relogin = await page.request.post(`${process.env.E2E_API_URL}/auth/login`, {
    data: { email: account.email, password: account.password, devicePlatform: 'web' },
  });
  expect([401, 403]).toContain(relogin.status());
});
