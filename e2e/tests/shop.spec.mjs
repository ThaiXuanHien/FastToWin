import { randomUUID } from 'node:crypto';
import { test, expect, tag, click, login } from '../support/game.mjs';

test('Gold exchange preserves balances and one wallet entry after retry and reload', async ({ actors, request }, testInfo) => {
  test.skip(!process.env.E2E_POSTGRES_SETTINGS, 'Durable wallet requires the isolated PostgreSQL fixture');
  const player = await actors('Wallet exchange');
  const { page } = player;
  const commands = [];
  const results = [];
  const histories = [];
  page.on('websocket', socket => {
    socket.on('framesent', ({ payload }) => {
      const message = JSON.parse(String(payload));
      if (message.type === 'exchange_gems_for_gold') commands.push(message);
    });
    socket.on('framereceived', ({ payload }) => {
      const message = JSON.parse(String(payload));
      if (message.type === 'gold_exchange_result') results.push(message);
      if (message.type === 'wallet_history') histories.push(message.transactions);
    });
  });
  await login(player);
  await click(page, tag(page, 'header_gem'));
  await expect(tag(page, 'gem_package_fasttowin_gems_80')).toBeAttached();
  await click(page, tag(page, 'shop_tab:GOLD'));
  const exchange = tag(page, 'gold_exchange:gold_bag');
  // Deliberately tap the disabled canvas action and verify it is inert.
  // Keep geometry checks while opting out of the helper's normal enabled wait.
  await expect(exchange).toContainText('Không đủ Gem.');
  await click(page, exchange, { allowDisabled: true });
  await expect(tag(page, 'gold_exchange_confirmation')).not.toBeAttached();
  expect(commands).toHaveLength(0);
  const initialGold = await tag(page, 'header_gold').innerText();
  expect(initialGold).toBe('0');
  await expect(tag(page, 'header_gem')).toHaveText('0');

  // Seed only this disposable account through the local dev verifier. The
  // unavailable Web payment gateway remains real; this is not a provider test.
  const seedSession = await request.post(`${process.env.E2E_API_URL}/auth/login`, { data: player.account });
  expect(seedSession.ok()).toBeTruthy();
  const seedToken = (await seedSession.json()).accessToken;
  const seed = await request.post(`${process.env.E2E_API_URL}/api/store-purchases/verify`, {
    headers: { Authorization: `Bearer ${seedToken}` },
    data: {
      requestId: randomUUID(), store: 'GOOGLE_PLAY', productId: 'fasttowin_gems_80',
      purchaseToken: `dev:GOOGLE_PLAY:${randomUUID()}`,
    },
  });
  expect(seed.ok()).toBeTruthy();
  expect((await seed.json()).status).toBe('GRANTED');
  // A fresh API login used by setup replaces the old device session. Sign in
  // again through the visible form before testing persisted granted funds.
  await login(player, { onboarding: false });
  await expect(tag(page, 'header_gem')).toHaveText('80');
  await click(page, tag(page, 'header_gem'));
  await click(page, tag(page, 'shop_tab:GOLD'));
  await click(page, exchange);
  await expect(tag(page, 'gold_exchange_confirmation')).toBeAttached();
  await click(page, tag(page, 'gold_exchange_confirm'));
  await expect.poll(() => results.at(-1)?.status).toBe('GRANTED');
  // Canvas already closes the modal; Compose can retain the closed dialog's
  // accessibility tree. F5 also exercises restoring authoritative balances.
  await player.navigate(() => page.reload());
  await expect(tag(page, 'header_gem')).toHaveText('70');
  const afterGold = await tag(page, 'header_gold').innerText();
  expect(afterGold).toBe('1000');
  expect(commands).toHaveLength(1);

  // Model replay of the exact client request after a lost acknowledgement;
  // the first exchange above was made entirely through visible controls.
  const duplicate = commands[0];
  expect(player.sockets.size).toBe(1);
  [...player.sockets][0].upstream.send(JSON.stringify(duplicate));
  await expect.poll(() => results.at(-1)?.status).toBe('ALREADY_GRANTED');
  await expect(tag(page, 'header_gem')).toHaveText('70');
  await expect(tag(page, 'header_gold')).toHaveText(afterGold);

  await player.navigate(() => page.reload());
  await expect(tag(page, 'header_gem')).toHaveText('70');
  await expect(tag(page, 'header_gold')).toHaveText(afterGold);
  await click(page, page.getByRole('button', { name: 'Quay lại', exact: true }));
  await click(page, tag(page, 'bottom_tab:account'));
  await click(page, tag(page, 'profile_section_wallet'));
  await expect(tag(page, 'wallet_history_content')).toBeAttached();
  await expect(page.locator('[id^="wallet_transaction:"]')).toHaveCount(2);
  const exchangeEntries = histories.at(-1).filter(row => row.sourceType === 'GOLD_EXCHANGE');
  expect(exchangeEntries).toHaveLength(1);
  expect(exchangeEntries[0]).toMatchObject({ sourceId: duplicate.requestId, goldDelta: 1000, gemsDelta: -10 });
  expect(exchangeEntries[0].createdAtEpochMillis).toBeGreaterThan(0);
  expect(exchangeEntries[0].createdAtEpochMillis).toBeLessThanOrEqual(Date.now() + 3000);
  await page.screenshot({ path: testInfo.outputPath('wallet-exchange-history.png') });
});
