import { test, expect, tag, click, login } from '../support/game.mjs';

class ReportFixturePeer {
  constructor(actor) {
    this.actor = actor;
    this.messages = [];
    this.socket = new WebSocket(process.env.E2E_API_URL.replace('http:', 'ws:') + '/game');
    this.socket.addEventListener('message', event => this.messages.push(JSON.parse(event.data)));
    this.socket.addEventListener('error', () => {});
  }
  async connect() {
    await new Promise((resolve, reject) => {
      this.socket.addEventListener('open', resolve, { once: true });
      this.socket.addEventListener('error', () => reject(new Error('Local report fixture socket unavailable')), { once: true });
    });
    this.send({ type: 'connect_account', accessToken: this.actor.accessToken, protocolVersion: 44 });
    this.playerId = (await this.next('session_ready')).playerId;
    return this;
  }
  send(message) { this.socket.send(JSON.stringify(message)); }
  async next(type, predicate = () => true) {
    const deadline = Date.now() + 15_000;
    while (Date.now() < deadline) {
      const index = this.messages.findIndex(message => message.type === type && predicate(message));
      if (index >= 0) return this.messages.splice(index, 1)[0];
      await new Promise(resolve => setTimeout(resolve, 30));
    }
    throw new Error(`Missing local report fixture event: ${type}`);
  }
  close() { this.socket.close(); }
}

