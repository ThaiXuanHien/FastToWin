'use strict';

// All credentials/CSRF and retry state live only in this document's memory.
const app = document.getElementById('app');
const roles = { OWNER: 'Owner', OPERATOR: 'Operator', SUPPORT: 'Support' };
const labels = {
  REGISTERED: 'Đã đăng ký', GUEST: 'Khách', ACTIVE: 'Đang hoạt động', DELETED: 'Đã xóa', BANNED: 'Đã khóa',
  CASUAL: 'Đấu thường', RANKED: 'Đấu hạng', WIN: 'Thắng', LOSS: 'Thua', DRAW: 'Hòa',
  MATCH: 'Thưởng trận đấu', MATCH_REWARD: 'Thưởng trận đấu', DAILY_CHECK_IN: 'Điểm danh', MISSION: 'Nhiệm vụ',
  SEASON_REWARD: 'Thưởng mùa giải', CLAN_QUEST: 'Nhiệm vụ bang', CLAN_DONATION: 'Đóng góp bang',
  GOLD_EXCHANGE: 'Đổi Vàng', COSMETIC_PURCHASE: 'Mua vật phẩm', STORE_PURCHASE: 'Mua trong cửa hàng',
  TOURNAMENT_ENTRY: 'Phí tham gia giải', TOURNAMENT_ENTRY_REFUND: 'Hoàn phí giải', TOURNAMENT_ENTRY_REVERSED: 'Phí giải đã đảo',
  ADMIN_GIFT: 'Quà quản trị', COMPENSATION: 'Bồi hoàn',
  ADMIN_LOGIN: 'Đăng nhập quản trị', ADMIN_LOGOUT: 'Đăng xuất quản trị', ADMIN_SESSION: 'Khôi phục phiên',
  ADMIN_BOOTSTRAP: 'Cấp Owner ban đầu', ADMIN_CREATE: 'Cấp tài khoản quản trị', ADMIN_ACCESS: 'Thay đổi quyền truy cập',
  ADMIN_LIST: 'Xem tài khoản quản trị', AUDIT_READ: 'Xem nhật ký kiểm toán', USER_SEARCH: 'Tra cứu người chơi',
  USER_DETAIL: 'Xem hồ sơ người chơi', USER_MATCHES: 'Xem lịch sử trận', USER_WALLET: 'Xem lịch sử ví', USER_GIFTS: 'Xem nguồn quà',
};
const label = value => Object.hasOwn(labels, value) ? labels[value] : value;
let session = null;
let sessionVersion = 0;
let viewVersion = 0;
let loggedOut = false;
let authRequests = 0;
const pending = new Set();
const aborted = () => new DOMException('Request superseded', 'AbortError');

function node(tag, text, attributes = {}) {
  const result = document.createElement(tag);
  if (text !== undefined && text !== null) result.textContent = String(text);
  for (const [name, value] of Object.entries(attributes)) result.setAttribute(name, value);
  return result;
}
function append(parent, ...children) { parent.append(...children); return parent; }
function button(text, action, attributes = {}) {
  const result = node('button', text, { type: 'button', ...attributes });
  if (action) result.addEventListener('click', action);
  return result;
}
function cancelRequests() { for (const controller of pending) controller.abort(); pending.clear(); }
function changeView() { viewVersion++; cancelRequests(); app.replaceChildren(); }
function feedback(parent) {
  const status = node('p', '', { role: 'status', class: 'status', 'aria-live': 'polite' });
  const error = node('div', '', { role: 'alert', class: 'alert', tabindex: '-1' });
  append(parent, status, error);
  return { status, error, clear() { status.textContent = ''; error.textContent = ''; }, fail(e, focus = false) {
    status.textContent = ''; error.textContent = e.message || 'Không thể kết nối. Vui lòng thử lại.';
    if (focus) error.focus();
  } };
}
function field(form, label, name, type = 'text', attributes = {}) {
  const wrapper = node('div', null, { class: 'field' });
  const id = `field-${name}`;
  const input = node(type === 'textarea' ? 'textarea' : 'input', null, { id, name, ...(type === 'textarea' ? {} : { type }), ...attributes });
  append(wrapper, node('label', label, { for: id }), input); form.append(wrapper); return input;
}
function roleField(form, label, name) {
  const wrapper = node('div', null, { class: 'field' });
  const input = node('select', null, { id: `field-${name}`, name });
  for (const [value, text] of Object.entries(roles)) input.append(node('option', text, { value }));
  append(wrapper, node('label', label, { for: input.id }), input); form.append(wrapper); return input;
}
const date = value => value == null ? 'Chưa có dữ liệu' : new Date(value).toLocaleString('vi-VN');
const amount = value => new Intl.NumberFormat('vi-VN').format(value);

