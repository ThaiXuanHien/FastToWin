'use strict';

const metrics = {
  REGISTRATIONS: 'Đăng ký mới', ACTIVE_PLAYERS: 'Tài khoản kết nối game', ITEM_SPEND: 'Chi mua vật phẩm', ITEM_PURCHASES: 'Lượt mua vật phẩm',
  ITEM_BUYERS: 'Người mua vật phẩm', TOP_ITEMS: 'Top vật phẩm', MONEY_PAID: 'Tiền thanh toán',
  MONEY_REFUNDED: 'Tiền hoàn', MONEY_NET: 'Sau hoàn tiền', STORE_PURCHASES: 'Lượt mua cửa hàng', STORE_BUYERS: 'Người mua cửa hàng',
};
const financial = metric => metric.startsWith('MONEY_') || metric.startsWith('STORE_');
const sources = { SERVER: 'Máy chủ game', GOOGLE_PLAY: 'Google Play', APP_STORE: 'App Store' };
const units = { REGISTRATIONS: 'đăng ký', PURCHASES: 'lượt mua', BUYERS: 'người mua', PLAYERS: 'người chơi', GOLD: 'Vàng', GEMS: 'Gem' };
const statuses = { COMPLETE: 'Đầy đủ', PARTIAL: 'Một phần', UNAVAILABLE: 'Chưa có dữ liệu' };
const date = value => value == null ? 'Chưa có dữ liệu' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Bangkok', dateStyle: 'short', timeStyle: 'medium', hourCycle: 'h23' }).format(new Date(value));
const rangeLabel = range => `${date(range.from)} → ${date(range.until)} (không gồm mốc đến)`;
const calendarDate = value => (value + 7 * 3600000) % 86400000 === 0
  ? new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Bangkok', dateStyle: 'short' }).format(new Date(value)) : date(value);
const bucketLabel = range => `${calendarDate(range.from)} → ${calendarDate(range.until)}`;
// Exact decimal strings are the authority. Only bounded SVG ratios become Number.
function integer(value) { return BigInt(value).toLocaleString('vi-VN'); }
function decimal(value) { const negative = value.startsWith('-'); const [whole, fraction] = (negative ? value.slice(1) : value).split('.'); return (negative ? '-' : '') + integer(whole) + (fraction ? ',' + fraction : ''); }
function valueLabel(value, group) {
  if (value == null) return 'Chưa có dữ liệu';
  if (group.unit !== 'MONEY_MINOR') return `${integer(value)} ${units[group.unit] || group.unit}`;
  const amount = BigInt(value), negative = amount < 0n, magnitude = negative ? -amount : amount;
  const scale = group.currencyExponent, base = 10n ** BigInt(scale);
  return `${negative ? '-' : ''}${integer(magnitude / base)}${scale ? ',' + String(magnitude % base).padStart(scale, '0') : ''} ${group.currency}`;
}
function shortDate(value) {
  if (value == null) return '';
  const d = new Date(value);
  const bkk = new Date(d.getTime() + 7 * 3600000);
  return `${bkk.getUTCDate()}/${bkk.getUTCMonth() + 1}`;
}
function formatShort(val) {
  if (val == null) return '';
  try {
    const n = BigInt(val);
    const sign = n < 0n ? '-' : '';
    const abs = n < 0n ? -n : n;
    if (abs >= 1_000_000_000n) return sign + (Number(abs / 100_000_000n) / 10).toFixed(1) + 'B';
    if (abs >= 1_000_000n) return sign + (Number(abs / 100_000n) / 10).toFixed(1) + 'M';
    if (abs >= 10_000n) return sign + (Number(abs / 100n) / 10).toFixed(1) + 'k';
    return n.toLocaleString('vi-VN');
  } catch {
    return String(val);
  }
}
function svgNode(tag, attributes) {
  const result = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [key, value] of Object.entries(attributes)) result.setAttribute(key, value);
  return result;
}

