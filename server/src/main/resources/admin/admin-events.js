'use strict';

const states = { DRAFT: 'Bản nháp', SCHEDULED: 'Đã lên lịch', ACTIVE: 'Đang diễn ra', ENDED: 'Đã kết thúc', CANCELLED: 'Đã hủy' };
const modes = { CASUAL: 'Đấu thường', RANKED: 'Đấu hạng' };
const holidays = {
  lunar: ['Tết Nguyên đán', 'Lunar New Year'],
  newyear: ['Tết Dương lịch', 'New Year', '01-01', '01-02'],
  march8: ['Ngày 8 tháng 3', 'March 8', '03-08', '03-09'],
  october20: ['Ngày 20 tháng 10', 'October 20', '10-20', '10-21'],
  halloween: ['Halloween', 'Halloween', '10-31', '11-01'],
  november19: ['Ngày 19 tháng 11', 'November 19', '11-19', '11-20'],
  november20: ['Ngày Nhà giáo Việt Nam', 'Vietnamese Teachers’ Day', '11-20', '11-21'],
  christmas: ['Giáng sinh (24–25/12)', 'Christmas (December 24–25)', '12-24', '12-26'],
};
const utcInput = value => new Date(value).toISOString().slice(0, 23).replace(/:00\.000$/, '').replace(/\.000$/, '');
const policy = 'Mỗi loại thưởng lấy hệ số cao nhất riêng biệt; không cộng dồn hay nhân các sự kiện (không ×4).';
const exclusions = 'Chỉ trận trực tuyến đấu thường / đấu hạng đủ điều kiện. Rời trận có chủ đích nhận 0. Không áp dụng Gem, Elo, giải đấu, ngoại tuyến, nhiệm vụ, điểm danh, thành tích hoặc quà admin. Quà admin là chiến dịch riêng.';

