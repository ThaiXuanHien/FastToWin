import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { test, expect, login } from '../support/admin-fixture.mjs';
import { compensation, reportedRound } from '../support/admin-compensation-fixture.mjs';

const artifacts = fileURLToPath(new URL('../../.artifacts/compensation-counter-mapping/screenshots/', import.meta.url));
await mkdir(artifacts, { recursive: true });

async function openRound(page, fixture, role, width) {
  fixture.role = role;
  await page.setViewportSize({ width, height: 1000 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await login(page, fixture);
  await page.getByRole('button', { name: 'Đền bù', exact: true }).click();
  await page.getByRole('button', { name: 'Xem sự cố Sự cố kết nối', exact: true }).click();
  await page.getByRole('button', { name: 'Xem đợt 1', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Báo cáo đợt 1', exact: true })).toBeVisible();
}

async function fact(scope, label, value) {
  const term = scope.getByText(label, { exact: true });
  await expect(term).toHaveCount(1);
  await expect(term.locator('xpath=following-sibling::dd')).toHaveText(value);
}

async function settled(page) {
  await page.getByRole('dialog').evaluate(async dialog => {
    await Promise.all(dialog.getAnimations().map(animation => animation.finished));
  });
  await expect(page.getByRole('dialog')).toHaveCSS('opacity', '1');
}

for (const width of [360, 1440]) {
  for (const role of ['OWNER', 'OPERATOR', 'SUPPORT']) {
    test(`counter mapping report ${role} ${width}`, async ({ page, fixture }) => {
      compensation(fixture, reportedRound());
      await openRound(page, fixture, role, width);
      const main = page.locator('main');
      // Hand-derived literal expectations; never derive them from the response/formatter.
      for (const [label, value] of [
        ['Đủ điều kiện (đã chốt)', '101'], ['Chờ giao', '13'], ['Đã giao', '83'],
        ['Đã nhận thưởng', '37'], ['Hết hạn', '17'], ['Đã hủy chưa nhận', '19'], ['Bỏ qua', '5'],
      ]) await fact(main, label, value);
      const paid = main.getByText('Tổng thưởng đã trả', { exact: true }).locator('xpath=following-sibling::*[1]');
      await expect(paid.getByRole('img')).toHaveCount(2);
      await expect(paid.getByRole('img', { name: 'Vàng (GOLD): 3.700', exact: true })).toHaveText('3.700');
      await expect(paid.getByRole('img', { name: 'Kinh nghiệm (XP): 1.110', exact: true })).toHaveText('1.110');
      await expect(main).toContainText('Các bộ đếm có thể chồng lấp; không cộng chúng thành tổng.');
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      if (role === 'OWNER') {
        await page.getByRole('heading', { name: 'Báo cáo đợt 1', exact: true }).evaluate(el => el.scrollIntoView({ block: 'start' }));
        await page.screenshot({ path: artifacts + `report-${width}.png` });
      }
    });
  }
  for (const role of ['OWNER', 'OPERATOR']) {
    test(`counter mapping prior ${role} ${width}`, async ({ page, fixture }) => {
      compensation(fixture);
      await openRound(page, fixture, role, width);
      await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
      await page.getByLabel('Lý do xem trước', { exact: true }).fill('Verify prior counts');
      await page.getByRole('button', { name: 'Tạo bản xem trước', exact: true }).click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toHaveAccessibleName(/Xem trước/);
      await fact(dialog, 'Người đã nhận ở các đợt trước (khác nhau)', '2');
      await fact(dialog, 'Biên nhận đã trả ở các đợt trước', '3');
      await fact(dialog, 'Người trong đợt này đã nhận trước', '1');
      if (role === 'OWNER') {
        await dialog.getByText('Người đã nhận ở các đợt trước (khác nhau)', { exact: true }).evaluate(el => el.scrollIntoView({ block: 'start' }));
        await settled(page);
        await page.screenshot({ path: artifacts + `preview-${width}.png` });
        await page.getByRole('button', { name: 'Tiếp tục công bố', exact: true }).click();
        await expect(dialog).toHaveAccessibleName('Xác nhận công bố đợt');
        await fact(dialog, 'Đã nhận ở đợt trước', '2');
        await fact(dialog, 'Biên nhận trước', '3');
        await fact(dialog, 'Trùng người đã nhận', '1');
        await settled(page);
        await page.screenshot({ path: artifacts + `confirm-${width}.png` });
      } else {
        await expect(page.getByRole('button', { name: 'Tiếp tục công bố', exact: true })).toHaveCount(0);
      }
      // Opening confirmation is read-only; this follow-up never sends publish/cancel mutations.
      expect(fixture.calls.filter(call => /\/(publish|cancel)$/.test(call.route))).toHaveLength(0);
    });
  }
}
