import { test, expect, tag, click, login } from '../support/game.mjs';

test('connection feedback offers safe reload and reconnect actions without a red service error', async ({ actors }, testInfo) => {
  const actor = await actors('Recovery');
  let failLeaderboard = true;
  let offline = false;
  const sockets = new Set();
  const sent = [];
  await actor.context.routeWebSocket('**/game', route => {
    const upstream = route.connectToServer();
    const pair = { route, upstream };
    sockets.add(pair);
    route.onClose(async (code, reason) => { sockets.delete(pair); await upstream.close({ code, reason }); });
    upstream.onClose(async (code, reason) => { sockets.delete(pair); await route.close({ code, reason }); });
    upstream.onMessage(message => { if (!offline) route.send(message); });
    route.onMessage(message => {
      const command = JSON.parse(String(message));
      sent.push(command.type);
      // Keep an opened retry attempt unresponsive until the user explicitly retries.
      // This exercises reconnect/authentication recovery without a fake successful reply.
      if (offline) return;
      if (command.type === 'get_leaderboard' && failLeaderboard) {
        route.send(JSON.stringify({ type: 'error', code: 'LEADERBOARD_UNAVAILABLE',
          message: 'Dịch vụ tạm thời không khả dụng. Vui lòng thử lại.' }));
      } else upstream.send(message);
    });
  });
  await login(actor);
  await click(actor.page, tag(actor.page, 'bottom_tab:leaderboard'));
  const notice = tag(actor.page, 'connection_recovery_notice');
  await expect(notice).toBeAttached();
  await expect(notice).toContainText('Chưa thể hoàn tất lúc này');
  await expect(tag(actor.page, 'connection_recovery_action')).toContainText('Tải lại');
  await expect(actor.page.getByText(/Dịch vụ tạm thời không khả dụng/)).not.toBeAttached();
  await actor.page.screenshot({ path: testInfo.outputPath('service-reload.png') });

  failLeaderboard = false;
  const before = sent.length;
  await click(actor.page, tag(actor.page, 'connection_recovery_action'));
  await expect(notice).not.toBeAttached();
  await expect.poll(() => sent.slice(before).includes('get_leaderboard')).toBe(true);
  expect(sent.slice(before).every(type => ['list_rooms', 'get_profile', 'get_play_quota',
    'get_leaderboard', 'measure_latency', 'get_gem_store_catalog', 'get_wallet_history'].includes(type))).toBe(true);

  offline = true;
  await Promise.all([...sockets].flatMap(pair => [
    pair.route.close({ code: 1012, reason: 'Local connection test' }),
    pair.upstream.close({ code: 1012, reason: 'Local connection test' }),
  ]));
  await expect(notice).toBeAttached({ timeout: 15_000 });
  await expect(notice).toContainText('Đang kết nối lại…');
  await expect(tag(actor.page, 'connection_recovery_action')).toContainText('Thử lại');
  await expect(actor.page.getByText(/Dịch vụ tạm thời không khả dụng/)).not.toBeAttached();
  await actor.page.screenshot({ path: testInfo.outputPath('reconnecting.png') });
  offline = false;
  // Once traffic flows again the client's own retry can finish before the tap lands, and the
  // notice then leaves by itself. Both paths must end connected; the manual retry itself is
  // covered in connection-states.spec.mjs.
  const retry = tag(actor.page, 'connection_recovery_action');
  if (await retry.isVisible()) await retry.click({ timeout: 5_000 }).catch(() => {});
  await expect(notice).not.toBeAttached({ timeout: 20_000 });
  await expect(tag(actor.page, 'leaderboard_screen')).toBeAttached();
});
