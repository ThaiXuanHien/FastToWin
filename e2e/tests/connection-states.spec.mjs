import { test, expect, tag, click, login } from '../support/game.mjs';

// Phone-sized walk through every connectivity state outside a match. Screenshots are attached
// for design review; assertions guard the behaviour (no full-screen takeover, no red error).
test('connection notice moves from reconnecting to offline to restored without a red service error', async ({ actors }, testInfo) => {
  const actor = await actors('States');
  const { page, context } = actor;
  let failStatus = 0;
  let offline = false;
  const sockets = new Set();
  await context.routeWebSocket('**/game', route => {
    // Offline: the handshake fails at once, like a phone without network.
    if (offline) { route.close({ code: 1012, reason: 'offline' }); return; }
    const upstream = route.connectToServer();
    const pair = { route, upstream };
    sockets.add(pair);
    route.onClose(async (code, reason) => { sockets.delete(pair); await upstream.close({ code, reason }); });
    upstream.onClose(async (code, reason) => { sockets.delete(pair); await route.close({ code, reason }); });
    upstream.onMessage(message => route.send(message));
    route.onMessage(message => upstream.send(message));
  });
  await context.route('**/api/status', route => {
    if (offline || failStatus > 0) { failStatus = Math.max(0, failStatus - 1); return route.abort('internetdisconnected'); }
    return route.continue();
  });
  const shot = name => page.screenshot({ path: testInfo.outputPath(`${name}.png`) });
  const redError = () => page.getByText(/Dịch vụ tạm thời không khả dụng/);
  const notice = tag(page, 'connection_recovery_notice');

  await login(actor);
  await expect(tag(page, 'home_screen')).toBeAttached();

  // Like the first poll after the phone resumes the app: one failed status check is not "offline".
  failStatus = 1;
  await page.waitForTimeout(12_000);
  await expect(tag(page, 'offline_screen')).not.toBeAttached();
  await expect(notice).not.toBeAttached();
  await shot('1-home-after-one-failed-status-check');

  // The network goes away: the live socket drops and new requests fail.
  offline = true;
  await Promise.all([...sockets].flatMap(pair => [
    pair.route.close({ code: 1012, reason: 'network lost' }), pair.upstream.close({ code: 1012, reason: 'network lost' }),
  ]));
  await expect(notice).toContainText('Đang kết nối lại…', { timeout: 15_000 });
  await expect(tag(page, 'offline_screen')).not.toBeAttached();
  await expect(redError()).not.toBeAttached();
  await shot('2-reconnecting');

  await expect(notice).toContainText('Bạn đang ngoại tuyến', { timeout: 20_000 });
  await expect(notice).toContainText('Kiểm tra Wi-Fi hoặc dữ liệu di động');
  await expect(tag(page, 'connection_recovery_practice')).toBeAttached();
  await expect(tag(page, 'home_screen')).toBeAttached();
  await expect(redError()).not.toBeAttached();
  await shot('3-offline-home-still-usable');

  offline = false;
  await click(page, tag(page, 'connection_recovery_action'));
  await expect(notice).toContainText('Đã kết nối lại', { timeout: 30_000 });
  await shot('4-back-online');
  await expect(notice).not.toBeAttached({ timeout: 10_000 });
  await expect(redError()).not.toBeAttached();
  await shot('5-home-clean-after-reconnect');
});
