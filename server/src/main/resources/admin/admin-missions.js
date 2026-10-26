'use strict';
const scopes = { PERSONAL: 'Cá nhân', CLAN: 'Bang hội' };
const actions = { PLAY: 'Chơi trận', WIN: 'Thắng trận', CORRECT: 'Chọn đúng số', ACCURACY: 'Độ chính xác', PERFECT_WIN: 'Thắng hoàn hảo', WIN_STREAK: 'Chuỗi thắng', CHECK_IN: 'Điểm danh', DONATE_GOLD: 'Quyên góp Vàng', DONATE_GEMS: 'Quyên góp Gem' };
const periods = { DAILY: 'Hàng ngày', WEEKLY: 'Hàng tuần' };
const difficulties = { EASY: 'Dễ', NORMAL: 'Thường', HARD: 'Khó', ELITE: 'Tinh anh' };
const states = { DRAFT: 'Bản nháp', PUBLISHED: 'Đã phát hành', ARCHIVED: 'Đã lưu trữ' };
const modes = { CASUAL: 'Đấu thường', RANKED: 'Đấu hạng' };
const noMatch = action => ['WIN_STREAK', 'CHECK_IN', 'DONATE_GOLD', 'DONATE_GEMS'].includes(action);
const rewardKeys = { xp: 'XP', gold: 'Vàng', gems: 'Gem', contributionPoints: 'Điểm cống hiến' };
const units = draft => draft.scope === 'CLAN' && draft.action === 'WIN_STREAK' ? 'thành viên đạt mốc' : ({ CORRECT: 'số đúng', DONATE_GOLD: 'Vàng', DONATE_GEMS: 'Gem', CHECK_IN: 'lần điểm danh', WIN_STREAK: 'trận thắng liên tiếp' }[draft.action] || 'trận');
export function missionSummary(draft) {
  const condition = draft.matchType ? ` · ${modes[draft.matchType] || draft.matchType}` : '';
  if (draft.scope === 'CLAN' && draft.action === 'WIN_STREAK') return `${draft.target} thành viên đạt chuỗi ${draft.streakThreshold} trận thắng liên tiếp${condition}`;
  return `${actions[draft.action] || draft.action}: ${draft.target} ${units(draft)}${draft.action === 'ACCURACY' ? ` đạt ít nhất ${draft.accuracyPercent}%` : ''}${condition}`;
}
export function validateMission(draft) {
  const errors = {}, integer = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;
  if (!/^[A-Z][A-Z0-9_]{0,31}$/.test(draft.code) || draft.code === 'LEGACY_CLAN_QUEST') errors.code = 'Dùng 1–32 chữ in hoa, số hoặc dấu gạch dưới; bắt đầu bằng chữ. Mã legacy được dành riêng.';
  for (const [key, options] of Object.entries({ scope: scopes, action: actions, period: periods, difficulty: difficulties })) if (!Object.hasOwn(options, draft[key])) errors[key] = 'Chọn giá trị được hỗ trợ.';
  if (!integer(draft.target, 1, 1e9)) errors.target = 'Mục tiêu phải là số nguyên từ 1 đến 1.000.000.000.';
  for (const key of Object.keys(rewardKeys)) if (!integer(draft.reward?.[key] ?? 0, 0, 1e9)) errors[key] = 'Thưởng phải là số nguyên từ 0 đến 1.000.000.000.';
  if (draft.scope === 'PERSONAL' && draft.reward?.contributionPoints) errors.contributionPoints = 'Điểm cống hiến chỉ dành cho nhiệm vụ bang.';
  const currency = draft.action === 'DONATE_GOLD' ? 'gold' : draft.action === 'DONATE_GEMS' ? 'gems' : null;
  if (currency && draft.reward?.[currency] > (draft.scope === 'CLAN' ? 0 : draft.target)) errors[currency] = draft.scope === 'CLAN' ? 'Nhiệm vụ bang không hoàn lại cùng tài sản quyên góp. Chọn XP hoặc tài sản khác.' : 'Thưởng cùng tài sản không được vượt mục tiêu quyên góp.';
  if (draft.action === 'ACCURACY' ? !integer(draft.accuracyPercent, 1, 100) : draft.accuracyPercent != null) errors.accuracyPercent = 'Độ chính xác từ 1 đến 100%, chỉ dùng cho hành động Độ chính xác.';
  if (draft.scope === 'CLAN' && draft.action === 'WIN_STREAK' ? !integer(draft.streakThreshold, 1, 1e9) : draft.streakThreshold != null) errors.streakThreshold = 'Nhập chuỗi thắng mỗi thành viên từ 1 đến 1.000.000.000; chỉ dùng cho chuỗi thắng bang.';
  if (draft.matchType != null && (noMatch(draft.action) || !Object.hasOwn(modes, draft.matchType))) errors.matchType = 'Loại trận không phù hợp hành động này.';
  for (const key of ['startsOn', 'endsOn']) if (draft[key] != null && (!/^\d{4}-\d{2}-\d{2}$/.test(draft[key]) || draft[key] < '1970-01-01' || !Number.isFinite(Date.parse(draft[key])) || new Date(draft[key]).toISOString().slice(0, 10) !== draft[key])) errors[key] = 'Nhập ngày hợp lệ từ năm 1970 đến 9999.';
  if (draft.startsOn && draft.endsOn && draft.startsOn >= draft.endsOn) errors.endsOn = 'Ngày kết thúc phải sau ngày bắt đầu (không bao gồm ngày kết thúc).';
  return errors;
}
// The document owns credentials and request fences. This small submission owns only retry identity.
export function createMissionSubmission({ role, current, send, ownerOnly = false, uuid = () => crypto.randomUUID() }) {
  let busy = false, signature, requestId, conflicted = false;
  return { async submit(payload) {
    if (!current()) throw new Error('Yêu cầu đã hết hiệu lực.');
    if (!['OWNER', 'OPERATOR'].includes(role()) || (ownerOnly && role() !== 'OWNER')) throw new Error('Không có quyền thực hiện.');
    if (busy) throw new Error('Đang xử lý yêu cầu.');
    if (conflicted) throw new Error('Tải lại nhiệm vụ trước khi tiếp tục.');
    const next = JSON.stringify(payload);
    if (signature !== next) { signature = next; requestId = uuid(); }
    busy = true;
    try { return await send({ ...payload, requestId }); }
    catch (error) { if (error.status === 409) conflicted = true; throw error; }
    finally { busy = false; }
  } };
}
export function openMissions(ui) {
  const { node, append, button, iconButton, field, modal, feedback, shell, channel, pagination, amount, api, role } = ui;
  const base = '/missions', path = id => `${base}/${encodeURIComponent(id)}`;
  const permitted = () => ['OWNER', 'OPERATOR'].includes(role()), owner = () => role() === 'OWNER';
  let selectedScope = 'PERSONAL';
  function select(parent, label, name, options) {
    const wrapper = node('div', null, { class: 'field' }), control = node('select', null, { id: 'field-' + name, name });
    for (const [value, text] of Object.entries(options)) control.append(node('option', text, { value }));
    parent.append(append(wrapper, node('label', label, { for: control.id }), control)); return control;
  }
  function facts(parent, entries) {
    const dl = node('dl', null, { class: 'facts' });
    for (const [label, value] of entries) dl.append(append(node('div'), node('dt', label), node('dd', value)));
    parent.append(dl);
  }
  const rewardText = reward => Object.entries(rewardKeys).map(([key, label]) => `${amount(reward[key] || 0)} ${label}`).join(' · ');
  function definition(parent, draft, reward = draft.reward) {
    parent.append(node('h3', missionSummary(draft)));
    facts(parent, [['Nhóm', scopes[draft.scope]], ['Kỳ', periods[draft.period]], ['Độ khó', difficulties[draft.difficulty]], ['Mục tiêu', `${amount(draft.target)} ${units(draft)}`], ['Thưởng một lần / người nhận', rewardText(reward)], ['Lịch · Giờ Việt Nam (UTC+7)', `${draft.startsOn || 'Từ kỳ khả dụng tiếp theo'} → ${draft.endsOn || 'Không giới hạn'} (kết thúc không bao gồm)`]]);
    if (draft.scope === 'CLAN') parent.append(node('p', 'Tiến độ tổng hợp theo bang; mỗi người nhận một lần theo quyền do máy chủ xác định. Điểm cống hiến tách khỏi XP bang.', { class: 'muted' }));
  }
  function missionModal(title, opener) { const surface = modal(title, opener); surface.dialog.classList.add('mission-modal', 'event-modal'); return surface; }
  function modalRequest(surface) {
    const next = channel();
    return () => { const task = next(), current = task.current; task.current = () => current() && permitted() && surface.dialog.isConnected && surface.dialog.open; return task; };
  }
  function errorFields(form, notices, errors, controls) {
    form.querySelectorAll('.mission-field-error').forEach(el => el.remove());
    for (const control of Object.values(controls)) { control.removeAttribute('aria-invalid'); const hints = (control.getAttribute('aria-describedby') || '').split(/\s+/).filter(id => id && id !== control.id + '-error'); if (hints.length) control.setAttribute('aria-describedby', hints.join(' ')); else control.removeAttribute('aria-describedby'); }
    if (!Object.keys(errors).length) return false;
    notices.fail(new Error('Kiểm tra các trường sau:'), true);
    const list = node('ul'); notices.error.append(list);
    for (const [key, message] of Object.entries(errors)) {
      const control = controls[key]; if (!control) continue;
      control.parentElement.hidden = false;
      const id = control.id + '-error'; control.setAttribute('aria-invalid', 'true'); control.setAttribute('aria-describedby', [control.getAttribute('aria-describedby'), id].filter(Boolean).join(' '));
      control.parentElement.append(node('p', message, { id, class: 'mission-field-error' }));
      const link = node('a', message, { href: '#' + control.id }); link.addEventListener('click', event => { event.preventDefault(); control.focus(); }); list.append(append(node('li'), link));
    }
    return true;
  }
  function mutation(form, submit, notices, surface, target, payload, success, method = 'POST', ownerOnly = false) {
    const next = modalRequest(surface); let task, busy = false, conflicted = false;
    const submission = createMissionSubmission({ role, ownerOnly, current: () => permitted() && surface.dialog.open && surface.dialog.isConnected, send: body => { task = next(); return api(target, task, body, method); } });
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (busy || conflicted || !permitted() || (ownerOnly && !owner())) return;
      notices.clear(); const body = payload(); if (!body) return;
      const original = submit.textContent; busy = true; surface.pending(true); submit.disabled = true; submit.textContent = 'Đang xử lý…';
      try { const result = await submission.submit(body); if (task?.current()) success(result); }
      catch (error) {
        if (!task?.current() || error.name === 'AbortError') return;
        if (error.status === 409) {
          conflicted = true; notices.fail(new Error('Phiên bản hoặc yêu cầu đã thay đổi. Tải lại nhiệm vụ rồi xem trước lại trước khi tiếp tục.'), true);
          notices.error.append(button('Tải lại nhiệm vụ', () => { surface.finish(); target === base ? list() : detail(target.split('/')[2]); }));
        } else notices.fail(new Error((error.message || 'Không thể kết nối.') + ' Kết quả chưa được xác nhận; thử lại cùng nội dung giữ nguyên mã yêu cầu.'), true);
      } finally { if (task?.current()) { busy = false; surface.pending(false); submit.textContent = original; submit.disabled = conflicted; if (conflicted) form.querySelectorAll('input,textarea,select').forEach(control => control.disabled = true); } }
    });
  }
  function list() {
    if (!permitted()) return;
    const main = shell('Nhiệm vụ', 'missions'); main.append(node('p', 'Quản lý catalog cho các kỳ tương lai. Nhiệm vụ và phần thưởng của kỳ đã xác lập luôn được giữ nguyên.', { class: 'muted' }));
    const toolbar = append(node('div', null, { class: 'toolbar' }), iconButton('Tạo nhiệm vụ', () => edit(), 'add', { class: 'primary' }), iconButton('Làm mới nhiệm vụ', () => load(currentPage), 'refresh')); main.append(toolbar);
    const groups = node('div', null, { class: 'mission-groups', role: 'group', 'aria-label': 'Nhóm catalog nhiệm vụ' }); main.append(groups);
    for (const [value, text] of Object.entries(scopes)) groups.append(button(text, () => { selectedScope = value; for (const control of groups.children) control.setAttribute('aria-pressed', String(control.textContent === text)); load(0); }, { 'aria-pressed': String(selectedScope === value) }));
    const search = node('form', null, { class: 'event-search mission-search' }); main.append(search);
    const query = field(search, 'Tìm mã nhiệm vụ', 'mission-query', 'search', { maxlength: '128' }), state = select(search, 'Trạng thái nhiệm vụ', 'mission-state', { '': 'Tất cả trạng thái', ...states }); search.append(iconButton('Tìm nhiệm vụ', null, 'search', { type: 'submit' }));
    const notices = feedback(main), results = node('div', null, { class: 'results' }); main.append(results); const next = channel(); let currentPage = 0;
    search.addEventListener('submit', event => { event.preventDefault(); load(0); }); state.addEventListener('change', () => load(0));
    async function load(page = 0) {
      currentPage = page; const task = next(); results.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải nhiệm vụ…';
      try {
        const params = new URLSearchParams({ page, pageSize: 25, scope: selectedScope }); if (state.value) params.set('state', state.value); if (query.value.trim()) params.set('query', query.value.trim());
        const data = await api(base + '?' + params, task); if (!task.current() || !permitted()) return;
        notices.status.textContent = `${amount(data.total)} nhiệm vụ ${scopes[selectedScope].toLowerCase()}.`;
        if (!data.items.length) results.append(node('p', 'Không có nhiệm vụ phù hợp. Tạo bản nháp để bổ sung catalog.', { class: 'muted' }));
        for (const mission of data.items) {
          const info = append(node('div', null, { class: 'row-info' }), node('strong', mission.draft.code), node('p', missionSummary(mission.draft)), node('p', `${periods[mission.draft.period]} · ${difficulties[mission.draft.difficulty]}`, { class: 'muted' }), node('p', rewardText(mission.draft.reward), { class: 'muted' }), node('p', `Hiệu lực đã phát hành: ${mission.effectiveFrom || 'Chưa phát hành'}`, { class: 'muted' }));
          results.append(append(node('article', null, { class: 'event-row mission-row' }), info, node('span', states[mission.state], { class: 'badge' }), iconButton('Xem nhiệm vụ ' + mission.draft.code, () => detail(mission.id), 'eye')));
        } pagination(results, data, load);
      } catch (error) { if (task.current() && error.name !== 'AbortError') { notices.fail(error); notices.error.append(button('Thử lại tải nhiệm vụ', () => load(currentPage))); } }
    } load();
  }
  function detail(id, message = '') {
    if (!permitted()) return;
    const main = shell('Chi tiết nhiệm vụ', 'missions'); main.append(append(node('div', null, { class: 'toolbar' }), iconButton('Quay lại nhiệm vụ', list, 'back'), iconButton('Làm mới nhiệm vụ', load, 'refresh')));
    const notices = feedback(main), container = node('div', null, { class: 'stack' }); main.append(node('p', message, { class: 'confirmation', role: 'status' }), container); const next = channel();
    async function load() {
      const task = next(); container.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải nhiệm vụ…';
      try {
        const mission = await api(path(id), task); if (!task.current() || !permitted()) return;
        main.querySelector('h1').textContent = mission.draft.code; notices.status.textContent = ''; container.append(node('span', states[mission.state], { class: 'badge' }));
        const actions = node('div', null, { class: 'event-actions' }); container.append(actions);
        if (mission.state !== 'ARCHIVED') append(actions, button('Xem trước', event => preview(mission, event.currentTarget), { class: 'primary' }), iconButton(mission.publishedRevision ? 'Sửa bản nháp kỳ sau' : 'Sửa bản nháp', () => edit(mission), 'edit'));
        actions.append(button('Nhân bản nhiệm vụ', () => edit(mission, true)));
        if (mission.publishedRevision) { if (owner() && mission.state !== 'ARCHIVED') actions.append(button('Ngừng từ kỳ tiếp theo', () => confirm(mission, 'archive'))); }
        else if (mission.state === 'DRAFT') actions.append(button('Xóa bản nháp', () => confirm(mission, 'delete')));
        facts(container, [['Phiên bản catalog', amount(mission.version)], ['Revision đã phát hành', mission.publishedRevision == null ? 'Chưa có' : String(mission.publishedRevision)], ['Hiệu lực đã phát hành', mission.effectiveFrom || 'Chưa có']]);
        if (mission.publishedRevision) container.append(node('p', `Revision đã phát hành: ${mission.publishedRevision}. Bản phát hành bất biến. Cấu hình dưới đây là ${mission.state === 'DRAFT' ? 'bản nháp cho kỳ sau; lưu chưa thay đổi bản đang áp dụng' : 'cấu hình catalog'}. Sửa/ngừng không đổi tiến độ, thưởng hoặc quyền nhận của instance đã xác lập.`, { class: 'muted' }));
        definition(container, mission.draft);
      } catch (error) { if (task.current() && error.name !== 'AbortError') { notices.fail(error); notices.error.append(button('Thử lại tải nhiệm vụ', load)); } }
    } load();
  }
  function edit(mission, clone = false) {
    if (!permitted() || (mission?.state === 'ARCHIVED' && !clone)) return;
    const existing = mission && !clone, draft = mission?.draft, surface = missionModal(clone ? 'Nhân bản nhiệm vụ' : existing ? 'Sửa bản nháp nhiệm vụ' : 'Tạo nhiệm vụ');
    const form = node('form', null, { class: 'stack modal-form', novalidate: '' }); surface.dialog.append(form); const notices = feedback(form), controls = {};
    form.append(node('p', 'Ngày và ranh giới kỳ theo Giờ Việt Nam (UTC+7). Ngày bắt đầu bao gồm, ngày kết thúc không bao gồm; ngày mới 00:00, tuần mới thứ Hai. Lưu nháp chưa thay đổi kỳ hiện tại.', { class: 'muted' }));
    function input(key, label, type = 'text', attributes = {}) { const control = field(form, label, 'mission-' + key, type, attributes); controls[key] = control; return control; }
    function choice(key, label, options) { return controls[key] = select(form, label, 'mission-' + key, options); }
    const code = input('code', 'Mã nhiệm vụ', 'text', { maxlength: '32', required: '' }); code.value = existing ? draft.code : ''; if (existing) code.readOnly = true;
    const scope = choice('scope', 'Nhóm nhiệm vụ', scopes), action = choice('action', 'Loại hành động', actions), period = choice('period', 'Kỳ nhiệm vụ', periods), difficulty = choice('difficulty', 'Độ khó', difficulties);
    scope.value = draft?.scope || selectedScope; action.value = draft?.action || 'PLAY'; period.value = draft?.period || 'DAILY'; difficulty.value = draft?.difficulty || 'NORMAL';
    const target = input('target', 'Mục tiêu', 'number', { min: '1', max: '1000000000', step: '1' }); target.value = draft?.target ?? 1;
    const matchType = choice('matchType', 'Loại trận', { '': 'Mọi trận trực tuyến đủ điều kiện', ...modes }); matchType.value = draft?.matchType || '';
    const accuracy = input('accuracyPercent', 'Độ chính xác tối thiểu (%)', 'number', { min: '1', max: '100', step: '1' }); accuracy.value = draft?.accuracyPercent ?? 90;
    const streak = input('streakThreshold', 'Chuỗi thắng mỗi thành viên', 'number', { min: '1', max: '1000000000', step: '1' }); streak.value = draft?.streakThreshold ?? 3;
    const hint = node('p', '', { class: 'muted', id: 'mission-condition-hint' }); form.append(hint); target.setAttribute('aria-describedby', hint.id);
    const start = input('startsOn', 'Ngày bắt đầu (bao gồm)', 'date', { min: '1970-01-01', max: '9999-12-31' }), end = input('endsOn', 'Ngày kết thúc (không bao gồm)', 'date', { min: '1970-01-01', max: '9999-12-31' }); start.value = draft?.startsOn || ''; end.value = draft?.endsOn || '';
    form.append(node('h3', 'Thưởng một lần / người nhận'));
    for (const [key, label] of Object.entries(rewardKeys)) { const control = input(key, label, 'number', { min: '0', max: '1000000000', step: '1' }); control.value = clone && !owner() ? 0 : draft?.reward?.[key] || 0; if (!owner()) control.readOnly = true; }
    const economy = node('p', '', { class: 'muted' }); form.append(economy);
    if (!owner()) form.append(node('p', 'Chỉ Owner thay đổi phần thưởng. Operator giữ nguyên thưởng khi sửa nháp, hoặc tạo/nhân bản với thưởng bằng 0.', { class: 'muted' }));
    const reason = input('reason', 'Lý do thay đổi', 'textarea', { required: '', maxlength: '500' });
    const submit = node('button', 'Lưu bản nháp', { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    function conditions() {
      const clanStreak = scope.value === 'CLAN' && action.value === 'WIN_STREAK'; target.parentElement.querySelector('label').textContent = clanStreak ? 'Số thành viên đạt mốc' : 'Mục tiêu';
      matchType.parentElement.hidden = noMatch(action.value); if (noMatch(action.value)) matchType.value = '';
      accuracy.parentElement.hidden = action.value !== 'ACCURACY'; streak.parentElement.hidden = !clanStreak;
      // An incompatible existing reward stays visible for correction; never silently erase an asset.
      controls.contributionPoints.parentElement.hidden = scope.value !== 'CLAN' && +controls.contributionPoints.value === 0;
      hint.textContent = action.value === 'WIN_STREAK' ? `${clanStreak ? 'Đếm thành viên đạt chuỗi cá nhân; không cộng độ dài chuỗi của nhiều người. ' : ''}Chuỗi thắng tính chung Đấu thường và Đấu hạng; không tách theo loại trận.` : `Đơn vị mục tiêu: ${units({ scope: scope.value, action: action.value })}.`;
      economy.textContent = action.value.startsWith('DONATE_') ? scope.value === 'CLAN' ? 'Nhiệm vụ bang: quyên góp Vàng không thưởng lại Vàng; quyên góp Gem không thưởng lại Gem. Chọn XP hoặc tài sản khác.' : 'Nhiệm vụ cá nhân: thưởng cùng tài sản không được vượt mục tiêu quyên góp.' : 'Điểm cống hiến chỉ dùng cho nhiệm vụ bang, tách khỏi XP bang.';
      for (const key of ['gold', 'gems']) controls[key].setAttribute('aria-describedby', 'mission-economy-hint'); economy.id = 'mission-economy-hint';
    }
    scope.addEventListener('change', conditions); action.addEventListener('change', conditions); conditions();
    mutation(form, submit, notices, surface, existing ? path(mission.id) + '/draft' : base, () => {
      const number = control => control.value.trim() === '' ? NaN : Number(control.value);
      const config = { code: code.value.trim(), scope: scope.value, action: action.value, period: period.value, difficulty: difficulty.value, target: number(target), reward: Object.fromEntries(Object.keys(rewardKeys).map(key => [key, number(controls[key])])) };
      if (matchType.value && !noMatch(config.action)) config.matchType = matchType.value;
      if (config.action === 'ACCURACY') config.accuracyPercent = number(accuracy);
      if (config.scope === 'CLAN' && config.action === 'WIN_STREAK') config.streakThreshold = number(streak);
      if (start.value) config.startsOn = start.value; if (end.value) config.endsOn = end.value;
      const errors = validateMission(config); if (!reason.value.trim() || reason.value.length > 500 || /[\x00-\x1f\x7f]/.test(reason.value)) errors.reason = 'Nhập lý do từ 1 đến 500 ký tự, không có ký tự điều khiển.';
      if (errorFields(form, notices, errors, controls)) return null;
      return { draft: config, expectedVersion: existing ? mission.version : 0, reason: reason.value.trim() };
    }, saved => { surface.finish(); detail(saved.id, 'Đã lưu bản nháp. Chưa phát hành; kỳ hiện tại giữ nguyên.'); }, existing ? 'PUT' : 'POST'); surface.show(code);
  }
  function preview(mission, opener) {
    if (!permitted()) return;
    const surface = missionModal('Xem trước nhiệm vụ', opener), content = node('div', null, { class: 'stack' }); surface.dialog.append(content); const notices = feedback(content), next = modalRequest(surface);
    const retry = button('Thử lại xem trước', load), actions = append(node('div', null, { class: 'modal-actions' }), surface.cancel, retry); content.append(actions); surface.show(surface.cancel); load();
    async function load() {
      const task = next(); notices.clear(); notices.status.textContent = 'Đang xem trước…'; surface.pending(true); retry.disabled = true;
      try {
        const data = await api(path(mission.id) + '/preview', task, {}); if (!task.current()) return;
        const result = node('div', null, { class: 'stack' }); definition(result, data.mission.draft, data.reward);
        facts(result, [['Kỳ hiệu lực dự kiến', `${data.effectiveFrom} · ${periods[data.mission.draft.period]} · Giờ Việt Nam (UTC+7)`], ['Phiên bản xem trước', String(data.mission.version)], ['Mã trong danh sách kỳ dự kiến', data.selectedCodes.join(', ') || 'Chưa được chọn']]);
        for (const warning of data.warnings) result.append(node('p', warning === 'MISSION_POOL_SHORTAGE' ? 'Thiếu nhiệm vụ cho một số slot; máy chủ chọn số nhiệm vụ khả dụng, không lặp để lấp slot.' : warning, { class: 'alert' }));
        result.append(node('p', 'Đây là danh sách dự kiến; kế hoạch kỳ được máy chủ đóng băng khi xác lập. Phát hành không thay đổi instance hoặc phần thưởng đã có.', { class: 'muted' }));
        content.insertBefore(result, actions); notices.status.textContent = ''; retry.remove();
        if (owner() && data.mission.state === 'DRAFT') actions.append(button('Tiếp tục phát hành', () => { surface.finish(); confirm(data.mission, 'publish', data.effectiveFrom); }, { class: 'primary' }));
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
      finally { if (task.current()) { surface.pending(false); retry.disabled = false; } }
    }
  }
  function confirm(mission, action, effectiveFrom) {
    if (!permitted() || (action !== 'delete' && !owner())) return;
    const labels = { publish: 'Phát hành nhiệm vụ', archive: 'Ngừng từ kỳ tiếp theo', delete: 'Xóa bản nháp' }, surface = missionModal(labels[action]), form = node('form', null, { class: 'stack modal-form', novalidate: '' }); surface.dialog.append(form); const notices = feedback(form);
    form.append(node('p', action === 'publish' ? `Phát hành ${mission.draft.code} từ ${effectiveFrom} · Giờ Việt Nam (UTC+7). Kỳ đã xác lập giữ nguyên.` : action === 'archive' ? 'Ngừng tạo instance từ kỳ tiếp theo của revision đã phát hành. Lịch sử, tiến độ và quyền nhận thưởng vẫn được giữ; không xóa nhiệm vụ đã dùng.' : 'Chỉ xóa bản nháp chưa từng phát hành. Không xóa lịch sử hoặc phần thưởng.'));
    if (action === 'publish') definition(form, mission.draft);
    const reason = field(form, 'Lý do thay đổi', 'mission-reason', 'textarea', { required: '', maxlength: '500' }), submit = node('button', labels[action], { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutation(form, submit, notices, surface, path(mission.id) + (action === 'delete' ? '/draft' : '/' + action), () => {
      const errors = !reason.value.trim() || reason.value.length > 500 || /[\x00-\x1f\x7f]/.test(reason.value) ? { reason: 'Nhập lý do từ 1 đến 500 ký tự, không có ký tự điều khiển.' } : {};
      if (errorFields(form, notices, errors, { reason })) return null;
      return { expectedVersion: mission.version, reason: reason.value.trim() };
    }, saved => { surface.finish(); action === 'delete' ? list() : detail(saved.id, action === 'publish' ? 'Đã phát hành cho kỳ tiếp theo. Kỳ hiện tại giữ nguyên.' : 'Đã ngừng từ kỳ tiếp theo; lịch sử vẫn được giữ.'); }, action === 'delete' ? 'DELETE' : 'POST', action !== 'delete'); surface.show(reason);
  }
  list();
}
