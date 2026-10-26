import { test, expect, tag, click, fill, login } from '../support/game.mjs';

function scheduleLocalTime(epochMillis, timeZone) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(epochMillis).map(({ type, value }) => [type, value]));
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

async function fillScheduleField(page, id, label, value) {
  // Reveal the Compose field, then target its actual HTML input. A long label
  // can put the semantics centre above the editable native overlay.
  await click(page, tag(page, id));
  const input = page.locator(`input[data-fasttowin-native-input][aria-label="${label}"]`);
  const inputIsExposed = async () => await input.isVisible() && await input.evaluate(node => {
    const box = node.getBoundingClientRect();
    return document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2) === node;
  });
  // A mounted overlay can still be clipped by the canvas modal. Scroll until
  // the native input's centre actually receives a pointer, not merely exists.
  for (let attempt = 0; attempt < 8 && !await inputIsExposed(); attempt++) {
    const dialog = await tag(page, 'arcade_dialog').boundingBox();
    const fields = await page.locator('input, textarea').evaluateAll(nodes => nodes.map(node => {
      const box = node.getBoundingClientRect();
      return { x: box.x, y: box.y, width: box.width, height: box.height };
    }));
    const x = dialog.x + dialog.width / 2;
    const y = [0.5, 0.7, 0.3].map(fraction => dialog.y + dialog.height * fraction)
      .find(candidate => !fields.some(box => box.width > 0 && box.height > 0 &&
        x >= box.x && x <= box.x + box.width && candidate >= box.y && candidate <= box.y + box.height));
    expect(y, 'Canvas scroll point outside native inputs').toBeDefined();
    await page.mouse.move(x, y);
    await page.mouse.wheel(0, 180);
    await page.waitForTimeout(250);
  }
  await expect(input).toBeVisible();
  expect(await inputIsExposed(), 'Native input centre is exposed after canvas scroll').toBe(true);
  // Click the actual input before typing: programmatic fill focus can race
  // Compose moving the HTML overlay, which blurs it before any input event.
  await input.click();
  await expect(input).toBeFocused();
  await input.fill(value);
  await expect(input).toHaveValue(value);
  await page.mouse.click(4, 4);
}

