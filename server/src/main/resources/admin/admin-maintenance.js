'use strict';

const states = { ACTIVE: 'Đang mở', ANNOUNCING: 'Đang báo trước', DRAINING: 'Đang chờ trận kết thúc', MAINTENANCE: 'Đang bảo trì' };
const modes = { PLANNED: 'Theo kế hoạch', EMERGENCY: 'Khẩn cấp' };
const causes = { UPDATE: 'Cập nhật', INCIDENT: 'Sự cố', SECURITY: 'Bảo mật', OVERLOAD: 'Quá tải', OTHER: 'Khác' };
const sources = { DATABASE: 'Cơ sở dữ liệu', ENVIRONMENT: 'Biến môi trường', UNAVAILABLE: 'Chưa có quan sát' };
const date = value => value == null ? 'Chưa có' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Bangkok', dateStyle: 'short', timeStyle: 'medium' }).format(value) + ' (Bangkok)';
const localDate = value => value == null ? '' : new Date(value + 7 * 3600000).toISOString().slice(0, 23);

// Reuse the admin shell's session, channel, network policy and native modal.
export function openMaintenance(ui) {
  const { node, append, button, iconButton, field, modal, feedback, shell, channel, api, health, role } = ui;
  const main = shell('Bảo trì', 'maintenance');
  main.append(node('p', 'Thông báo người chơi, chờ trận đang diễn ra và quản lý thời gian bảo trì.', { class: 'muted' }));
  const toolbar = node('div', null, { class: 'toolbar' });
  toolbar.append(iconButton('Làm mới', load, 'refresh')); main.append(toolbar);
  const notices = feedback(main), result = node('div', null, { class: 'stack maintenance-status' }); main.append(result);
  const request = channel();
  function facts(parent, entries) {
    const list = node('dl', null, { class: 'facts' });
    for (const [key, value] of entries) append(list, append(node('div'), node('dt', key), node('dd', value)));
    parent.append(list);
  }
  function select(parent, label, name, options) {
    const wrap = node('div', null, { class: 'field' }), control = node('select', null, { id: 'maintenance-' + name });
    for (const [value, text] of Object.entries(options)) control.append(node('option', text, { value }));
    append(wrap, node('label', label, { for: control.id }), control); parent.append(wrap); return control;
  }
  async function load() {
    const task = request(); result.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang đọc trạng thái…';
    try {
      const data = await api('/maintenance', task); notices.clear();
      const { persisted: saved, effective, runtime } = data, enabled = saved.state !== 'ACTIVE', overridden = effective.source === 'ENVIRONMENT';
      const heading = node('div', null, { class: 'maintenance-heading' });
      append(heading, node('h2', states[effective.state] || 'Chưa xác định'), node('span', sources[effective.source] || 'Chưa xác định', { class: 'badge' })); result.append(heading);
      if (overridden) result.append(node('p', 'FASTTOWIN_MAINTENANCE đang bắt buộc đóng dịch vụ. Không thể tắt qua quản trị.', { class: 'alert', role: 'status' }));
      if (!runtime.available) result.append(node('p', 'Quan sát máy chủ đang không khả dụng. Không dùng dữ liệu cũ để xác nhận dịch vụ đã mở hoặc kết nối đã đóng.', { class: 'alert' }));
      const actions = node('div', null, { class: 'toolbar' }); result.append(actions);
      if (role() === 'OWNER') {
        const edit = button('Cấu hình bảo trì', () => form(data, edit), { class: 'primary' }); edit.disabled = overridden; actions.append(edit);
        if (enabled) { const off = button('Tắt bảo trì', () => disable(data, off)); off.disabled = overridden; actions.append(off); }
      } else result.append(node('p', 'Chỉ Owner được thay đổi chính sách. Bạn có quyền xem trạng thái.', { class: 'muted' }));
      result.append(node('h3', 'Máy chủ đang quan sát'));
      facts(result, [['Phiên bản máy chủ quan sát', runtime.version ?? 'Chưa có'], ['Quan sát lúc', date(runtime.observedAtEpochMillis)],
        ['Trận máy chủ đang theo dõi', runtime.activeMatches == null ? 'Chưa có' : `${runtime.activeMatches} trận`]]);
      result.append(node('p', 'Chưa xác nhận tất cả kết nối đã đóng. Thời điểm và phiên bản trên là quan sát gần nhất của máy chủ.', { class: 'muted' }));
      result.append(node('h3', 'Chính sách đã lưu'));
      facts(result, [['Trạng thái đã lưu', states[saved.state]], ['Nguồn đã lưu', sources[data.persistedSource]], ['Phiên bản đã lưu', saved.version],
        ['Chế độ đã lưu', modes[saved.mode] || 'Chưa bật'], ['Nguyên nhân', causes[saved.cause] || 'Chưa có'],
        ['Bắt đầu lúc', date(saved.startedAtEpochMillis)], ['Người thay đổi', saved.actorId || 'Chưa có'],
        ['Thay đổi lúc', date(saved.changedAtEpochMillis)], ['Mốc chuyển giai đoạn', date(saved.transitionAtEpochMillis)],
        ['Hạn chờ trận kết thúc', date(saved.drainDeadlineEpochMillis)], ['Kết thúc dự kiến', date(saved.expectedEndEpochMillis)]]);
      result.append(node('p', 'Thời gian kết thúc chỉ là dự kiến. Dịch vụ không tự mở lại theo giờ này.', { class: 'muted' }));
      for (const [label, text, lang] of [['Thông báo tiếng Việt', saved.messageVi, 'vi'], ['Thông báo tiếng Anh', saved.messageEn, 'en']]) {
        if (text) append(result, append(node('section', null, { class: 'maintenance-message', lang }), node('h3', label), node('p', text, { class: 'gift-body' })));
      }
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  function surface(title, opener) {
    const value = modal(title, opener); value.dialog.classList.add('maintenance-modal'); return value;
  }
  function form(data, opener) {
    if (role() !== 'OWNER') return;
    const saved = data.persisted, enabled = saved.state !== 'ACTIVE', view = surface('Cấu hình bảo trì', opener);
    const editor = node('form', null, { class: 'stack modal-form' }); view.dialog.append(editor);
    const mode = select(editor, 'Chế độ', 'mode', modes); mode.value = saved.mode || 'PLANNED';
    if (enabled && saved.mode === 'EMERGENCY') mode.querySelector('[value="PLANNED"]').disabled = true;
    const cause = select(editor, 'Nguyên nhân', 'cause', causes); cause.value = saved.cause || 'UPDATE';
    const vi = field(editor, 'Thông báo tiếng Việt', 'maintenance-vi', 'textarea', { required: '', maxlength: '1000', lang: 'vi' }); vi.value = saved.messageVi || '';
    const en = field(editor, 'Thông báo tiếng Anh', 'maintenance-en', 'textarea', { required: '', maxlength: '1000', lang: 'en' }); en.value = saved.messageEn || '';
    const pair = node('div', null, { class: 'gift-field-pair' }); editor.append(pair);
    const warning = field(pair, 'Báo trước (phút)', 'maintenance-warning', 'number', { required: '', min: '0', max: '60', step: '1', ...(enabled ? { readonly: '' } : {}) }); warning.value = saved.warningMinutes;
    const drain = field(pair, 'Chờ trận kết thúc (phút)', 'maintenance-drain', 'number', { required: '', min: '1', max: '60', step: '1', ...(enabled ? { readonly: '' } : {}) }); drain.value = saved.drainMinutes;
    editor.append(node('p', enabled ? 'Lịch hiện tại được giữ nguyên. Muốn đặt lịch mới hoặc đổi khẩn cấp về kế hoạch, hãy tắt rồi bật lại. Có thể nâng kế hoạch thành khẩn cấp.' : 'Kế hoạch: báo trước 0–60 phút, chờ trận 1–60 phút. Khẩn cấp: đóng ngay, không chờ lịch.', { class: 'muted' }));
    const end = field(editor, 'Kết thúc dự kiến (Bangkok)', 'maintenance-end', 'datetime-local', { step: '0.001', max: '9999-12-31T23:59' }); end.value = localDate(saved.expectedEndEpochMillis);
    editor.append(node('p', 'Chỉ để thông báo, không tự mở lại dịch vụ.', { class: 'muted' }));
    const reason = field(editor, 'Lý do nội bộ', 'maintenance-reason', 'textarea', { required: '', maxlength: '500' }); reason.value = saved.reason || '';
    const errors = feedback(editor), next = node('button', 'Xem lại thay đổi', { type: 'submit', class: 'primary' });
    append(editor, append(node('div', null, { class: 'modal-actions' }), view.cancel, next));
    editor.addEventListener('submit', event => {
      event.preventDefault();
      if (![vi.value, en.value, reason.value].every(value => value.trim())) { errors.fail(new Error('Nhập đủ thông báo hai ngôn ngữ và lý do nội bộ.'), true); return; }
      const expectedEndEpochMillis = end.value ? Date.parse(end.value + '+07:00') : null;
      if (expectedEndEpochMillis != null && (!Number.isFinite(expectedEndEpochMillis) || expectedEndEpochMillis < 0 || expectedEndEpochMillis > 253402300799999)) { errors.fail(new Error('Thời gian dự kiến không hợp lệ.'), true); return; }
      const payload = { enabled: true, mode: mode.value, cause: cause.value, messageVi: vi.value.trim(), messageEn: en.value.trim(),
        warningMinutes: enabled ? saved.warningMinutes : Number(warning.value), drainMinutes: enabled ? saved.drainMinutes : Number(drain.value),
        expectedEndEpochMillis, expectedVersion: saved.version, requestId: crypto.randomUUID(), reason: reason.value.trim() };
      editor.remove(); confirmation(view, payload);
    }); view.show(mode);
  }
  function disable(data, opener) {
    if (role() !== 'OWNER') return;
    const view = surface('Tắt bảo trì', opener), editor = node('form', null, { class: 'stack modal-form' }); view.dialog.append(editor);
    editor.append(node('p', 'Cho phép người chơi bắt đầu hoạt động mới sau khi máy chủ quan sát chính sách mở.'));
    const observation = node('section', null, { class: 'maintenance-message', 'aria-label': 'Kiểm tra máy chủ' });
    const status = node('p', '', { role: 'status', 'aria-live': 'polite' }), timestamp = node('p', '', { class: 'muted' });
    const refresh = iconButton('Kiểm tra lại máy chủ', observe, 'refresh');
    append(observation, append(node('div', null, { class: 'maintenance-heading' }), node('h3', 'Kiểm tra máy chủ'), refresh), status, timestamp,
      node('p', 'Chỉ kiểm tra phản hồi /health trong tối đa 5 giây; không xác nhận cơ sở dữ liệu, trận đấu hay an toàn bảo mật. Quan sát quá 30 giây được đánh dấu cũ. Owner tự quyết định và xác nhận mở lại.', { class: 'muted' }));
    editor.append(observation);
    const observeRequest = channel(); let observationTask, timeout, expiry;
    const visible = task => task.current() && view.dialog.isConnected && view.dialog.open;
    function clearObservation() { clearTimeout(timeout); clearTimeout(expiry); }
    view.dialog.addEventListener('close', () => { clearObservation(); observationTask?.controller.abort(); });
    async function observe() {
      clearObservation(); const task = observeRequest(); observationTask = task;
      status.textContent = 'Đang kiểm tra máy chủ…'; timestamp.textContent = ''; observation.setAttribute('aria-busy', 'true');
      const attemptedAt = Date.now();
      const unavailable = () => { status.textContent = 'Không có quan sát khả dụng'; timestamp.textContent = 'Thử kiểm tra lúc: ' + date(attemptedAt); observation.removeAttribute('aria-busy'); };
      timeout = setTimeout(() => { if (visible(task)) unavailable(); task.controller.abort(); }, 5000);
      try {
        const response = await health(task);
        if (!visible(task)) return;
        if (response !== 'OK') { unavailable(); return; }
        const observedAt = Date.now();
        status.textContent = 'Máy chủ phản hồi OK'; timestamp.textContent = 'Quan sát lúc: ' + date(observedAt);
        expiry = setTimeout(() => { if (visible(task)) status.textContent = 'Quan sát đã cũ — hãy kiểm tra lại'; }, 30000);
      } catch { if (visible(task)) unavailable(); }
      finally { if (visible(task)) { clearTimeout(timeout); observation.removeAttribute('aria-busy'); } }
    }
    const reason = field(editor, 'Lý do nội bộ', 'maintenance-off-reason', 'textarea', { required: '', maxlength: '500' });
    const errors = feedback(editor), submit = node('button', 'Xác nhận lưu', { type: 'submit', class: 'primary' });
    append(editor, append(node('div', null, { class: 'modal-actions' }), view.cancel, submit));
    let payload;
    editor.addEventListener('submit', event => { event.preventDefault();
      if (!reason.value.trim()) { errors.fail(new Error('Nhập lý do nội bộ.'), true); return; }
      if (!payload || payload.reason !== reason.value.trim()) payload = { enabled: false, expectedVersion: data.persisted.version, requestId: crypto.randomUUID(), reason: reason.value.trim() };
      send(view, submit, errors, payload);
    }); view.show(reason); observe();
  }
  function confirmation(view, payload) {
    const content = node('div', null, { class: 'stack modal-form' }); view.dialog.append(content);
    content.append(node('h3', 'Xác nhận thay đổi'));
    content.append(node('p', payload.mode === 'EMERGENCY' ? 'Trận chưa hoàn tất sẽ dừng khi máy chủ áp dụng chính sách khẩn cấp. Kết quả đã ghi nhận được giữ nguyên.' : 'Người chơi nhận thông báo; khi chờ trận kết thúc, không bắt đầu phòng hoặc trận mới.'));
    facts(content, [['Chế độ', modes[payload.mode]], ['Báo trước', `${payload.warningMinutes} phút`], ['Chờ trận', `${payload.drainMinutes} phút`], ['Kết thúc dự kiến', date(payload.expectedEndEpochMillis)], ['Lý do nội bộ', payload.reason]]);
    append(content, node('p', payload.messageVi, { class: 'gift-body', lang: 'vi' }), node('p', payload.messageEn, { class: 'gift-body', lang: 'en' }));
    const errors = feedback(content), submit = button('Xác nhận lưu', () => send(view, submit, errors, payload), { class: 'primary' });
    append(content, append(node('div', null, { class: 'modal-actions' }), view.cancel, submit)); submit.focus();
  }
  async function send(view, submit, errors, payload) {
    if (submit.disabled || role() !== 'OWNER') return;
    const task = channel()(); view.pending(true); submit.disabled = true; errors.clear(); let conflict = false;
    try {
      await api('/maintenance', task, payload);
      view.finish(); await load(); // Replay is historical acknowledgement, always observe afresh.
    } catch (error) {
      if (!task.current() || error.name === 'AbortError') return;
      if (error.status === 409) {
        conflict = true;
        errors.fail(new Error(error.code === 'MODE_LOCKED' ? 'Không thể đổi chế độ hoặc lịch đang chạy. Tải lại trạng thái; tắt rồi bật lại để đặt kế hoạch mới.' : 'Chính sách đã thay đổi. Tải lại trạng thái trước khi tạo yêu cầu mới.'), true);
        errors.error.append(button('Tải lại trạng thái', () => { view.finish(); load(); }));
      } else errors.fail(error, true);
    } finally { if (task.current()) { view.pending(false); submit.disabled = conflict; } }
  }
  load();
}
