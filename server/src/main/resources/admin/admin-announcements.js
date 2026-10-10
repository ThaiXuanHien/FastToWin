'use strict';

const exact = value => new Intl.NumberFormat('vi-VN').format(BigInt(value));
const utc = value => new Date(value).toISOString();
const states = { DRAFT: 'Bản nháp', PUBLISHED: 'Đã công bố', CANCELLED: 'Đã hủy' };
const categories = { GENERAL: 'Chung', EVENT: 'Sự kiện', MAINTENANCE: 'Bảo trì', COMPENSATION: 'Đền bù' };
const actions = { NONE: 'Không có hành động', REWARD_EVENT: 'Xem sự kiện thưởng', GIFT_MAILBOX: 'Mở hòm quà', APP_UPDATE: 'Kiểm tra cập nhật ứng dụng' };
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const phase = (item, now) => item.status !== 'PUBLISHED' ? states[item.status] : now < item.startsAtEpochMillis ? 'Đã công bố · Đã lên lịch' : now >= item.endsAtEpochMillis ? 'Đã công bố · Đã hết hạn' : 'Đã công bố · Đang có hiệu lực';
const metrics = { frozen: 'Người nhận đã chốt', eligible: 'Đủ điều kiện (đã đánh giá)', pending: 'Chờ lưu trong ứng dụng', stored: 'Đã lưu trong ứng dụng', read: 'Đã đọc', dismissed: 'Đã ẩn trong hộp thư', bannerHidden: 'Đã ẩn banner', skipped: 'Bỏ qua', pushAccepted: 'Nhà cung cấp đã nhận', pushFailed: 'Push thất bại', pushUnknown: 'Push chưa rõ kết quả', pushInFlight: 'Push đang xử lý', pushDisabled: 'Push tắt', pushOptedOut: 'Chưa đồng ý nhận push', pushNoToken: 'Không có địa chỉ push', pushUnavailable: 'Nhà cung cấp chưa khả dụng', pushExpired: 'Push đã hết hạn' };

