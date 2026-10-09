'use strict';

// All credentials/CSRF and retry state live only in this document's memory.
const app = document.getElementById('app');
const roles = { OWNER: 'Owner', OPERATOR: 'Operator', SUPPORT: 'Support' };
const labels = {
  ANALYTICS_READ: 'Xem thống kê',
  REGISTERED: 'Đã đăng ký', GUEST: 'Khách', ACTIVE: 'Đang hoạt động', DELETED: 'Đã xóa', BANNED: 'Đã khóa',
  CASUAL: 'Đấu thường', RANKED: 'Đấu hạng', WIN: 'Thắng', LOSS: 'Thua', DRAW: 'Hòa',
  MATCH: 'Thưởng trận đấu', MATCH_REWARD: 'Thưởng trận đấu', DAILY_CHECK_IN: 'Điểm danh', MISSION: 'Nhiệm vụ',
  SEASON_REWARD: 'Thưởng mùa giải', CLAN_QUEST: 'Nhiệm vụ bang', CLAN_DONATION: 'Đóng góp bang',
  GOLD_EXCHANGE: 'Đổi Vàng', COSMETIC_PURCHASE: 'Mua vật phẩm', STORE_PURCHASE: 'Mua trong cửa hàng',
  TOURNAMENT_ENTRY: 'Phí tham gia giải', TOURNAMENT_ENTRY_REFUND: 'Hoàn phí giải', TOURNAMENT_ENTRY_REVERSED: 'Phí giải đã đảo',
  ADMIN_GIFT: 'Quà quản trị', COMPENSATION: 'Bồi hoàn',
  GIFT_LIST: 'Xem chiến dịch quà', GIFT_DETAIL: 'Xem báo cáo quà', GIFT_CREATE: 'Tạo bản nháp quà', GIFT_UPDATE: 'Sửa bản nháp quà',
  GIFT_PREVIEW: 'Xem trước quà', GIFT_PUBLISH: 'Công bố quà', GIFT_CANCEL: 'Hủy chiến dịch quà',
  EVENT_LIST: 'Xem sự kiện thưởng', EVENT_DETAIL: 'Xem chi tiết sự kiện', EVENT_CREATE: 'Tạo bản nháp sự kiện', EVENT_UPDATE: 'Sửa bản nháp sự kiện', EVENT_PREVIEW: 'Xem trước sự kiện', EVENT_SCHEDULE: 'Lên lịch sự kiện', EVENT_CANCEL: 'Hủy sự kiện',
  MAINTENANCE_READ: 'Xem trạng thái bảo trì', MAINTENANCE_UPDATE: 'Cập nhật bảo trì', MAINTENANCE_TRANSITION: 'Chuyển giai đoạn bảo trì',
  TOURNAMENT_MAINTENANCE_REFUND: 'Hoàn phí giải do bảo trì',
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
// Decorative vectors use fixed paths; all server-provided content stays text-only.
function icon(name) {
  const paths = {
    analytics: 'M4 3v18h17M8 16l4-6 4 3 5-8',
    bolt: 'M13 2 4 14h7l-1 8 10-13h-7l1-7Z',
    users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
    accounts: 'M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11ZM9 12l2 2 4-4',
    audit: 'M9 3H5v18h14V3h-4M9 2h6v4H9ZM8 11h8M8 15h8',
    arrow: 'M5 12h14M13 6l6 6-6 6',
    refresh: 'M20 11a8 8 0 0 0-14-5L3 9M3 3v6h6M4 13a8 8 0 0 0 14 5l3-3M15 15h6v6',
    logout: 'M9 3H4v18h5M9 12h12M17 8l4 4-4 4',
    lock: 'M7 10V7a5 5 0 0 1 10 0v3M5 10h14v12H5ZM12 15v3',
    close: 'M6 6l12 12M18 6 6 18',
    previous: 'M15 6l-6 6 6 6',
    next: 'M9 6l6 6-6 6',
    back: 'M19 12H5M11 6l-6 6 6 6',
    edit: 'm16 3 5 5M4 20l4-1L21 6a2 2 0 0 0-3-3L5 16l-1 4Z',
    eye: 'M2 12s3-7 10-7 10 7 10 7-3 7-10 7S2 12 2 12ZM15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
    search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
    add: 'M12 5v14M5 12h14',
    gifts: 'M3 8h18v4H3ZM5 12v9h14v-9M12 8v13M12 8H7a3 3 0 1 1 3-3l2 3ZM12 8h5a3 3 0 1 0-3-3l-2 3Z',
    maintenance: 'M4 4h16v6H4ZM4 14h16v6H4ZM7 7h1M7 17h1M12 7h5M12 17h5',
    events: 'M5 5h14v16H5ZM8 2v6M16 2v6M5 10h14M9 15h6M12 12v6',
    compensation: 'M12 3 3 7v6c0 5 9 9 9 9s9-4 9-9V7l-9-4ZM8 12l3 3 5-6',
    coin: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 7v10M15 9h-4a2 2 0 0 0 0 4h2a2 2 0 0 1 0 4H9',
    star: 'm12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9L12 3Z',
  };
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  for (const [key, value] of Object.entries({ viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.7', 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true', focusable: 'false', class: 'icon' })) svg.setAttribute(key, value);
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path'); path.setAttribute('d', paths[name]); svg.append(path);
  return svg;
}
function brand() {
  return append(node('div', null, { class: 'brand' }), append(node('span', null, { class: 'brand-mark' }), icon('bolt')), node('span', 'FastToWin'), node('span', 'Quản trị', { class: 'brand-label' }));
}
function avatar(text) {
  return node('span', Array.from(String(text).trim())[0]?.toLocaleUpperCase('vi-VN') || '?', { class: 'avatar', 'aria-hidden': 'true' });
}
function decoratedButton(text, action, name, attributes = {}) {
  const control = button(text, action, attributes); control.prepend(icon(name)); return control;
}
function iconButton(label, action, name, attributes = {}) {
  const { class: variant = '', ...rest } = attributes;
  const control = decoratedButton('', action, name, { class: ('icon-button ' + variant).trim(), 'aria-label': label, 'data-tooltip': label, ...rest });
  const tooltip = append(node('span', null, { class: 'tooltip', 'aria-hidden': 'true' }), node('span', control.getAttribute('data-tooltip'), { class: 'tooltip-label' }));
  tooltip.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); });
  control.append(tooltip);
  for (const event of ['pointerenter', 'focus']) control.addEventListener(event, () => control.removeAttribute('data-tooltip-dismissed'));
  return control;
}
document.addEventListener('keydown', event => {
  if (event.key === 'Escape') document.querySelectorAll('.icon-button[data-tooltip]').forEach(control => control.setAttribute('data-tooltip-dismissed', ''));
});
let modalSequence = 0;
function modal(title, restoreFocus = document.activeElement) {
  const headingId = 'modal-heading-' + ++modalSequence;
  const dialog = node('dialog', null, { class: 'modal', 'aria-labelledby': headingId });
  const opener = restoreFocus;
  let busy = false;
  const dismiss = () => { if (!busy) dialog.close(); };
  const close = iconButton('Đóng hộp thoại', dismiss, 'close');
  const cancel = button('Hủy', dismiss, { class: 'text-button' });
  const header = append(node('div', null, { class: 'modal-header' }), node('h2', title, { id: headingId }), close);
  dialog.append(header);
  dialog.addEventListener('cancel', event => { event.preventDefault(); dismiss(); });
  dialog.addEventListener('keydown', event => {
    if (event.key !== 'Tab') return;
    const controls = [...dialog.querySelectorAll('button, input, select, textarea, [tabindex="0"]')].filter(control => !control.disabled && control.getClientRects().length);
    const first = controls[0], last = controls.at(-1);
    if (!first) { event.preventDefault(); return; }
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  dialog.addEventListener('click', event => {
    if (event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dismiss();
  });
  dialog.addEventListener('close', () => {
    for (const field of dialog.querySelectorAll('input[type="password"]')) field.value = '';
    dialog.replaceChildren(); dialog.remove();
    if (opener?.isConnected && !document.querySelector('dialog[open]')) opener.focus();
  });
  return { dialog, cancel,
    show(firstField) { app.append(dialog); dialog.showModal(); firstField.focus(); },
    finish() { dialog.close(); },
    pending(value) {
      busy = value; close.disabled = value; cancel.disabled = value;
      dialog.setAttribute('aria-busy', String(value));
      for (const field of dialog.querySelectorAll('input, select, textarea')) field.disabled = value;
    },
  };
}
function cancelRequests() { for (const controller of pending) controller.abort(); pending.clear(); }
function changeView() {
  viewVersion++; cancelRequests();
  for (const dialog of app.querySelectorAll('dialog[open]')) dialog.close();
  app.replaceChildren();
}
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
  const wrapper = node(type === 'checkbox' ? 'label' : 'div', null, { class: type === 'checkbox' ? 'checkbox' : 'field' });
  const id = `field-${name}`;
  const input = node(type === 'textarea' ? 'textarea' : 'input', null, { id, name, ...(type === 'textarea' ? {} : { type }), ...attributes });
  append(wrapper, node(type === 'checkbox' ? 'span' : 'label', label, type === 'checkbox' ? {} : { for: id }), input); form.append(wrapper); return input;
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
const navigationRequest = channel();
function beginNavigation(title, section) {
  // Every destination's shell advances the shared view fence synchronously.
  // Keep navigation available, but remove the outgoing private view before import.
  const notices = feedback(shell(title, section));
  notices.status.textContent = 'Đang tải…';
  return { task: navigationRequest(), notices };
}
async function api(path, task, payload, csrf = session?.csrfToken, method = 'POST', liveness = false) {
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
    const response = await fetch(liveness ? '/health' : '/admin/api' + path, {
      credentials: 'same-origin', cache: 'no-store', signal: task.controller.signal,
      ...(payload === undefined ? {} : { method, headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-Admin-CSRF': csrf } : {}) }, body: JSON.stringify(payload) }),
    });
    if (!task.current()) throw aborted();
    if ((navigator.serviceWorker?.controller ?? null) !== controller) throw new Error('Kết nối đã thay đổi. Kết quả chưa được xác nhận; vui lòng thử lại.');
    const body = liveness ? await response.text() : await response.json();
    if (!task.current()) throw aborted();
    if ((navigator.serviceWorker?.controller ?? null) !== controller) throw new Error('Kết nối đã thay đổi. Kết quả chưa được xác nhận; vui lòng thử lại.');
    if (response.status === 401 && session) { showLogin('Phiên quản trị đã hết hạn. Vui lòng đăng nhập lại.'); throw aborted(); }
    if (response.status === 403 && session && path !== '/session') { restore(); throw aborted(); }
    if (!response.ok) { const error = new Error(typeof body.message === 'string' ? body.message : 'Yêu cầu không thành công. Vui lòng thử lại.'); error.status = response.status; error.code = body.code; throw error; }
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
  header.append(brand());
  const identity = node('div', null, { class: 'identity' });
  append(identity, avatar(session.admin.email), append(node('div', null, { class: 'identity-info' }), node('span', session.admin.email), node('span', roles[session.admin.role], { class: 'role' })), iconButton('Đăng xuất', logout, 'logout'));
  header.append(identity);
  const layout = node('div', null, { class: 'layout' });
  const nav = node('nav', null, { 'aria-label': 'Quản trị' });
  const links = [['users', 'Người chơi', directory]];
  if (session.admin.role !== 'SUPPORT') links.push(['analytics', 'Thống kê', analytics]);
  links.push(['maintenance', 'Bảo trì', maintenance]);
  if (session.admin.role !== 'SUPPORT') links.push(['gifts', 'Quà admin', giftCampaigns]);
  if (session.admin.role !== 'SUPPORT') links.push(['events', 'Sự kiện thưởng', rewardEvents]);
  links.push(['compensation', 'Đền bù', compensation]);
  if (session.admin.role === 'OWNER') links.push(['accounts', 'Tài khoản quản trị', accounts], ['audit', 'Nhật ký kiểm toán', audit]);
  for (const [key, label, action] of links) nav.append(decoratedButton(label, action, key, key === section ? { 'aria-current': 'page' } : {}));
  const main = node('main', null, { id: 'main', class: 'panel stack page-' + section, tabindex: '-1' });
  main.append(node('h1', title));
  append(layout, nav, main); append(app, header, layout);
  return main;
}
async function analytics() {
  if (!session || session.admin.role === 'SUPPORT') return;
  const { task, notices } = beginNavigation('Thống kê', 'analytics');
  try {
    const { openAnalytics } = await import('./admin-analytics.js');
    if (!task.current()) return;
    openAnalytics({ node, append, button, iconButton, field, modal, feedback, shell, channel,
      role: () => session?.admin.role,
      api: (path, request) => api(path, request),
    });
  } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(new Error('Không thể tải thống kê. Vui lòng thử lại.')); }
  finally { pending.delete(task.controller); }
}
async function maintenance() {
  if (!session) return;
  const { task, notices } = beginNavigation('Bảo trì', 'maintenance');
  try {
    const { openMaintenance } = await import('./admin-maintenance.js');
    if (!task.current()) return;
    openMaintenance({ node, append, button, iconButton, field, modal, feedback, shell, channel,
      role: () => session?.admin.role,
      api: (path, request, payload, method) => api(path, request, payload, session?.csrfToken, method),
      health: request => api('/health', request, undefined, undefined, 'GET', true),
    });
  } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(new Error('Không thể tải quản lý bảo trì. Vui lòng thử lại.')); }
  finally { pending.delete(task.controller); }
}
async function giftCampaigns() {
  if (!session || session.admin.role === 'SUPPORT') return;
  const { task, notices } = beginNavigation('Quà admin', 'gifts');
  try {
    const { openGiftCampaigns } = await import('./admin-gifts.js');
    if (!task.current()) return;
    openGiftCampaigns({ node, append, button, iconButton, field, modal, feedback, shell, channel, pagination, amount,
      role: () => session?.admin.role,
      api: (path, request, payload, method) => api(path, request, payload, session?.csrfToken, method),
    });
  } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(new Error('Không thể tải chiến dịch quà. Vui lòng thử lại.')); }
  finally { pending.delete(task.controller); }
}
async function rewardEvents() {
  if (!session || session.admin.role === 'SUPPORT') return;
  const { task, notices } = beginNavigation('Sự kiện thưởng', 'events');
  try {
    const { openRewardEvents } = await import('./admin-events.js');
    if (!task.current()) return;
    openRewardEvents({ node, append, button, icon, iconButton, field, modal, feedback, shell, channel, pagination, amount,
      role: () => session?.admin.role,
      api: (path, request, payload, method) => api(path, request, payload, session?.csrfToken, method),
    });
  } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(new Error('Không thể tải sự kiện thưởng. Vui lòng thử lại.')); }
  finally { pending.delete(task.controller); }
}
async function compensation() {
  if (!session) return;
  const { task, notices } = beginNavigation('Đền bù', 'compensation');
  try {
    const { openCompensation } = await import('./admin-compensation.js');
    if (!task.current() || !session) return;
    openCompensation({ node, append, button, icon, iconButton, field, modal, feedback, shell, channel,
      role: () => session?.admin.role,
      api: (path, request, payload, method) => api(path, request, payload, session?.csrfToken, method),
    });
  } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(new Error('Không thể tải đền bù. Vui lòng thử lại.')); }
  finally { pending.delete(task.controller); }
}
function showLogin(message = '', blocked = false) {
  sessionVersion++; session = null; changeView();
  const header = node('header', null, { class: 'header' }); header.append(brand());
  const main = node('main', null, { id: 'main', class: 'panel login stack' });
  append(main, append(node('span', null, { class: 'login-mark' }), icon('lock')), node('p', 'Không gian quản trị', { class: 'eyebrow' }), node('h1', 'Đăng nhập quản trị'), node('p', 'Sử dụng tài khoản quản trị được Owner cấp.', { class: 'muted' }));
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
  const pager = node('div', null, { class: 'pagination', role: 'group', 'aria-label': 'Phân trang' });
  const before = iconButton('Trang trước', () => load(data.page - 1), 'previous'); before.disabled = data.page === 0;
  const after = iconButton('Trang sau', () => load(data.page + 1), 'next'); after.disabled = (data.page + 1) * data.pageSize >= data.total;
  append(pager, node('span', `Trang ${data.page + 1} · ${amount(data.total)} mục`), before, after); parent.append(pager);
}
function directory() {
  if (!session) return;
  const main = shell('Người chơi', 'users');
  const support = session.admin.role === 'SUPPORT';
  const help = node('p', support ? 'Support chỉ tìm bằng ID hoặc tên. Email được che bởi máy chủ.' : 'Tra cứu ID, tên hoặc email. Dữ liệu người chơi chỉ để đọc.', { class: 'muted', id: 'search-help' }); main.append(help);
  const form = node('form', null, { class: 'search' });
  const query = field(form, 'Tìm người chơi', 'query', 'search', { maxlength: '128', 'aria-describedby': 'search-help', autocomplete: 'off' });
  query.setAttribute('placeholder', support ? 'Nhập ID hoặc tên người chơi…' : 'Nhập ID, tên hoặc email…');
  const search = iconButton('Tìm kiếm', null, 'search', { type: 'submit', class: 'primary' }); form.append(search); main.append(form);
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
        const row = node('article', null, { class: 'row' }), text = node('div', null, { class: 'row-info' });
        const metadata = node('div', null, { class: 'row-meta' });
        append(text, node('strong', user.displayName), node('p', user.email ?? 'Không có email', { class: 'muted' }));
        append(metadata, node('span', label(user.accountType), { class: 'badge' }), node('span', label(user.status), { class: 'badge badge-' + (user.status === 'ACTIVE' ? 'active' : 'inactive') }));
        append(row, avatar(user.displayName), text, metadata, iconButton('Xem người chơi', () => detail(user.id), 'eye')); results.append(row);
      }
      pagination(results, data, load);
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  form.addEventListener('submit', event => { event.preventDefault(); currentQuery = query.value.trim(); load(); });
  main.append(iconButton('Làm mới', () => load(), 'refresh')); load();
}
function detail(id) {
  if (!session) return;
  const main = shell('Hồ sơ người chơi', 'users');
  const toolbar = node('div', null, { class: 'toolbar' });
  append(toolbar, iconButton('Quay lại danh sách', directory, 'back'), iconButton('Làm mới', load, 'refresh')); main.append(toolbar);
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
  main.append(node('p', 'Cấp tài khoản và quản lý quyền truy cập.', { class: 'muted' }));
  const toolbar = node('div', null, { class: 'toolbar' }); main.append(toolbar);
  const createControl = iconButton('Cấp tài khoản', create, 'add', { class: 'primary' });
  const refresh = iconButton('Làm mới', () => { if (!mutationPending) load(currentPage); }, 'refresh');
  append(toolbar, createControl, refresh);
  const notices = feedback(main), confirmation = node('p', '', { role: 'status', 'aria-live': 'polite', class: 'confirmation' });
  main.append(confirmation);
  const results = node('div', null, { class: 'results' }); main.append(results);
  const request = channel(); let currentPage = 0, mutationPending = false;
  const editControls = [];
  function setMutationPending(value, surface) {
    mutationPending = value; surface.pending(value);
    for (const control of editControls) control.disabled = value;
    createControl.disabled = value; refresh.disabled = value;
  }
  function create() {
    if (mutationPending) return;
    confirmation.textContent = '';
    const surface = modal('Cấp tài khoản mới');
    const form = node('form', null, { class: 'stack modal-form' }); surface.dialog.append(form);
    const email = field(form, 'Email quản trị mới', 'new-email', 'email', { required: '', maxlength: '254', autocomplete: 'off' });
    const password = field(form, 'Mật khẩu tạm', 'new-password', 'password', { required: '', minlength: '12', maxlength: '128', autocomplete: 'new-password', 'aria-describedby': 'new-password-help' });
    form.append(node('p', 'Mật khẩu từ 12 đến 128 ký tự.', { class: 'muted field-help', id: 'new-password-help' }));
    const role = roleField(form, 'Vai trò mới', 'new-role'); role.value = 'SUPPORT';
    const reason = field(form, 'Lý do tạo', 'create-reason', 'textarea', { required: '', maxlength: '500' });
    const notices = feedback(form), submit = node('button', 'Tạo tài khoản', { type: 'submit', class: 'primary' });
    form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutationForm(form, submit, notices, '/accounts', () => ({ email: email.value.trim(), password: password.value, role: role.value, reason: reason.value.trim() }), () => {
      password.value = ''; surface.finish(); confirmation.textContent = 'Đã tạo tài khoản quản trị.'; load(currentPage);
    }, value => setMutationPending(value, surface));
    surface.show(email);
  }
  async function load(page = 0) {
    currentPage = page; const task = request(); results.replaceChildren(); editControls.length = 0; notices.clear(); notices.status.textContent = 'Đang tải tài khoản quản trị…';
    try {
      const data = await api(`/accounts?page=${page}&pageSize=25`, task); notices.status.textContent = `${amount(data.total)} tài khoản quản trị.`;
      if (!data.items.length) results.append(node('p', 'Chưa có tài khoản quản trị.'));
      for (const account of data.items) {
        const row = node('article', null, { class: 'row account-row' });
        const editControl = iconButton('Sửa quyền ' + account.email, () => edit(account), 'edit', { 'data-tooltip': 'Sửa quyền' }); editControl.disabled = mutationPending; editControls.push(editControl);
        append(row, avatar(account.email), append(node('div', null, { class: 'row-info' }), node('strong', account.email)), append(node('div', null, { class: 'row-meta' }), node('span', roles[account.role], { class: 'badge' }), node('span', account.active ? 'Đang hoạt động' : 'Đã thu hồi', { class: 'badge badge-' + (account.active ? 'active' : 'inactive') })), editControl);
        results.append(row);
      }
      pagination(results, data, load);
    } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
  }
  function edit(account) {
    if (mutationPending) return;
    confirmation.textContent = '';
    const surface = modal('Quyền truy cập');
    const form = node('form', null, { class: 'stack modal-form' }); surface.dialog.append(form);
    append(form, node('p', account.email), node('p', 'Thay đổi quyền sẽ đăng xuất mọi phiên của tài khoản này.', { class: 'muted' }));
    const role = roleField(form, 'Vai trò truy cập', 'access-role'); role.value = account.role;
    const active = field(form, 'Cho phép truy cập', 'access-active', 'checkbox'); active.checked = account.active; active.parentElement.className = 'checkbox';
    const reason = field(form, 'Lý do thay đổi', 'access-reason', 'textarea', { required: '', maxlength: '500' });
    const notices = feedback(form), submit = node('button', 'Lưu quyền truy cập', { type: 'submit', class: 'primary' });
    form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutationForm(form, submit, notices, '/accounts/' + encodeURIComponent(account.id) + '/access', () => ({ role: role.value, active: active.checked, reason: reason.value.trim() }), () => {
      if (account.id === session.admin.id) { loggedOut = true; showLogin('Quyền của bạn đã thay đổi. Vui lòng đăng nhập lại.'); }
      else { surface.finish(); confirmation.textContent = 'Đã cập nhật quyền truy cập.'; load(currentPage); }
    }, value => setMutationPending(value, surface));
    surface.show(role);
  }
  load();
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
  main.append(iconButton('Làm mới', () => load(currentPage), 'refresh')); load();
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
