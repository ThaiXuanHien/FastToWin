export const maintenancePath = '/admin/api/maintenance';
export function maintenance(fixture, state = 'ACTIVE', source = 'DATABASE') {
  const saved = { version: 7, state, mode: state === 'ACTIVE' ? null : 'PLANNED', cause: 'UPDATE',
    messageVi: 'Cập nhật máy chủ', messageEn: 'Server update', warningMinutes: 5, drainMinutes: 15,
    startedAtEpochMillis: 1791000000000, transitionAtEpochMillis: 1791000300000,
    drainDeadlineEpochMillis: 1791001200000, expectedEndEpochMillis: null, reason: 'Kiểm thử',
    actorId: 'owner-maintenance-7', changedAtEpochMillis: 1791000060000 };
  const data = { persisted: saved, persistedSource: 'DATABASE', effective: { state, source, mode: saved.mode, version: 6 },
    runtime: { version: 6, observedAtEpochMillis: 1791000000000, activeMatches: 3, available: true } };
  fixture.handlers.set(maintenancePath, async (call, send) => {
    if (call.method === 'POST') {
      Object.assign(saved, call.body, { version: saved.version + 1, state: !call.body.enabled ? 'ACTIVE' : call.body.mode === 'EMERGENCY' ? 'MAINTENANCE' : 'ANNOUNCING' });
    }
    send(200, data); return true;
  });
  return data;
}
export async function openMaintenance(page, fixture, login) {
  await login(page, fixture); await page.getByRole('button', { name: 'Bảo trì', exact: true }).click();
  await page.getByRole('heading', { name: 'Bảo trì', exact: true }).waitFor();
}
export async function fillMaintenance(page) {
  await page.getByLabel('Thông báo tiếng Việt').fill('Cập nhật máy chủ');
  await page.getByLabel('Thông báo tiếng Anh').fill('Server update');
  await page.getByLabel('Lý do nội bộ').fill('Kiểm thử cục bộ');
}
