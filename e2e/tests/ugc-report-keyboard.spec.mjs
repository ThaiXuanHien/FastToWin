import { test, expect, tag, click, login } from '../support/game.mjs';

test('report draft survives target tabs, keyboard resize and scrolling', async ({ actors }, testInfo) => {
  const peer = await actors('Reported player');
  // Set up the report target through the real local protocol; room creation
  // itself is covered separately and is not the behavior under test here.
  const socket = new WebSocket(process.env.E2E_API_URL.replace('http:', 'ws:') + '/game');
  const roomId = await new Promise((resolve, reject) => {
    const fail = error => { socket.close(); reject(error); };
    const timer = setTimeout(() => fail(new Error('Local report target room unavailable')), 15_000);
    socket.addEventListener('error', () => { clearTimeout(timer); fail(new Error('Local report target socket failed')); });
    socket.addEventListener('open', () => socket.send(JSON.stringify({
      type: 'connect_account', accessToken: peer.accessToken, protocolVersion: 44,
    })));
    socket.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (message.type === 'session_ready') socket.send(JSON.stringify({
        type: 'create_room', roomName: 'Report UI fixture', password: '', gameMode: 'ORDER', matchType: 'CASUAL',
      }));
      if (message.type === 'room_created') { clearTimeout(timer); resolve(message.game.roomId); }
      if (message.type === 'error') { clearTimeout(timer); fail(new Error(message.code)); }
    });
  });
  try {
    const player = await actors('Reporter', { iosStandalone: true });
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
    await click(page, tag(page, 'ugc_safety_menu'));
    await click(page, page.getByRole('button', { name: `Biệt danh: ${peer.account.displayName}`, exact: true }));
    await expect(tag(page, 'ugc_report_dialog')).toBeAttached();
    const note = page.locator('textarea[data-fasttowin-native-input]');
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
  } finally {
    socket.send(JSON.stringify({ type: 'leave_room', roomId }));
    socket.close();
  }
});
