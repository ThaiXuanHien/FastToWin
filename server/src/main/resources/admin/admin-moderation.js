'use strict';

const states = { NEW: 'Mới', REVIEWING: 'Đang xem xét', RESOLVED: 'Đã xử lý', DISMISSED: 'Bỏ qua' };
const types = { PLAYER_PROFILE: 'Hồ sơ người chơi', PLAYER_AVATAR: 'Ảnh đại diện', CLAN: 'Bang hội', ROOM: 'Phòng chơi', TOURNAMENT: 'Giải đấu' };
const reasons = { HARASSMENT: 'Quấy rối', HATE: 'Thù ghét', SEXUAL_CONTENT: 'Nội dung tình dục', VIOLENCE: 'Bạo lực / đe dọa', SPAM: 'Spam', PERSONAL_INFORMATION: 'Thông tin riêng tư', OTHER: 'Khác' };
const writable = role => role === 'OWNER' || role === 'OPERATOR';
const readable = role => writable(role) || role === 'SUPPORT';

// One live dialog owns one retry identity. An unknown response can be retried;
// a stale revision must be reloaded and explicitly reviewed again.
export function createModerationSubmission({ send, role, current, uuid = () => crypto.randomUUID() }) {
  let busy = false, conflicted = false, signature = '', requestId;
  return { async submit(payload) {
    if (!current()) throw new DOMException('Request superseded', 'AbortError');
    if (busy) throw new Error('Đang xử lý yêu cầu trước.');
    if (conflicted) throw new Error('Tải lại báo cáo và xem xét phiên bản mới trước khi xử lý.');
    const restricted = Object.hasOwn(payload, 'blocked') || payload.removeContent === true || payload.status !== 'REVIEWING';
    if (!readable(role()) || (restricted && !writable(role()))) throw new Error('Bạn không có quyền thực hiện thao tác này.');
    const reason = payload.reason?.trim();
    if (!Number.isSafeInteger(payload.expectedVersion) || payload.expectedVersion < 0 || !reason || [...reason].length > 500 || /[\u0000-\u001f\u007f]/u.test(reason)) throw new Error('Nhập lý do từ 1–500 ký tự và tải lại phiên bản hợp lệ.');
    const body = { ...payload, reason };
    const next = JSON.stringify(body);
    if (next !== signature) { signature = next; requestId = uuid(); }
    busy = true;
    try {
      const result = await send({ ...body, requestId });
      if (!current()) throw new DOMException('Request superseded', 'AbortError');
      return result;
    } catch (error) { if (error.status === 409) conflicted = true; throw error; }
    finally { busy = false; }
  } };
}