export function openAnnouncements(ui) {
  const { node, append, button, iconButton, field, modal, feedback, shell, channel, api, role } = ui;
  const base = '/announcements', path = id => base + '/' + encodeURIComponent(id);
  const readable = () => ['OWNER', 'OPERATOR', 'SUPPORT'].includes(role());
  const writable = () => ['OWNER', 'OPERATOR'].includes(role());
  const owner = () => role() === 'OWNER';
  function facts(parent, entries) {
    const dl = node('dl', null, { class: 'facts' });
    for (const [label, value] of entries) dl.append(append(node('div'), node('dt', label), node('dd', value)));
    parent.append(dl);
  }
  function languages(parent, item, shortened = false, limit = 240) {
    const content = node('div', null, { class: 'announcement-languages' });
    for (const [lang, title, original, name] of [['vi', item.titleVi, item.bodyVi, 'Tiếng Việt'], ['en', item.titleEn, item.bodyEn, 'English']]) {
      let text = original;
      if (shortened && text.length > limit) { text = text.slice(0, limit); if (/[\uD800-\uDBFF]$/.test(text)) text = text.slice(0, -1); }
      content.append(append(node('section', null, { class: 'announcement-copy stack', lang, 'aria-label': name }), node('p', name, { class: 'muted' }), node('h3', title), node('p', text, { class: 'announcement-body' })));
    } parent.append(content);
  }
  function definitionFacts(parent, item) {
    facts(parent, [['Trạng thái', states[item.status]], ['Phân loại', categories[item.category]], ['Ưu tiên', exact(item.priority)], ['Bắt đầu UTC (bao gồm)', utc(item.startsAtEpochMillis)], ['Kết thúc UTC (không bao gồm)', utc(item.endsAtEpochMillis)], ['Trong ứng dụng', 'Luôn bật'], ['Banner', item.showBanner ? 'Bật' : 'Tắt'], ['Push tùy chọn', item.pushEnabled ? 'Bật — chỉ gửi khi có sự đồng ý riêng' : 'Tắt'], ['Hành động', actions[item.action]], ['Tham chiếu đích', item.targetId || 'Không có'], ['Thông báo được sửa lại', item.supersedesAnnouncementId || 'Không có'], ['Nhóm người nhận', item.audience === 'USER_IDS' ? 'Danh sách UUID đã xác minh' : 'Tài khoản đã đăng ký tại lúc công bố'], ['UUID đã chọn', item.selectedUserIds.join('\n') || 'Toàn bộ nhóm đã đăng ký'], ['Phiên bản', exact(item.version)]]);
  }
  function surface(title) { const result = modal(title); result.dialog.classList.add('announcement-modal'); return result; }
  function formBody(dialog) { const form = node('form', null, { class: 'stack modal-form' }); dialog.dialog.append(form); return form; }
  function input(form, label, name, type = 'text', value = '', attrs = {}) { const result = field(form, label, 'announcement-' + name, type, attrs); result.value = value ?? ''; return result; }
  function select(form, label, name, options, value) {
    const wrapper = node('div', null, { class: 'field' }), result = node('select', null, { id: 'announcement-' + name });
    for (const [key, text] of Object.entries(options)) result.append(node('option', text, { value: key }));
    result.value = value; append(wrapper, node('label', label, { for: result.id }), result); form.append(wrapper); return result;
  }
  function footer(form, dialog, label) { const submit = button(label, null, { type: 'submit', class: 'primary' }); form.append(append(node('div', null, { class: 'modal-actions' }), dialog.cancel, submit)); return submit; }
  function parseUtc(control) {
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?$/.test(control.value)) throw new Error('Nhập đầy đủ năm và giờ UTC.');
    const value = Date.parse(control.value + 'Z');
    if (!Number.isFinite(value) || value < 0 || value > 253402300799999 || !utc(value).startsWith(control.value.slice(0, 16))) throw new Error('Thời điểm UTC không hợp lệ.');
    return value;
  }
  function uuidValue(value, name) { if (!uuid.test(value)) throw new Error(name + ' phải là UUID đầy đủ.'); return value.toLowerCase(); }
  function mutation(form, submit, notices, dialog, target, payload, success, reload, method = 'POST', allowed = writable) {
    const request = channel(); let busy = false, signature, requestId, conflicted = false;
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (busy || conflicted || !allowed() || !form.reportValidity()) return;
      let body; try { body = payload(); } catch (error) { notices.fail(error, true); return; }
      const next = JSON.stringify(body); if (next !== signature) { signature = next; requestId = crypto.randomUUID(); }
      busy = true; notices.clear(); dialog.pending(true); submit.disabled = true;
      const title = submit.textContent; submit.textContent = 'Đang xử lý…';
      const task = request(), current = task.current;
      task.current = () => current() && allowed() && dialog.dialog.isConnected && dialog.dialog.open;
      try { const result = await api(target, task, { ...body, requestId }, method); if (task.current()) success(result); }
      catch (error) {
        if (!task.current() || error.name === 'AbortError') return;
        if (error.status === 409) { conflicted = true; notices.fail(new Error('Phiên bản hoặc người nhận đã thay đổi. Tải lại và xem trước lại.'), true); notices.error.append(button('Tải lại dữ liệu', () => { dialog.finish(); reload(); })); }
        else notices.fail(new Error((error.message || 'Không thể kết nối.') + ' Kết quả chưa được xác nhận; thử lại cùng nội dung sẽ giữ nguyên mã yêu cầu.'), true);
      } finally {
        if (task.current()) { busy = false; dialog.pending(false); submit.textContent = title; submit.disabled = conflicted; if (conflicted) for (const control of form.querySelectorAll('input,select,textarea')) control.disabled = true; }
      }
    });
  }
  function pagination(parent, data, load) {
    const page = BigInt(data.page), size = BigInt(data.pageSize), total = BigInt(data.total);
    parent.append(append(node('div', null, { class: 'pagination' }), iconButton('Trang trước', () => load(data.page - 1), 'previous', { ...(page === 0n ? { disabled: '' } : {}) }), node('span', `Trang ${exact(page + 1n)} · ${exact(total)} thông báo`), iconButton('Trang sau', () => load(data.page + 1), 'next', { ...((page + 1n) * size >= total || (page + 1n) * size > 2147483647n ? { disabled: '' } : {}) })));
  }
  function list() {
    if (!readable()) return;
    const main = shell('Thông báo', 'announcements');
    main.append(node('p', 'Thông báo trong ứng dụng và lịch sử luôn được lưu. Push là tùy chọn riêng. Thông báo không trao quà, đổi ví hoặc bật bảo trì.', { class: 'muted' }));
    const toolbar = node('div', null, { class: 'toolbar' });
    if (writable()) toolbar.append(iconButton('Tạo thông báo', () => edit(), 'add', { class: 'primary' }));
    const filter = select(toolbar, 'Trạng thái thông báo', 'status', { '': 'Tất cả lịch sử', ...states }, '');
    toolbar.append(iconButton('Làm mới thông báo', () => load(currentPage), 'refresh')); main.append(toolbar);
    const notices = feedback(main), results = node('div'), request = channel(); main.append(results); let currentPage = 0;
    filter.addEventListener('change', () => load(0));
    async function load(page = 0) {
      currentPage = page; notices.clear(); notices.status.textContent = 'Đang tải thông báo…'; const task = request();
      try {
        const data = await api(base + `?page=${page}&pageSize=25` + (filter.value ? '&status=' + filter.value : ''), task);
        if (!task.current() || !readable()) return; results.replaceChildren(); notices.status.textContent = exact(data.total) + ' thông báo.';
        for (const row of data.items) { const item = row.announcement; results.append(append(node('article', null, { class: 'announcement-row' }), append(node('div', null, { class: 'row-info' }), node('strong', item.titleVi), node('p', phase(item, row.serverNowEpochMillis) + ' · ' + utc(item.startsAtEpochMillis), { class: 'muted' })), iconButton('Xem thông báo ' + item.titleVi, () => detail(item.id), 'eye'))); }
        if (!data.items.length) results.append(node('p', 'Chưa có thông báo.', { class: 'muted' })); pagination(results, data, load);
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
    } load();
  }
  function detail(id, message = '') {
    if (!readable()) return;
    const main = shell('Chi tiết thông báo', 'announcements'); main.append(append(node('div', null, { class: 'toolbar' }), iconButton('Quay lại thông báo', list, 'back'), iconButton('Làm mới thông báo', load, 'refresh')));
    const notices = feedback(main), content = node('div', null, { class: 'stack' }), request = channel(); append(main, node('p', message, { role: 'status', class: 'confirmation' }), content);
    async function load() {
      const task = request(); notices.status.textContent = 'Đang tải chi tiết…';
      try {
        const row = await api(path(id), task); if (!task.current() || !readable()) return; content.replaceChildren(); notices.clear(); const item = row.announcement;
        main.querySelector('h1').textContent = item.titleVi; const controls = node('div', null, { class: 'announcement-actions' });
        if (writable() && item.status === 'DRAFT') append(controls, iconButton('Sửa thông báo', () => edit(item), 'edit'), button('Xem trước', () => preview(item)));
        if (writable() && item.status !== 'DRAFT') controls.append(button('Tạo bản sửa mới', () => edit({ ...item, id: null, version: null, status: 'DRAFT', supersedesAnnouncementId: item.id, pushEnabled: false })));
        if (owner() && item.status === 'PUBLISHED') controls.append(button('Hủy thông báo', () => confirm(row, null, true)));
        controls.append(button('Báo cáo', () => report(row))); content.append(controls, node('p', phase(item, row.serverNowEpochMillis), { class: 'muted' })); languages(content, item); definitionFacts(content, item);
        if (item.status !== 'DRAFT') content.append(node('p', 'Nội dung đã công bố được giữ nguyên. Muốn sửa, tạo thông báo mới có tham chiếu bản cũ.', { class: 'muted' }));
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
    } load();
  }
  function edit(item) {
    if (!writable() || (item?.id && item.status !== 'DRAFT')) return;
    const dialog = surface(item?.id ? 'Sửa thông báo' : 'Tạo thông báo'), form = formBody(dialog);
    form.append(node('p', 'Nhập năm và giờ UTC. Bắt đầu được bao gồm, kết thúc không được bao gồm. Nội dung chỉ là văn bản; không trao thưởng hoặc bật bảo trì.', { class: 'muted' }));
    const viTitle = input(form, 'Tiêu đề (VI)', 'title-vi', 'text', item?.titleVi, { required: '', maxlength: '160' });
    const enTitle = input(form, 'Title (EN)', 'title-en', 'text', item?.titleEn, { required: '', maxlength: '160' });
    const viBody = input(form, 'Nội dung (VI)', 'body-vi', 'textarea', item?.bodyVi, { required: '', maxlength: '4000', rows: '4' });
    const enBody = input(form, 'Body (EN)', 'body-en', 'textarea', item?.bodyEn, { required: '', maxlength: '4000', rows: '4' });
    const category = select(form, 'Phân loại', 'category', categories, item?.category || 'GENERAL');
    const priority = input(form, 'Ưu tiên (0–100)', 'priority', 'number', item?.priority ?? 0, { min: '0', max: '100', step: '1', required: '' });
    const start = input(form, 'Bắt đầu (UTC, bao gồm)', 'start', 'datetime-local', item ? utc(item.startsAtEpochMillis).slice(0, 23) : '', { step: '0.001', required: '' });
    const end = input(form, 'Kết thúc (UTC, không bao gồm)', 'end', 'datetime-local', item ? utc(item.endsAtEpochMillis).slice(0, 23) : '', { step: '0.001', required: '' });
    const audience = select(form, 'Nhóm người nhận', 'audience', { ALL_REGISTERED: 'Tài khoản đã đăng ký tại lúc công bố', USER_IDS: 'Danh sách UUID đã xác minh' }, item?.audience || 'ALL_REGISTERED');
    const ids = input(form, 'UUID người chơi (mỗi dòng hoặc dấu phẩy)', 'ids', 'textarea', item?.selectedUserIds?.join('\n'), { rows: '3' });
    const toggleIds = () => { ids.parentElement.hidden = audience.value !== 'USER_IDS'; ids.required = audience.value === 'USER_IDS'; }; audience.addEventListener('change', toggleIds); toggleIds();
    const action = select(form, 'Hành động', 'action', actions, item?.action || 'NONE');
    const target = input(form, 'UUID sự kiện hoặc quà đã công bố', 'target', 'text', item?.targetId);
    const toggleTarget = () => { target.parentElement.hidden = !['REWARD_EVENT', 'GIFT_MAILBOX'].includes(action.value); target.required = !target.parentElement.hidden; }; action.addEventListener('change', toggleTarget); toggleTarget();
    form.append(node('p', 'Cập nhật ứng dụng chỉ mở khả năng cập nhật hiện có của thiết bị; không cài đặt tự động.', { class: 'muted' }));
    const reference = input(form, 'UUID thông báo cũ được sửa lại (tùy chọn)', 'reference', 'text', item?.supersedesAnnouncementId);
    const banner = field(form, 'Hiện banner trong ứng dụng', 'announcement-banner', 'checkbox'); banner.checked = item?.showBanner ?? false;
    const push = field(form, 'Gửi push tùy chọn', 'announcement-push', 'checkbox'); push.checked = item?.pushEnabled ?? false;
    const capability = node('p', 'Đang kiểm tra nhà cung cấp push…', { class: 'muted', role: 'status' }); form.append(capability);
    form.append(node('p', 'Trong ứng dụng luôn bật. Push chỉ gửi cho tài khoản đã đồng ý riêng và có địa chỉ nhận; nhà cung cấp nhận không có nghĩa người chơi đã đọc. Kết quả chưa rõ sẽ không tự gửi lại.', { class: 'muted' }));
    const why = input(form, 'Lý do bản nháp nội bộ', 'reason', 'textarea', '', { required: '', maxlength: '500', rows: '2' }), notices = feedback(form), submit = footer(form, dialog, 'Lưu bản nháp');
    mutation(form, submit, notices, dialog, item?.id ? path(item.id) : base, () => {
      const startsAtEpochMillis = parseUtc(start), endsAtEpochMillis = parseUtc(end); if (endsAtEpochMillis <= startsAtEpochMillis) throw new Error('Kết thúc phải sau bắt đầu UTC.');
      const selectedUserIds = audience.value === 'USER_IDS' ? ids.value.split(/[\s,]+/).filter(Boolean).map(value => uuidValue(value, 'Người nhận')) : [];
      if (audience.value === 'USER_IDS' && (!selectedUserIds.length || selectedUserIds.length > 10000)) throw new Error('Chọn từ 1 đến 10.000 UUID người chơi.');
      return { titleVi: viTitle.value, titleEn: enTitle.value, bodyVi: viBody.value, bodyEn: enBody.value, category: category.value, priority: Number(priority.value), startsAtEpochMillis, endsAtEpochMillis, showBanner: banner.checked, pushEnabled: push.checked, action: action.value, targetId: target.required ? uuidValue(target.value.trim(), 'Tham chiếu đích') : null, supersedesAnnouncementId: reference.value.trim() ? uuidValue(reference.value.trim(), 'Thông báo cũ') : null, audience: audience.value, selectedUserIds, reason: why.value, ...(item?.id ? { expectedVersion: item.version } : {}) };
    }, result => { dialog.finish(); detail(result.announcement.id, 'Đã lưu bản nháp.'); }, () => item?.id ? detail(item.id) : list(), item?.id ? 'PUT' : 'POST');
    dialog.show(viTitle); const task = channel()(), current = task.current; task.current = () => current() && writable() && dialog.dialog.open && dialog.dialog.isConnected;
    api(base + '/capabilities', task).then(data => { if (task.current()) capability.textContent = data.pushConfigured ? 'Nhà cung cấp push khả dụng; vẫn cần sự đồng ý riêng.' : 'Nhà cung cấp push chưa khả dụng. Nội dung trong ứng dụng vẫn được lưu.'; }).catch(error => { if (task.current() && error.name !== 'AbortError') capability.textContent = 'Chưa xác nhận khả năng push. Nội dung trong ứng dụng vẫn được lưu.'; });
  }
  async function preview(item) {
    if (!writable() || item.status !== 'DRAFT') return;
    const dialog = surface('Xem trước thông báo'), form = formBody(dialog), notices = feedback(form); form.append(dialog.cancel); dialog.show(dialog.cancel);
    const task = channel()(), current = task.current; task.current = () => current() && writable() && dialog.dialog.open && dialog.dialog.isConnected; notices.status.textContent = 'Đang xác minh người nhận…';
    try {
      const data = await api(path(item.id) + '/preview', task, {}); if (!task.current()) return;
      form.replaceChildren(); form.append(node('h3', 'Nội dung đầy đủ trong ứng dụng')); languages(form, data.announcement);
      form.append(node('h3', 'Lời nhắc push rút gọn')); languages(form, data.announcement, true, data.pushReminderBodyLimit);
      facts(form, [['Số người nhận', exact(data.recipientCount)], ['UUID trùng (chỉ nhận một lần)', exact(data.duplicateCount)], ['UUID không hợp lệ', data.invalidUserIds.join('\n') || 'Không có'], ['Push tùy chọn', data.announcement.pushEnabled ? 'Bật' : 'Tắt'], ['Nhà cung cấp push', data.pushConfigured ? 'Khả dụng' : 'Chưa khả dụng'], ['Phiên bản đã xem trước', exact(data.announcement.version)]]);
      definitionFacts(form, data.announcement);
      form.append(node('p', 'Push cần đồng ý riêng. Nhà cung cấp nhận không chứng minh đã đọc. Kết quả chưa rõ không tự gửi lại. Thông báo không thay đổi quà, ví hoặc bảo trì.', { class: 'muted' }));
      const actions = append(node('div', null, { class: 'modal-actions' }), dialog.cancel);
      if (owner()) actions.append(button('Tiếp tục công bố', () => { dialog.finish(); confirm({ announcement: data.announcement }, data); }, { class: 'primary', ...(data.invalidUserIds.length ? { disabled: '' } : {}) }));
      form.append(actions); form.scrollTop = 0; dialog.cancel.focus({ preventScroll: true });
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  function confirm(row, previewData, cancelling = false) {
    if (!owner()) return;
    const item = row.announcement, dialog = surface(cancelling ? 'Hủy thông báo' : 'Xác nhận công bố'), form = formBody(dialog);
    form.append(node('p', cancelling ? 'Hủy dừng phần chưa lưu và push chưa bắt đầu. Push đang gửi có thể hoàn tất; không thể thu hồi. Lịch sử đã lưu được giữ nguyên. Không đổi quà, ví hoặc bảo trì.' : 'Chốt nhóm người nhận tại lúc công bố. Nội dung đã công bố không thể sửa. Push chỉ gửi khi đã chọn và người chơi đã đồng ý riêng.', { class: 'muted' }));
    languages(form, item); if (previewData) facts(form, [['Số người nhận đã xem trước', exact(previewData.recipientCount)], ['Phiên bản', exact(item.version)]]);
    const why = input(form, cancelling ? 'Lý do hủy nội bộ' : 'Lý do công bố nội bộ', 'confirm-reason', 'textarea', '', { required: '', maxlength: '500', rows: '2' });
    const notices = feedback(form), submit = footer(form, dialog, cancelling ? 'Xác nhận hủy' : 'Công bố thông báo');
    mutation(form, submit, notices, dialog, path(item.id) + (cancelling ? '/cancel' : '/publish'), () => ({ expectedVersion: item.version, reason: why.value, ...(previewData ? { previewToken: previewData.previewToken } : {}) }), result => { dialog.finish(); detail(result.announcement.id, cancelling ? 'Đã hủy thông báo.' : 'Đã công bố thông báo.'); }, () => detail(item.id), 'POST', owner); dialog.show(why);
  }
  function report(row) {
    if (!readable()) return;
    const dialog = surface('Báo cáo thông báo'), form = formBody(dialog);
    facts(form, Object.entries(metrics).map(([key, label]) => [label, exact(row.counts[key])]));
    form.append(node('p', 'Các bộ đếm là những sự kiện khác nhau và có thể chồng lấp; không cộng thành tổng. Đã lưu không chứng minh đã hiển thị. Nhà cung cấp đã nhận không chứng minh đã đọc. Push chưa rõ kết quả không tự gửi lại.', { class: 'muted' }), dialog.cancel); dialog.show(dialog.cancel); form.scrollTop = 0;
  }
  list();
}
