import { player } from './admin-fixture.mjs';
export const base = '/admin/api/incidents';
export const incident = (patch = {}) => ({ id: '11111111-1111-4111-8111-111111111111', incidentKey: 'LOCAL_OUTAGE', titleVi: 'Sự cố kết nối', titleEn: 'Connection incident', descriptionVi: 'Kết nối có thể bị gián đoạn.', descriptionEn: 'Your connection may have been interrupted.', affectedFromEpochMillis: 1791417600000, affectedUntilEpochMillis: 1791421200000, causeCode: 'PRIVATE_DIAGNOSTIC', evidence: 'PRIVATE_EVIDENCE', responsibleAdminId: '22222222-2222-4222-8222-222222222222', version: 2, createdByAdminId: 'creator', updatedByAdminId: 'editor', createdAtEpochMillis: 1791417600000, updatedAtEpochMillis: 1791417600000, ...patch });
export const round = (patch = {}) => ({ incidentId: incident().id, incidentKey: incident().incidentKey, incidentVersion: 2, round: { campaignId: '33333333-3333-4333-8333-333333333333', incidentId: incident().id, roundNumber: 1, supplementalReason: null, receivingReasonVi: 'Bạn được đền bù.', receivingReasonEn: 'You receive compensation.', createdByAdminId: 'creator', updatedByAdminId: 'editor' }, campaign: { id: '33333333-3333-4333-8333-333333333333', version: 3, state: 'DRAFT', createdAtEpochMillis: 1791417600000, publishedAtEpochMillis: null, draft: { titleVi: incident().titleVi, titleEn: incident().titleEn, bodyVi: 'Bạn được đền bù.', bodyEn: 'You receive compensation.', gold: 100, xp: 30, audience: 'USER_IDS', userIds: [player.id], availableAtEpochMillis: 1791417600000, expiresAtEpochMillis: 1794009600000 } }, report: { eligible: '3', pending: '0', delivered: '2', claimed: '1', expired: '0', cancelled: '0', skipped: '0', claimedGold: '100', claimedXp: '30' }, ...patch });
// Intentionally synthetic mapping vector, not a realizable lifecycle snapshot:
// PUBLISHED has no cancelled count; cancellation zeros pending/expired.
// Distinct values expose label/field swaps. Paid totals describe 37 claims
// of the existing 100 GOLD / 30 XP reward; lifecycle semantics are tested elsewhere.
export function reportedRound() {
  const row = round();
  return {
    ...row,
    campaign: { ...row.campaign, state: 'PUBLISHED', publishedAtEpochMillis: 1791417600000 },
    report: {
      eligible: '101', pending: '13', delivered: '83', claimed: '37',
      expired: '17', cancelled: '19', skipped: '5', claimedGold: '3700', claimedXp: '1110',
    },
  };
}
export function compensation(fixture, initialRound = round()) {
  const state = { incident: incident(), rounds: initialRound ? [initialRound] : [], token: 'a'.repeat(64) };
  const pageOf = (items, call) => ({ items, page: Number(call.query.get('page') || 0), pageSize: 25, total: String(items.length) });
  fixture.handlers.set(base + '/capabilities', async (_, send) => { send(200, { sources: [{ source: 'ALL_REGISTERED', available: true, explanation: 'Registered snapshot' }, { source: 'USER_IDS', available: true, explanation: 'Verified UUIDs' }, { source: 'ACTIVITY', available: false, explanation: 'Không có lịch sử hoạt động đầy đủ và thời hạn lưu đáng tin cậy.' }] }); return true; });
  fixture.handlers.set(base, async (call, send) => { if (call.method === 'POST') state.incident = incident(call.body); send(200, call.method === 'POST' ? state.incident : pageOf([state.incident], call)); return true; });
  fixture.handlers.set(base + '/' + incident().id, async (call, send) => { if (call.method === 'PUT') state.incident = { ...state.incident, ...call.body, version: state.incident.version + 1 }; send(200, state.incident); return true; });
  const rounds = base + '/' + incident().id + '/rounds';
  function register(row) {
    const target = rounds + '/' + row.campaign.id;
    fixture.handlers.set(target, async (call, send) => { if (call.method === 'PUT') { row.campaign.draft = { ...row.campaign.draft, ...call.body }; row.campaign.version++; } send(200, row); return true; });
    fixture.handlers.set(target + '/preview', async (_, send) => { send(200, { incidentId: state.incident.id, incidentVersion: state.incident.version, campaignId: row.campaign.id, version: row.campaign.version, roundNumber: row.round.roundNumber, source: row.campaign.draft.audience, userIds: row.campaign.draft.userIds, sampleUserIds: [player.id], invalidUserIds: [], duplicateUserIds: [player.id], duplicateCount: '1', recipientCount: '9007199254740993', totalGold: '900719925474099300', totalXp: '270215977642229790', previousClaimedUserCount: '2', previousClaimedEntitlementCount: '3', selectedPreviouslyClaimedUserCount: '1', previewToken: state.token }); return true; });
    for (const action of ['publish', 'cancel']) fixture.handlers.set(target + '/' + action, async (_, send) => { row.campaign.state = action === 'publish' ? 'PUBLISHED' : 'CANCELLED'; row.campaign.version++; if (action === 'publish') row.campaign.publishedAtEpochMillis = 1791417600000; send(200, row); return true; });
  }
  state.rounds.forEach(register);
  fixture.handlers.set(rounds, async (call, send) => { if (call.method === 'POST') { const draft = round(); draft.round = { ...draft.round, roundNumber: call.body.expectedNextRoundNumber, supplementalReason: call.body.supplementalReason, receivingReasonVi: call.body.receivingReasonVi, receivingReasonEn: call.body.receivingReasonEn }; draft.campaign.draft = { ...draft.campaign.draft, ...call.body }; draft.campaign.version = 1; state.rounds.unshift(draft); register(draft); send(200, draft); } else send(200, pageOf(state.rounds, call)); return true; });
  return state;
}
