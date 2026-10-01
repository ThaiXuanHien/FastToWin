import { test, expect, tag, click, login, createRoom, joinAndStart } from '../support/game.mjs';

for (const size of [
  { width: 375, height: 812, fontScale: 'LARGE' },
  { width: 844, height: 390, fontScale: 'STANDARD' },
  { width: 834, height: 1112, fontScale: 'STANDARD' },
]) {
  test(`adaptive number grid keeps all 50 square cells visible and playable (${size.width}x${size.height})`, async ({ actors }, testInfo) => {
    const player = await actors('Number grid', {
      iosStandalone: true,
      preferences: { languageCode: 'vi', fontScale: size.fontScale, visualEffectsEnabled: false },
    });
    const { page } = player;
    const opponent = await actors('Grid opponent');
    await page.setViewportSize({ width: size.width, height: size.height });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await login(player);
    await login(opponent);
    const room = await createRoom(player);
    await joinAndStart(player, opponent, room);
    await expect(tag(page, 'game_number_50')).toBeAttached();
    await expect.poll(async () => (await tag(page, 'game_number_50').boundingBox())?.height || 0).toBeGreaterThan(10);
    const grid = await tag(page, 'number_grid').boundingBox();
    const cells = await Promise.all(Array.from({ length: 50 }, (_, i) =>
      tag(page, `game_number_${i + 1}`).boundingBox()));
    expect(cells.every(cell => cell && cell.width > 0 && cell.height > 0)).toBe(true);
    const xs = [...new Set(cells.map(cell => Math.round(cell.x)))].sort((a, b) => a - b);
    const ys = [...new Set(cells.map(cell => Math.round(cell.y)))].sort((a, b) => a - b);
    // Restore the original adaptive layout: choose 5–10 columns from the
    // available space, without forcing 50 into complete 5x10 / 10x5 rows.
    expect(xs.length).toBeGreaterThanOrEqual(5);
    expect(xs.length).toBeLessThanOrEqual(10);
    expect(ys).toHaveLength(Math.ceil(50 / xs.length));
    for (const cell of cells) {
      expect(Math.abs(cell.width - cell.height)).toBeLessThanOrEqual(1);
      expect(Math.abs(cell.width - cells[0].width)).toBeLessThanOrEqual(1);
      expect(Math.abs(cell.height - cells[0].height)).toBeLessThanOrEqual(1);
      expect(cell.x).toBeGreaterThanOrEqual(grid.x - 1);
      expect(cell.y).toBeGreaterThanOrEqual(grid.y - 1);
      expect(cell.x + cell.width).toBeLessThanOrEqual(grid.x + grid.width + 1);
      expect(cell.y + cell.height).toBeLessThanOrEqual(grid.y + grid.height + 1);
      expect(cell.y + cell.height).toBeLessThanOrEqual(size.height);
    }
    const horizontalPitch = xs[1] - xs[0];
    const verticalPitch = ys[1] - ys[0];
    for (let i = 1; i < xs.length; i++) expect(Math.abs(xs[i] - xs[i - 1] - horizontalPitch)).toBeLessThanOrEqual(1);
    for (let i = 1; i < ys.length; i++) expect(Math.abs(ys[i] - ys[i - 1] - verticalPitch)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath('number-grid.png') });
    await click(page, tag(page, 'game_number_1'));
    await expect(tag(page, 'player_score_local').getByText('10', { exact: true })).toBeAttached();
  });
}
