// Synthetic HTTP fixtures only. Service/PostgreSQL acceptance is a separate gate.
export const raceConfig = () => ({ goldRate: '0.01', gemRate: '10', rewards: [{ xp: 30, gold: 300, gems: 3 }, { xp: 20, gold: 200, gems: 2 }, { xp: 10, gold: 100, gems: 1 }] });
export const raceView = (overrides = {}) => ({ weekStart: '2099-01-05', version: 1, state: 'DRAFT', config: raceConfig(), ...overrides });
export function races(fixture, initial = []) {
  const rows = new Map(initial.map(row => [row.weekStart, row]));
  const root = '/admin/api/clan-race';
  fixture.handlers.set(root, (call, send) => {
    if (call.method === 'GET') { const page = Number(call.query.get('page') || 0); const all = [...rows.values()].sort((a, b) => b.weekStart.localeCompare(a.weekStart)); send(200, { items: all.slice(page * 25, page * 25 + 25), page, pageSize: 25, total: all.length }); }
    else { const row = raceView({ weekStart: call.body.weekStart, config: call.body.config }); rows.set(row.weekStart, row); routes(row.weekStart); send(200, row); }
    return true;
  });
  function routes(week) {
    const route = root + '/' + week;
    fixture.handlers.set(route, (call, send) => { send(200, rows.get(week)); return true; });
    fixture.handlers.set(route + '/preview', (call, send) => {
      const race = rows.get(week);
      const [whole, fraction = ''] = race.config.goldRate.split('.'); const units = (BigInt(whole) * 1000000n + BigInt(fraction.padEnd(6, '0'))) * 100n;
      const decimal = (units % 1000000n).toString().padStart(6, '0').replace(/0+$/, '');
      const maximumBudget = Object.fromEntries(['xp', 'gold', 'gems'].map(key => [key, race.config.rewards.reduce((sum, reward) => sum + BigInt(reward[key]) * 50n, 0n).toString()]));
      send(200, { race, startEpochMillis: Date.parse(week + 'T00:00:00+07:00'), endEpochMillis: Date.parse(week + 'T00:00:00+07:00') + 604800000, gold100Points: (units / 1000000n).toString() + (decimal ? '.' + decimal : ''), gem1Points: race.config.gemRate, maximumRecipientsPerRank: 50, maximumRecipients: 150, maximumBudget, serverNowEpochMillis: Date.parse('2026-10-25T00:00:00+07:00') }); return true;
    });
    for (const action of ['draft', 'clone', 'publish', 'archive']) fixture.handlers.set(route + '/' + action, (call, send) => {
      const old = rows.get(week);
      if (old.version !== call.body.expectedVersion) { send(409, { code: 'STALE_VERSION', message: 'Stale revision' }); return true; }
      const row = action === 'clone' ? raceView({ weekStart: call.body.weekStart, config: old.config }) : { ...old, version: old.version + 1, ...(action === 'draft' ? { config: call.body.config } : action === 'publish' ? { state: 'PUBLISHED', publishedRevision: old.version + 1 } : { state: 'INACTIVE' }) };
      rows.set(row.weekStart, row); routes(row.weekStart); send(200, row); return true;
    });
  }
  for (const row of initial) routes(row.weekStart);
  return rows;
}
