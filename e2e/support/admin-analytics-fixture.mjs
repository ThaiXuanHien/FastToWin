// Bounded, synthetic aggregate evidence. Never represents live store turnover.
export const analyticsPath = '/admin/api/analytics';
const day = 86400000;
export const until = Date.parse('2026-10-06T00:00:00+07:00');
export const from = until - 7 * day;
export const coverage = (source = 'SERVER', status = 'COMPLETE') => ({ source, environment: 'LIVE', status, coverageStart: from - 30 * day, freshness: until - 60000 });
export function group(overrides = {}) {
  return { source: 'SERVER', unit: 'REGISTRATIONS', currency: null, currencyExponent: null, summary: '17',
    series: Array.from({ length: 7 }, (_, i) => ({ from: from + i * day, until: from + (i + 1) * day, value: String([1, 4, 0, 3, 5, 2, 2][i]) })),
    comparison: { range: { from: from - 7 * day, until: from }, value: '10', absoluteChange: '7', percentageChange: '70', coverage: coverage() }, top: [], ...overrides };
}
export function snapshot(metric = 'REGISTRATIONS', groups = [group()], sources = [coverage()]) {
  return { range: { from, until }, comparisonRange: { from: from - 7 * day, until: from }, timezone: 'Asia/Bangkok', bucket: 'DAY', snapshotAt: until, metrics: [{ metric, coverage: sources, groups }] };
}
function defaultSnapshot(call) {
  const metric = call.query.get('metric') || 'REGISTRATIONS';
  if (metric.startsWith('MONEY_') || metric.startsWith('STORE_')) return snapshot(metric, [], [coverage('GOOGLE_PLAY', 'UNAVAILABLE'), coverage('APP_STORE', 'UNAVAILABLE')]);
  const unit = metric === 'REGISTRATIONS' ? 'REGISTRATIONS' : metric === 'ITEM_SPEND' ? 'GOLD' : metric === 'ITEM_BUYERS' ? 'BUYERS' : 'PURCHASES';
  return snapshot(metric, [group({ unit, currency: metric === 'REGISTRATIONS' ? null : 'GOLD', currencyExponent: metric === 'REGISTRATIONS' ? null : 0 })]);
}
export function analytics(fixture, response = defaultSnapshot) {
  fixture.handlers.set(analyticsPath, async (call, send) => { send(200, typeof response === 'function' ? response(call) : response); return true; });
}
