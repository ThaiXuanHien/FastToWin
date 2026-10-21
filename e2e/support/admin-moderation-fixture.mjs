export const base = '/admin/api/moderation';
export const reportId = '55555555-5555-4555-8555-555555555555';
export const ownerId = '00000000-0000-4000-8000-000000000001';
export function moderation(fixture, patch = {}) {
  const state = { failOnce: false, conflict: false, row: { report: { id: reportId, version: 1, status: 'NEW', targetType: 'PLAYER_PROFILE', targetId: ownerId, reason: 'HARASSMENT', note: 'Người chơi dùng biệt danh xúc phạm.', evidenceText: '<img src=x onerror=alert(1)>', targetRevision: 'fixture-revision', createdAtEpochMillis: 1792497000000, closedAtEpochMillis: null, ...patch }, targetChanged: false, targetMissing: false, targetOwnerId: ownerId, restriction: { userId: ownerId, version: 0, blocked: false } } };
  fixture.handlers.set(base + '/reports', async (call, send) => { const items = !call.query.get('status') || call.query.get('status') === state.row.report.status ? [state.row] : []; send(200, { items, page: Number(call.query.get('page') || 0), pageSize: 25, total: items.length }); return true; });
  fixture.handlers.set(base + '/reports/' + reportId, async (_, send) => { send(200, state.row); return true; });
  fixture.handlers.set(base + '/reports/' + reportId + '/resolve', async (call, send) => {
    if (state.conflict) { send(409, { code: 'CONFLICT', message: 'Phiên bản đã thay đổi.' }); return true; }
    if (state.failOnce) { state.failOnce = false; send(503, { code: 'UNAVAILABLE', message: 'Thử lại.' }); return true; }
    state.row.report = { ...state.row.report, status: call.body.status, version: state.row.report.version + 1 };
    send(200, state.row); return true;
  });
  fixture.handlers.set(base + '/users/' + ownerId + '/restriction', async (call, send) => { state.row.restriction = { ...state.row.restriction, blocked: call.body.blocked, version: state.row.restriction.version + 1 }; send(200, state.row.restriction); return true; });
  return state;
}