for (const viewport of [
  { name: 'phone', width: 375, height: 812, timeZone: 'Asia/Bangkok', timeZoneLabel: 'Giờ Việt Nam (UTC+7)' },
  { name: 'desktop', width: 1024, height: 768, timeZone: 'UTC', timeZoneLabel: 'UTC' },
]) {
  test(`scheduled tournament creation and check-in persist after reload on ${viewport.name}`, async ({ actors }, testInfo) => {
    const host = await actors(`Schedule${viewport.name}`);
    const { page } = host;
    const commands = [];
    const updates = [];
    page.on('websocket', socket => {
      socket.on('framesent', ({ payload }) => {
        const message = JSON.parse(String(payload));
        if (['create_tournament', 'check_in_tournament'].includes(message.type)) commands.push(message);
      });
      socket.on('framereceived', ({ payload }) => {
        const message = JSON.parse(String(payload));
        if (message.type === 'tournament_updated') updates.push(message.tournament);
      });
    });
    await page.setViewportSize(viewport);
    await login(host);
    await click(page, tag(page, 'home_tournament'));
    await expect(tag(page, 'tournament_screen')).toBeAttached();
    await click(page, tag(page, 'open_create_tournament'));
    await fill(page, tag(page, 'tournament_name'), `Schedule ${viewport.name}`);
    // The memory wallet intentionally has no durable player record, so a paid
    // creation is rejected. Server feedback must be inside the open modal.
    await click(page, tag(page, 'create_tournament'));
    await expect(tag(page, 'tournament_create_error')).toBeAttached();
    await expect(tag(page, 'tournament_create_error')).toContainText('Không tìm thấy dữ liệu được yêu cầu.');
    expect(commands).toHaveLength(1);
    expect(updates).toHaveLength(0);
    await click(page, tag(page, 'tournament_fee_0'));
    await click(page, tag(page, 'tournament_schedule_toggle'));
    await fillScheduleField(page, 'tournament_schedule_local_start', 'Ngày và giờ bắt đầu tại địa phương', 'invalid');
    await fillScheduleField(page, 'tournament_schedule_time_zone', 'Múi giờ', viewport.timeZone);
    await expect(tag(page, 'tournament_schedule_error')).toBeAttached();
    // Deliberately tap the disabled action; invalid input must remain inert.
    await click(page, tag(page, 'create_tournament'), { allowDisabled: true });
    expect(commands).toHaveLength(1);

    // Real server time, with enough room to type and submit. Check-in opens
    // 15 minutes before this start, within 40–100 seconds of this calculation.
    // No production debug clock or database mutation is used.
    const now = Date.now();
    let startsAt = Math.floor((now + 16 * 60_000) / 60_000) * 60_000;
    if (startsAt - 15 * 60_000 - now < 40_000) startsAt += 60_000;
    const startText = scheduleLocalTime(startsAt, viewport.timeZone);
    const opensText = scheduleLocalTime(startsAt - 15 * 60_000, viewport.timeZone);
    await fillScheduleField(page, 'tournament_schedule_local_start', 'Ngày và giờ bắt đầu tại địa phương', startText);
    await page.mouse.click(4, 4);
    await expect(page.getByText(`Đăng ký đóng: ${opensText}`, { exact: true })).toBeAttached();
    await expect(page.getByText(`Check-in mở: ${opensText}`, { exact: true })).toBeAttached();
    await expect(page.getByText(`Bắt đầu: ${startText}`, { exact: true })).toBeAttached();
    await click(page, tag(page, 'create_tournament'));
    await expect.poll(() => updates.at(-1)?.schedule?.startsAtEpochMillis).toBe(startsAt);
    expect(updates.at(-1).schedule.timeZoneId).toBe(viewport.timeZone);
    expect(commands).toHaveLength(2);
    await host.navigate(() => page.reload());
    await expect(tag(page, 'tournament_screen')).toBeAttached();
    await expect(tag(page, 'tournament_countdown')).toBeAttached();
    await expect(tag(page, 'start_tournament')).not.toBeAttached();
    await expect(tag(page, 'tournament_check_in_count')).toContainText('0/4');
    await expect(tag(page, 'tournament_check_in')).toBeAttached({ timeout: 120_000 });
    await click(page, tag(page, 'tournament_check_in'));
    await expect(tag(page, 'tournament_check_in')).toContainText('Đã check-in');
    // Deliberate repeated taps must not submit another check-in command.
    await click(page, tag(page, 'tournament_check_in'), { allowDisabled: true });
    expect(commands.filter(message => message.type === 'check_in_tournament')).toHaveLength(1);
    await expect(tag(page, 'tournament_check_in_count')).toContainText('1/4');
    await page.screenshot({ path: testInfo.outputPath(`scheduled-${viewport.name}-checked-in.png`) });
    await host.navigate(() => page.reload());
    await expect(tag(page, 'tournament_screen')).toBeAttached();
    await expect(tag(page, 'tournament_check_in')).toContainText('Đã check-in');
    // Deliberate repeated taps must not submit another check-in command.
    await click(page, tag(page, 'tournament_check_in'), { allowDisabled: true });
    expect(commands.filter(message => message.type === 'check_in_tournament')).toHaveLength(1);
    await expect(tag(page, 'tournament_check_in_count')).toContainText('1/4');
    await expect(page.getByText(viewport.timeZoneLabel, { exact: true })).toBeAttached();
    // Host cancellation uses the same visible lobby action; the test account
    // and tournament are disposable and teardown owns their cleanup.
    await click(page, tag(page, 'leave_tournament'));
    await expect(tag(page, 'open_create_tournament')).toBeAttached();
    await expect(page.getByRole('button', {
      name: /Giải đã hủy: mọi phí đã trả được hoàn một lần\./,
    })).toBeAttached();
  });
}
