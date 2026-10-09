// Authorized owned loopback acceptance. No traces, provider calls or credential output.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { randomUUID, createHash } from 'node:crypto';
import assert from 'node:assert/strict';
import { chromium, request } from 'playwright';
const artifact = fileURLToPath(new URL('../../.artifacts/admin-compensation-delivery/task4/', import.meta.url));
const settings = JSON.parse(await readFile(process.env.ADMIN_LIVE_SETTINGS, 'utf8'));
assert.equal(settings.url, 'http://127.0.0.1:18082');
assert.match(settings.schema, /^comp_task4_[0-9a-f]{32}$/);
assert.equal(settings.databaseUrl, 'jdbc:postgresql://127.0.0.1:15432/admin_ui?currentSchema=' + settings.schema);
const browser = await chromium.launch(), context = await browser.newContext(), page = await context.newPage();
const publicHttp = await request.newContext({ baseURL: settings.url });
const evidence = { status: 'RUNNING', target: settings.url, schema: settings.schema, traces: 'off', stages: [], assets: {}, rounds: [] }, accounts = [];
let headers, incident;
const checked = async response => { assert.ok([200, 201].includes(response.status()), 'Unexpected HTTP status ' + response.status()); return response.json(); };
const adminPath = suffix => settings.url + '/admin/api/incidents/' + incident.id + suffix;
const adminGet = suffix => context.request.get(adminPath(suffix)).then(checked);
async function account(name) {
  const auth = await checked(await publicHttp.post('/auth/register', { data: { email: `comp-task4-${randomUUID()}@example.invalid`, password: 'Local-' + randomUUID() + '9!', displayName: name, devicePlatform: 'ANDROID' } })); accounts.push(auth);
  const verification = await checked(await publicHttp.post('/auth/email-verification/request', { data: { accessToken: auth.accessToken } })); assert.match(verification.devEmailVerificationCode || '', /^\d{6}$/);
  await checked(await publicHttp.post('/auth/email-verification/confirm', { data: { accessToken: auth.accessToken, verificationCode: verification.devEmailVerificationCode } }));
  // HTTP sessions are real; identify the persisted player through the established game handshake.
  const ws = new WebSocket(settings.url.replace('http:', 'ws:') + '/game');
  const ready = new Promise((resolve, reject) => { const timer = setTimeout(() => reject(new Error('Session handshake timed out')), 10000); ws.addEventListener('message', event => { const body = JSON.parse(event.data); if (body.type === 'session_ready') { clearTimeout(timer); resolve(body); } }); ws.addEventListener('error', reject); });
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); }); ws.send(JSON.stringify({ type: 'connect_account', accessToken: auth.accessToken, protocolVersion: 43 })); const session = await ready; ws.close(); auth.id = session.playerId; return auth;
}
const playerHeaders = auth => ({ Authorization: 'Bearer ' + auth.accessToken });
const mailbox = auth => publicHttp.get('/api/gifts/mailbox?page=0&pageSize=100', { headers: playerHeaders(auth) }).then(checked);
const claim = (auth, roundId) => publicHttp.post('/api/gifts/mailbox/' + roundId + '/claim', { headers: playerHeaders(auth) }).then(checked);
async function fill(values) { for (const [label, value] of values) await page.getByLabel(label, { exact: true }).fill(String(value)); }
async function roundDraft(number, players, now) {
  await page.getByRole('button', { name: number === 1 ? 'Tạo đợt 1' : 'Tạo đợt bổ sung', exact: true }).click();
  await page.getByLabel('Nguồn người nhận', { exact: true }).waitFor();
  await fill([['Lý do nhận người chơi (VI)', 'Bạn được đền bù cho sự cố cục bộ.'], ['Player receiving reason (EN)', 'You receive compensation for this local incident.'], ['Vàng mỗi người', number === 1 ? 100 : 40], ['Kinh nghiệm mỗi người', number === 1 ? 30 : 12], ['Có thể nhận từ (UTC)', new Date(now - 1000).toISOString().slice(0, 23)], ['Lý do bản nháp nội bộ', 'Owned local round ' + number]]);
  if (number > 1) { await page.getByLabel('Lý do đợt bổ sung nội bộ', { exact: true }).fill('Owned explicit additional round'); await page.getByLabel('Nguồn người nhận', { exact: true }).selectOption('USER_IDS'); await page.getByLabel('UUID người chơi (mỗi dòng hoặc dấu phẩy)', { exact: true }).fill(players.map(a => a.id).join('\n')); }
  const response = page.waitForResponse(r => r.url().endsWith('/rounds') && r.request().method() === 'POST'); await page.getByRole('button', { name: 'Lưu bản nháp đợt', exact: true }).click(); const row = await checked(await response); assert.equal(row.round.roundNumber, number); evidence.rounds.push(row.campaign.id); await page.getByRole('button', { name: 'Xem trước', exact: true }).waitFor(); return row;
}
async function publish(row, expectedPrevious) {
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await page.getByLabel('Lý do xem trước', { exact: true }).fill('Owned local preview');
  const previewResponse = page.waitForResponse(r => r.url().endsWith('/preview')); await page.getByRole('button', { name: 'Tạo bản xem trước', exact: true }).click(); const preview = await checked(await previewResponse);
  assert.equal(preview.recipientCount, '2'); assert.equal(preview.previousClaimedUserCount, expectedPrevious); assert.equal(preview.previousClaimedEntitlementCount, expectedPrevious); assert.equal(preview.selectedPreviouslyClaimedUserCount, expectedPrevious);
  await page.getByRole('button', { name: 'Tiếp tục công bố', exact: true }).waitFor(); await page.getByRole('dialog').evaluate(async el => { await Promise.all(el.getAnimations().map(a => a.finished)); }); await page.screenshot({ path: artifact + `live-round${row.round.roundNumber}-preview.png` });
  await page.getByRole('button', { name: 'Tiếp tục công bố', exact: true }).click(); await page.getByLabel('Lý do công bố nội bộ', { exact: true }).fill('Owned local publish');
  const publishResponse = page.waitForResponse(r => r.url().endsWith('/publish')); await page.getByRole('button', { name: 'Công bố đợt', exact: true }).click(); const response = await publishResponse, published = await checked(response);
  assert.equal(published.report.eligible, '2'); assert.equal(published.campaign.state, 'PUBLISHED'); await page.getByRole('dialog').waitFor({ state: 'detached' });
  return { published, body: response.request().postDataJSON(), preview };
}
async function waitDelivery(auth, id) {
  for (let attempt = 0; attempt < 80; attempt++) { const box = await mailbox(auth), gift = box.items.find(g => g.campaignId === id); if (gift) return gift; await new Promise(resolve => setTimeout(resolve, 100)); }
  throw new Error('Durable delivery missing');
}
try {
  assert.equal((await publicHttp.get('/health')).status(), 200);
  for (const name of ['admin.js', 'admin.css', 'admin-compensation.js']) {
    const response = await publicHttp.get('/admin/' + name); assert.equal(response.status(), 200); assert.equal(response.headers()['cache-control'], 'no-store'); const served = createHash('sha256').update(await response.body()).digest('hex'), source = createHash('sha256').update(await readFile(new URL('../../server/src/main/resources/admin/' + name, import.meta.url))).digest('hex'); assert.equal(served, source); evidence.assets[name] = { source, served };
  }
  const players = [await account('LOCAL compensation claimant'), await account('LOCAL compensation unclaimed')]; evidence.playerIds = players.map(a => a.id);
  await page.goto(settings.url + '/admin/'); await page.getByLabel('Email quản trị', { exact: true }).fill(settings.email); await page.getByLabel('Mật khẩu', { exact: true }).fill((await readFile(settings.passwordFile, 'utf8')).trim());
  const loginResponse = page.waitForResponse(r => r.url().endsWith('/admin/api/login')); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); const session = await checked(await loginResponse); headers = { Origin: settings.url, 'X-Admin-CSRF': session.csrfToken };
  await page.getByRole('button', { name: 'Đền bù', exact: true }).click(); await page.getByRole('button', { name: 'Tạo sự cố', exact: true }).click(); const now = Date.now(), key = 'LOCAL_' + randomUUID().replaceAll('-', '').toUpperCase();
  await fill([['Mã sự cố bất biến', key], ['Tiêu đề người chơi (VI)', 'LOCAL sự cố đền bù'], ['Player title (EN)', 'LOCAL compensation incident'], ['Giải thích người chơi (VI)', 'Kết nối đã gián đoạn trong kiểm thử cục bộ.'], ['Player explanation (EN)', 'Connection interrupted during owned local testing.'], ['Ảnh hưởng từ (UTC, bao gồm)', new Date(now - 60000).toISOString().slice(0, 23)], ['Ảnh hưởng đến (UTC, không bao gồm)', new Date(now).toISOString().slice(0, 23)], ['Mã nguyên nhân nội bộ', 'PRIVATE_CAUSE_TASK4'], ['Bằng chứng nội bộ', 'PRIVATE_EVIDENCE_TASK4'], ['UUID admin chịu trách nhiệm (đang hoạt động)', session.admin.id], ['Lý do ghi nhận nội bộ', 'PRIVATE_AUDIT_TASK4']]);
  const created = page.waitForResponse(r => r.url().endsWith('/admin/api/incidents') && r.request().method() === 'POST'); await page.getByRole('button', { name: 'Lưu sự cố', exact: true }).click(); incident = await checked(await created); evidence.incidentId = incident.id; evidence.stages.push('browser-created-owned-incident'); await page.getByRole('button', { name: 'Tạo đợt 1', exact: true }).waitFor();
  const first = await roundDraft(1, players, now), firstPublish = await publish(first, '0'); evidence.stages.push('browser-round1-preview-publish');
  const late = await account('LOCAL late registration'); evidence.latePlayerId = late.id;
  const gift = await waitDelivery(players[0], first.campaign.id); assert.equal(gift.compensation.roundNumber, 1); assert.equal(gift.compensation.incidentId, incident.id);
  const privateNames = ['evidence', 'causeCode', 'responsibleAdminId', 'createdByAdminId', 'updatedByAdminId', 'supplementalReason', 'requestId', 'reason', 'previewToken']; const publicJson = JSON.stringify(gift);
  for (const key of privateNames) assert.ok(!publicJson.includes('"' + key + '"')); for (const text of ['PRIVATE_CAUSE_TASK4', 'PRIVATE_EVIDENCE_TASK4', 'PRIVATE_AUDIT_TASK4']) assert.ok(!publicJson.includes(text)); evidence.privateFieldsExcluded = privateNames; evidence.publicGiftHash = createHash('sha256').update(publicJson).digest('hex');
  assert.ok(!(await mailbox(late)).items.some(g => g.campaignId === first.campaign.id)); assert.equal((await adminGet('/rounds/' + first.campaign.id)).report.eligible, '2'); evidence.lateRegistrationExcluded = true;
  const paid1 = await claim(players[0], first.campaign.id), replayClaim = await claim(players[0], first.campaign.id); assert.equal(paid1.awardedGold, 100); assert.equal(paid1.awardedXp, 30); assert.equal(replayClaim.gold, paid1.gold); assert.equal(replayClaim.xp, paid1.xp);
  const replayPublish = await checked(await context.request.post(adminPath('/rounds/' + first.campaign.id + '/publish'), { headers, data: firstPublish.body })); assert.deepEqual(replayPublish, firstPublish.published); evidence.identicalPublishRetry = true; evidence.identicalPlayerClaimRetry = true; evidence.publishPayloadHash = createHash('sha256').update(JSON.stringify(firstPublish.body)).digest('hex'); evidence.stages.push('actual-delivery-claim-identical-retries');
  await page.getByRole('button', { name: 'Quay lại sự cố', exact: true }).click(); await page.getByRole('button', { name: 'Tạo đợt bổ sung', exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: 'Sửa sự cố', exact: true }).count(), 0);
  const second = await roundDraft(2, players, Date.now()), secondPublish = await publish(second, '1'); assert.notEqual(second.campaign.id, first.campaign.id); evidence.previousPaidPreview = { distinct: secondPublish.preview.previousClaimedUserCount, entitlements: secondPublish.preview.previousClaimedEntitlementCount, overlap: secondPublish.preview.selectedPreviouslyClaimedUserCount };
  await waitDelivery(players[0], second.campaign.id); await waitDelivery(players[1], second.campaign.id); const paid2 = await claim(players[0], second.campaign.id); assert.equal(paid2.awardedGold, 40); assert.equal(paid2.awardedXp, 12); evidence.stages.push('explicit-browser-round2-previous-paid-preview-and-claim');
  await page.getByRole('button', { name: 'Hủy đợt', exact: true }).click(); await page.getByLabel('Lý do hủy nội bộ', { exact: true }).fill('Owned cancellation of unclaimed'); await page.getByRole('button', { name: 'Xác nhận hủy đợt', exact: true }).click(); await page.getByRole('dialog').waitFor({ state: 'detached' });
  const cancelledClaim = await publicHttp.post('/api/gifts/mailbox/' + second.campaign.id + '/claim', { headers: playerHeaders(players[1]) }); assert.equal(cancelledClaim.status(), 409); const terminal = await adminGet('/rounds/' + second.campaign.id); assert.equal(terminal.report.claimed, '1'); assert.equal(terminal.report.cancelled, '1'); assert.equal(terminal.report.claimedGold, '40'); evidence.round2Report = terminal.report; evidence.stages.push('browser-cancel-unclaimed-paid-receipt-retained');
  const adminWallet = await checked(await context.request.get(settings.url + '/admin/api/users/' + players[0].id + '/wallet?page=0&pageSize=100')); for (const [id, gold, xp] of [[first.campaign.id, 100, 30], [second.campaign.id, 40, 12]]) { const entries = adminWallet.items.filter(entry => entry.sourceType === 'COMPENSATION' && entry.sourceId === id); assert.equal(entries.length, 1); assert.equal(entries[0].goldDelta, gold); assert.equal(entries[0].xpDelta, xp); } evidence.walletReceiptsPerRound = 1;
  evidence.status = 'PASS';
} finally {
  if (headers && incident) {
    for (const id of evidence.rounds) { const row = await adminGet('/rounds/' + id); if (row.campaign.state !== 'CANCELLED') await checked(await context.request.post(adminPath('/rounds/' + id + '/cancel'), { headers, data: { incidentVersion: incident.version, expectedVersion: row.campaign.version, requestId: randomUUID(), reason: 'Owned local fixture completion' } })); }
    await context.request.post(settings.url + '/admin/api/logout', { headers });
  }
  for (const auth of accounts) await publicHttp.post('/auth/logout', { data: { refreshToken: auth.refreshToken } });
  await writeFile(artifact + 'live-integration.json', JSON.stringify(evidence, null, 2)); await context.close(); await browser.close(); await publicHttp.dispose();
}
console.log(JSON.stringify({ status: evidence.status, stages: evidence.stages, walletReceiptsPerRound: evidence.walletReceiptsPerRound, lateRegistrationExcluded: evidence.lateRegistrationExcluded }));
