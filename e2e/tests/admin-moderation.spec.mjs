import { test, expect, login } from '../support/admin-fixture.mjs';
import { moderation, base, reportId } from '../support/admin-moderation-fixture.mjs';
async function open(page, fixture) {
  await login(page, fixture); await page.getByRole('button', { name: 'Kiểm duyệt', exact: true }).click();
  await page.getByRole('button', { name: 'Xem báo cáo ' + reportId }).click();
}
for (const role of ['OWNER', 'OPERATOR', 'SUPPORT']) test(`moderation ${role} shows evidence as text and grants only appropriate actions`, async ({ page, fixture }) => {
  moderation(fixture); fixture.role = role; await open(page, fixture);
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('<img src=x onerror=alert(1)>', { exact: true })).toBeVisible();
  await expect(dialog.locator('img')).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: 'Khóa tài khoản', exact: true })).toHaveCount(role === 'SUPPORT' ? 0 : 1);
  await dialog.getByRole('button', { name: 'Xử lý báo cáo', exact: true }).click();
  await expect(page.getByLabel('Gỡ nội dung đã được báo cáo')).toHaveCount(role === 'SUPPORT' ? 0 : 1);
  if (role === 'SUPPORT') await expect(page.getByLabel('Kết quả xử lý').locator('option')).toHaveCount(1);
});
test('moderation retry preserves reason and request identity and sends current version/CSRF', async ({ page, fixture }) => {
  const state = moderation(fixture); state.failOnce = true; await open(page, fixture);
  await page.getByRole('button', { name: 'Xử lý báo cáo', exact: true }).click();
  await page.getByLabel('Gỡ nội dung đã được báo cáo').check();
  await page.getByLabel('Lý do xử lý nội bộ', { exact: true }).fill('Nội dung được xác nhận vi phạm');
  await page.getByRole('button', { name: 'Lưu xử lý', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Thử lại');
  await expect(page.getByLabel('Lý do xử lý nội bộ', { exact: true })).toHaveValue('Nội dung được xác nhận vi phạm');
  await page.getByRole('button', { name: 'Lưu xử lý', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const calls = fixture.calls.filter(call => call.route === base + '/reports/' + reportId + '/resolve');
  expect(calls).toHaveLength(2); expect(calls[0].body).toEqual(calls[1].body);
  expect(calls[1].body).toMatchObject({ expectedVersion: 1, removeContent: true, status: 'RESOLVED' });
  expect(calls[1].headers['x-admin-csrf']).toBe('fixture-csrf');
});
for (const width of [375, 1280]) test(`moderation readable and keyboard dismissal ${width}`, async ({ page, fixture }) => {
  await page.setViewportSize({ width, height: 812 }); moderation(fixture); await open(page, fixture);
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Xem báo cáo ' + reportId })).toBeFocused();
});