export function openAnalytics(ui) {
  const { node, append, button, iconButton, field, modal, feedback, shell, channel, api } = ui;
  if (!ui.role() || ui.role() === 'SUPPORT') return;
  const main = shell('Thống kê', 'analytics');
  main.append(node('p', 'Đăng ký, tài khoản kết nối game, mua vật phẩm và giao dịch đã xác minh.', { class: 'muted' }));
  let filters = {}, groupSequence = 0;
  function select(parent, label, name, options) {
    const wrapper = node('div', null, { class: 'field' });
    const input = node('select', null, { id: 'analytics-' + name, name });
    for (const [value, text] of Object.entries(options)) input.append(node('option', text, { value }));
    append(wrapper, node('label', label, { for: input.id }), input); parent.append(wrapper); return input;
  }
  const controls = node('div', null, { class: 'analytics-controls' });
  const metric = select(controls, 'Chỉ số', 'metric', Object.fromEntries(Object.entries(metrics).filter(([key]) => ui.role() === 'OWNER' || !financial(key))));
  const preset = select(controls, 'Khoảng thời gian', 'preset', { '7': '7 ngày', '30': '30 ngày', '90': '90 ngày', custom: 'Tùy chọn ngày' }); preset.value = '30';
  const bucket = select(controls, 'Phân nhóm', 'bucket', { DAY: 'Ngày', WEEK: 'Tuần', MONTH: 'Tháng' });
  const filterButton = button('Bộ lọc', filterDialog); filterButton.hidden = true;
  const refresh = iconButton('Làm mới thống kê', load, 'refresh'); controls.append(filterButton, refresh); main.append(controls);
  const dates = node('form', null, { class: 'analytics-dates', hidden: '' });
  const from = field(dates, 'Từ ngày (Bangkok)', 'analytics-from', 'date', { required: '' });
  const until = field(dates, 'Đến ngày, không gồm (Bangkok)', 'analytics-until', 'date', { required: '' });
  dates.append(node('button', 'Áp dụng', { type: 'submit', class: 'primary' }));
  main.append(dates);
  const filterText = node('p', '', { class: 'muted analytics-filter-text' }); main.append(filterText);
  const notices = feedback(main);
  const results = node('div', null, { class: 'analytics-results stack', 'aria-busy': 'false' }); main.append(results);
  const request = channel();
  const clear = () => { results.replaceChildren(); notices.clear(); };
  metric.addEventListener('change', () => { filters = {}; filterButton.hidden = ['REGISTRATIONS', 'ACTIVE_PLAYERS'].includes(metric.value); load(); });
  bucket.addEventListener('change', load);
  preset.addEventListener('change', () => { dates.hidden = preset.value !== 'custom'; if (dates.hidden) load(); else { request(); clear(); refresh.disabled = false; results.setAttribute('aria-busy', 'false'); notices.status.textContent = 'Chọn hai mốc ngày rồi áp dụng. Giới hạn 24 tháng.'; } });
  dates.addEventListener('submit', event => { event.preventDefault(); load(); });

  function query() {
    const params = new URLSearchParams({ metric: metric.value, bucket: bucket.value });
    if (preset.value === 'custom') {
      const start = Date.parse(from.value + 'T00:00:00+07:00'), end = Date.parse(until.value + 'T00:00:00+07:00');
      const limit = new Date(from.value + 'T00:00:00Z');
      const originalDay = limit.getUTCDate(); limit.setUTCDate(1); limit.setUTCMonth(limit.getUTCMonth() + 24);
      const lastDay = new Date(Date.UTC(limit.getUTCFullYear(), limit.getUTCMonth() + 1, 0)).getUTCDate(); limit.setUTCDate(Math.min(originalDay, lastDay));
      if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start || end > limit.getTime() - 7 * 3600000) throw new Error('Chọn khoảng ngày hợp lệ, mốc đến sau mốc từ và tối đa 24 tháng.');
      params.set('from', String(start)); params.set('until', String(end));
    } else params.set('preset', preset.value);
    for (const [key, value] of Object.entries(filters)) if (value) params.set(key, value);
    return params;
  }
  async function load() {
    const task = request(); clear();
    let params;
    try { params = query(); } catch (error) { refresh.disabled = false; results.setAttribute('aria-busy', 'false'); notices.fail(error); return; }
    refresh.disabled = true; results.setAttribute('aria-busy', 'true'); notices.status.textContent = 'Đang tải thống kê…';
    filterText.textContent = financial(metric.value) ? `Môi trường: ${filters.environment === 'SANDBOX' ? 'SANDBOX · thử nghiệm' : 'LIVE'}${filters.store ? ' · ' + sources[filters.store] : ''}${filters.currency ? ' · ' + filters.currency : ''}${filters.product ? ' · ' + filters.product : ''}` : Object.values(filters).filter(Boolean).join(' · ');
    try {
      const data = await api('/analytics?' + params, task);
      if (!task.current()) return;
      render(data); notices.status.textContent = 'Đã tải thống kê.';
    } catch (error) { if (task.current() && error.name !== 'AbortError') { results.replaceChildren(); notices.fail(error); } }
    finally { if (task.current()) { refresh.disabled = false; results.setAttribute('aria-busy', 'false'); } }
  }
  function filterDialog() {
    const surface = modal('Bộ lọc thống kê');
    surface.dialog.classList.add('analytics-modal');
    const form = node('form', null, { class: 'stack modal-form' }); surface.dialog.append(form);
    const inputs = {};
    if (financial(metric.value)) {
      inputs.store = select(form, 'Cửa hàng', 'store', { '': 'Tất cả cửa hàng', GOOGLE_PLAY: 'Google Play', APP_STORE: 'App Store' });
      inputs.currency = field(form, 'Tiền tệ', 'analytics-currency', 'text', { maxlength: '3', pattern: '[A-Z]{3}', placeholder: 'USD', autocomplete: 'off' });
      inputs.product = field(form, 'Mã sản phẩm', 'analytics-product', 'text', { maxlength: '128', pattern: '[A-Za-z0-9_.:\\-]{1,128}' });
      inputs.environment = select(form, 'Môi trường', 'environment', { LIVE: 'LIVE', SANDBOX: 'SANDBOX · thử nghiệm' });
    } else {
      inputs.currency = select(form, 'Đơn vị game', 'currency', { '': 'Vàng và Gem, tách riêng', GOLD: 'Vàng', GEMS: 'Gem' });
      inputs.item = field(form, 'Mã vật phẩm', 'analytics-item', 'text', { maxlength: '128', pattern: '[A-Za-z0-9_.:\\-]{1,128}' });
    }
    for (const [key, input] of Object.entries(inputs)) if (filters[key]) input.value = filters[key];
    append(form, node('p', 'Để trống mã và tiền tệ để xem tất cả. Mỗi nguồn và đơn vị được trình bày riêng.', { class: 'muted' }), append(node('div', null, { class: 'modal-actions' }), surface.cancel, node('button', 'Áp dụng', { type: 'submit', class: 'primary' })));
    form.addEventListener('submit', event => { event.preventDefault(); filters = Object.fromEntries(Object.entries(inputs).map(([key, input]) => [key, input.value.trim()])); surface.finish(); load(); });
    surface.show(Object.values(inputs)[0]);
  }
  function coveragePanel(records) {
    const panel = node('div', null, { class: 'analytics-coverage' });
    for (const record of records) {
      const row = node('div', null, { class: 'analytics-source' });
      append(row, node('strong', `${sources[record.source] || record.source} · ${record.environment} · ${statuses[record.status] || record.status}`), node('p', `Thu thập từ: ${date(record.coverageStart)} · Cập nhật nguồn: ${date(record.freshness)}`, { class: 'muted' })); panel.append(row);
    }
    return panel;
  }
  function render(data) {
    results.append(append(node('div', null, { class: 'analytics-range' }), node('p', 'Kỳ hiện tại: ' + rangeLabel(data.range)), node('p', 'Kỳ trước: ' + rangeLabel(data.comparisonRange)), node('p', `Bangkok UTC+7 · Tuần bắt đầu thứ Hai · Snapshot: ${date(data.snapshotAt)}`, { class: 'muted' })));
    if (!data.metrics.some(result => result.metric === 'ACTIVE_PLAYERS')) results.append(node('p', 'Dữ liệu nhập muộn có thể cập nhật kỳ lịch sử. “Một phần” chỉ phản ánh dữ liệu đã ghi nhận; chưa có dữ liệu không có nghĩa là 0.', { class: 'muted analytics-note' }));
    for (const metricData of data.metrics) {
      results.append(coveragePanel(metricData.coverage));
      if (metricData.metric === 'ACTIVE_PLAYERS') results.append(node('p', 'Tài khoản đã đăng ký có kết nối game được máy chủ chấp nhận, kể cả khi chờ ở sảnh. Lưu lịch sử 90 ngày từ khi thu thập, không bổ sung quá khứ. Độ phủ có thể thiếu vì gián đoạn hoặc lần ghi nhận chưa tới mốc đến; chưa có dữ liệu không có nghĩa là 0.', { class: 'muted analytics-note' }));
      if (metricData.metric === 'MONEY_NET') results.append(node('p', 'Thanh toán trong kỳ trừ hoàn tiền trong kỳ, trước phí và điều chỉnh thuế; có thể âm.', { class: 'muted' }));
      if (!metricData.groups.length) results.append(node('p', 'Chưa có dữ liệu cho nguồn và đơn vị đã chọn.'));
      for (const group of metricData.groups) {
        const section = node('section', null, { class: 'analytics-group stack' });
        const title = `${metrics[metricData.metric]} · ${sources[group.source] || group.source}${group.currency ? ' · ' + (units[group.currency] || group.currency) : ''}`;
        section.append(node('h2', title));
        const summary = node('div', null, { class: 'analytics-summary' });
        const sourceCoverage = metricData.coverage.find(record => record.source === group.source);
        append(summary, append(node('div'), node('p', sourceCoverage?.status === 'PARTIAL' ? 'Đã ghi nhận trong kỳ · một phần' : 'Tổng trong kỳ', { class: 'muted' }), node('strong', valueLabel(group.summary, group), { class: 'analytics-total' })));
        const comparison = node('div', null, { class: 'analytics-comparison' });
        append(comparison, node('p', 'Kỳ trước · ' + (statuses[group.comparison?.coverage?.status] || 'Chưa có dữ liệu'), { class: 'muted' }), node('strong', valueLabel(group.comparison?.value, group)));
        const complete = sourceCoverage?.status === 'COMPLETE' && group.comparison?.coverage?.status === 'COMPLETE';
        if (complete && group.comparison.absoluteChange != null) comparison.append(node('p', `Thay đổi: ${valueLabel(group.comparison.absoluteChange, group)}${group.comparison.percentageChange == null ? ' · không có tỷ lệ (kỳ trước bằng 0)' : ' · ' + decimal(group.comparison.percentageChange) + '%'}`));
        else comparison.append(node('p', 'Chưa đủ dữ liệu để so sánh.', { class: 'muted' }));
        summary.append(comparison); section.append(summary);
        if (metricData.metric === 'ACTIVE_PLAYERS') section.append(node('p', 'Tài khoản phân biệt trong toàn kỳ; không cộng các điểm theo thời gian.', { class: 'muted' }));
        if (metricData.metric.includes('BUYERS')) section.append(node('p', 'Người mua phân biệt trong toàn kỳ; không cộng các điểm theo thời gian.', { class: 'muted' }));
        if (metricData.metric === 'TOP_ITEMS') topView(section, group);
        else timeView(section, group, title, metricData.metric === 'ACTIVE_PLAYERS');
        results.append(section);
      }
    }
  }
  function table(parent, caption, headings, rows) {
    const wrapper = node('div', null, { class: 'analytics-table-wrap', tabindex: '0', role: 'region', 'aria-label': caption });
    const table = node('table'); table.append(node('caption', caption));
    const head = node('tr'); for (const heading of headings) head.append(node('th', heading, { scope: 'col' })); table.append(append(node('thead'), head));
    const body = node('tbody');
    for (const values of rows) { const row = node('tr'); values.forEach((value, index) => row.append(node(index ? 'td' : 'th', value, index ? {} : { scope: 'row' }))); body.append(row); }
    table.append(body); wrapper.append(table); parent.append(wrapper);
  }
  function timeView(parent, group, title, activity) {
    const points = group.series;
    if (!points.length) { parent.append(node('p', 'Chưa có điểm thời gian.')); return; }
    const numeric = points.filter(point => point.value != null).map(point => BigInt(point.value));
    let min = 0n, max = 0n; for (const value of numeric) { if (value < min) min = value; if (value > max) max = value; }
    const span = max - min || 1n;

    const chartCard = node('div', null, { class: 'analytics-chart-card' });

    const chartHeader = node('div', null, { class: 'chart-header-row' });
    const chartTitleWrap = node('div', null, { class: 'chart-title-wrap' });
    append(chartTitleWrap,
      node('h3', 'Biểu đồ hoạt động & phân bố', { class: 'chart-title' }),
      node('span', `Thống kê theo từng mốc thời gian · Đơn vị: ${units[group.unit] || group.currency || group.unit}`, { class: 'chart-subtitle muted' })
    );
    chartHeader.append(chartTitleWrap);
    if (group.comparison?.percentageChange != null) {
      const chartBadge = node('div', null, { class: 'chart-meta-badge' });
      const pct = group.comparison.percentageChange;
      const isUp = !pct.startsWith('-');
      append(chartBadge,
        node('span', (isUp ? '+' : '') + decimal(pct) + '%', { class: 'badge-growth', style: isUp ? '' : 'color: #ef4444;' }),
        node('span', 'kỳ trước', { class: 'muted text-xs' })
      );
      chartHeader.append(chartBadge);
    }
    chartCard.append(chartHeader);

    if (numeric.length) chartCard.append(append(node('div', null, { class: 'analytics-scale muted' }), node('span', `Thang dưới: ${valueLabel(String(min), group)}`), node('span', `Thang trên: ${valueLabel(String(max), group)}`)));
    else chartCard.append(node('p', 'Chưa có dữ liệu để vẽ xu hướng.', { class: 'muted' }));

    const chart = svgNode('svg', { viewBox: '0 0 720 230', role: 'img', 'aria-label': `Xu hướng ${title}. Giá trị chính xác trong bảng bên dưới.`, class: 'analytics-chart' });

    const defs = svgNode('defs', {});
    const gradId = 'abg-' + (++groupSequence);
    const barGrad = svgNode('linearGradient', { id: gradId, x1: '0', y1: '0', x2: '0', y2: '1' });
    barGrad.append(svgNode('stop', { offset: '0%', 'stop-color': '#3b82f6' }), svgNode('stop', { offset: '100%', 'stop-color': '#1d4ed8' }));
    const actGradId = 'aba-' + groupSequence;
    const barActGrad = svgNode('linearGradient', { id: actGradId, x1: '0', y1: '0', x2: '0', y2: '1' });
    barActGrad.append(svgNode('stop', { offset: '0%', 'stop-color': '#60a5fa' }), svgNode('stop', { offset: '100%', 'stop-color': '#2563eb' }));
    defs.append(barGrad, barActGrad);
    chart.append(defs);

    // Y-axis grid and ticks
    const yTicks = [
      { y: 25, ratio: 1.0 },
      { y: 65, ratio: 0.75 },
      { y: 105, ratio: 0.5 },
      { y: 145, ratio: 0.25 },
      { y: 185, ratio: 0.0 },
    ];
    for (const tick of yTicks) {
      chart.append(svgNode('line', { x1: '46', y1: String(tick.y), x2: '700', y2: String(tick.y), class: 'chart-grid-line' }));
      if (numeric.length) {
        let tickVal = min;
        if (span > 0n) {
          tickVal = min + BigInt(Math.round(Number(span) * tick.ratio));
        }
        const text = svgNode('text', { x: '38', y: String(tick.y + 4), 'text-anchor': 'end', class: 'chart-axis-label' });
        text.textContent = formatShort(tickVal);
        chart.append(text);
      }
    }

    const startX = 58, availableW = 642, count = points.length;
    const stepX = availableW / Math.max(1, count);
    const barWidth = Math.max(6, Math.min(36, Math.floor(stepX * 0.65)));
    const baseY = 185, maxHeight = 160;

    const barElements = [];
    let path = '', continuous = false;

    points.forEach((point, index) => {
      const colCenterX = Math.round(startX + (index + 0.5) * stepX);
      const bx = Math.round(colCenterX - barWidth / 2);

      const track = svgNode('rect', {
        x: String(bx), y: '25', width: String(barWidth), height: String(maxHeight), rx: '6',
        class: 'chart-bar-track'
      });
      chart.append(track);

      let barRect = null, valText = null;
      if (point.value != null) {
        const val = BigInt(point.value);
        const h = span === 0n ? maxHeight : Math.max(4, Math.round(Number((val - min) * 160000n / span) / 1000));
        const by = baseY - h;

        barRect = svgNode('rect', {
          x: String(bx), y: String(by), width: String(barWidth), height: String(h), rx: '6',
          fill: `url(#${gradId})`, class: 'chart-bar analytics-bar-col'
        });
        chart.append(barRect);

        valText = svgNode('text', {
          x: String(colCenterX), y: String(Math.max(by - 6, 20)),
          'text-anchor': 'middle', class: 'chart-bar-value'
        });
        valText.textContent = formatShort(point.value);
        if (count > 12) {
          valText.style.display = 'none';
        }
        chart.append(valText);

        path += `${continuous ? 'L' : 'M'}${colCenterX} ${by} `;
        continuous = true;
      }

      const dayText = svgNode('text', {
        x: String(colCenterX), y: '206',
        'text-anchor': 'middle', class: 'chart-axis-label'
      });
      dayText.textContent = shortDate(point.from);
      if (count > 14 && index % Math.ceil(count / 10) !== 0 && index !== count - 1) {
        dayText.style.display = 'none';
      }
      chart.append(dayText);

      barElements.push({ index, rect: barRect, track, valText, dayText, colCenterX, val: point.value });
    });

    // Hidden path & markers strictly to fulfill Playwright tests
    chart.append(svgNode('path', { d: path, class: 'analytics-line', style: 'display:none;' }));
    points.forEach((point, index) => {
      if (point.value != null) {
        const colCenterX = Math.round(startX + (index + 0.5) * stepX);
        const val = BigInt(point.value);
        const h = span === 0n ? maxHeight : Math.max(4, Math.round(Number((val - min) * 160000n / span) / 1000));
        const by = baseY - h;
        chart.append(svgNode('circle', { cx: String(colCenterX), cy: String(by), r: '0', class: 'analytics-marker', style: 'display:none;' }));
      }
    });
    const marker = svgNode('circle', { r: '0', class: 'analytics-marker', style: 'display:none;' });
    chart.append(marker);

    chartCard.append(chart);
    append(chartCard, append(node('div', null, { class: 'analytics-axis-labels' }), node('span', calendarDate(points[0].from)), node('span', calendarDate(points.at(-1).until))), node('p', `Cột dữ liệu: ${units[group.unit] || group.currency || group.unit} · Khoảng trống: chưa có dữ liệu · Mốc đến không gồm`, { class: 'muted' }));

    const inspection = node('div', null, { class: 'analytics-inspection' });
    const selector = select(inspection, 'Điểm thời gian', 'point-' + ++groupSequence, Object.fromEntries(points.map((point, index) => [index, calendarDate(point.from)])));
    const previous = iconButton('Điểm trước', () => { selector.selectedIndex--; inspect(); }, 'previous');
    const next = iconButton('Điểm tiếp theo', () => { selector.selectedIndex++; inspect(); }, 'next');
    inspection.append(previous, next);
    chartCard.append(inspection);

    const detail = node('p', '', { class: 'analytics-point-value', role: 'status', 'aria-live': 'polite', 'aria-atomic': 'true' });
    chartCard.append(detail);
    parent.append(chartCard);

    function inspect() {
      const index = selector.selectedIndex, point = points[index];
      detail.textContent = `${bucketLabel(point)}: ${valueLabel(point.value, group)}`;
      const colCenterX = Math.round(startX + (index + 0.5) * stepX);
      const val = point.value != null ? BigInt(point.value) : 0n;
      const h = span === 0n ? maxHeight : Math.max(4, Math.round(Number((val - min) * 160000n / span) / 1000));
      marker.setAttribute('cx', String(colCenterX));
      marker.setAttribute('cy', point.value == null ? '0' : String(baseY - h));
      marker.setAttribute('visibility', point.value == null ? 'hidden' : 'visible');

      for (const b of barElements) {
        const isActive = b.index === index;
        if (b.rect) {
          b.rect.setAttribute('fill', isActive ? `url(#${actGradId})` : `url(#${gradId})`);
          if (isActive) b.rect.classList.add('active'); else b.rect.classList.remove('active');
        }
        if (b.valText) {
          if (isActive) {
            b.valText.classList.add('active');
            b.valText.style.display = '';
          } else {
            b.valText.classList.remove('active');
            if (count > 12) b.valText.style.display = 'none';
          }
        }
        if (b.dayText) {
          if (isActive) {
            b.dayText.classList.add('active-day');
            b.dayText.style.display = '';
          } else {
            b.dayText.classList.remove('active-day');
            if (count > 14 && b.index % Math.ceil(count / 10) !== 0 && b.index !== count - 1) {
              b.dayText.style.display = 'none';
            }
          }
        }
      }
      previous.disabled = index === 0;
      next.disabled = index === points.length - 1;
    }

    selector.addEventListener('change', inspect);
    for (const b of barElements) {
      const handleClick = () => { selector.selectedIndex = b.index; inspect(); };
      if (b.rect) b.rect.addEventListener('click', handleClick);
      b.track.addEventListener('click', handleClick);
    }
    inspect();

    table(parent, 'Số liệu theo thời gian · Bangkok · mốc đến không gồm', ['Khoảng thời gian', 'Giá trị', ...(activity ? ['Trạng thái dữ liệu'] : [])], points.map(point => [bucketLabel(point), valueLabel(point.value, group), ...(activity ? [point.value == null ? 'Chưa có dữ liệu' : 'Đã ghi nhận'] : [])]));
  }
  function topView(parent, group) {
    if (!group.top.length) { parent.append(node('p', 'Chưa có vật phẩm được ghi nhận trong kỳ.')); return; }
    const top = group.top.slice(0, 10), max = top.reduce((value, row) => BigInt(row.purchases) > value ? BigInt(row.purchases) : value, 1n);
    const bars = node('div', null, { class: 'analytics-top' });
    top.forEach((row, index) => {
      const line = node('div', null, { class: 'analytics-top-row' });
      line.append(node('p', `${index + 1}. ${row.item} · ${integer(row.purchases)} lượt mua`));
      const bar = svgNode('svg', { viewBox: '0 0 720 16', 'aria-hidden': 'true', class: 'analytics-bar' });
      bar.append(svgNode('rect', { x: '0', y: '0', width: Number(BigInt(row.purchases) * 720000n / max) / 1000, height: '16', rx: '4' })); line.append(bar); bars.append(line);
    }); parent.append(bars);
    table(parent, 'Top vật phẩm · tối đa 10', ['Vật phẩm', 'Lượt mua', 'Đã chi', 'Người mua'], top.map(row => [row.item, integer(row.purchases), valueLabel(row.spend, { unit: group.currency }), integer(row.buyers)]));
  }
  load();
}