// A channel fences success, errors and finally callbacks from older requests.
function channel() {
  let sequence = 0;
  let previous;
  return () => {
    previous?.abort();
    const controller = new AbortController(); previous = controller; pending.add(controller);
    const s = sessionVersion, v = viewVersion, n = ++sequence;
    const current = () => !controller.signal.aborted && s === sessionVersion && v === viewVersion && n === sequence;
    return { controller, current, sessionVersion: s };
  };
}
async function api(path, task, payload, csrf = session?.csrfToken) {
  let authRequest = false;
  try {
    let controller;
    try {
      const network = await import('./admin-network.js');
      controller = await network.ensureAdminNetwork();
    } catch {
      if (task.current()) showNetworkBlocked();
      throw aborted();
    }
    if (!task.current() || (navigator.serviceWorker?.controller ?? null) !== controller) throw aborted();
    authRequest = path === '/login' || path === '/logout';
    if (authRequest) authRequests++;
    const response = await fetch('/admin/api' + path, {
      credentials: 'same-origin', cache: 'no-store', signal: task.controller.signal,
      ...(payload === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-Admin-CSRF': csrf } : {}) }, body: JSON.stringify(payload) }),
    });
    if (!task.current()) throw aborted();
    if ((navigator.serviceWorker?.controller ?? null) !== controller) throw new Error('Kết nối đã thay đổi. Kết quả chưa được xác nhận; vui lòng thử lại.');
    const body = await response.json();
    if (!task.current()) throw aborted();
    if ((navigator.serviceWorker?.controller ?? null) !== controller) throw new Error('Kết nối đã thay đổi. Kết quả chưa được xác nhận; vui lòng thử lại.');
    if (response.status === 401 && session) { showLogin('Phiên quản trị đã hết hạn. Vui lòng đăng nhập lại.'); throw aborted(); }
    if (response.status === 403 && session && path !== '/session') { restore(); throw aborted(); }
    if (!response.ok) throw new Error(typeof body.message === 'string' ? body.message : 'Yêu cầu không thành công. Vui lòng thử lại.');
    return body;
  } catch (error) {
    if (!task.current()) throw aborted();
    if (error.name === 'AbortError') throw error;
    if (error instanceof TypeError || error instanceof SyntaxError) throw new Error('Không thể kết nối. Vui lòng thử lại.');
    throw error;
  } finally { if (authRequest) authRequests--; pending.delete(task.controller); }
}
function showNetworkBlocked() {
  sessionVersion++; session = null; changeView();
  const main = node('main', null, { id: 'main', class: 'panel login stack' });
  append(main, node('h1', 'Cần cập nhật trang quản trị'), node('p', 'Chưa thể kết nối an toàn. Kiểm tra mạng rồi thử lại; nếu vẫn lỗi, đóng các thẻ của trang này và mở lại.', { role: 'alert' }), button('Thử lại', restore));
  app.append(main);
}
function shell(title, section) {
  changeView();
  const header = node('header', null, { class: 'header' });
  append(header, node('div', 'FastToWin · Quản trị', { class: 'brand' }));
  const identity = node('div', null, { class: 'identity' });
  append(identity, node('span', session.admin.email), node('span', roles[session.admin.role], { class: 'role' }), button('Đăng xuất', logout));
  header.append(identity);
  const layout = node('div', null, { class: 'layout' });
  const nav = node('nav', null, { 'aria-label': 'Quản trị' });
  const links = [['users', 'Người chơi', directory]];
  if (session.admin.role === 'OWNER') links.push(['accounts', 'Tài khoản quản trị', accounts], ['audit', 'Nhật ký kiểm toán', audit]);
  for (const [key, label, action] of links) nav.append(button(label, action, key === section ? { 'aria-current': 'page' } : {}));
  const main = node('main', null, { id: 'main', class: 'panel stack', tabindex: '-1' });
  append(main, node('p', 'Bảng điều khiển / ' + title, { class: 'eyebrow' }), node('h1', title));
  append(layout, nav, main); append(app, header, layout);
  return main;
}
function showLogin(message = '', blocked = false) {
  sessionVersion++; session = null; changeView();
  const header = node('header', null, { class: 'header' }); header.append(node('div', 'FastToWin · Quản trị', { class: 'brand' }));
  const main = node('main', null, { id: 'main', class: 'panel login stack' });
  append(main, node('p', 'Quyền truy cập riêng', { class: 'eyebrow' }), node('h1', 'Đăng nhập quản trị'), node('p', 'Sử dụng tài khoản quản trị được Owner cấp.', { class: 'muted' }));
  const form = node('form', null, { class: 'stack' });
  const email = field(form, 'Email quản trị', 'email', 'email', { autocomplete: 'username', required: '', maxlength: '254' });
  const password = field(form, 'Mật khẩu', 'password', 'password', { autocomplete: 'current-password', required: '', maxlength: '128' });
  const notices = feedback(form); if (message) notices.error.textContent = message;
  const submit = node('button', 'Đăng nhập', { type: 'submit', class: 'primary' }); submit.disabled = blocked; form.append(submit);
  const request = channel(); let busy = false;
  form.addEventListener('submit', async event => {
    event.preventDefault(); if (busy || blocked) return;
    busy = true; submit.disabled = true; submit.textContent = 'Đang đăng nhập…'; notices.clear();
    const task = request();
    try {
      const result = await api('/login', task, { email: email.value.trim(), password: password.value }, null);
      password.value = ''; sessionVersion++; session = result; loggedOut = false; directory();
    } catch (error) { if (task.current() && error.name !== 'AbortError') { password.value = ''; notices.fail(error, true); } }
    finally { if (task.current()) { busy = false; submit.disabled = false; submit.textContent = 'Đăng nhập'; } }
  });
  main.append(form); append(app, header, main);
}
async function restore() {
  if (loggedOut) { showLogin(); return; }
  sessionVersion++; session = null; changeView();
  const main = node('main', null, { id: 'main', class: 'panel login' }); main.append(node('p', 'Đang kiểm tra phiên quản trị…', { role: 'status' })); app.append(main);
  const task = channel()();
  try { session = await api('/session', task); sessionVersion++; directory(); }
  catch (error) { if (task.current() && error.name !== 'AbortError') showLogin(error.message); }
}
async function logout() {
  const csrf = session?.csrfToken;
  loggedOut = true;
  // Hide sensitive data immediately; retain only the in-flight logout header.
  showLogin('Đang đăng xuất…', true);
  const task = channel()();
  try { await api('/logout', task, {}, csrf); if (task.current()) showLogin('Đã đăng xuất.'); }
  catch (error) { if (task.current() && error.name !== 'AbortError') showLogin('Dữ liệu đã được ẩn. Đăng xuất chưa được xác nhận; vui lòng thử lại bằng cách đăng nhập.'); }
}
function pagination(parent, data, load) {
  const pager = node('div', null, { class: 'pagination', 'aria-label': 'Phân trang' });
  const before = button('Trang trước', () => load(data.page - 1)); before.disabled = data.page === 0;
  const after = button('Trang sau', () => load(data.page + 1)); after.disabled = (data.page + 1) * data.pageSize >= data.total;
  append(pager, before, node('span', `Trang ${data.page + 1} · ${amount(data.total)} mục`), after); parent.append(pager);
}
function directory() {
  if (!session) return;
  const main = shell('Người chơi', 'users');
  const support = session.admin.role === 'SUPPORT';
  const help = node('p', support ? 'Support chỉ tìm bằng ID hoặc tên. Email được che bởi máy chủ.' : 'Tra cứu ID, tên hoặc email. Dữ liệu người chơi chỉ để đọc.', { class: 'muted', id: 'search-help' }); main.append(help);
  const form = node('form', null, { class: 'search' });
  const query = field(form, 'Tìm người chơi', 'query', 'search', { maxlength: '128', 'aria-describedby': 'search-help', autocomplete: 'off' });
  const search = node('button', 'Tìm kiếm', { type: 'submit', class: 'primary' }); form.append(search); main.append(form);
  const notices = feedback(main), results = node('div', null, { class: 'results' }); main.append(results);
  const request = channel(); let currentQuery = '';
  async function load(page = 0) {
    const task = request(); results.replaceChildren(); notices.clear();
    if (support && currentQuery.includes('@')) { notices.fail(new Error('Support chỉ tìm bằng ID hoặc tên.'), true); return; }
    notices.status.textContent = 'Đang tải người chơi…';
    try {
      const data = await api(`/users?q=${encodeURIComponent(currentQuery)}&page=${page}&pageSize=25`, task);
      notices.status.textContent = `${amount(data.total)} người chơi phù hợp.`;
      if (!data.items.length) results.append(node('p', 'Không có người chơi phù hợp.', { class: 'muted' }));
      for (const user of data.items) {
        const row = node('article', null, { class: 'row' }), text = node('div');
        append(text, node('strong', user.displayName), node('p', user.email ?? 'Không có email'), node('p', user.id, { class: 'mono muted' }), node('span', label(user.accountType), { class: 'badge' }), node('span', label(user.status), { class: 'badge' }), node('p', 'Ngày tạo: ' + date(user.createdAtEpochMillis), { class: 'muted' }));
        append(row, text, button('Xem người chơi', () => detail(user.id))); results.append(row);
      }
      pagination(results, data, load);
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  form.addEventListener('submit', event => { event.preventDefault(); currentQuery = query.value.trim(); load(); });
  main.append(button('Làm mới', () => load())); load();
}
function detail(id) {
  if (!session) return;
  const main = shell('Hồ sơ người chơi', 'users');
  const toolbar = node('div', null, { class: 'toolbar' });
  append(toolbar, button('Quay lại danh sách', directory), button('Làm mới', load)); main.append(toolbar);
  const notices = feedback(main), content = node('div', null, { class: 'stack' }); main.append(content);
  const request = channel();
  async function load() {
    const task = request(); content.replaceChildren(); main.querySelector('h1').textContent = 'Hồ sơ người chơi'; notices.clear(); notices.status.textContent = 'Đang tải hồ sơ…';
    try {
      const user = await api('/users/' + encodeURIComponent(id), task);
      notices.status.textContent = ''; main.querySelector('h1').textContent = user.displayName;
      append(content, node('p', user.email ?? 'Không có email'), node('p', user.id, { class: 'mono muted' }));
      const metrics = node('dl', null, { class: 'metrics' });
      for (const [label, value] of [['Cấp độ', user.level], ['XP', user.xp], ['Vàng', user.gold], ['Gem', user.gems], ['Elo', user.elo]]) append(metrics, append(node('div', null, { class: 'metric' }), node('dt', label), node('dd', amount(value))));
      const facts = node('dl', null, { class: 'facts' });
      for (const [name, value] of [['Loại tài khoản', label(user.accountType)], ['Trạng thái', label(user.status)], ['Ngày tạo', date(user.createdAtEpochMillis)], ['Hoạt động gần nhất', date(user.lastActivityAtEpochMillis)], ['Bang', user.clanName ?? 'Không có bang'], ['ID bang', user.clanId ?? '—']]) append(facts, append(node('div'), node('dt', name), node('dd', value)));
      append(content, metrics, facts); histories(content, id);
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  load();
}
function histories(parent, id) {
  const section = node('section', null, { class: 'stack', 'aria-label': 'Lịch sử người chơi' });
  section.append(node('h2', 'Lịch sử người chơi'));
  const tabs = node('div', null, { class: 'tabs', 'aria-label': 'Chọn lịch sử' }); section.append(tabs);
  const notices = feedback(section), results = node('div', null, { class: 'results' }); section.append(results); parent.append(section);
  const request = channel(), pages = { matches: 0, wallet: 0, gifts: 0 };
  let selected = 'matches'; const controls = {};
  for (const [key, label] of [['matches', 'Trận đấu'], ['wallet', 'Ví'], ['gifts', 'Nguồn quà']]) {
    controls[key] = button(label, () => { selected = key; load(pages[key]); }, { 'aria-pressed': String(key === selected) }); tabs.append(controls[key]);
  }
  async function load(page) {
    pages[selected] = page; const type = selected, task = request();
    for (const [key, control] of Object.entries(controls)) control.setAttribute('aria-pressed', String(key === type));
    results.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải lịch sử…';
    try {
      const data = await api(`/users/${encodeURIComponent(id)}/${type}?page=${page}&pageSize=25`, task);
      notices.status.textContent = `${amount(data.total)} bản ghi.`;
      if (!data.items.length) results.append(node('p', 'Chưa có lịch sử.', { class: 'muted' }));
      for (const entry of data.items) {
        const row = node('article', null, { class: 'history-row' });
        if (type === 'matches') append(row, node('strong', label(entry.matchType)), node('p', `${label(entry.outcome)} · Điểm: ${amount(entry.score)}`), node('p', date(entry.finishedAtEpochMillis), { class: 'muted' }));
        else append(row, node('strong', label(entry.sourceType)), node('p', 'Nguồn: ' + entry.sourceId, { class: 'mono' }), node('p', `Vàng: ${amount(entry.goldDelta)} · Gem: ${amount(entry.gemsDelta)} · XP: ${amount(entry.xpDelta)}`), node('p', date(entry.createdAtEpochMillis), { class: 'muted' }));
        row.append(node('p', entry.id, { class: 'mono muted' })); results.append(row);
      }
      pagination(results, data, load);
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  load(0);
}
function mutationForm(form, submit, notices, path, payload, success, pendingChange = () => {}) {
  const request = channel(); let busy = false, previousPayload = null, requestId = null;
  // Editing any field ends the old retry; a failed identical submission retains its ID.
  form.addEventListener('input', () => { previousPayload = null; requestId = null; });
  form.addEventListener('submit', async event => {
    event.preventDefault(); if (busy) return;
    const body = payload(); const signature = JSON.stringify(body);
    if (signature !== previousPayload) { requestId = crypto.randomUUID(); previousPayload = signature; }
    busy = true; submit.disabled = true; pendingChange(true); const label = submit.textContent; submit.textContent = 'Đang lưu…'; notices.clear();
    const task = request();
    try {
      const result = await api(path, task, { ...body, requestId });
      previousPayload = null; requestId = null;
      success(result);
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error, true); }
    finally { if (task.current()) { busy = false; submit.disabled = false; submit.textContent = label; pendingChange(false); } }
  });
}
function accounts() {
  if (session?.admin.role !== 'OWNER') return;
  const main = shell('Tài khoản quản trị', 'accounts');
  main.append(node('p', 'Owner quản lý quyền truy cập. Mỗi thay đổi phải có lý do và sẽ thu hồi phiên của tài khoản đích.', { class: 'muted' }));
  const notices = feedback(main), results = node('div', null, { class: 'results' }); main.append(results);
  const forms = node('div', null, { class: 'forms' }); main.append(forms);
  const create = node('form', null, { class: 'stack' }); create.append(node('h2', 'Cấp tài khoản mới'));
  const email = field(create, 'Email quản trị mới', 'new-email', 'email', { required: '', maxlength: '254', autocomplete: 'off' });
  const password = field(create, 'Mật khẩu tạm', 'new-password', 'password', { required: '', minlength: '12', maxlength: '128', autocomplete: 'new-password' });
  create.append(node('p', 'Mật khẩu từ 12 đến 128 ký tự.', { class: 'muted' }));
  const role = roleField(create, 'Vai trò mới', 'new-role'); role.value = 'SUPPORT';
  role.querySelector('option[value="SUPPORT"]').defaultSelected = true;
  const reason = field(create, 'Lý do tạo', 'create-reason', 'textarea', { required: '', maxlength: '500' });
  const createNotices = feedback(create), submit = node('button', 'Tạo tài khoản', { type: 'submit', class: 'primary' }); create.append(submit); forms.append(create);
  const editor = node('section', null, { class: 'stack', 'aria-label': 'Chỉnh quyền' }); forms.append(editor);
  mutationForm(create, submit, createNotices, '/accounts', () => ({ email: email.value.trim(), password: password.value, role: role.value, reason: reason.value.trim() }), () => {
    password.value = ''; create.reset(); createNotices.status.textContent = 'Đã tạo tài khoản quản trị.'; load(currentPage);
  });
  const request = channel(); let currentPage = 0, accessPending = false, cancelEdit;
  const editControls = [];
  const refresh = button('Làm mới', () => { if (!accessPending) { editor.replaceChildren(); load(currentPage); } });
  function setAccessPending(value) {
    accessPending = value;
    for (const control of editControls) control.disabled = value;
    cancelEdit.disabled = value; refresh.disabled = value;
  }
  async function load(page = 0) {
    currentPage = page; const task = request(); results.replaceChildren(); editControls.length = 0; notices.clear(); notices.status.textContent = 'Đang tải tài khoản quản trị…';
    try {
      const data = await api(`/accounts?page=${page}&pageSize=25`, task); notices.status.textContent = `${amount(data.total)} tài khoản quản trị.`;
      if (!data.items.length) results.append(node('p', 'Chưa có tài khoản quản trị.'));
      for (const account of data.items) {
        const row = node('article', null, { class: 'row' });
        const editControl = button('Sửa quyền ' + account.email, () => edit(account)); editControl.disabled = accessPending; editControls.push(editControl);
        append(row, append(node('div'), node('strong', account.email), node('p', `${roles[account.role]} · ${account.active ? 'Đang hoạt động' : 'Đã thu hồi'}`), node('p', account.id, { class: 'mono muted' })), editControl);
        results.append(row);
      }
      pagination(results, data, load);
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  function edit(account) {
    if (accessPending) return;
    editor.replaceChildren();
    const form = node('form', null, { class: 'stack' }); append(form, node('h2', 'Quyền truy cập'), node('p', account.email));
    const role = roleField(form, 'Vai trò truy cập', 'access-role'); role.value = account.role;
    const active = field(form, 'Cho phép truy cập', 'access-active', 'checkbox'); active.checked = account.active; active.parentElement.className = 'checkbox';
    const reason = field(form, 'Lý do thay đổi', 'access-reason', 'textarea', { required: '', maxlength: '500' });
    const notices = feedback(form), submit = node('button', 'Lưu quyền truy cập', { type: 'submit', class: 'primary' });
    cancelEdit = button('Hủy', () => { if (!accessPending) editor.replaceChildren(); });
    append(form, submit, cancelEdit); editor.append(form); role.focus();
    mutationForm(form, submit, notices, '/accounts/' + encodeURIComponent(account.id) + '/access', () => ({ role: role.value, active: active.checked, reason: reason.value.trim() }), () => {
      if (account.id === session.admin.id) { loggedOut = true; showLogin('Quyền của bạn đã thay đổi. Vui lòng đăng nhập lại.'); }
      else { editor.replaceChildren(); load(currentPage); }
    }, setAccessPending);
  }
  main.append(refresh); load();
}
function audit() {
  if (session?.admin.role !== 'OWNER') return;
  const main = shell('Nhật ký kiểm toán', 'audit');
  main.append(node('p', 'Lịch sử truy cập và thay đổi quyền quản trị được lưu bởi máy chủ.', { class: 'muted' }));
  const notices = feedback(main), results = node('div', null, { class: 'results' }); main.append(results);
  const request = channel(); let currentPage = 0;
  async function load(page = 0) {
    currentPage = page; const task = request(); results.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải nhật ký…';
    try {
      const data = await api(`/audit?page=${page}&pageSize=25`, task); notices.status.textContent = `${amount(data.total)} bản ghi kiểm toán.`;
      if (!data.items.length) results.append(node('p', 'Chưa có nhật ký kiểm toán.'));
      for (const entry of data.items) {
        const row = node('article', null, { class: 'history-row' });
        append(row, node('strong', label(entry.action)), node('p', date(entry.createdAtEpochMillis), { class: 'muted' }));
        for (const [label, value] of [['Người thực hiện', entry.actorId], ['Đối tượng', entry.targetId], ['Mã yêu cầu', entry.requestId], ['Lý do', entry.reason], ['Trước', entry.beforeSummary], ['Sau', entry.afterSummary]]) row.append(node('p', `${label}: ${value ?? '—'}`));
        row.append(node('p', entry.id, { class: 'mono muted' })); results.append(row);
      }
      pagination(results, data, load);
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  main.append(button('Làm mới', () => load(currentPage))); load();
}

// BFCache snapshots contain only the cleared view. Revalidate when returning.
window.addEventListener('pagehide', () => { sessionVersion++; session = null; changeView(); });
window.addEventListener('pageshow', event => { if (event.persisted) restore(); });
window.addEventListener('popstate', () => { if (loggedOut) showLogin(); else restore(); });
navigator.serviceWorker?.addEventListener('controllerchange', () => {
  // Invalidate rendered data and callbacks. Abort cannot undo a server mutation.
  // Login/logout already show no private view. Wait for their cookie-changing
  // response before enabling another sign-in; api rejects the changed controller.
  if (authRequests) return;
  restore();
});
restore();