export function openModeration(ui) {
  const { node, append, button, iconButton, field, modal, feedback, shell, channel, api, role } = ui;
  const base = '/moderation', path = id => `${base}/reports/${encodeURIComponent(id)}`;
  const time = value => value == null ? '—' : new Date(value).toLocaleString('vi-VN');
  const count = value => new Intl.NumberFormat('vi-VN').format(BigInt(value));
  function select(parent, label, values, initial) {
    const id = 'moderation-' + crypto.randomUUID(), wrapper = node('div', null, { class: 'field' }), control = node('select', null, { id });
    for (const [value, title] of Object.entries(values)) control.append(node('option', title, { value }));
    control.value = initial; append(wrapper, node('label', label, { for: id }), control); parent.append(wrapper); return control;
  }
  function facts(parent, values) {
    const list = node('dl', null, { class: 'facts' });
    for (const [label, value] of values) list.append(append(node('div'), node('dt', label), node('dd', value ?? '—')));
    parent.append(list);
  }
  function list(message = '') {
    if (!readable(role())) return;
    const main = shell('Kiểm duyệt', 'moderation');
    if (message) main.append(node('p', message, { role: 'status', class: 'confirmation' }));
    main.append(node('p', 'Xem xét nội dung thực tế trước khi xử lý. Báo cáo không tự động khóa người chơi.', { class: 'muted' }));
    const toolbar = node('div', null, { class: 'toolbar' }), filter = select(toolbar, 'Trạng thái báo cáo', { '': 'Tất cả', ...states }, 'NEW');
    let page = 0;
    toolbar.append(iconButton('Làm mới báo cáo', () => load(page), 'refresh')); main.append(toolbar);
    const notices = feedback(main), results = node('div', null, { class: 'stack' }), request = channel(); main.append(results);
    filter.addEventListener('change', () => load(0));
    async function load(nextPage) {
      page = nextPage; notices.clear(); notices.status.textContent = 'Đang tải báo cáo…'; const task = request();
      try {
        const data = await api(`${base}/reports?page=${page}&pageSize=25${filter.value ? '&status=' + filter.value : ''}`, task);
        if (!task.current() || !readable(role())) return;
        results.replaceChildren(); notices.status.textContent = `${count(data.total)} báo cáo.`;
        for (const item of data.items) {
          const report = item.report;
          const row = node('article', null, { class: 'announcement-row moderation-row' });
          append(row, append(node('div', null, { class: 'row-info' }), node('strong', types[report.targetType] || report.targetType), node('p', `${states[report.status] || report.status} · ${reasons[report.reason] || report.reason}`, { class: 'muted' }), node('p', time(report.createdAtEpochMillis), { class: 'muted' })), iconButton(`Xem báo cáo ${report.id}`, () => detail(report.id), 'eye'));
          results.append(row);
        }
        if (!data.items.length) results.append(node('p', 'Không có báo cáo ở trạng thái này.', { class: 'muted' }));
        results.append(append(node('div', null, { class: 'pagination' }), iconButton('Trang trước', () => load(page - 1), 'previous', { ...(page === 0 ? { disabled: '' } : {}) }), node('span', `Trang ${count(page + 1)} · ${count(data.total)} báo cáo`), iconButton('Trang sau', () => load(page + 1), 'next', { ...((BigInt(page) + 1n) * BigInt(data.pageSize) >= BigInt(data.total) ? { disabled: '' } : {}) })));
      } catch (error) { if (task.current() && error.name !== 'AbortError') { notices.fail(error); notices.error.append(button('Thử tải lại', () => load(page))); } }
    }
    load(0);
  }
  async function detail(id) {
    if (!readable(role())) return;
    const dialog = modal('Chi tiết báo cáo'); dialog.dialog.classList.add('announcement-modal', 'moderation-detail');
    const body = node('div', null, { class: 'stack modal-form' }); dialog.dialog.append(body); const notices = feedback(body);
    body.append(dialog.cancel); dialog.show(dialog.cancel); const request = channel(), task = request();
    const originalCurrent = task.current;
    task.current = () => originalCurrent() && dialog.dialog.isConnected && dialog.dialog.open && readable(role());
    notices.status.textContent = 'Đang tải chi tiết…';
    try {
      const view = await api(path(id), task); if (!task.current()) return;
      notices.clear(); const report = view.report;
      facts(body, [['Loại nội dung', types[report.targetType]], ['Trạng thái', states[report.status]], ['Lý do báo cáo', reasons[report.reason]], ['Thời điểm', time(report.createdAtEpochMillis)], ['Đóng báo cáo', time(report.closedAtEpochMillis)], ['Phiên bản', String(report.version)]]);
      // All untrusted text uses the shared text-only node factory, never innerHTML.
      if (view.lastActionReason) body.append(node('h3', 'Lý do xử lý gần nhất'), node('p', view.lastActionReason, { class: 'announcement-body moderation-copy' }));
      body.append(node('h3', 'Nội dung được ghi nhận'), node('p', report.evidenceText || 'Không có bản chụp văn bản.', { class: 'announcement-body moderation-copy' }), node('h3', 'Ghi chú của người báo cáo'), node('p', report.note || 'Không có ghi chú.', { class: 'announcement-body moderation-copy' }));
      if (report.targetType === 'PLAYER_AVATAR') {
        body.append(node('p', 'Không lưu bản sao ảnh làm bằng chứng. Kiểm tra ảnh hiện tại trước khi xử lý.', { class: 'muted' }));
        if (!view.targetMissing && report.targetId) {
          const imageArea = node('div', null, { class: 'moderation-avatar' });
          const loadImage = button('Xem ảnh hiện tại', () => {
            loadImage.disabled = true;
            const image = node('img', null, { src: '/api/avatar/' + encodeURIComponent(report.targetId), alt: 'Ảnh đại diện hiện tại của đối tượng báo cáo', width: '160', height: '160', referrerpolicy: 'no-referrer' });
            image.addEventListener('error', () => { imageArea.replaceChildren(node('p', 'Không tải được ảnh hiện tại. Ảnh có thể đã được thay hoặc gỡ.', { role: 'status' })); });
            imageArea.append(image);
          });
          body.append(loadImage, imageArea);
        }
      }
      if (view.targetMissing || view.targetChanged) body.append(node('p', view.targetMissing ? 'Đối tượng không còn tồn tại. Không thể gỡ nội dung từ báo cáo này.' : 'Nội dung đã thay đổi từ lúc báo cáo. Hãy đánh giá lại trước khi xử lý; thao tác gỡ phiên bản cũ bị chặn.', { role: 'status', class: 'alert' }));
      const actions = node('div', null, { class: 'modal-actions' });
      if (report.status === 'NEW' || report.status === 'REVIEWING') {
        actions.append(button('Xử lý báo cáo', () => { dialog.finish(); resolve(view); }, { class: 'primary' }));
      }
      if (writable(role()) && view.targetOwnerId && view.restriction) actions.append(iconButton(view.restriction.blocked ? 'Mở khóa tài khoản' : 'Khóa tài khoản', () => { dialog.finish(); restriction(view); }, 'lock'));
      body.append(actions); body.append(dialog.cancel);
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  function resolve(view) {
    const report = view.report, dialog = modal('Xử lý báo cáo'); dialog.dialog.classList.add('announcement-modal');
    const form = node('form', null, { class: 'stack modal-form' }); dialog.dialog.append(form);
    const status = select(form, 'Kết quả xử lý', writable(role()) ? { REVIEWING: states.REVIEWING, RESOLVED: states.RESOLVED, DISMISSED: states.DISMISSED } : { REVIEWING: states.REVIEWING }, 'REVIEWING');
    let remove;
    if (writable(role())) {
      remove = field(form, 'Gỡ nội dung đã được báo cáo', 'moderation-remove', 'checkbox');
      if (view.targetMissing || view.targetChanged) { remove.disabled = true; form.append(node('p', 'Nội dung đã đổi hoặc không còn tồn tại; không thể gỡ từ phiên bản báo cáo cũ.', { class: 'muted' })); }
    }
    form.append(node('p', 'Chỉ gỡ phần nội dung vi phạm. Không hủy trận/giải hoặc thay đổi số dư.', { class: 'muted' }));
    const reason = field(form, 'Lý do xử lý nội bộ', 'moderation-reason', 'textarea', { required: '', maxlength: '500', rows: '3' }), notices = feedback(form);
    const submit = button('Lưu xử lý', null, { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'modal-actions' }), dialog.cancel, submit));
    status.addEventListener('change', () => { if (status.value !== 'RESOLVED' && remove) remove.checked = false; });
    remove?.addEventListener('change', () => { if (remove.checked) status.value = 'RESOLVED'; });
    bindMutation(dialog, form, submit, notices, path(report.id) + '/resolve', () => ({ expectedVersion: report.version, status: status.value, reason: reason.value, removeContent: Boolean(remove?.checked) }), () => { dialog.finish(); list('Đã lưu kết quả xử lý báo cáo.'); });
    dialog.show(status);
  }
  function restriction(view) {
    if (!writable(role())) return;
    const current = view.restriction, blocked = !current.blocked, dialog = modal(blocked ? 'Khóa tài khoản' : 'Mở khóa tài khoản');
    const form = node('form', null, { class: 'stack modal-form' }); dialog.dialog.append(form);
    form.append(node('p', blocked ? 'Tài khoản sẽ bị ngắt các phiên đang hoạt động và không thể đăng nhập/kết nối lại. Thao tác này không xóa tài khoản hay thay đổi số dư.' : 'Cho phép người chơi đăng nhập lại. Nội dung đã gỡ vẫn giữ trạng thái kiểm duyệt.'));
    const reason = field(form, 'Lý do xử lý nội bộ', 'moderation-restriction-reason', 'textarea', { required: '', maxlength: '500', rows: '3' }), notices = feedback(form);
    const submit = button(blocked ? 'Xác nhận khóa' : 'Xác nhận mở khóa', null, { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'modal-actions' }), dialog.cancel, submit));
    bindMutation(dialog, form, submit, notices, `${base}/users/${encodeURIComponent(view.targetOwnerId)}/restriction`, () => ({ expectedVersion: current.version, reason: reason.value, blocked }), () => { dialog.finish(); list(blocked ? 'Đã khóa tài khoản.' : 'Đã mở khóa tài khoản.'); });
    dialog.show(reason);
  }
  function bindMutation(dialog, form, submit, notices, target, payload, success) {
    const request = channel();
    const lockedControls = [...form.querySelectorAll('input,select,textarea')].filter(control => control.disabled);
    const live = () => dialog.dialog.isConnected && dialog.dialog.open && readable(role());
    const submission = createModerationSubmission({ role, current: live, send: async body => {
      const task = request(), before = task.current; task.current = () => before() && live();
      return api(target, task, body, 'POST');
    } });
    let busy = false, conflict = false;
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (busy || conflict || !live() || !form.reportValidity()) return;
      busy = true; notices.clear(); dialog.pending(true); submit.disabled = true; const caption = submit.textContent; submit.textContent = 'Đang xử lý…';
      try { await submission.submit(payload()); if (live()) success(); }
      catch (error) {
        if (!live() || error.name === 'AbortError') return;
        if (error.status === 409) { conflict = true; notices.fail(new Error('Phiên bản đã thay đổi. Tải lại để xem xét lại trước khi xử lý.'), true); notices.error.append(button('Tải lại báo cáo', () => { dialog.finish(); list(); })); }
        else notices.fail(new Error((error.message || 'Không thể kết nối.') + ' Thử lại cùng nội dung sẽ giữ nguyên mã yêu cầu.'), true);
      } finally { if (live()) { busy = false; dialog.pending(false); for (const control of lockedControls) control.disabled = true; submit.textContent = caption; submit.disabled = conflict; } }
    });
  }
  list();
}
