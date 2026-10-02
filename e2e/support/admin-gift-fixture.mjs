import { player, pageOf } from './admin-fixture.mjs';
export const giftId = '00000000-0000-0000-0000-000000000019';
export const counters = { eligible: 10, pending: 1, delivered: 8, claimed: 3, expired: 2, cancelled: 0, skipped: 1, claimedGold: 300, claimedXp: 90 };
export function gifts(fixture) {
  const state = { detail: null, previewCalls: 0 };
  fixture.handlers.set('/admin/api/gift-campaigns', async (call, send) => {
    if (call.method === 'GET') send(200, pageOf(state.detail ? [state.detail] : [], Number(call.query.get('page') || 0), state.detail ? 26 : 0));
    else {
      state.detail = { campaign: { id: giftId, version: 1, state: 'DRAFT', draft: { ...call.body, expiresAtEpochMillis: call.body.expiresAtEpochMillis ?? call.body.availableAtEpochMillis + 30 * 86400000 }, createdAtEpochMillis: Date.now(), publishedAtEpochMillis: null, cancelledAtEpochMillis: null, cancellationReason: null }, counters: Object.fromEntries(Object.keys(counters).map(k => [k, 0])) };
      send(200, state.detail);
    } return true;
  });
  fixture.handlers.set('/admin/api/gift-campaigns/' + giftId, async (call, send) => {
    if (call.method === 'PUT') { state.detail.campaign.draft = { ...call.body }; state.detail.campaign.version++; }
    send(200, state.detail); return true;
  });
  fixture.handlers.set(`/admin/api/gift-campaigns/${giftId}/preview`, async (_, send) => {
    state.previewCalls++; send(200, { campaignId: giftId, version: state.detail.campaign.version, recipientCount: 10, duplicateCount: 2, invalidUserIds: [], totalGold: state.detail.campaign.draft.gold * 10, totalXp: state.detail.campaign.draft.xp * 10, previewToken: 'preview-' + state.previewCalls }); return true;
  });
  for (const operation of ['publish', 'cancel']) fixture.handlers.set(`/admin/api/gift-campaigns/${giftId}/${operation}`, async (call, send) => {
    if (fixture.role !== 'OWNER') { send(403, { code: 'FORBIDDEN', message: 'Không có quyền.' }); return true; }
    state.detail.campaign.state = operation === 'publish' ? 'PUBLISHED' : 'CANCELLED'; state.detail.campaign.version++;
    state.detail.counters = { ...counters, cancelled: operation === 'cancel' ? 6 : 0 };
    state.detail.campaign.cancellationReason = operation === 'cancel' ? call.body.reason : null;
    send(200, state.detail); return true;
  });
  return state;
}
export async function fillGift(page, title = 'Quà kiểm thử') {
  for (const [name, value] of [['Tiêu đề tiếng Việt', title], ['Tiêu đề tiếng Anh', 'Test gift'], ['Nội dung tiếng Việt', 'Cảm ơn bạn!\nQuà tháng 10.'], ['Nội dung tiếng Anh', 'Thank you!'], ['Vàng mỗi người', '100'], ['XP mỗi người', '30'], ['Lý do chiến dịch', 'Local test'], ['Thời gian phát (Bangkok)', '2026-10-02T12:00']]) await page.getByLabel(name, { exact: true }).fill(value);
}
export { player };