test('report draft survives target tabs, keyboard resize and scrolling', async ({ actors }, testInfo) => {
  const peer = await actors('Reported player');
  const player = await actors('Reporter', { iosStandalone: true });
  const host = new ReportFixturePeer(peer);
  const reporter = new ReportFixturePeer(player);
  let roomId;
  try {
    await Promise.all([host.connect(), reporter.connect()]);
    // Establish an actual friendship through the isolated local protocol.
    host.send({ type: 'get_profile' });
    const playerCode = (await host.next('profile_data')).profile.playerCode;
    reporter.send({ type: 'send_friend_request', playerCode });
    const incoming = await host.next('friends_data', message => message.social.incomingRequests.some(request => request.userId === reporter.playerId));
    const requestId = incoming.social.incomingRequests.find(request => request.userId === reporter.playerId).requestId;
    host.send({ type: 'respond_friend_request', requestId, accept: true });
    await reporter.next('friends_data', message => message.social.friends.some(friend => friend.userId === host.playerId));
    reporter.close();
    host.send({ type: 'create_room', roomName: 'Report UI fixture', password: '', gameMode: 'ORDER', matchType: 'CASUAL' });
    roomId = (await host.next('room_created')).game.roomId;
    const { page } = player;
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.addInitScript(() => {
      const viewport = window.visualViewport;
      let height = window.innerHeight;
      Object.defineProperty(window, 'outerHeight', { configurable: true, get: () => window.innerHeight });
      Object.defineProperty(window, 'visualViewport', { configurable: true, value: {
        get height() { return height; }, get width() { return window.innerWidth; },
        offsetTop: 0, offsetLeft: 0, pageTop: 0, pageLeft: 0, scale: 1,
        addEventListener(...args) { viewport.addEventListener(...args); },
        removeEventListener(...args) { viewport.removeEventListener(...args); },
      } });
      window.__setReportKeyboardHeight = next => { height = next; viewport.dispatchEvent(new Event('resize')); };
    });
    await login(player);
    await player.navigate(() => page.goto(`/room/${roomId}`));
    await expect(page.getByText('Report UI fixture', { exact: true })).toBeAttached();
    await expect(tag(page, 'ugc_safety_menu')).not.toBeAttached();
    await expect(page.locator('[id^="report:"]')).toHaveCount(0);
    await page.screenshot({ path: testInfo.outputPath('room-without-report-phone.png') });
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.evaluate(() => window.__setReportKeyboardHeight(window.innerHeight));
    await page.screenshot({ path: testInfo.outputPath('room-without-report-desktop.png') });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.evaluate(() => window.__setReportKeyboardHeight(window.innerHeight));
    // Room URLs join the fixture room. Leave through the real control before
    // navigating: an admitted room intentionally owns the current game route.
    await click(page, page.getByRole('button', { name: /^rời phòng$/i }));
    await player.navigate(() => page.goto('/friends'));
    await expect(tag(page, 'friends_screen')).toBeAttached();
    const backgroundInput = page.locator('input[data-fasttowin-native-input][aria-label="Mã người chơi"]');
    await backgroundInput.fill('FTWKEEP12');
    await expect(tag(page, `report:PLAYER_PROFILE:${host.playerId}`)).not.toBeAttached();
    const friendMenu = tag(page, `friend_more:${host.playerId}`);
    for (let scroll = 0; scroll < 8 && await friendMenu.count() === 0; scroll++) {
      await page.mouse.move(195, 420);
      await page.mouse.wheel(0, 240);
      await page.waitForTimeout(150);
    }
    await click(page, friendMenu);
    await page.screenshot({ path: testInfo.outputPath('friend-report-menu-phone.png') });
    await click(page, tag(page, `report:PLAYER_PROFILE:${host.playerId}`));
    await expect(tag(page, 'ugc_report_dialog')).toBeAttached();
    const note = page.locator('textarea[data-fasttowin-native-input]');
    await expect(backgroundInput).not.toBeVisible();
    await click(page, tag(page, 'ugc_report_note'));
    await note.fill('Nội dung cần giữ khi đổi loại báo cáo');
    await click(page, tag(page, 'ugc_report_avatar'));
    await expect(tag(page, 'ugc_report_avatar')).toHaveAttribute('aria-selected', 'true');
    await expect(note).toHaveValue('Nội dung cần giữ khi đổi loại báo cáo');
    await click(page, tag(page, 'ugc_report_nickname'));
    await click(page, tag(page, 'ugc_report_note'));
    await note.tap();
    await note.press('ControlOrMeta+End');
    const original = await note.elementHandle();
    await original.evaluate(element => {
      window.__reportBlurCount = 0;
      element.addEventListener('blur', () => window.__reportBlurCount++);
    });
    for (const height of [700, 500, 400, 500]) {
      await page.evaluate(height => window.__setReportKeyboardHeight(height), height);
      await expect(page.locator('canvas').first()).toHaveCSS('height', `${height}px`);
      await expect(note).toBeFocused();
      await expect.poll(() => note.evaluate((element, visibleHeight) => {
        const box = element.getBoundingClientRect();
        const x = box.x + box.width / 2, y = box.y + box.height / 2;
        return box.top >= 0 && box.bottom <= visibleHeight && document.elementFromPoint(x, y) === element;
      }, height), { message: 'Focused editor stays exposed above the keyboard inside the scrolling body' }).toBe(true);
      await expect.poll(async () => {
        const cancel = await page.getByRole('button', { name: /^hủy$/i }).last().boundingBox();
        return !!cancel && cancel.height > 0 && cancel.y >= 0 && cancel.y + cancel.height <= height;
      }, { message: 'Cancel stays reachable above the keyboard without scrolling the entire form' }).toBe(true);
    }
    expect(await original.evaluate(element => element.isConnected)).toBe(true);
    expect(await page.evaluate(() => window.__reportBlurCount)).toBe(0);
    await note.pressSequentially(' thêm');
    await expect(note).toHaveValue('Nội dung cần giữ khi đổi loại báo cáo thêm');
    await page.screenshot({ path: testInfo.outputPath('report-keyboard-phone.png') });
    // Switching type while the keyboard is still open scrolls through the form.
    // The native editor must follow the canvas rather than cover the tabs/footer.
    await click(page, tag(page, 'ugc_report_avatar'));
    await expect(tag(page, 'ugc_report_avatar')).toHaveAttribute('aria-selected', 'true');
    await expect(note).toHaveValue('Nội dung cần giữ khi đổi loại báo cáo thêm');
    await click(page, tag(page, 'ugc_report_note'));
    await note.tap();
    await expect(note).toBeFocused();
    await page.screenshot({ path: testInfo.outputPath('report-keyboard-avatar-phone.png') });
    await note.evaluate(element => element.blur());
    await page.evaluate(() => window.__setReportKeyboardHeight(window.innerHeight));
    await expect(note).not.toBeFocused();
    await click(page, tag(page, 'ugc_report_avatar'));
    await expect(tag(page, 'ugc_report_avatar')).toHaveAttribute('aria-selected', 'true');
    await expect(note).toHaveValue('Nội dung cần giữ khi đổi loại báo cáo thêm');
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.evaluate(() => window.__setReportKeyboardHeight(window.innerHeight));
    await page.screenshot({ path: testInfo.outputPath('report-desktop.png') });
    await note.evaluate(element => element.blur());
    await click(page, page.getByRole('button', { name: /^hủy$/i }).last());
    await expect(tag(page, 'ugc_report_dialog')).not.toBeAttached();
    await expect(note).not.toBeAttached();
    await expect(backgroundInput).toBeVisible();
    await expect(backgroundInput).toHaveValue('FTWKEEP12');
    await click(page, tag(page, `friend_more:${host.playerId}`));
    await page.screenshot({ path: testInfo.outputPath('friend-report-menu-desktop.png') });
    await expect(tag(page, `report:PLAYER_PROFILE:${host.playerId}`)).toBeAttached();
  } finally {
    if (roomId) host.send({ type: 'leave_room', roomId });
    host.close();
    reporter.close();
  }
});
