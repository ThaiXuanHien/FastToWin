import { pageOf } from './admin-fixture.mjs';
export const eventPath = '/admin/api/events';
export function eventView(overrides = {}) {
  return { id: '00000000-0000-0000-0000-000000000018', version: 3, state: 'DRAFT', actorId: 'editor-2', creatorId: 'creator-1', reason: 'Local fixture draft', changedAtEpochMillis: 1791417600000,
    draft: { nameVi: 'Cuối tuần nhân đôi', nameEn: 'Double weekend', contentVi: 'Chơi trận trực tuyến.\nNội dung <script>alert(1)</script> chỉ là văn bản.', contentEn: 'Online matches only.', timezone: 'America/New_York', startsAtEpochMillis: Date.parse('2026-10-31T12:00:00Z'), endsAtEpochMillis: Date.parse('2026-11-01T12:00:00Z'), goldMultiplier: 2, xpMultiplier: 1, matchTypes: ['CASUAL', 'RANKED'] }, ...overrides };
}
export function events(fixture, initial = eventView()) {
  const state = { event: structuredClone(initial), token: 'exact-server-preview-v3' };
  const authorized = send => { if (fixture.role !== 'SUPPORT') return true; send(403, { code: 'FORBIDDEN', message: 'Forbidden' }); return false; };
  fixture.handlers.set(eventPath, async (call, send) => {
    if (!authorized(send)) return true;
    if (call.method === 'GET') {
      const stateFilter = call.query.get('state'), query = call.query.get('query') || '';
      send(200, pageOf((!stateFilter || stateFilter === state.event.state) && state.event.draft.nameVi.toLowerCase().includes(query.toLowerCase()) ? [state.event] : [], Number(call.query.get('page') || 0), 26));
    } else { state.event = eventView({ version: 1, draft: call.body.draft, reason: call.body.reason }); send(200, state.event); }
    return true;
  });
  fixture.handlers.set(eventPath + '/' + state.event.id, async (_, send) => { if (authorized(send)) send(200, state.event); return true; });
  for (const operation of ['draft', 'preview', 'schedule', 'cancel']) fixture.handlers.set(eventPath + '/' + state.event.id + '/' + operation, async (call, send) => {
    if (!authorized(send)) return true;
    if (operation === 'preview') send(200, { event: state.event, previewToken: state.token, policy: 'MAX_PER_ASSET', serverNowEpochMillis: 1791417600000, overlaps: [{ ...state.event.draft, id: 'overlap', nameVi: 'Sự kiện giao nhau', goldMultiplier: 1, xpMultiplier: 2 }] });
    else if (call.body.expectedVersion !== state.event.version) send(409, { code: 'CONFLICT', message: 'Phiên bản đã thay đổi.' });
    else { state.event = { ...state.event, version: state.event.version + 1, state: operation === 'draft' ? 'DRAFT' : operation === 'schedule' ? 'SCHEDULED' : 'CANCELLED', ...(operation === 'draft' ? { draft: call.body.draft } : {}), ...(operation === 'schedule' ? { publisherId: 'owner-1', publishedAtEpochMillis: 1791417600000 } : {}) }; send(200, state.event); }
    return true;
  });
  return state;
}
