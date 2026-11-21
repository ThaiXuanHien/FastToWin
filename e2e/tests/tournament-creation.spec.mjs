import { test, expect, tag, click, fill, login } from '../support/game.mjs';

const durable = Boolean(process.env.E2E_POSTGRES_SETTINGS);

for (const viewport of [
  { name: 'phone', width: 375, height: 812 },
  { name: 'desktop', width: 1024, height: 768 },
]) {
  test(`paid tournament creation uses manual start on ${viewport.name}`, async ({ actors }, testInfo) => {
    const host = await actors(`Cup${viewport.name}`);
    const { page } = host;
    const commands = [];
    const updates = [];
    const gameSockets = [];
    page.on('websocket', socket => {
      if (new URL(socket.url()).pathname === '/game') gameSockets.push(socket);
      socket.on('framesent', ({ payload }) => {
        const message = JSON.parse(String(payload));
        if (message.type === 'create_tournament') commands.push(message);
      });
      socket.on('framereceived', ({ payload }) => {
        const message = JSON.parse(String(payload));
        if (message.type === 'tournament_updated') updates.push(message.tournament);
      });
    });
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await login(host);
    if (durable) {
      await click(page, tag(page, 'daily_check_in_claim'));
      await expect(tag(page, 'header_gold')).toHaveText('50');
      const receipt = page.getByRole('dialog');
      await click(page, receipt.getByRole('button', { name: 'Đóng', exact: true }));
      await expect(receipt).not.toBeAttached();
    }

    await click(page, tag(page, 'home_tournament'));
    await expect(tag(page, 'tournament_screen')).toBeAttached();
    await click(page, tag(page, 'open_create_tournament'));
    await fill(page, tag(page, 'tournament_name'), `Cup ${viewport.name}`);
    await expect(tag(page, 'tournament_fee_0')).not.toBeAttached();
    await expect(tag(page, 'tournament_schedule_toggle')).not.toBeAttached();
    await click(page, tag(page, 'tournament_fee_50'));
    const prizePreview = tag(page, 'tournament_create_form')
      .getByText('Giải thưởng cơ bản khi đủ người: 200 Vàng', { exact: true });
    await expect(prizePreview).toBeAttached();
    await expect.poll(async () => {
      const prize = await prizePreview.boundingBox();
      const fee = await tag(page, 'tournament_fee_50').boundingBox();
      return Boolean(prize && fee && prize.y + prize.height <= fee.y);
    }).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`manual-tournament-${viewport.name}.png`) });
    await click(page, tag(page, 'create_tournament'));

    if (!durable) {
      // In-memory mode has no wallet record. The error belongs inside the modal.
      await expect(tag(page, 'tournament_create_error')).toContainText('Không tìm thấy dữ liệu được yêu cầu.');
      expect(updates).toHaveLength(0);
    } else {
      await expect(tag(page, 'start_tournament')).toBeAttached();
      await expect(tag(page, 'tournament_check_in')).not.toBeAttached();
      await expect(page.getByText('Giải thưởng cơ bản khi đủ người: 200 Vàng', { exact: true })).toBeAttached();
      await expect(page.getByText('Quỹ hiện tại: 50 Vàng', { exact: true })).toBeAttached();
      const createdNotice = page.getByText('Đã tạo giải 4 người.', { exact: true });
      await expect(createdNotice).toBeAttached();
      expect(updates.at(-1)).toMatchObject({ entryFee: 50, prizePool: 50, schedule: null });

      const viewer = await actors(`Viewer${viewport.name}`);
      await viewer.page.setViewportSize({ width: viewport.width, height: viewport.height });
      await login(viewer);
      await click(viewer.page, tag(viewer.page, 'home_tournament'));
      const listing = tag(viewer.page, `public_tournament_${updates.at(-1).tournamentId}`);
      const listedPrize = listing.getByText('Giải thưởng cơ bản khi đủ người: 200 Vàng', { exact: true });
      await expect(listedPrize).toBeAttached();
      await expect(listing.getByText('Quỹ hiện tại: 50 Vàng', { exact: true })).toBeAttached();
      await viewer.page.mouse.move(viewport.width / 2, viewport.height * 0.8);
      await expect.poll(async () => {
        const bounds = await listedPrize.boundingBox();
        if (bounds && bounds.y >= 60 && bounds.y + bounds.height < viewport.height - 45) return true;
        await viewer.page.mouse.wheel(0, 210);
        return false;
      }).toBe(true);
      await viewer.page.screenshot({ path: testInfo.outputPath(`public-prize-${viewport.name}.png`) });

      const connection = gameSockets.at(-1);
      await click(page, tag(page, 'leave_tournament'));
      await expect(page.getByText('Chủ giải đã hủy giải đấu.', { exact: true })).toBeAttached();
      await expect(tag(page, 'open_create_tournament')).toBeAttached();
      await expect(createdNotice).not.toBeAttached();
      expect(connection.isClosed()).toBe(false);
      expect(gameSockets.at(-1)).toBe(connection);
    }
    expect(commands).toHaveLength(1);
    expect(commands[0]).toMatchObject({ entryFee: 50, schedule: null });
  });
}
