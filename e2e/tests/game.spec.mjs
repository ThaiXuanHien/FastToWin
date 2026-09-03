import { test, expect, tag, click, login, createRoom, joinAndStart, selectNumber } from '../support/game.mjs';

test('login, F5 and browser Back/Forward preserve the account', async ({ actors }) => {
  const player = await actors('Login');
  await login(player);
  await player.page.reload();
  await expect(tag(player.page, 'home_screen')).toBeAttached();
  await click(player.page, player.page.getByRole('button', { name: 'Phòng', exact: true }));
  await expect(player.page).toHaveURL(/\/rooms$/);
  await player.page.goBack();
  await expect(tag(player.page, 'home_screen')).toBeAttached();
  await player.page.goForward();
  await expect(player.page).toHaveURL(/\/rooms$/);
  await expect(tag(player.page, 'create_room_open')).toBeAttached();
  await expect(tag(player.page, 'auth_open_login')).not.toBeAttached();
});

test('two players finish all 50 numbers, decline rematch and leave results independently', async ({ actors }) => {
  const host = await actors('Host');
  const guest = await actors('Guest');
  await login(host);
  await login(guest);
  const room = await createRoom(host);
  await joinAndStart(host, guest, room);
  for (let number = 1; number <= 50; number++) {
    // 26–24 correct selections, so the result must not be a draw.
    const hostPlays = number <= 25 || number === 50;
    await selectNumber(hostPlays ? host : guest, hostPlays ? guest : host, number);
  }
  await expect(host.page.getByText('CHIẾN THẮNG!', { exact: true })).toBeAttached();
  await expect(guest.page.getByText('THUA CUỘC', { exact: true })).toBeAttached();
  for (const actor of [host, guest]) {
    await expect(actor.page.getByText('260', { exact: true })).toBeAttached();
    await expect(actor.page.getByText('240', { exact: true })).toBeAttached();
  }
  await click(host.page, tag(host.page, 'result_rematch_action'));
  await expect(tag(host.page, 'result_rematch_action')).toHaveText(/ĐÃ MỜI/);
  await expect(tag(guest.page, 'decline_rematch')).toBeAttached();
  await click(guest.page, tag(guest.page, 'decline_rematch'));
  await expect(host.page.getByText('Đối thủ đã từ chối đấu lại.', { exact: true })).toBeAttached();
  await expect(tag(host.page, 'result_rematch_action')).toHaveText(/Mời đấu lại/);
  // Reload the recipient's results too: room and final score must survive F5.
  await guest.page.reload();
  await expect(tag(guest.page, 'result_screen')).toBeAttached();
  await click(host.page, host.page.getByRole('button', { name: 'Về sảnh', exact: true }));
  await expect(host.page).toHaveURL(/\/rooms$/);
  await expect(tag(guest.page, 'result_screen')).toBeAttached();
  await expect(guest.page).toHaveURL(room.url);
});

test('the higher score wins even when the opponent selects number 50', async ({ actors }) => {
  const host = await actors('Leader');
  const guest = await actors('LastTap');
  await login(host);
  await login(guest);
  const room = await createRoom(host);
  await joinAndStart(host, guest, room);
  for (let number = 1; number <= 50; number++) {
    await selectNumber(number <= 26 ? host : guest, number <= 26 ? guest : host, number);
  }
  await expect(host.page.getByText('260', { exact: true })).toBeAttached();
  await expect(host.page.getByText('240', { exact: true })).toBeAttached();
  await expect(host.page.getByText('CHIẾN THẮNG!', { exact: true })).toBeAttached();
  await expect(guest.page.getByText('THUA CUỘC', { exact: true })).toBeAttached();
});

test('mid-game websocket outage and F5 preserve progress without stale rematch error', async ({ actors }) => {
  const host = await actors('Reconnect');
  const guest = await actors('Peer');
  await login(host);
  await login(guest);
  const room = await createRoom(host);
  await joinAndStart(host, guest, room);
  await selectNumber(host, guest, 1);
  await selectNumber(guest, host, 2);
  await selectNumber(host, guest, 3);
  await host.page.reload();
  await expect(host.page).toHaveURL(room.url);
  await expect(host.page.getByText('3/50', { exact: true })).toBeAttached();
  await host.disconnect();
  try {
    await expect(host.page.getByText('Đang kết nối lại trận đấu…', { exact: true })).toBeAttached();
    await expect(host.page.getByText('3/50', { exact: true })).toBeAttached();
  } finally { host.reconnect(); }
  await expect(host.page.getByText('Đang kết nối lại trận đấu…', { exact: true })).not.toBeAttached();
  await selectNumber(guest, host, 4);
  for (let number = 5; number <= 50; number++) await selectNumber(host, guest, number);
  await expect(host.page.getByText(/Chưa kết nối được máy chủ/)).not.toBeAttached();
  await expect(guest.page.getByText(/Chưa kết nối được máy chủ/)).not.toBeAttached();
  await expect(tag(host.page, 'result_rematch_action')).toHaveText(/Mời đấu lại/);
});
