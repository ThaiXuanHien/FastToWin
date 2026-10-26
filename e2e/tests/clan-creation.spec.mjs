import { test, expect, tag, click, login } from '../support/game.mjs';

test('clan creation displays authoritative level and new price before enabling submit', async ({ actors }, testInfo) => {
  const player = await actors('Clan requirements');
  const { page } = player;
  await login(player);
  await click(page, tag(page, 'bottom_tab:clan'));
  await click(page, tag(page, 'open_create_clan'));
  await expect(tag(page, 'create_clan_level_requirement')).toHaveText('Yêu cầu cấp 10 • Hiện tại: cấp 1');
  await expect(tag(page, 'create_clan_cost')).toHaveText(/10[., ]000.*100/);
  await page.locator('[data-fasttowin-native-input][aria-label="Tên bang"]').fill('Bang chưa đủ cấp');
  const submit = page.getByRole('button', { name: 'TẠO', exact: true });
  await expect(submit).toBeDisabled();
  await page.screenshot({ path: testInfo.outputPath('clan-create-requirements-phone.png') });
  await page.setViewportSize({ width: 1280, height: 800 });
  await expect(tag(page, 'create_clan_level_requirement')).toHaveText('Yêu cầu cấp 10 • Hiện tại: cấp 1');
  await expect(submit).toBeDisabled();
  await page.screenshot({ path: testInfo.outputPath('clan-create-requirements-desktop.png') });
});
