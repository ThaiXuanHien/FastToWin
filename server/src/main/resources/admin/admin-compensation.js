'use strict';

const states = { DRAFT: 'Bản nháp', PUBLISHED: 'Đã công bố', CANCELLED: 'Đã hủy' };
const sources = { ALL_REGISTERED: 'Tất cả tài khoản đã đăng ký', USER_IDS: 'Danh sách UUID đã xác minh', ACTIVITY: 'Theo hoạt động — chưa khả dụng' };
const exact = value => new Intl.NumberFormat('vi-VN').format(BigInt(value));
const utc = value => new Date(value).toISOString();
const utcInput = value => utc(value).slice(0, 23);

// Aggregate transport is decimal strings. Never reuse the legacy numeric gift formatter.
export function openCompensation(ui) {
  const { node, append, button, icon, iconButton, field, modal, feedback, shell, channel, api, role } = ui;
  const base = '/incidents', incidentPath = id => base + '/' + encodeURIComponent(id);
  const roundsPath = id => incidentPath(id) + '/rounds';
  const roundPath = row => roundsPath(row.incidentId) + '/' + encodeURIComponent(row.campaign.id);
  const readable = () => ['OWNER', 'OPERATOR', 'SUPPORT'].includes(role());
  const writable = () => ['OWNER', 'OPERATOR'].includes(role());
  const owner = () => role() === 'OWNER';
  function facts(parent, entries) {
    const dl = node('dl', null, { class: 'facts' });
    for (const [label, value] of entries) dl.append(append(node('div'), node('dt', label), node('dd', value)));
    parent.append(dl);
  }
  function paginate(parent, data, load) {
    const page = BigInt(data.page), size = BigInt(data.pageSize), total = BigInt(data.total);
    const nav = node('div', null, { class: 'pagination' });
    append(nav, iconButton('Trang trước', () => load(data.page - 1), 'previous', { ...(page === 0n ? { disabled: '' } : {}) }),
      node('span', `Trang ${exact(page + 1n)} · ${exact(total)} mục`),
      iconButton('Trang sau', () => load(data.page + 1), 'next', { ...((page + 1n) * size >= total || (page + 1n) * size > 2147483647n ? { disabled: '' } : {}) }));
    parent.append(nav);
  }
  function rewards(parent, gold, xp) {
    const summary = node('div', null, { class: 'compensation-rewards' });
    for (const [name, value, label] of [['coin', gold, 'Vàng (GOLD)'], ['star', xp, 'Kinh nghiệm (XP)']]) {
      const accessible = `${label}: ${exact(value)}`;
      const glyph = icon(name);
      // Mission uses filled MonetizationOn in gold and a filled primary Star.
      if (name === 'coin') glyph.querySelector('path').setAttribute('d', 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm1 15v2h-2v-2c-1.6-.3-2.8-1.3-2.9-3h2c.1.9.7 1.4 1.9 1.4 1.3 0 1.8-.6 1.8-1.2 0-.8-.7-1.2-2.3-1.6-2.2-.6-3.2-1.4-3.2-3.1 0-1.5 1.1-2.6 2.7-2.9V5h2v1.6c1.7.4 2.6 1.6 2.7 3h-2c-.1-.8-.7-1.2-1.7-1.2s-1.7.5-1.7 1.1c0 .7.6 1 2.2 1.5 2.4.7 3.3 1.6 3.3 3.2 0 1.4-1 2.5-2.6 2.8Z');
      summary.append(append(node('span', null, { class: 'compensation-reward ' + name, role: 'img', 'aria-label': accessible, title: accessible }), glyph, node('span', exact(value))));
    }
    parent.append(summary);
  }
  function languages(parent, viTitle, enTitle, viBody, enBody) {
    const content = node('div', null, { class: 'compensation-languages' });
    for (const [lang, title, body, label] of [['vi', viTitle, viBody, 'Tiếng Việt'], ['en', enTitle, enBody, 'English']]) {
      content.append(append(node('section', null, { class: 'compensation-copy stack', lang, 'aria-label': label }), node('p', label, { class: 'muted' }), node('h3', title), node('p', body, { class: 'compensation-body' })));
    } parent.append(content);
  }
  function incidentCopy(parent, item) {
    languages(parent, item.titleVi, item.titleEn, item.descriptionVi, item.descriptionEn);
    facts(parent, [['Mã sự cố', item.incidentKey], ['Ảnh hưởng từ UTC (bao gồm)', utc(item.affectedFromEpochMillis)], ['Ảnh hưởng đến UTC (không bao gồm)', utc(item.affectedUntilEpochMillis)]]);
  }
  function roundCopy(parent, row) {
    const draft = row.campaign.draft;
    languages(parent, draft.titleVi, draft.titleEn, draft.bodyVi, draft.bodyEn); rewards(parent, draft.gold, draft.xp);
    facts(parent, [['Nguồn người nhận', sources[draft.audience]], ['UUID được chọn', draft.userIds.join('\n') || 'Toàn bộ tài khoản đã đăng ký tại lúc công bố'], ['Có thể nhận từ UTC', utc(draft.availableAtEpochMillis)], ['Hết hạn UTC', utc(draft.expiresAtEpochMillis)], ['Lý do nhận (VI)', row.round.receivingReasonVi], ['Receiving reason (EN)', row.round.receivingReasonEn]]);
    parent.append(node('p', 'Số thưởng cho mỗi người. Đền bù không nhân hệ số sự kiện và không tăng Vàng kiếm được trên bảng xếp hạng.', { class: 'muted' }));
  }
  function report(parent, row) {
    parent.append(node('h2', 'Báo cáo đợt ' + row.round.roundNumber));
    facts(parent, Object.entries({ eligible: 'Đủ điều kiện (đã chốt)', pending: 'Chờ giao', delivered: 'Đã giao', claimed: 'Đã nhận thưởng', expired: 'Hết hạn', cancelled: 'Đã hủy chưa nhận', skipped: 'Bỏ qua' }).map(([key, label]) => [label, exact(row.report[key])]));
    parent.append(node('p', 'Tổng thưởng đã trả', { class: 'muted' })); rewards(parent, row.report.claimedGold, row.report.claimedXp);
    parent.append(node('p', 'Đã giao không có nghĩa là đã trả thưởng. Các bộ đếm có thể chồng lấp; không cộng chúng thành tổng. Hủy chỉ dừng phần chưa nhận, giữ nguyên thưởng và biên nhận đã trả.', { class: 'muted' }));
  }
  function approvalFacts(parent, row) {
    facts(parent, [['Số đợt được xác nhận', exact(row.round.roundNumber)], ['Lý do đợt bổ sung — chỉ nội bộ', row.round.supplementalReason || 'Đợt đầu tiên']]);
  }
  function surface(title) {
    const result = modal(title); result.dialog.classList.add('compensation-modal'); return result;
  }
  function bodyForm(surface) { const form = node('form', null, { class: 'stack modal-form' }); surface.dialog.append(form); return form; }
  function input(form, label, name, type, value, attrs = {}) { const control = field(form, label, 'compensation-' + name, type, attrs); if (value != null) control.value = value; return control; }
  function reason(form, label) { return input(form, label, 'reason', 'textarea', '', { required: '', maxlength: '500', rows: '2' }); }
  function actions(form, surface, label) {
    const submit = button(label, null, { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'actions' }), surface.cancel, submit)); return submit;
  }
  function parseUtc(control) {
    const value = control.value;
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(value)) throw new Error('Nhập đầy đủ năm và giờ UTC.');
    const millis = Date.parse(value + 'Z');
    if (!Number.isFinite(millis) || millis < 0 || millis > 253402300799999 || utc(millis).slice(0, 16) !== value.slice(0, 16)) throw new Error('Ngày hoặc giờ UTC không hợp lệ.');
    return millis;
  }
  function utcField(form, label, name, value, required = true) { return input(form, label, name, 'datetime-local', value == null ? '' : utcInput(value), { step: '.001', min: '1970-01-01T00:00', max: '9999-12-31T23:59:59.999', ...(required ? { required: '' } : {}) }); }
  function mutate(form, submit, notices, dialog, target, payload, success, reload, method = 'POST', allowed = writable) {
    const request = channel(); let busy = false, signature, requestId, conflicted = false;
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (busy || conflicted || !allowed()) return;
      let body;
      try { body = payload(); } catch (error) { notices.fail(error, true); return; }
      const next = JSON.stringify(body); if (next !== signature) { signature = next; requestId = crypto.randomUUID(); }
      busy = true; notices.clear(); dialog.pending(true); submit.disabled = true;
      const original = submit.textContent; submit.textContent = 'Đang xử lý…';
      const task = request(), current = task.current;
      task.current = () => current() && allowed() && dialog.dialog.isConnected && dialog.dialog.open;
      try { const result = await api(target, task, { ...body, requestId }, method); if (task.current()) success(result); }
      catch (error) {
        if (!task.current() || error.name === 'AbortError') return;
        if (error.status === 409) {
          conflicted = true; notices.fail(new Error('Phiên bản, nhóm người nhận hoặc biên nhận đã thay đổi. Tải lại dữ liệu và xem trước lại.'), true);
          notices.error.append(button('Tải lại dữ liệu', () => { dialog.finish(); reload(); }));
        } else notices.fail(new Error((error.message || 'Không thể kết nối.') + ' Kết quả chưa được xác nhận; thử lại cùng nội dung sẽ giữ nguyên mã yêu cầu.'), true);
      } finally {
        if (task.current()) { busy = false; dialog.pending(false); submit.textContent = original; submit.disabled = conflicted;
          if (conflicted) for (const control of form.querySelectorAll('input,select,textarea')) control.disabled = true;
        }
      }
    });
  }
  function list() {
    if (!readable()) return;
    const main = shell('Đền bù', 'compensation'); main.append(node('p', 'Ghi nhận sự cố, tạo đợt đền bù rõ ràng rồi xem trước và xác nhận. Không tự phát hiện sự cố, tạo đợt hay gửi thông báo.', { class: 'muted' }));
    const toolbar = node('div', null, { class: 'toolbar' });
    if (writable()) toolbar.append(iconButton('Tạo sự cố', () => editIncident(), 'add', { class: 'primary' }));
    toolbar.append(iconButton('Làm mới sự cố', () => load(currentPage), 'refresh')); main.append(toolbar);
    const notices = feedback(main), results = node('div'); main.append(results); const request = channel(); let currentPage = 0;
    async function load(page = 0) {
      currentPage = page; results.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải sự cố…'; const task = request();
      try {
        const data = await api(base + `?page=${page}&pageSize=25`, task); if (!task.current() || !readable()) return;
        notices.status.textContent = exact(data.total) + ' sự cố.';
        for (const item of data.items) results.append(append(node('article', null, { class: 'compensation-row' }), append(node('div', null, { class: 'row-info' }), node('strong', item.titleVi), node('p', item.incidentKey, { class: 'muted' })), iconButton('Xem sự cố ' + item.titleVi, () => detail(item.id), 'eye')));
        if (!data.items.length) results.append(node('p', 'Chưa có sự cố.', { class: 'muted' })); paginate(results, data, load);
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
    } load();
  }
  function detail(id, message = '') {
    if (!readable()) return;
    const main = shell('Chi tiết sự cố', 'compensation'); main.append(append(node('div', null, { class: 'toolbar' }), iconButton('Quay lại sự cố', list, 'back'), iconButton('Làm mới sự cố', load, 'refresh')));
    const notices = feedback(main), content = node('div', null, { class: 'stack' }); append(main, node('p', message, { class: 'confirmation', role: 'status' }), content); const request = channel();
    async function load() {
      const task = request(); content.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải sự cố và lịch sử…';
      try {
        const item = await api(incidentPath(id), task), first = await api(roundsPath(id) + '?page=0&pageSize=25', task);
        let frozen = first.items.some(row => row.campaign.publishedAtEpochMillis != null), hasDraft = first.items.some(row => row.campaign.state === 'DRAFT');
        // Publication evidence can be on an older page, including a cancelled round.
        for (let page = 1; BigInt(page) * 25n < BigInt(first.total) && !(frozen && hasDraft); page++) {
          const data = await api(roundsPath(id) + `?page=${page}&pageSize=25`, task);
          frozen ||= data.items.some(row => row.campaign.publishedAtEpochMillis != null); hasDraft ||= data.items.some(row => row.campaign.state === 'DRAFT');
        }
        if (!task.current() || !readable()) return;
        notices.status.textContent = ''; main.querySelector('h1').textContent = item.titleVi;
        const controls = node('div', null, { class: 'compensation-actions' });
        if (writable() && !frozen) controls.append(iconButton('Sửa sự cố', () => editIncident(item), 'edit'));
        const maximum = first.items[0]?.round.roundNumber ?? 0;
        if (writable() && !hasDraft && maximum < 2147483647) controls.append(button(maximum ? 'Tạo đợt bổ sung' : 'Tạo đợt 1', () => editRound(item, null, maximum + 1), { class: 'primary' }));
        content.append(controls); incidentCopy(content, item);
        content.append(node('h2', 'Chỉ nội bộ — không gửi cho người chơi'));
        facts(content, [['Mã nguyên nhân', item.causeCode], ['Bằng chứng nội bộ', item.evidence], ['Admin chịu trách nhiệm', item.responsibleAdminId], ['Người tạo', item.createdByAdminId], ['Người sửa gần nhất', item.updatedByAdminId], ['Phiên bản sự cố', exact(item.version)]]);
        if (frozen) content.append(node('p', 'Sự cố đã được khóa vì từng có đợt công bố. Hủy đợt không mở lại quyền sửa.', { class: 'muted' }));
        if (hasDraft) content.append(node('p', 'Đang có một bản nháp. Công bố hoặc hủy trước khi tạo đợt bổ sung.', { class: 'muted' }));
        content.append(node('h2', 'Lịch sử các đợt')); const history = node('div'); content.append(history); const historyRequest = channel();
        function render(data) {
          history.replaceChildren();
          for (const row of data.items) history.append(append(node('article', null, { class: 'compensation-row' }), append(node('div', null, { class: 'row-info' }), node('strong', `Đợt ${row.round.roundNumber} · ${states[row.campaign.state]}`), node('p', row.round.supplementalReason || 'Đợt đầu tiên', { class: 'muted' })), iconButton('Xem đợt ' + row.round.roundNumber, () => roundDetail(item, row.campaign.id), 'eye')));
          if (!data.items.length) history.append(node('p', 'Chưa có đợt. Tạo đợt đầu tiên bằng hành động ở trên.', { class: 'muted' })); paginate(history, data, loadHistory);
        }
        async function loadHistory(page) { const next = historyRequest(); try { const data = await api(roundsPath(id) + `?page=${page}&pageSize=25`, next); if (next.current() && readable()) render(data); } catch (error) { if (next.current() && error.name !== 'AbortError') notices.fail(error); } }
        render(first);
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
    } load();
  }
  function editIncident(item) {
    if (!writable()) return;
    const dialog = surface(item ? 'Sửa sự cố' : 'Tạo sự cố'), form = bodyForm(dialog);
    form.append(node('p', 'Nhập đầy đủ năm và giờ UTC. Khoảng ảnh hưởng bao gồm thời điểm bắt đầu, không bao gồm kết thúc.', { class: 'muted' }));
    const key = input(form, 'Mã sự cố bất biến', 'key', 'text', item?.incidentKey, { required: '', maxlength: '128', pattern: '[A-Za-z0-9][A-Za-z0-9_-]{0,127}', ...(item ? { readonly: '' } : {}) });
    const viTitle = input(form, 'Tiêu đề người chơi (VI)', 'title-vi', 'text', item?.titleVi, { required: '', maxlength: '160' });
    const enTitle = input(form, 'Player title (EN)', 'title-en', 'text', item?.titleEn, { required: '', maxlength: '160' });
    const viBody = input(form, 'Giải thích người chơi (VI)', 'body-vi', 'textarea', item?.descriptionVi, { required: '', maxlength: '4000', rows: '3' });
    const enBody = input(form, 'Player explanation (EN)', 'body-en', 'textarea', item?.descriptionEn, { required: '', maxlength: '4000', rows: '3' });
    const from = utcField(form, 'Ảnh hưởng từ (UTC, bao gồm)', 'from', item?.affectedFromEpochMillis), until = utcField(form, 'Ảnh hưởng đến (UTC, không bao gồm)', 'until', item?.affectedUntilEpochMillis);
    form.append(node('h3', 'Chỉ nội bộ — không gửi cho người chơi'));
    const cause = input(form, 'Mã nguyên nhân nội bộ', 'cause', 'text', item?.causeCode, { required: '', maxlength: '128' });
    const evidence = input(form, 'Bằng chứng nội bộ', 'evidence', 'textarea', item?.evidence, { required: '', maxlength: '10000', rows: '3' });
    const admin = input(form, 'UUID admin chịu trách nhiệm (đang hoạt động)', 'admin', 'text', item?.responsibleAdminId, { required: '' });
    const why = reason(form, 'Lý do ghi nhận nội bộ'), notices = feedback(form), submit = actions(form, dialog, 'Lưu sự cố');
    mutate(form, submit, notices, dialog, item ? incidentPath(item.id) : base, () => {
      const fromMillis = parseUtc(from), untilMillis = parseUtc(until); if (untilMillis <= fromMillis) throw new Error('Kết thúc phải sau bắt đầu UTC.');
      return { incidentKey: key.value, titleVi: viTitle.value, titleEn: enTitle.value, descriptionVi: viBody.value, descriptionEn: enBody.value, affectedFromEpochMillis: fromMillis, affectedUntilEpochMillis: untilMillis, causeCode: cause.value, evidence: evidence.value, responsibleAdminId: admin.value.trim(), reason: why.value, ...(item ? { expectedVersion: item.version } : {}) };
    }, result => { dialog.finish(); detail(result.id, 'Đã lưu sự cố.'); }, () => item ? detail(item.id) : list(), item ? 'PUT' : 'POST'); dialog.show(key);
  }
  function roundDetail(item, id, message = '') {
    if (!readable()) return;
    const main = shell('Chi tiết đợt đền bù', 'compensation'); main.append(append(node('div', null, { class: 'toolbar' }), iconButton('Quay lại sự cố', () => detail(item.id), 'back'), iconButton('Làm mới đợt', load, 'refresh')));
    const notices = feedback(main), content = node('div', null, { class: 'stack' }); append(main, node('p', message, { class: 'confirmation', role: 'status' }), content); const request = channel();
    async function load() {
      const task = request(); content.replaceChildren(); notices.clear();
      try {
        const currentIncident = await api(incidentPath(item.id), task), row = await api(roundsPath(item.id) + '/' + encodeURIComponent(id), task);
        if (!task.current() || !readable()) return;
        main.querySelector('h1').textContent = `Đợt ${row.round.roundNumber} · ${states[row.campaign.state]}`;
        const controls = node('div', null, { class: 'compensation-actions' });
        if (writable() && row.campaign.state === 'DRAFT') append(controls, button('Xem trước', () => preview(currentIncident, row), { class: 'primary' }), iconButton('Sửa bản nháp đợt', () => editRound(currentIncident, row), 'edit'));
        if (owner() && row.campaign.state !== 'CANCELLED') controls.append(button('Hủy đợt', () => confirm(currentIncident, row, 'cancel')));
        content.append(controls); roundCopy(content, row); report(content, row);
        facts(content, [['UUID đợt / nguồn ví', row.campaign.id], ['Phiên bản đợt', exact(row.campaign.version)], ['Lý do bổ sung nội bộ', row.round.supplementalReason || 'Đợt đầu tiên'], ['Người tạo', row.round.createdByAdminId ?? 'Không rõ'], ['Người công bố', row.round.publishedByAdminId ?? (row.campaign.publishedAtEpochMillis == null ? 'Chưa công bố' : 'Không rõ')], ['Người sửa gần nhất', row.round.updatedByAdminId ?? 'Không rõ'], ['Người hủy', row.round.cancelledByAdminId ?? 'Chưa hủy']]);
        if (row.campaign.state !== 'DRAFT') content.append(node('p', 'Định nghĩa đã công bố hoặc hủy chỉ để xem. Người đăng ký sau khi công bố không được thêm vào đợt.', { class: 'muted' }));
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
    } load();
  }
  function editRound(item, row, number) {
    if (!writable() || (row && row.campaign.state !== 'DRAFT')) return;
    const draft = row?.campaign.draft, nextNumber = row?.round.roundNumber ?? number;
    const dialog = surface(row ? 'Sửa bản nháp đợt ' + nextNumber : 'Tạo đợt ' + nextNumber), form = bodyForm(dialog);
    form.append(node('p', 'Đợt được tạo bằng số xác nhận rõ ràng, không tự tăng số khi thử lại. Thời gian nhập luôn là UTC, gồm đầy đủ năm.', { class: 'muted' }));
    const supplemental = nextNumber > 1 ? input(form, 'Lý do đợt bổ sung nội bộ', 'supplemental', 'textarea', row?.round.supplementalReason, { required: '', maxlength: '500', rows: '2' }) : null;
    const vi = input(form, 'Lý do nhận người chơi (VI)', 'receiving-vi', 'textarea', row?.round.receivingReasonVi, { required: '', maxlength: '1000', rows: '2' });
    const en = input(form, 'Player receiving reason (EN)', 'receiving-en', 'textarea', row?.round.receivingReasonEn, { required: '', maxlength: '1000', rows: '2' });
    const gold = input(form, 'Vàng mỗi người', 'gold', 'number', draft?.gold ?? 0, { required: '', min: '0', max: '2147483647', step: '1' });
    const xp = input(form, 'Kinh nghiệm mỗi người', 'xp', 'number', draft?.xp ?? 0, { required: '', min: '0', max: '2147483647', step: '1' });
    const rewardSummary = node('div'); form.append(rewardSummary); const showAmounts = () => { rewardSummary.replaceChildren(); if (/^\d+$/.test(gold.value) && /^\d+$/.test(xp.value)) rewards(rewardSummary, gold.value, xp.value); }; gold.addEventListener('input', showAmounts); xp.addEventListener('input', showAmounts); showAmounts();
    const available = utcField(form, 'Có thể nhận từ (UTC)', 'available', draft?.availableAtEpochMillis), expiry = utcField(form, 'Hết hạn (UTC, bỏ trống = 30 ngày)', 'expiry', draft?.expiresAtEpochMillis, false);
    const wrapper = node('div', null, { class: 'field' }), audience = node('select', null, { id: 'compensation-audience', disabled: '' }); append(wrapper, node('label', 'Nguồn người nhận', { for: audience.id }), audience); form.append(wrapper);
    const ids = input(form, 'UUID người chơi (mỗi dòng hoặc dấu phẩy)', 'ids', 'textarea', draft?.userIds.join('\n'), { rows: '3' });
    const capability = node('p', 'Đang kiểm tra nguồn người nhận…', { class: 'muted', role: 'status' }); form.append(capability);
    const why = reason(form, 'Lý do bản nháp nội bộ'), notices = feedback(form), submit = actions(form, dialog, 'Lưu bản nháp đợt'); submit.disabled = true;
    const request = channel(), task = request(), current = task.current; task.current = () => current() && writable() && dialog.dialog.isConnected && dialog.dialog.open;
    const sync = () => { ids.closest('.field').hidden = audience.value !== 'USER_IDS'; }; audience.addEventListener('change', sync);
    mutate(form, submit, notices, dialog, row ? roundPath(row) : roundsPath(item.id), () => {
      if (!['ALL_REGISTERED', 'USER_IDS'].includes(audience.value) || audience.disabled) throw new Error('Nguồn người nhận chưa khả dụng.');
      const goldValue = Number(gold.value), xpValue = Number(xp.value); if (!Number.isInteger(goldValue) || !Number.isInteger(xpValue) || goldValue + xpValue <= 0) throw new Error('Cần ít nhất một loại thưởng lớn hơn 0.');
      const from = parseUtc(available), until = expiry.value ? parseUtc(expiry) : null; if (until != null && until <= from) throw new Error('Hết hạn phải sau thời điểm có thể nhận UTC.');
      return { incidentVersion: item.version, receivingReasonVi: vi.value, receivingReasonEn: en.value, gold: goldValue, xp: xpValue, availableAtEpochMillis: from, expiresAtEpochMillis: until, audience: audience.value, userIds: audience.value === 'USER_IDS' ? ids.value.split(/[\s,]+/).filter(Boolean) : [], reason: why.value, ...(supplemental ? { supplementalReason: supplemental.value } : {}), ...(row ? { expectedVersion: row.campaign.version } : { expectedNextRoundNumber: number }) };
    }, result => { dialog.finish(); roundDetail(item, result.campaign.id, 'Đã lưu bản nháp đợt.'); }, () => detail(item.id), row ? 'PUT' : 'POST');
    dialog.show(supplemental || vi);
    api(base + '/capabilities', task).then(data => {
      if (!task.current()) return;
      for (const source of data.sources) audience.append(node('option', sources[source.source] || source.source, { value: source.source, ...(!source.available || !['ALL_REGISTERED', 'USER_IDS'].includes(source.source) ? { disabled: '' } : {}) }));
      const activity = data.sources.find(source => source.source === 'ACTIVITY'); capability.textContent = 'Theo hoạt động chưa khả dụng: ' + (activity?.explanation || 'Chưa xác minh đủ dữ liệu hoạt động.');
      audience.disabled = false; audience.value = draft?.audience ?? 'ALL_REGISTERED'; sync(); submit.disabled = !audience.selectedOptions[0] || audience.selectedOptions[0].disabled;
    }).catch(error => { if (task.current() && error.name !== 'AbortError') notices.fail(error); });
  }
  function preview(item, row) {
    if (!writable() || row.campaign.state !== 'DRAFT') return;
    const dialog = surface('Xem trước đợt ' + row.round.roundNumber), form = bodyForm(dialog); roundCopy(form, row);
    const why = reason(form, 'Lý do xem trước'), notices = feedback(form), submit = actions(form, dialog, 'Tạo bản xem trước');
    mutate(form, submit, notices, dialog, roundPath(row) + '/preview', () => ({ incidentVersion: item.version, expectedVersion: row.campaign.version, reason: why.value }), result => {
      form.replaceChildren(); incidentCopy(form, item); roundCopy(form, row); approvalFacts(form, row);
      facts(form, [['Nguồn chọn', sources[result.source]], ['Điều kiện UUID', result.userIds.join('\n') || 'Tất cả tài khoản đã đăng ký tại lúc công bố'], ['Người nhận', exact(result.recipientCount)], ['Người đã nhận ở các đợt trước (khác nhau)', exact(result.previousClaimedUserCount)], ['Biên nhận đã trả ở các đợt trước', exact(result.previousClaimedEntitlementCount)], ['Người trong đợt này đã nhận trước', exact(result.selectedPreviouslyClaimedUserCount)], ['UUID trùng', result.duplicateUserIds.join('\n') || 'Không có'], ['Số mục trùng thêm', exact(result.duplicateCount)], ['UUID không hợp lệ', result.invalidUserIds.join('\n') || 'Không có'], ['Mẫu tối đa 10 UUID', result.sampleUserIds.join('\n') || 'Không có']]);
      form.append(node('p', 'Tổng thưởng dự kiến', { class: 'muted' })); rewards(form, result.totalGold, result.totalXp);
      form.append(node('p', 'Công bố chốt nhóm người nhận. Có thể trả lại cho người đã nhận ở đợt trước khi đợt bổ sung được xác nhận; đây là nguồn ví riêng.', { class: 'muted' }));
      const controls = append(node('div', null, { class: 'actions' }), dialog.cancel);
      if (owner() && !result.invalidUserIds.length) controls.append(button('Tiếp tục công bố', () => { dialog.finish(); confirm(item, row, 'publish', result); }, { class: 'primary' }));
      form.append(controls);
      form.scrollTop = 0;
    }, () => roundDetail(item, row.campaign.id)); dialog.show(why);
  }
  function confirm(item, row, action, preview) {
    if (!owner()) return;
    const publish = action === 'publish', dialog = surface(publish ? 'Xác nhận công bố đợt' : 'Xác nhận hủy đợt'), form = bodyForm(dialog);
    form.append(node('p', publish ? 'Chốt nhóm người nhận và nội dung. Sự cố sẽ khóa vĩnh viễn sau công bố đầu tiên. Người đăng ký sau đó không được thêm vào đợt.' : 'Dừng phần chưa nhận. Thưởng và biên nhận đã trả được giữ nguyên.', { class: 'muted' }));
    roundCopy(form, row); approvalFacts(form, row);
    if (publish) { facts(form, [['Người nhận', exact(preview.recipientCount)], ['Đã nhận ở đợt trước', exact(preview.previousClaimedUserCount)], ['Biên nhận trước', exact(preview.previousClaimedEntitlementCount)], ['Trùng người đã nhận', exact(preview.selectedPreviouslyClaimedUserCount)]]); rewards(form, preview.totalGold, preview.totalXp); }
    const why = reason(form, publish ? 'Lý do công bố nội bộ' : 'Lý do hủy nội bộ'), notices = feedback(form), submit = actions(form, dialog, publish ? 'Công bố đợt' : 'Xác nhận hủy đợt');
    mutate(form, submit, notices, dialog, roundPath(row) + '/' + action, () => ({ incidentVersion: publish ? preview.incidentVersion : item.version, expectedVersion: publish ? preview.version : row.campaign.version, ...(publish ? { previewToken: preview.previewToken } : {}), reason: why.value }), result => { dialog.finish(); roundDetail(item, result.campaign.id, publish ? 'Đã công bố đợt.' : 'Đã hủy đợt.'); }, () => roundDetail(item, row.campaign.id), 'POST', owner); dialog.show(why);
  }
  list();
}