// Auth, CSRF, service worker and shared navigation fences stay in admin.js.
export function openRewardEvents(ui) {
  const { node, append, button, icon, iconButton, field, modal, feedback, shell, channel, pagination, amount, api, role } = ui;
  const base = '/events', path = id => base + '/' + encodeURIComponent(id);
  const permitted = () => role() === 'OWNER' || role() === 'OPERATOR';
  const owner = () => role() === 'OWNER';
  function select(parent, label, name, options) {
    const wrapper = node('div', null, { class: 'field' }), control = node('select', null, { id: 'field-' + name, name });
    for (const [value, text] of Object.entries(options)) control.append(node('option', text, { value }));
    append(wrapper, node('label', label, { for: control.id }), control); parent.append(wrapper); return control;
  }
  function facts(parent, entries) {
    const dl = node('dl', null, { class: 'facts' });
    for (const [label, value] of entries) dl.append(append(node('div'), node('dt', label), node('dd', value)));
    parent.append(dl);
  }
  function factors(parent, draft) {
    const summary = node('div', null, { class: 'event-factors' });
    for (const [label, factor, name] of [['GOLD', draft.goldMultiplier, 'coin'], ['XP', draft.xpMultiplier, 'star']]) {
      const accessible = `${label === 'GOLD' ? 'Vàng (GOLD)' : 'Kinh nghiệm (XP)'} ×${factor}`;
      summary.append(append(node('span', null, { class: 'event-factor', role: 'img', 'aria-label': accessible, title: accessible }), icon(name), node('span', `×${factor}`)));
    } parent.append(summary);
  }
  function date(value, zone) {
    return new Intl.DateTimeFormat('vi-VN', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3, hourCycle: 'h23' }).format(value) + ` (${zone})`;
  }
  function definition(parent, draft) {
    const languages = node('div', null, { class: 'event-languages' });
    for (const [lang, language, name, content] of [['vi', 'Tiếng Việt', draft.nameVi, draft.contentVi], ['en', 'English', draft.nameEn, draft.contentEn]]) {
      languages.append(append(node('section', null, { lang, class: 'event-message stack', 'aria-label': language }), node('p', language, { class: 'muted' }), node('h3', name), node('p', content, { class: 'event-body' })));
    }
    parent.append(languages); factors(parent, draft);
    facts(parent, [['Bắt đầu (bao gồm)', date(draft.startsAtEpochMillis, draft.timezone)], ['Kết thúc (không bao gồm)', date(draft.endsAtEpochMillis, draft.timezone)], ['Bắt đầu UTC', new Date(draft.startsAtEpochMillis).toISOString()], ['Kết thúc UTC', new Date(draft.endsAtEpochMillis).toISOString()], ['Chế độ', draft.matchTypes.map(mode => modes[mode]).join(' · ')], ['Múi giờ hiển thị', draft.timezone]]);
    append(parent, node('p', policy, { class: 'muted' }), node('p', exclusions, { class: 'muted' }));
  }
  function eventModal(title, opener) {
    const surface = modal(title, opener); surface.dialog.classList.add('event-modal'); return surface;
  }
  function modalRequest(surface) {
    const next = channel();
    return () => {
      const task = next(), current = task.current;
      task.current = () => current() && permitted() && surface.dialog.isConnected && surface.dialog.open;
      return task;
    };
  }
  function mutation(form, submit, notices, surface, target, payload, success, method = 'POST') {
    const request = modalRequest(surface); let busy = false, signature, requestId, conflicted = false;
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (busy || conflicted || !permitted()) return;
      let body;
      try { body = payload(); } catch (error) { notices.fail(error, true); return; }
      const next = JSON.stringify(body);
      if (signature !== next) { signature = next; requestId = crypto.randomUUID(); }
      const original = submit.textContent; busy = true; surface.pending(true); submit.disabled = true; submit.textContent = 'Đang xử lý…'; notices.clear(); const task = request();
      try { const result = await api(target, task, { ...body, requestId }, method); if (task.current()) success(result); }
      catch (error) {
        if (!task.current() || error.name === 'AbortError') return;
        if (error.status === 409) {
          conflicted = true; notices.fail(new Error('Phiên bản hoặc yêu cầu đã thay đổi. Tải lại sự kiện và xem trước lại trước khi tiếp tục.'), true);
          const reload = button('Tải lại sự kiện', () => { surface.finish(); if (target === base) list(); else detail(target.split('/')[2]); }); notices.error.append(reload);
        } else notices.fail(new Error((error.message || 'Không thể kết nối.') + ' Kết quả chưa được xác nhận; thử lại cùng nội dung sẽ giữ nguyên mã yêu cầu.'), true);
      } finally {
        if (task.current()) {
          busy = false; surface.pending(false); submit.textContent = original; submit.disabled = conflicted;
          if (conflicted) for (const input of form.querySelectorAll('input,textarea,select')) input.disabled = true;
        }
      }
    });
  }
  function list() {
    if (!permitted()) return;
    const main = shell('Sự kiện thưởng', 'events'); main.append(node('p', 'Tạo bản nháp song ngữ, xem trước rồi xác nhận lịch thưởng trận. Lịch sử đã kết thúc chỉ để xem.', { class: 'muted' }));
    const toolbar = node('div', null, { class: 'toolbar' }); append(toolbar, iconButton('Tạo sự kiện', () => edit(), 'add', { class: 'primary' }), iconButton('Làm mới sự kiện', () => load(currentPage), 'refresh')); main.append(toolbar);
    const search = node('form', null, { class: 'event-search' }); main.append(search);
    const query = field(search, 'Tìm sự kiện', 'event-query', 'search', { maxlength: '128' });
    const filter = select(search, 'Trạng thái sự kiện', 'event-state', { '': 'Tất cả / lịch sử', ...states });
    search.append(iconButton('Tìm sự kiện', null, 'search', { type: 'submit' }));
    const notices = feedback(main), results = node('div', null, { class: 'results' }); main.append(results);
    const request = channel(); let currentPage = 0;
    search.addEventListener('submit', event => { event.preventDefault(); load(0); }); filter.addEventListener('change', () => load(0));
    async function load(page = 0) {
      currentPage = page; results.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải sự kiện…'; const task = request();
      try {
        const params = new URLSearchParams({ page, pageSize: 25 }); if (query.value.trim()) params.set('query', query.value.trim()); if (filter.value) params.set('state', filter.value);
        const data = await api(base + '?' + params, task); if (!task.current() || !permitted()) return;
        notices.status.textContent = `${amount(data.total)} sự kiện.`;
        if (!data.items.length) results.append(node('p', 'Không có sự kiện phù hợp.', { class: 'muted' }));
        for (const event of data.items) {
          const info = append(node('div', null, { class: 'row-info' }), node('strong', event.draft.nameVi)); factors(info, event.draft); info.append(node('p', date(event.draft.startsAtEpochMillis, event.draft.timezone), { class: 'muted' }));
          results.append(append(node('article', null, { class: 'event-row' }), info, node('span', states[event.state], { class: 'badge' }), iconButton('Xem sự kiện ' + event.draft.nameVi, () => detail(event.id), 'eye')));
        } pagination(results, data, load);
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
    } load();
  }
  function detail(id, message = '') {
    if (!permitted()) return;
    const main = shell('Chi tiết sự kiện', 'events'), toolbar = node('div', null, { class: 'toolbar' });
    append(toolbar, iconButton('Quay lại sự kiện', list, 'back'), iconButton('Làm mới sự kiện', load, 'refresh')); main.append(toolbar);
    const notices = feedback(main), container = node('div', null, { class: 'stack' }); append(main, node('p', message, { class: 'confirmation', role: 'status' }), container); const request = channel();
    async function load() {
      const task = request(); container.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải sự kiện…';
      try {
        const event = await api(path(id), task); if (!task.current() || !permitted()) return;
        main.querySelector('h1').textContent = event.draft.nameVi; notices.status.textContent = '';
        container.append(node('span', states[event.state], { class: 'badge' })); const actions = node('div', null, { class: 'event-actions' });
        if (event.state === 'DRAFT') append(actions, button('Xem trước', eventClick => preview(event, eventClick.currentTarget), { class: 'primary' }), iconButton('Sửa bản nháp', () => edit(event), 'edit'));
        if (owner() && !['CANCELLED', 'ENDED'].includes(event.state)) actions.append(button('Hủy sự kiện', () => cancel(event)));
        container.append(actions); definition(container, event.draft);
        facts(container, [['ID sự kiện', event.id], ['Phiên bản', amount(event.version)], ['Người tạo', event.creatorId ?? 'Không rõ (dữ liệu cũ)'], ['Người công bố', event.publisherId ?? (event.publishedAtEpochMillis == null ? 'Chưa công bố' : 'Không rõ (dữ liệu cũ)')], ['Ngày công bố', event.publishedAtEpochMillis == null ? 'Chưa công bố' : date(event.publishedAtEpochMillis, event.draft.timezone)], ['Người thay đổi gần nhất', event.actorId], ['Lý do thay đổi gần nhất', event.reason], ['Thay đổi lúc', date(event.changedAtEpochMillis, event.draft.timezone)]]);
        if (event.state !== 'DRAFT') container.append(node('p', 'Định nghĩa đã công bố hoặc kết thúc chỉ để xem. Hủy không thay đổi cấu hình đã ghi nhận của trận đang chơi.', { class: 'muted' }));
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
    } load();
  }
  function edit(event) {
    if (!permitted() || (event && event.state !== 'DRAFT')) return;
    const draft = event?.draft, surface = eventModal(event ? 'Sửa bản nháp sự kiện' : 'Tạo sự kiện thưởng'), form = node('form', null, { class: 'stack modal-form' }); surface.dialog.append(form);
    form.append(node('p', 'Giờ nhập luôn là UTC (không theo múi giờ máy). Múi giờ IANA chỉ dùng hiển thị. Bản nháp không tự lên lịch hay lặp hằng năm.', { class: 'muted' }));
    const nameVi = field(form, 'Tên tiếng Việt', 'event-name-vi', 'text', { required: '', maxlength: '400' });
    const contentVi = field(form, 'Nội dung tiếng Việt', 'event-content-vi', 'textarea', { required: '', maxlength: '4000' });
    const nameEn = field(form, 'Tên tiếng Anh', 'event-name-en', 'text', { required: '', maxlength: '400', lang: 'en' });
    const contentEn = field(form, 'Nội dung tiếng Anh', 'event-content-en', 'textarea', { required: '', maxlength: '4000', lang: 'en' });
    if (!event) {
      const suggestions = node('section', null, { class: 'event-suggestions stack', 'aria-label': 'Gợi ý bản nháp' }); form.append(suggestions);
      const preset = select(suggestions, 'Gợi ý ngày lễ', 'event-holiday', { '': 'Tự soạn', ...Object.fromEntries(Object.entries(holidays).map(([key, values]) => [key, values[0]])) });
      const year = field(suggestions, 'Năm ngày lễ', 'event-year', 'number', { min: '1970', max: '9998', step: '1' });
      suggestions.append(node('p', 'Chọn năm rồi áp dụng gợi ý. Tết Nguyên đán: tự nhập ngày Gregorian cho từng năm; không tính ngày âm lịch. Mọi người đủ điều kiện đều có thể tham gia, không nhắm giới tính hay nghề nghiệp.', { class: 'muted' }));
      preset.addEventListener('change', () => { if (preset.value === 'lunar') { start.value = ''; end.value = ''; } });
      suggestions.append(button('Áp dụng gợi ý', () => {
        const suggestion = holidays[preset.value]; if (!suggestion) return;
        if (!/^\d{4}$/.test(year.value) || +year.value < 1970 || +year.value > 9998) { notices.fail(new Error('Nhập năm ngày lễ từ 1970 đến 9998.'), true); return; }
        nameVi.value = suggestion[0]; nameEn.value = suggestion[1]; contentVi.value = 'Thưởng GOLD và XP cho trận trực tuyến đủ điều kiện.'; contentEn.value = 'GOLD and XP rewards for eligible online matches.';
        start.value = suggestion[2] ? `${year.value}-${suggestion[2]}T00:00` : ''; end.value = suggestion[3] ? `${year.value}-${suggestion[3]}T00:00` : ''; gold.value = '2'; xp.value = '2'; notices.clear();
      }));
    }
    const timezone = field(form, 'Múi giờ hiển thị (IANA)', 'event-timezone', 'text', { required: '' }); timezone.value = draft?.timezone || 'Asia/Ho_Chi_Minh';
    const start = field(form, 'Bắt đầu (UTC, bao gồm)', 'event-start', 'datetime-local', { required: '', min: '1970-01-01T00:00', max: '9999-12-31T23:59:59.999', step: '0.001' });
    const end = field(form, 'Kết thúc (UTC, không bao gồm)', 'event-end', 'datetime-local', { required: '', min: '1970-01-01T00:00', max: '9999-12-31T23:59:59.999', step: '0.001' });
    const pair = node('div', null, { class: 'event-field-pair' }); form.append(pair);
    const gold = select(pair, 'Hệ số GOLD', 'event-gold', { 1: '×1 (cơ bản)', 2: '×2' }); const xp = select(pair, 'Hệ số XP', 'event-xp', { 1: '×1 (cơ bản)', 2: '×2' });
    const group = node('fieldset', null, { class: 'stack' }); group.append(node('legend', 'Chế độ trận')); form.append(group);
    const casual = field(group, 'Đấu thường', 'event-casual', 'checkbox'), ranked = field(group, 'Đấu hạng', 'event-ranked', 'checkbox');
    append(form, node('p', policy, { class: 'muted' }), node('p', exclusions, { class: 'muted' }));
    const reason = field(form, 'Lý do thay đổi', 'event-reason', 'textarea', { required: '', maxlength: '500' });
    const notices = feedback(form), submit = node('button', 'Lưu bản nháp', { type: 'submit', class: 'primary' }); append(form, append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    nameVi.value = draft?.nameVi || ''; nameEn.value = draft?.nameEn || ''; contentVi.value = draft?.contentVi || ''; contentEn.value = draft?.contentEn || '';
    start.value = draft ? utcInput(draft.startsAtEpochMillis) : ''; end.value = draft ? utcInput(draft.endsAtEpochMillis) : ''; gold.value = draft?.goldMultiplier || '1'; xp.value = draft?.xpMultiplier || '1'; casual.checked = draft ? draft.matchTypes.includes('CASUAL') : true; ranked.checked = draft ? draft.matchTypes.includes('RANKED') : true;
    mutation(form, submit, notices, surface, event ? path(event.id) + '/draft' : base, () => {
      const text = (value, max, label) => { if (!value.trim() || Array.from(value).length > max || /[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/.test(value)) throw new Error(`${label}: nội dung bắt buộc, tối đa ${max} ký tự.`); return value; };
      const zone = timezone.value.trim(); try { if (/^[+-]/.test(zone)) throw new Error(); new Intl.DateTimeFormat('vi', { timeZone: zone }); } catch { throw new Error('Nhập múi giờ IANA hợp lệ, ví dụ America/New_York hoặc UTC.'); }
      const from = Date.parse(start.value + 'Z'), until = Date.parse(end.value + 'Z');
      if (!Number.isSafeInteger(from) || !Number.isSafeInteger(until) || from < 0 || until > 253402300799999 || from >= until) throw new Error('Khoảng UTC phải có bắt đầu trước kết thúc.');
      const matchTypes = [casual.checked ? 'CASUAL' : null, ranked.checked ? 'RANKED' : null].filter(Boolean); if (!matchTypes.length) throw new Error('Chọn ít nhất một chế độ trận.');
      return { expectedVersion: event?.version || 0, reason: reason.value.trim(), draft: { nameVi: text(nameVi.value, 200, 'Tên tiếng Việt'), nameEn: text(nameEn.value, 200, 'Tên tiếng Anh'), contentVi: text(contentVi.value, 2000, 'Nội dung tiếng Việt'), contentEn: text(contentEn.value, 2000, 'Nội dung tiếng Anh'), timezone: zone, startsAtEpochMillis: from, endsAtEpochMillis: until, goldMultiplier: +gold.value, xpMultiplier: +xp.value, matchTypes } };
    }, saved => { surface.finish(); detail(saved.id, 'Đã lưu bản nháp. Chưa công bố.'); }, event ? 'PUT' : 'POST'); surface.show(nameVi);
  }
  async function preview(event, opener) {
    if (!permitted() || event.state !== 'DRAFT') return;
    const surface = eventModal('Xem trước sự kiện', opener), content = node('div', null, { class: 'stack' }); surface.dialog.append(content); const notices = feedback(content), request = modalRequest(surface);
    const retry = button('Thử lại xem trước', load), actions = append(node('div', null, { class: 'modal-actions' }), surface.cancel, retry); content.append(actions); surface.show(surface.cancel); load();
    async function load() {
      const task = request(); notices.clear(); notices.status.textContent = 'Đang xem trước…'; surface.pending(true); retry.disabled = true;
      try {
        const data = await api(path(event.id) + '/preview', task, {}); if (!task.current()) return;
        const result = node('div', null, { class: 'stack' }); definition(result, data.event.draft);
        facts(result, [['Phiên bản xem trước', amount(data.event.version)], ['Giờ máy chủ', date(data.serverNowEpochMillis, data.event.draft.timezone)]]);
        result.append(node('h3', 'Sự kiện giao nhau')); if (!data.overlaps.length) result.append(node('p', 'Không có sự kiện giao nhau.'));
        for (const overlap of data.overlaps) { const row = node('div', null, { class: 'event-overlap stack' }); row.append(node('strong', overlap.nameVi)); factors(row, overlap); row.append(node('p', `${date(overlap.startsAtEpochMillis, overlap.timezone)} → ${date(overlap.endsAtEpochMillis, overlap.timezone)}`, { class: 'muted' })); result.append(row); }
        content.insertBefore(result, actions); notices.status.textContent = ''; retry.remove();
        if (owner()) actions.append(button('Tiếp tục lên lịch', () => { surface.finish(); schedule(data, opener); }, { class: 'primary' }));
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
      finally { if (task.current()) { surface.pending(false); retry.disabled = false; } }
    }
  }
  function schedule(preview, opener) {
    if (!owner()) return;
    const event = preview.event, surface = eventModal('Xác nhận lên lịch', opener), form = node('form', null, { class: 'stack modal-form' }); surface.dialog.append(form);
    definition(form, event.draft); form.append(node('p', `Xác nhận phiên bản ${event.version}. Lên lịch đóng băng định nghĩa; máy chủ quyết định trạng thái theo thời gian. Không thay đổi trận đã bắt đầu.`, { class: 'muted' }));
    const reason = field(form, 'Lý do lên lịch', 'event-schedule-reason', 'textarea', { required: '', maxlength: '500' }); const notices = feedback(form), submit = node('button', 'Lên lịch sự kiện', { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutation(form, submit, notices, surface, path(event.id) + '/schedule', () => { if (!owner()) throw new Error('Chỉ Owner được lên lịch.'); return { expectedVersion: event.version, reason: reason.value.trim(), previewToken: preview.previewToken }; }, saved => { surface.finish(); detail(saved.id, 'Đã lên lịch theo xác nhận máy chủ.'); }); surface.show(reason);
  }
  function cancel(event) {
    if (!owner() || ['ENDED', 'CANCELLED'].includes(event.state)) return;
    const surface = eventModal('Hủy sự kiện'), form = node('form', null, { class: 'stack modal-form' }); surface.dialog.append(form);
    append(form, node('h3', event.draft.nameVi), node('p', 'Hủy chỉ ngăn áp dụng cho trận bắt đầu sau khi hủy thành công. Trận đã ghi nhận cấu hình giữ nguyên thưởng; không thu hồi kết quả.'));
    const reason = field(form, 'Lý do hủy', 'event-cancel-reason', 'textarea', { required: '', maxlength: '500' }); const notices = feedback(form), submit = node('button', 'Xác nhận hủy sự kiện', { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutation(form, submit, notices, surface, path(event.id) + '/cancel', () => { if (!owner()) throw new Error('Chỉ Owner được hủy.'); return { expectedVersion: event.version, reason: reason.value.trim() }; }, saved => { surface.finish(); detail(saved.id, 'Đã hủy theo xác nhận máy chủ. Cấu hình trận đã bắt đầu không đổi.'); }); surface.show(reason);
  }
  list();
}
