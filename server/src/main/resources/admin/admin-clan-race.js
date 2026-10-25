'use strict';
const assets = { xp: 'XP', gold: 'Vàng', gems: 'Gem' };
const configStates = { DRAFT: 'Bản nháp · chưa mở đua', PUBLISHED: 'Đã phát hành', INACTIVE: 'Chưa mở đua · đã ngừng' };
const maximumUnits = 10n ** 38n - 1n;
const integerText = value => new Intl.NumberFormat('vi-VN').format(BigInt(value));
const rewardText = reward => Object.entries(assets).map(([key, label]) => `${integerText(reward[key] ?? 0)} ${label}`).join(' · ');
const bangkokTime = value => new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Bangkok' }).format(new Date(value));
export function raceWeek(now = Date.now()) {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(now));
  const part = type => parts.find(p => p.type === type).value;
  const date = new Date(`${part('year')}-${part('month')}-${part('day')}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() - (date.getUTCDay() + 6) % 7); return date.toISOString().slice(0, 10);
}
function monday(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value < '1970-01-01') return false;
  const date = new Date(value + 'T00:00:00Z');
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value && date.getUTCDay() === 1;
}
function rateUnits(value) {
  if (value.length > 39 || !/^[0-9]+(?:\.[0-9]{1,6})?$/.test(value)) return null;
  const [whole, decimal = ''] = value.split('.'); const units = BigInt(whole) * 1000000n + BigInt(decimal.padEnd(6, '0'));
  return units > 0n && units <= maximumUnits ? units : null;
}
function points(units) { const fraction = (units % 1000000n).toString().padStart(6, '0').replace(/0+$/, ''); return `${units / 1000000n}${fraction ? '.' + fraction : ''}`; }
export function validateRace(weekStart, config, now = Date.now()) {
  const errors = {};
  if (!monday(weekStart) || weekStart <= raceWeek(now)) errors.weekStart = 'Chọn thứ Hai của tuần tương lai (Asia/Bangkok).';
  for (const key of ['goldRate', 'gemRate']) { const units = rateUnits(config[key]); if (units == null || units * (key === 'goldRate' ? 100n : 1n) > maximumUnits) errors[key] = 'Nhập số dương, tối đa 6 chữ số thập phân, trong giới hạn điểm; không dùng số mũ.'; }
  for (const [rank, reward] of config.rewards.entries()) {
    for (const key of Object.keys(assets)) if (!Number.isInteger(reward[key]) || reward[key] < 0 || reward[key] > 2147483647) errors[`${rank}-${key}`] = 'Nhập số nguyên từ 0 đến 2.147.483.647.';
    if (Object.values(reward).every(value => value === 0)) errors[`${rank}-xp`] = 'Mỗi hạng phải có ít nhất một tài sản thưởng dương.';
    const previous = config.rewards[rank - 1];
    if (previous && (Object.keys(assets).some(key => previous[key] < reward[key]) || Object.keys(assets).every(key => previous[key] === reward[key]))) errors[`${rank}-xp`] = 'Hạng trước phải không thấp hơn theo từng tài sản và cao hơn ở ít nhất một tài sản.';
  }
  return errors;
}
export function createRaceSubmission({ role, current, send, ownerOnly = false, uuid = () => crypto.randomUUID() }) {
  let busy = false, signature, requestId, conflicted = false;
  return { async submit(payload) {
    if (!current()) throw new Error('Yêu cầu đã hết hiệu lực.');
    if (!['OWNER', 'OPERATOR'].includes(role()) || (ownerOnly && role() !== 'OWNER')) throw new Error('Không có quyền thực hiện.');
    if (busy) throw new Error('Đang xử lý yêu cầu.');
    if (conflicted) throw new Error('Tải lại tuần đua trước khi tiếp tục.');
    const next = JSON.stringify(payload); if (signature !== next) { signature = next; requestId = uuid(); }
    busy = true;
    try { return await send({ ...payload, requestId }); }
    catch (error) { if (error.status === 409) conflicted = true; throw error; }
    finally { busy = false; }
  } };
}
export function openClanRace(ui) {
  const { node, append, button, iconButton, field, modal, feedback, shell, channel, pagination, api, role } = ui;
  const base = '/clan-race', path = week => base + '/' + encodeURIComponent(week);
  const permitted = () => ['OWNER', 'OPERATOR'].includes(role()), owner = () => role() === 'OWNER';
  const future = race => race.weekStart > raceWeek();
  function facts(parent, entries) { const dl = node('dl', null, { class: 'facts' }); for (const [label, value] of entries) dl.append(append(node('div'), node('dt', label), node('dd', value))); parent.append(dl); }
  function definition(parent, race, budget = false) {
    parent.append(node('h3', race.publishedRevision ? 'Thưởng đã đóng băng' : 'Thưởng bản nháp · chưa phát hành'));
    facts(parent, [['Điểm / 1 Vàng', race.config.goldRate], ['Điểm / 1 Gem', race.config.gemRate]]);
    const rewards = node('div', null, { class: 'race-rewards' }); parent.append(rewards);
    for (const [rank, reward] of race.config.rewards.entries()) {
      const item = append(node('section'), node('h4', `Top ${rank + 1} · mỗi thành viên đủ điều kiện`), node('p', rewardText(reward)));
      if (budget) item.append(node('p', 'Tối đa 50 người / hạng: ' + Object.entries(assets).map(([key, label]) => `${integerText(BigInt(reward[key]) * 50n)} ${label}`).join(' · '), { class: 'muted' }));
      rewards.append(item);
    }
    parent.append(node('p', 'Thưởng cho mỗi thành viên đủ điều kiện, không chia quỹ. Thành viên phải còn thuộc bang ngay trước mốc chốt và có điểm đua dương trong lần gia nhập đó của tuần. Quà nhận qua hộp thư trong 7 ngày từ lúc thực sự sẵn sàng.', { class: 'muted' }));
  }
  function status(race) {
    if (race.state !== 'PUBLISHED') return configStates[race.state] || race.state;
    if (race.settlement?.state === 'SETTLED') return 'Đã chốt';
    if (race.weekStart < raceWeek()) return race.settlement?.state === 'FAILED' ? 'Chốt lỗi · máy chủ sẽ thử lại' : 'Đang chốt';
    return race.weekStart === raceWeek() ? 'Đang đua · thưởng đã đóng băng' : 'Đã phát hành · tuần tương lai';
  }
  function settlement(parent, race) {
    parent.append(node('h3', 'Chốt tuần và phát quà'), node('p', status(race)));
    const value = race.settlement;
    if (!value) { if (race.state === 'PUBLISHED' && race.weekStart < raceWeek()) parent.append(node('p', 'Chưa có kết quả chốt. Máy chủ xử lý tự động; tải lại để xem trạng thái.', { class: 'muted' })); return; }
    facts(parent, [['Lần xử lý', String(value.attempts ?? 0)], ['Mã lỗi gần nhất', value.lastErrorCode || 'Không có']]);
    if (value.result) {
      const result = value.result; facts(parent, [['Revision đã chốt', String(result.revision)], ['Mốc chốt (Asia/Bangkok)', bangkokTime(result.cutoffEpochMillis)], ['Đã chốt lúc (Asia/Bangkok)', bangkokTime(result.settledAtEpochMillis)]]);
      parent.append(node('h3', 'Thưởng lịch sử · mỗi thành viên đủ điều kiện'));
      for (const winner of result.winners) parent.append(append(node('section', null, { class: 'race-history' }), node('h4', `Top ${winner.standing.rank} · ${winner.standing.name}`), node('p', `${winner.standing.points} điểm · ${winner.eligibleMembers} thành viên đủ điều kiện`), node('p', rewardText(winner.reward))));
      parent.append(node('h3', 'Bảng hạng tại mốc chốt'));
      if (!result.standings.length) parent.append(node('p', 'Không có bang có điểm dương tại mốc chốt.'));
      for (const standing of result.standings) parent.append(node('p', `#${standing.rank} · ${standing.name} · cấp ${standing.level} · ${standing.points} điểm`, { class: 'race-history' }));
    }
    const delivery = value.delivery || {};
    facts(parent, [['Chờ phát', String(delivery.pending ?? 0)], ['Chờ đăng ký (trong chờ phát)', String(delivery.waitingRegistration ?? 0)], ['Phát lỗi (trong chờ phát)', String(delivery.failed ?? 0)], ['Đã phát vào hộp thư', String(delivery.delivered ?? 0)], ['Đã nhận (trong đã phát)', String(delivery.claimed ?? 0)], ['Tài khoản không khả dụng', String(delivery.accountUnavailable ?? 0)]]);
    parent.append(node('p', 'Đã chốt không đồng nghĩa đã phát hoặc đã nhận hết. Các bộ đếm có phần giao nhau, không cộng thành tổng. Máy chủ tự thử lại việc phát quà; lịch sử chốt được giữ nguyên.', { class: 'muted' }));
  }
  function raceModal(title, opener) {
    const surface = modal(title, opener); surface.dialog.classList.add('race-modal', 'event-modal');
    // Disabling the focused submit can move focus outside the dialog. Repeated
    // native close requests can also be non-cancelable, so intercept Escape first.
    const guard = event => { if (event.key === 'Escape' && surface.dialog.open && surface.dialog.getAttribute('aria-busy') === 'true') event.preventDefault(); };
    document.addEventListener('keydown', guard, true);
    surface.dialog.addEventListener('close', () => document.removeEventListener('keydown', guard, true), { once: true });
    return surface;
  }
  function modalRequest(surface) { const next = channel(); return () => { const task = next(), current = task.current; task.current = () => current() && permitted() && surface.dialog.isConnected && surface.dialog.open; return task; }; }
  function errors(form, notices, values, controls) {
    form.querySelectorAll('.race-field-error').forEach(item => item.remove());
    for (const control of Object.values(controls)) { control.removeAttribute('aria-invalid'); const hints = (control.getAttribute('aria-describedby') || '').split(/\s+/).filter(id => id && id !== control.id + '-error'); if (hints.length) control.setAttribute('aria-describedby', hints.join(' ')); else control.removeAttribute('aria-describedby'); }
    if (!Object.keys(values).length) return false;
    notices.fail(new Error('Kiểm tra các trường sau:'), true); const list = node('ul'); notices.error.append(list);
    for (const [key, message] of Object.entries(values)) { const control = controls[key]; if (!control) continue; const id = control.id + '-error'; control.setAttribute('aria-invalid', 'true'); control.setAttribute('aria-describedby', [control.getAttribute('aria-describedby'), id].filter(Boolean).join(' ')); control.parentElement.append(node('p', message, { id, class: 'race-field-error' })); const link = node('a', message, { href: '#' + control.id }); link.addEventListener('click', event => { event.preventDefault(); control.focus(); }); list.append(append(node('li'), link)); }
    return true;
  }
  const reasonError = control => !control.value.trim() || control.value.length > 500 || /[\x00-\x1f\x7f-\x9f]/.test(control.value) ? { reason: 'Nhập lý do từ 1 đến 500 ký tự, không có ký tự điều khiển.' } : {};
  function mutation(form, submit, notices, surface, target, payload, success, sourceWeek, method = 'POST', ownerOnly = false) {
    const next = modalRequest(surface); let task, busy = false, conflicted = false;
    const submission = createRaceSubmission({ role, ownerOnly, current: () => permitted() && surface.dialog.isConnected && surface.dialog.open, send: body => { task = next(); return api(target, task, body, method); } });
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (busy || conflicted || !permitted() || (ownerOnly && !owner())) return;
      notices.clear(); const body = payload(); if (!body) return;
      const label = submit.textContent; busy = true; surface.pending(true); submit.disabled = true; submit.textContent = 'Đang xử lý…';
      try { const saved = await submission.submit(body); if (task?.current()) success(saved); }
      catch (error) {
        if (!task?.current() || error.name === 'AbortError') return;
        if (error.status === 409) { conflicted = true; notices.fail(new Error('Phiên bản hoặc tuần đã thay đổi. Tải lại tuần đua và xem trước lại.'), true); notices.error.append(button('Tải lại tuần đua', () => { surface.finish(); sourceWeek ? detail(sourceWeek) : list(); })); }
        else notices.fail(new Error((error.message || 'Không thể kết nối.') + ' Kết quả chưa được xác nhận; thử lại cùng nội dung giữ nguyên mã yêu cầu.'), true);
      } finally { if (task?.current()) { busy = false; surface.pending(false); submit.textContent = label; submit.disabled = conflicted; if (conflicted) form.querySelectorAll('input,select,textarea').forEach(control => control.disabled = true); } }
    });
  }
  function list() {
    if (!permitted()) return;
    const main = shell('Đua top bang', 'clanRace');
    main.append(node('p', 'Tuần từ 00:00 thứ Hai đến 00:00 thứ Hai kế tiếp, Asia/Bangkok. Không tự mở tuần mới hoặc sao chép cấu hình cũ.', { class: 'muted' }));
    const toolbar = node('div', null, { class: 'toolbar' }); if (owner()) toolbar.append(iconButton('Tạo tuần đua', () => edit(), 'add', { class: 'primary' })); toolbar.append(iconButton('Làm mới tuần đua', () => load(currentPage), 'refresh')); main.append(toolbar);
    const current = append(node('section', null, { class: 'race-current stack', 'aria-label': 'Tuần hiện tại' }), node('h2', 'Tuần hiện tại · ' + raceWeek())); main.append(current);
    const lookup = node('form', null, { class: 'race-lookup' }), week = field(lookup, 'Xem tuần theo ngày thứ Hai', 'race-lookup', 'date', { min: '1970-01-01', max: '9999-12-31' }); lookup.append(button('Xem tuần', null, { type: 'submit' })); main.append(lookup); const lookupNotices = feedback(main);
    lookup.addEventListener('submit', event => { event.preventDefault(); lookupNotices.clear(); if (!monday(week.value)) { lookupNotices.fail(new Error('Chọn ngày thứ Hai hợp lệ.'), true); week.setAttribute('aria-invalid', 'true'); return; } week.removeAttribute('aria-invalid'); detail(week.value); });
    main.append(node('h2', 'Cấu hình các tuần và lịch sử')); const notices = feedback(main), results = node('div', null, { class: 'results' }); main.append(results); const next = channel(); let currentPage = 0;
    async function load(page = 0) {
      currentPage = page; const task = next(); results.replaceChildren(); current.replaceChildren(node('h2', 'Tuần hiện tại · ' + raceWeek())); notices.clear(); notices.status.textContent = 'Đang tải tuần đua…';
      try {
        const [data, active] = await Promise.all([api(base + '?' + new URLSearchParams({ page, pageSize: 25 }), task), api(path(raceWeek()), task).catch(error => { if (error.status === 404) return null; throw error; })]);
        if (!task.current() || !permitted()) return; notices.status.textContent = `${integerText(data.total)} cấu hình tuần.`;
        if (!active || active.state !== 'PUBLISHED') current.append(node('p', 'Chưa mở đua tuần này. Không ghi điểm và chưa có quà tuần được phát hành.'));
        else { current.append(node('p', status(active))); definition(current, active); current.append(button('Xem tuần hiện tại', () => detail(active.weekStart))); }
        if (!data.items.length) results.append(node('p', 'Chưa có cấu hình tuần. Owner tạo bản nháp cho tuần tương lai.', { class: 'muted' }));
        for (const race of data.items) results.append(append(node('article', null, { class: 'event-row race-row' }), append(node('div', null, { class: 'row-info' }), node('strong', race.weekStart), node('p', `Phiên bản ${race.version} · ${race.publishedRevision == null ? 'Chưa phát hành' : 'Revision đóng băng ' + race.publishedRevision}`)), node('span', status(race), { class: 'badge' }), iconButton('Xem tuần ' + race.weekStart, () => detail(race.weekStart), 'eye')));
        pagination(results, data, load);
      } catch (error) { if (task.current() && error.name !== 'AbortError') { current.append(node('p', 'Chưa xác định trạng thái tuần hiện tại.', { class: 'muted' })); notices.fail(error); notices.error.append(button('Thử lại tải tuần đua', () => load(currentPage))); } }
    } load();
  }
  function detail(week, message = '') {
    if (!permitted()) return;
    const main = shell('Tuần đua ' + week, 'clanRace'); main.append(append(node('div', null, { class: 'toolbar' }), iconButton('Quay lại tuần đua', list, 'back'), iconButton('Làm mới tuần đua', load, 'refresh')));
    const notices = feedback(main), container = node('div', null, { class: 'stack' }); main.append(node('p', message, { class: 'confirmation', role: 'status' }), container); const next = channel();
    async function load() {
      const task = next(); container.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải tuần đua…';
      try {
        const race = await api(path(week), task); if (!task.current() || !permitted()) return; notices.status.textContent = ''; container.append(node('span', status(race), { class: 'badge' }));
        const actions = node('div', null, { class: 'event-actions' }); container.append(actions);
        actions.append(button('Xem trước', event => preview(race, event.currentTarget)), button('Nhân bản sang tuần tương lai', () => edit(race, true)));
        if (future(race) && race.state === 'DRAFT' && owner()) actions.append(iconButton('Sửa bản nháp', () => edit(race), 'edit'));
        if (future(race) && race.state !== 'INACTIVE' && owner()) actions.append(button('Ngừng tuần tương lai', () => confirm(race, 'archive')));
        facts(container, [['Tuần (Asia/Bangkok)', race.weekStart], ['Phiên bản', String(race.version)], ['Revision đã phát hành', race.publishedRevision == null ? 'Chưa có' : String(race.publishedRevision)]]);
        if (race.publishedRevision) container.append(node('p', 'Cấu hình đã phát hành được đóng băng. Bản nháp tuần tương lai không thay đổi thưởng hoặc điểm của tuần này.', { class: 'muted' }));
        if (!owner()) container.append(node('p', 'Operator được nhân bản nguyên cấu hình sang tuần tương lai và xem trước. Chỉ Owner đặt tỷ lệ, thưởng, phát hành hoặc ngừng tuần tương lai.', { class: 'muted' }));
        definition(container, race); settlement(container, race);
      } catch (error) { if (task.current() && error.name !== 'AbortError') { notices.fail(error.status === 404 ? new Error('Tuần này chưa có cấu hình; chưa mở đua.') : error); notices.error.append(button('Thử lại tải tuần đua', load)); } }
    } load();
  }
  function edit(race, clone = false) {
    if (!permitted() || (!clone && !owner()) || (race && !clone && (!future(race) || race.state !== 'DRAFT'))) return;
    const existing = race && !clone, surface = raceModal(clone ? 'Nhân bản tuần đua' : existing ? 'Sửa bản nháp tuần đua' : 'Tạo tuần đua');
    const form = node('form', null, { class: 'stack modal-form', novalidate: '' }); surface.dialog.append(form); const notices = feedback(form), controls = {};
    const input = (key, label, type = 'text', attributes = {}) => controls[key] = field(form, label, 'race-' + key, type, attributes);
    form.append(node('p', 'Chỉ cấu hình tuần tương lai. Tuần bắt đầu 00:00 thứ Hai (bao gồm), kết thúc 00:00 thứ Hai tiếp theo (không bao gồm), Asia/Bangkok. Lưu nháp chưa mở đua.', { class: 'muted' }));
    const week = input('weekStart', 'Tuần bắt đầu (thứ Hai)', 'date', { min: '1970-01-01', max: '9999-12-31', required: '' }); week.value = existing ? race.weekStart : ''; week.readOnly = !!existing;
    form.append(node('p', 'Điểm cho 1 đơn vị tiền, số dương tối đa 6 chữ số thập phân. Khoản nhỏ giữ điểm dương chính xác, không làm tròn từng giao dịch về 0.', { id: 'race-rate-hint', class: 'muted' }));
    for (const [key, label] of [['goldRate', 'Điểm / 1 Vàng'], ['gemRate', 'Điểm / 1 Gem']]) { const control = input(key, label, 'text', { inputmode: 'decimal', maxlength: '39', 'aria-describedby': 'race-rate-hint' }); control.value = race?.config[key] ?? ''; control.readOnly = clone; }
    const examples = node('p', '', { class: 'muted', 'aria-live': 'polite' }); form.append(examples);
    function updateExamples() { const gold = rateUnits(controls.goldRate.value), gem = rateUnits(controls.gemRate.value); examples.textContent = gold != null && gem != null && gold * 100n <= maximumUnits ? `Ví dụ: 100 Vàng = ${points(gold * 100n)} điểm; 1 Gem = ${points(gem)} điểm. 1 Vàng = ${points(gold)} điểm. Quyên góp Vàng thực tế theo bội số 100.` : 'Nhập tỷ lệ để xem ví dụ chính xác.'; }
    for (const key of ['goldRate', 'gemRate']) controls[key].addEventListener('input', updateExamples); updateExamples();
    form.append(node('h3', 'Thưởng mỗi thành viên đủ điều kiện'), node('p', 'Mỗi hạng có quà; hạng trước không thấp hơn theo từng tài sản và cao hơn ít nhất một tài sản. Tối đa 50 người/hạng, 150 người/3 hạng; không chia quỹ.', { id: 'race-reward-hint', class: 'muted' }));
    for (let rank = 0; rank < 3; rank++) {
      const group = node('fieldset', null, { class: 'race-reward-fields' }); group.append(node('legend', `Top ${rank + 1} · mỗi thành viên`)); form.append(group);
      for (const [key, label] of Object.entries(assets)) { const control = field(group, `Top ${rank + 1} · ${label}`, `race-${rank}-${key}`, 'text', { inputmode: 'numeric', maxlength: '10', 'aria-describedby': 'race-reward-hint' }); controls[`${rank}-${key}`] = control; control.value = race?.config.rewards[rank][key] ?? ''; control.readOnly = clone; }
    }
    if (clone) form.append(node('p', 'Nhân bản giữ nguyên tỷ lệ và thưởng của cấu hình nguồn. Chỉ Owner sửa kinh tế sau khi lưu nháp.', { class: 'muted' }));
    const reason = input('reason', 'Lý do thay đổi', 'textarea', { maxlength: '500', required: '' }), submit = node('button', 'Lưu bản nháp', { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutation(form, submit, notices, surface, clone ? path(race.weekStart) + '/clone' : existing ? path(race.weekStart) + '/draft' : base, () => {
      const number = text => /^\d{1,10}$/.test(text) && BigInt(text) <= 2147483647n ? Number(text) : NaN;
      const config = clone ? race.config : { goldRate: controls.goldRate.value, gemRate: controls.gemRate.value, rewards: [0, 1, 2].map(rank => Object.fromEntries(Object.keys(assets).map(key => [key, number(controls[`${rank}-${key}`].value)]))) };
      const values = { ...validateRace(week.value, config), ...reasonError(reason) }; if (errors(form, notices, values, controls)) return null;
      return { weekStart: week.value, ...(clone ? {} : { config }), expectedVersion: race?.version ?? 0, reason: reason.value.trim() };
    }, saved => { surface.finish(); detail(saved.weekStart, 'Đã lưu bản nháp; chưa mở đua.'); }, race?.weekStart, existing ? 'PUT' : 'POST', !clone); surface.show(week);
  }
  function preview(race, opener) {
    if (!permitted()) return;
    const surface = raceModal('Xem trước tuần đua', opener), content = node('div', null, { class: 'stack' }); surface.dialog.append(content); const notices = feedback(content), next = modalRequest(surface);
    const retry = button('Thử lại xem trước', load), actions = append(node('div', null, { class: 'modal-actions' }), surface.cancel, retry); content.append(actions); surface.show(surface.cancel); load();
    async function load() {
      const task = next(); notices.clear(); notices.status.textContent = 'Đang xem trước…'; surface.pending(true); retry.disabled = true;
      try {
        const data = await api(path(race.weekStart) + '/preview', task, {}); if (!task.current()) return;
        const result = node('div', null, { class: 'stack' }); definition(result, data.race, true);
        facts(result, [['Tuần và phiên bản', `${data.race.weekStart} · ${data.race.version}`], ['Bắt đầu (bao gồm, Asia/Bangkok)', bangkokTime(data.startEpochMillis)], ['Chốt (không bao gồm, Asia/Bangkok)', bangkokTime(data.endEpochMillis)], ['100 Vàng', data.gold100Points + ' điểm'], ['1 Gem', data.gem1Points + ' điểm'], ['Số người tối đa', `${data.maximumRecipientsPerRank} / hạng · ${data.maximumRecipients} / 3 hạng`], ['Ngân sách tối đa (3 hạng)', rewardText(data.maximumBudget)], ['Máy chủ xác nhận lúc (Asia/Bangkok)', bangkokTime(data.serverNowEpochMillis)]]);
        content.insertBefore(result, actions); notices.status.textContent = ''; retry.remove();
        if (owner() && data.race.state === 'DRAFT' && data.race.weekStart > raceWeek(data.serverNowEpochMillis)) actions.append(button('Tiếp tục phát hành', () => { surface.finish(); confirm(data.race, 'publish', data); }, { class: 'primary' }));
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
      finally { if (task.current()) { surface.pending(false); retry.disabled = false; } }
    }
  }
  function confirm(race, action, previewData) {
    if (!owner() || !future(race) || (action === 'publish' && race.state !== 'DRAFT')) return;
    const label = action === 'publish' ? 'Phát hành tuần đua' : 'Ngừng tuần tương lai', surface = raceModal(label), form = node('form', null, { class: 'stack modal-form', novalidate: '' }); surface.dialog.append(form); const notices = feedback(form);
    form.append(node('p', action === 'publish' ? `Phát hành tuần ${race.weekStart}, phiên bản ${race.version}. Cấu hình được đóng băng; chỉ bắt đầu từ tuần này, không mở bù tuần hiện tại.` : `Ngừng tuần tương lai ${race.weekStart}. Tuần hiện tại, lịch sử và quyền thưởng đã chốt được giữ nguyên.`));
    if (action === 'publish') { definition(form, race, true); facts(form, [['Ngân sách tối đa (150 người)', rewardText(previewData.maximumBudget)], ['Mốc chốt (Asia/Bangkok)', bangkokTime(previewData.endEpochMillis)]]); }
    const reason = field(form, 'Lý do thay đổi', 'race-reason', 'textarea', { maxlength: '500', required: '' }), submit = node('button', label, { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutation(form, submit, notices, surface, path(race.weekStart) + '/' + action, () => { if (errors(form, notices, reasonError(reason), { reason })) return null; return { expectedVersion: race.version, reason: reason.value.trim() }; }, saved => { surface.finish(); detail(saved.weekStart, action === 'publish' ? 'Đã phát hành; cấu hình tuần được đóng băng.' : 'Đã ngừng tuần tương lai.'); }, race.weekStart, 'POST', true); surface.show(reason);
  }
  list();
}
