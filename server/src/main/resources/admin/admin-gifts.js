'use strict';

const states = { DRAFT: 'Bản nháp', PUBLISHED: 'Đã công bố', CANCELLED: 'Đã hủy' };
const audiences = { ALL_REGISTERED: 'Tất cả tài khoản đã đăng ký', SINGLE_PLAYER: 'Một người chơi', USER_IDS: 'Danh sách UUID (nâng cao)', REGISTERED_BETWEEN: 'Đăng ký trong khoảng thời gian' };
const DAY = 86400000, BANGKOK = 7 * 3600000;
const localDate = value => new Date(value + BANGKOK).toISOString().slice(0, 23).replace(/:00\.000$/, '').replace(/\.000$/, '');
const epoch = value => Date.parse(value + '+07:00');
const date = value => value == null ? 'Chưa có dữ liệu' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Bangkok', dateStyle: 'short', timeStyle: 'short' }).format(value) + ' · Giờ Việt Nam (UTC+7)';

// One explicit adapter owns auth, session fencing, network policy and shared UI.
// Campaign state exists only in this view's closures; view/session changes remove it.
export function openGiftCampaigns(ui) {
  const { node, append, button, iconButton, field, modal, feedback, shell, channel, api, role } = ui;
  const amount = value => new Intl.NumberFormat('vi-VN').format(BigInt(value));
  function pagination(parent, data, load) {
    const pager = node('div', null, { class: 'pagination', role: 'group', 'aria-label': 'Phân trang' });
    const before = iconButton('Trang trước', () => load(data.page - 1), 'previous'); before.disabled = data.page === 0;
    const after = iconButton('Trang sau', () => load(data.page + 1), 'next');
    after.disabled = (BigInt(data.page) + 1n) * BigInt(data.pageSize) >= BigInt(data.total);
    append(pager, node('span', `Trang ${amount(data.page + 1)} · ${amount(data.total)} mục`), before, after);
    parent.append(pager);
  }
  const base = '/gift-campaigns';
  const path = id => base + '/' + encodeURIComponent(id);
  const owner = () => role() === 'OWNER';
  function giftModal(title, opener) { const surface = modal(title, opener); surface.dialog.classList.add('gift-modal'); return surface; }
  function select(parent, label, name, options) {
    const wrapper = node('div', null, { class: 'field' }), control = node('select', null, { id: 'field-' + name, name });
    for (const [value, text] of Object.entries(options)) control.append(node('option', text, { value }));
    append(wrapper, node('label', label, { for: control.id }), control); parent.append(wrapper); return control;
  }
  function facts(parent, entries, className = 'facts') {
    const list = node('dl', null, { class: className });
    if (entries.some(([, value]) => String(value).replace(/\D/g, '').length > 15)) list.classList.add('gift-large-values');
    for (const [title, value] of entries) append(list, append(node('div'), node('dt', title), node('dd', value)));
    parent.append(list);
  }
  function content(parent, draft) {
    const languages = node('div', null, { class: 'gift-languages' });
    for (const [lang, label, title, body] of [['vi', 'Tiếng Việt', draft.titleVi, draft.bodyVi], ['en', 'English', draft.titleEn, draft.bodyEn]]) {
      append(languages, append(node('section', null, { class: 'gift-message stack', lang, 'aria-label': label }), node('p', label, { class: 'muted' }), node('h3', title), node('p', body, { class: 'gift-body' })));
    } parent.append(languages);
  }
  function schedule(parent, draft) {
    facts(parent, [['Người nhận', audiences[isIndividual(draft) ? 'SINGLE_PLAYER' : draft.audience]], ['Phần thưởng mỗi người', `${amount(draft.gold)} Vàng · ${amount(draft.xp)} XP · ${amount(draft.gems ?? 0)} Gem`], ['Thời gian phát', date(draft.availableAtEpochMillis)], ['Hết hạn', date(draft.expiresAtEpochMillis)]]);
  }
  function isIndividual(draft) { return draft.audience === 'USER_IDS' && draft.userIds.length === 1; }
  function recipientFacts(parent, person, className = 'facts gift-recipient') {
    facts(parent, [['Người chơi', person.displayName], ['Mã người chơi', person.playerCode || '—'], ['Email', person.email ?? 'Không có email']], className);
  }
  function totals(parent, preview) {
    facts(parent, [['Người nhận', amount(preview.recipientCount)], ['Tổng Vàng', amount(preview.totalGold)], ['Tổng XP', amount(preview.totalXp)], ['Tổng Gem', amount(preview.totalGems ?? 0)]], 'gift-totals');
  }
  // Retry identity is bound to the exact payload, not a submit click.
  function mutation(form, submit, notices, surface, target, payload, success, method = 'POST', conflict) {
    const request = channel(); let busy = false, signature, requestId;
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (busy) return;
      let body;
      try { body = payload(); } catch (error) { notices.fail(error, true); return; }
      const next = JSON.stringify(body);
      if (next !== signature) { signature = next; requestId = crypto.randomUUID(); }
      busy = true; surface.pending(true); submit.disabled = true;
      const original = submit.textContent; submit.textContent = 'Đang xử lý…'; notices.clear(); const task = request();
      try { const result = await api(target, task, { ...body, requestId }, method); success(result); }
      catch (error) {
        if (!task.current() || error.name === 'AbortError') return;
        if (error.status === 409 && conflict) conflict(error);
        else notices.fail(error, true);
      } finally { if (task.current()) { busy = false; surface.pending(false); submit.disabled = false; submit.textContent = original; } }
    });
  }
  function list() {
    if (!role() || role() === 'SUPPORT') return;
    const main = shell('Quà admin', 'gifts');
    main.append(node('p', 'Soạn quà Vàng, XP và Gem, xem trước người nhận và theo dõi chiến dịch.', { class: 'muted' }));
    const toolbar = node('div', null, { class: 'toolbar' });
    append(toolbar, iconButton('Tạo chiến dịch quà', () => edit(), 'add', { class: 'primary' }), iconButton('Làm mới', () => load(currentPage), 'refresh')); main.append(toolbar);
    const notices = feedback(main), results = node('div', null, { class: 'results' }); main.append(results);
    const request = channel(); let currentPage = 0;
    async function load(page = 0) {
      currentPage = page; results.replaceChildren(); notices.clear(); notices.status.textContent = 'Đang tải chiến dịch…'; const task = request();
      try {
        const data = await api(`${base}?page=${page}&pageSize=25`, task); notices.status.textContent = `${amount(data.total)} chiến dịch.`;
        if (!data.items.length) results.append(node('p', 'Chưa có chiến dịch quà.', { class: 'muted' }));
        for (const { campaign, counters } of data.items) {
          const row = node('article', null, { class: 'gift-row' });
          append(row, append(node('div', null, { class: 'row-info' }), node('strong', campaign.draft.titleVi), node('p', `${amount(campaign.draft.gold)} Vàng · ${amount(campaign.draft.xp)} XP · ${amount(campaign.draft.gems ?? 0)} Gem / người`, { class: 'muted' }), node('p', date(campaign.draft.availableAtEpochMillis), { class: 'muted' })),
            append(node('div', null, { class: 'row-meta' }), node('span', states[campaign.state], { class: 'badge badge-' + (campaign.state === 'PUBLISHED' ? 'active' : campaign.state === 'CANCELLED' ? 'inactive' : 'draft') }), node('span', `Đã nhận: ${amount(counters.claimed)}`, { class: 'muted' })),
            iconButton('Xem chiến dịch ' + campaign.draft.titleVi, () => detail(campaign.id), 'eye'));
          results.append(row);
        } pagination(results, data, load);
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
    } load();
  }
  function detail(id, message = '', errorMessage = '') {
    if (!role() || role() === 'SUPPORT') return;
    const main = shell('Chiến dịch quà', 'gifts'), toolbar = node('div', null, { class: 'toolbar' });
    append(toolbar, iconButton('Quay lại chiến dịch', list, 'back'), iconButton('Làm mới', load, 'refresh')); main.append(toolbar);
    const notices = feedback(main), confirmation = node('p', message, { role: 'status', class: 'confirmation', 'aria-live': 'polite' }), container = node('div', null, { class: 'stack' });
    append(main, confirmation, container); const request = channel();
    async function load() {
      const task = request(); container.replaceChildren(); main.querySelector('h1').textContent = 'Chiến dịch quà'; notices.clear(); notices.status.textContent = 'Đang tải chiến dịch…';
      try {
        const data = await api(path(id), task), { campaign, counters } = data, draft = campaign.draft;
        main.querySelector('h1').textContent = draft.titleVi; notices.status.textContent = ''; if (errorMessage) notices.fail(new Error(errorMessage));
        const actions = node('div', null, { class: 'gift-actions' });
        container.append(node('span', states[campaign.state], { class: 'badge badge-' + (campaign.state === 'PUBLISHED' ? 'active' : campaign.state === 'CANCELLED' ? 'inactive' : 'draft') }));
        let previewControl;
        if (campaign.state === 'DRAFT') {
          previewControl = button('Xem trước', () => preview(data, previewControl), { class: 'primary' });
          append(actions, previewControl, iconButton('Sửa bản nháp', () => edit(data), 'edit'));
        }
        if (owner() && campaign.state !== 'CANCELLED') actions.append(button('Hủy chiến dịch', () => cancel(data)));
        container.append(actions); content(container, draft); schedule(container, draft);
        facts(container, [['ID chiến dịch', campaign.id], ['Phiên bản', amount(campaign.version)], ['Lý do chiến dịch', draft.reason], ['Ngày công bố', date(campaign.publishedAtEpochMillis)], ...(campaign.state === 'CANCELLED' ? [['Lý do hủy', campaign.cancellationReason], ['Ngày hủy', date(campaign.cancelledAtEpochMillis)]] : [])]);
        if (isIndividual(draft)) {
          try { recipientFacts(container, await api('/users/' + encodeURIComponent(draft.userIds[0]), task)); }
          catch (error) {
            if (error.status !== 404) throw error;
            container.append(node('p', draft.userIds[0], { class: 'mono gift-body' }));
          }
        }
        else if (draft.audience === 'USER_IDS') container.append(node('p', draft.userIds.join('\n'), { class: 'mono gift-body' }));
        if (draft.audience === 'REGISTERED_BETWEEN') facts(container, [['Đăng ký từ (bao gồm)', date(draft.registeredFromEpochMillis)], ['Đăng ký đến (không bao gồm)', date(draft.registeredUntilEpochMillis)]]);
        container.append(node('h2', 'Báo cáo chiến dịch'));
        facts(container, [['Đủ điều kiện', counters.eligible], ['Chờ phát', counters.pending], ['Đã phát', counters.delivered], ['Đã nhận', counters.claimed], ['Hết hạn', counters.expired], ['Đã hủy chưa nhận', counters.cancelled], ['Bỏ qua', counters.skipped], ['Vàng đã nhận', counters.claimedGold], ['XP đã nhận', counters.claimedXp], ['Gem đã nhận', counters.claimedGems ?? 0]].map(([key, value]) => [key, amount(value)]), 'gift-report');
        container.append(node('p', 'Các chỉ số có phần giao nhau: đã nhận và hết hạn nằm trong đã phát. Vàng / XP / Gem đã nhận là tổng thực trả; hủy không thu hồi quà đã nhận.', { class: 'muted' }));
      } catch (error) { if (task.current() && error.name !== 'AbortError') notices.fail(error); }
    } load();
  }
  function edit(data) {
    const campaign = data?.campaign, draft = campaign?.draft;
    const surface = giftModal(campaign ? 'Sửa bản nháp quà' : 'Tạo chiến dịch quà'), form = node('form', null, { class: 'stack modal-form' }); surface.dialog.append(form);
    form.append(node('p', 'Nội dung gửi người chơi cần có cả tiếng Việt và tiếng Anh.', { class: 'muted' }));
    const titleVi = field(form, 'Tiêu đề tiếng Việt', 'gift-title-vi', 'text', { required: '', maxlength: '160' });
    const bodyVi = field(form, 'Nội dung tiếng Việt', 'gift-body-vi', 'textarea', { required: '', maxlength: '4000' });
    const titleEn = field(form, 'Tiêu đề tiếng Anh', 'gift-title-en', 'text', { required: '', maxlength: '160', lang: 'en' });
    const bodyEn = field(form, 'Nội dung tiếng Anh', 'gift-body-en', 'textarea', { required: '', maxlength: '4000', lang: 'en' });
    const rewards = node('div', null, { class: 'gift-field-pair gift-reward-fields' }); form.append(rewards);
    const gold = field(rewards, 'Vàng mỗi người', 'gift-gold', 'number', { required: '', min: '0', max: '2147483647', step: '1' });
    const xp = field(rewards, 'XP mỗi người', 'gift-xp', 'number', { required: '', min: '0', max: '2147483647', step: '1' });
    const gems = field(rewards, 'Gem mỗi người', 'gift-gems', 'number', { required: '', min: '0', max: '2147483647', step: '1' });
    const audience = select(form, 'Người nhận', 'gift-audience', audiences);
    const activity = node('option', 'Theo hoạt động gần nhất — chưa hỗ trợ', { value: 'ACTIVITY', disabled: '' }); audience.append(activity);
    const activityHelp = node('p', 'Chưa có nguồn hoạt động gần nhất chính thức. Không dùng ngày đăng ký thay thế.', { class: 'muted gift-help' }); form.append(activityHelp);
    const individual = node('div', null, { class: 'stack' }); form.append(individual);
    const reference = field(individual, 'Mã người chơi hoặc email', 'gift-reference', 'text', { maxlength: '254', autocomplete: 'off', autocapitalize: 'none', spellcheck: 'false', 'aria-describedby': 'gift-reference-help' });
    individual.append(node('p', 'Nhập đúng mã trong hồ sơ game hoặc email đăng ký, rồi tra cứu để kiểm tra người nhận.', { class: 'muted gift-help', id: 'gift-reference-help' }));
    const recipientPanel = node('div', null, { class: 'gift-recipient', 'aria-live': 'polite' });
    const lookupNotices = feedback(individual), lookupRequest = channel(); let resolved = null;
    function clearRecipient() { lookupRequest(); resolved = null; recipientPanel.replaceChildren(); lookupNotices.clear(); lookup.disabled = false; }
    const lookup = button('Tra cứu người nhận', async () => {
      const value = reference.value.trim(); clearRecipient();
      if (!value) { lookupNotices.fail(new Error('Nhập mã người chơi hoặc email.'), true); return; }
      const task = lookupRequest(); lookup.disabled = true; lookupNotices.status.textContent = 'Đang tra cứu người nhận…';
      try {
        const person = await api('/gift-recipient?q=' + encodeURIComponent(value), task);
        if (!task.current() || !surface.dialog.open || reference.value.trim() !== value || audience.value !== 'SINGLE_PLAYER') return;
        resolved = { reference: value, person }; recipientPanel.replaceChildren(); recipientFacts(recipientPanel, person, 'facts');
        lookupNotices.status.textContent = 'Đã xác định người nhận.';
      } catch (error) { if (task.current() && surface.dialog.open && error.name !== 'AbortError') lookupNotices.fail(error, true); }
      finally { if (task.current() && surface.dialog.open) lookup.disabled = false; }
    });
    append(individual, lookup, recipientPanel); reference.addEventListener('input', clearRecipient);
    const idsGroup = node('div', null, { class: 'stack' }); form.append(idsGroup);
    const ids = field(idsGroup, 'ID người chơi', 'gift-ids', 'textarea', { 'aria-describedby': 'gift-ids-help' });
    idsGroup.append(node('p', 'Nhập từ 1 đến 10.000 UUID nội bộ, mỗi ID một dòng hoặc phân cách bằng dấu phẩy. Không cần đủ 10.000. Để dùng mã game hoặc email, chọn “Một người chơi”. ID trùng được gộp khi xem trước.', { class: 'muted gift-help', id: 'gift-ids-help' }));
    const bounds = node('div', null, { class: 'stack' }); form.append(bounds);
    const from = field(bounds, 'Đăng ký từ (bao gồm) · Giờ Việt Nam (UTC+7)', 'gift-from', 'datetime-local', { step: '0.001' });
    const until = field(bounds, 'Đăng ký đến (không bao gồm) · Giờ Việt Nam (UTC+7)', 'gift-until', 'datetime-local', { step: '0.001' });
    const available = field(form, 'Thời gian phát · Giờ Việt Nam (UTC+7)', 'gift-available', 'datetime-local', { required: '', step: '0.001' });
    const expires = field(form, 'Hết hạn · Giờ Việt Nam (UTC+7)', 'gift-expires', 'datetime-local', { required: '', step: '0.001' });
    form.append(node('p', 'Mặc định hết hạn sau 30 ngày từ thời gian phát. Có thể chỉnh mốc này; phát chậm không gia hạn.', { class: 'muted gift-help' }));
    const reason = field(form, 'Lý do chiến dịch', 'gift-reason', 'textarea', { required: '', maxlength: '500' });
    const values = { titleVi, titleEn, bodyVi, bodyEn, gold, xp, gems, reason, audience };
    for (const [key, input] of Object.entries(values)) input.value = draft?.[key] ?? (key === 'audience' ? 'ALL_REGISTERED' : ['gold', 'xp', 'gems'].includes(key) ? '0' : '');
    if (draft && isIndividual(draft)) audience.value = 'SINGLE_PLAYER';
    ids.value = draft?.userIds?.join('\n') ?? '';
    from.value = draft?.registeredFromEpochMillis == null ? '' : localDate(draft.registeredFromEpochMillis);
    until.value = draft?.registeredUntilEpochMillis == null ? '' : localDate(draft.registeredUntilEpochMillis);
    available.value = localDate(draft?.availableAtEpochMillis ?? Math.ceil(Date.now() / 60000) * 60000);
    expires.value = localDate(draft?.expiresAtEpochMillis ?? epoch(available.value) + 30 * DAY);
    let customExpiry = Boolean(draft);
    expires.addEventListener('input', () => { customExpiry = true; });
    available.addEventListener('input', () => { if (!customExpiry && Number.isFinite(epoch(available.value))) expires.value = localDate(epoch(available.value) + 30 * DAY); });
    function audienceChanged() { clearRecipient(); individual.hidden = audience.value !== 'SINGLE_PLAYER'; activityHelp.hidden = !individual.hidden; reference.required = !individual.hidden; idsGroup.hidden = audience.value !== 'USER_IDS'; bounds.hidden = audience.value !== 'REGISTERED_BETWEEN'; ids.required = !idsGroup.hidden; from.required = until.required = !bounds.hidden; }
    audience.addEventListener('change', audienceChanged); audienceChanged();
    const notices = feedback(form), submit = node('button', 'Lưu bản nháp', { type: 'submit', class: 'primary' });
    form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutation(form, submit, notices, surface, campaign ? path(campaign.id) : base, () => {
      const body = Object.fromEntries(Object.entries(values).map(([key, input]) => [key, ['gold', 'xp', 'gems'].includes(key) ? Number(input.value) : input.value.trim()]));
      if (!body.gold && !body.xp && !body.gems) throw new Error('Cần ít nhất một phần thưởng Vàng, XP hoặc Gem lớn hơn 0.');
      for (const key of ['titleVi', 'titleEn', 'bodyVi', 'bodyEn', 'reason']) if (!body[key]) throw new Error('Nội dung và lý do không được chỉ gồm khoảng trắng.');
      if (/[\u0000-\u001f\u007f]/.test(body.reason)) throw new Error('Lý do cần nằm trên một dòng và không chứa ký tự điều khiển.');
      body.userIds = body.audience === 'USER_IDS' ? ids.value.split(/[\s,;]+/).filter(Boolean) : [];
      if (body.audience === 'SINGLE_PLAYER') {
        if (!resolved || resolved.reference !== reference.value.trim()) throw new Error('Tra cứu và kiểm tra người nhận trước khi lưu bản nháp.');
        body.audience = 'USER_IDS'; body.userIds = [resolved.person.id];
      }
      if (body.userIds.length > 10000) throw new Error('Danh sách vượt giới hạn 10.000 ID. Có thể gửi cho một người bằng lựa chọn “Một người chơi”.');
      if (body.audience === 'USER_IDS' && !body.userIds.length) throw new Error('Nhập ít nhất một UUID hoặc chọn “Một người chơi” để dùng mã game hoặc email.');
      if (body.userIds.some(id => !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))) throw new Error('Danh sách nâng cao chỉ nhận UUID nội bộ. Để nhập mã game hoặc email, chọn “Một người chơi”.');
      body.registeredFromEpochMillis = body.audience === 'REGISTERED_BETWEEN' ? epoch(from.value) : null;
      body.registeredUntilEpochMillis = body.audience === 'REGISTERED_BETWEEN' ? epoch(until.value) : null;
      if (body.audience === 'REGISTERED_BETWEEN' && !(body.registeredFromEpochMillis >= 0 && body.registeredUntilEpochMillis > body.registeredFromEpochMillis)) throw new Error('Khoảng đăng ký cần có mốc kết thúc sau mốc bắt đầu.');
      body.availableAtEpochMillis = epoch(available.value); body.expiresAtEpochMillis = epoch(expires.value);
      if (!(body.availableAtEpochMillis >= 0 && body.expiresAtEpochMillis > body.availableAtEpochMillis && body.expiresAtEpochMillis <= 253402300799999)) throw new Error('Hết hạn cần sau thời gian phát; kiểm tra lại các mốc theo Giờ Việt Nam (UTC+7).');
      if (campaign) body.expectedVersion = campaign.version; return body;
    }, result => { surface.finish(); detail(result.campaign.id, 'Đã lưu bản nháp.'); }, campaign ? 'PUT' : 'POST', () => { surface.finish(); detail(campaign.id, '', 'Bản nháp đã thay đổi. Kiểm tra dữ liệu mới trước khi sửa lại.'); });
    surface.show(titleVi);
    if (draft && isIndividual(draft)) {
      const task = lookupRequest(); lookup.disabled = true; lookupNotices.status.textContent = 'Đang tải người nhận đã lưu…';
      api('/users/' + encodeURIComponent(draft.userIds[0]), task).then(person => {
        if (!task.current() || !surface.dialog.open || audience.value !== 'SINGLE_PLAYER') return;
        reference.value = person.playerCode || person.email || ''; recipientFacts(recipientPanel, person, 'facts');
        lookupNotices.status.textContent = 'Tra cứu lại để xác nhận người nhận trước khi lưu.';
      }).catch(error => { if (task.current() && surface.dialog.open && error.name !== 'AbortError') lookupNotices.fail(error); })
        .finally(() => { if (task.current() && surface.dialog.open) lookup.disabled = false; });
    }
  }
  async function preview(data, opener) {
    const { campaign } = data, request = channel(), task = request();
    const surface = giftModal('Xem trước chiến dịch', opener), panel = node('div', null, { class: 'stack' }); surface.dialog.append(panel);
    const notices = feedback(panel); panel.append(surface.cancel); notices.status.textContent = 'Đang tính người nhận…'; surface.pending(true); surface.show(surface.cancel);
    try {
      const result = await api(path(campaign.id) + '/preview', task, { expectedVersion: campaign.version });
      if (result.campaignId !== campaign.id || result.version !== campaign.version) throw Object.assign(new Error('Dữ liệu xem trước đã thay đổi.'), { status: 409 });
      if (isIndividual(campaign.draft) && !result.invalidUserIds.includes(campaign.draft.userIds[0])) {
        try { result.recipient = await api('/users/' + encodeURIComponent(campaign.draft.userIds[0]), task); }
        catch (error) { if (error.status !== 404) throw error; }
      }
      panel.replaceChildren(); totals(panel, result); content(panel, campaign.draft); schedule(panel, campaign.draft);
      if (result.recipient) recipientFacts(panel, result.recipient);
      panel.append(node('p', `${amount(result.duplicateCount)} ID trùng đã được gộp.`, { class: 'muted' }));
      if (result.invalidUserIds.length) append(panel, node('p', 'ID không hợp lệ hoặc không đủ điều kiện. Sửa bản nháp trước khi công bố.', { role: 'alert', class: 'alert' }), node('p', result.invalidUserIds.join('\n'), { class: 'mono gift-body' }));
      const actions = append(node('div', null, { class: 'modal-actions' }), surface.cancel); panel.append(actions);
      if (owner()) {
        const publish = button('Tiếp tục công bố', () => { surface.finish(); publishConfirmation(data, result, opener); }, { class: 'primary' });
        publish.disabled = result.invalidUserIds.length > 0 || BigInt(result.recipientCount) === 0n; actions.append(publish);
      } else panel.append(node('p', 'Owner sẽ xác nhận công bố chiến dịch.', { class: 'muted' }));
      surface.pending(false); surface.dialog.querySelector('.modal-header button').focus();
    } catch (error) {
      if (!task.current() || error.name === 'AbortError') return;
      if (error.status === 409) { surface.finish(); detail(campaign.id, '', 'Dữ liệu đã thay đổi. Xem trước lại trước khi công bố.'); }
      else { surface.pending(false); notices.fail(error, true); }
    }
  }
  function publishConfirmation(data, preview, opener) {
    const { campaign } = data, surface = giftModal('Xác nhận công bố', opener), form = node('form', null, { class: 'stack' }); surface.dialog.append(form);
    append(form, node('p', campaign.draft.titleVi), node('p', 'Công bố sẽ cố định nội dung, phần thưởng, người nhận và lịch phát. Sau đó không thể sửa chiến dịch.', { class: 'muted' }));
    totals(form, preview); facts(form, [['Thời gian phát', date(campaign.draft.availableAtEpochMillis)], ['Hết hạn', date(campaign.draft.expiresAtEpochMillis)], ['Lý do công bố', campaign.draft.reason]]);
    if (preview.recipient) recipientFacts(form, preview.recipient);
    const notices = feedback(form), submit = node('button', 'Công bố chiến dịch', { type: 'submit', class: 'primary' });
    form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutation(form, submit, notices, surface, path(campaign.id) + '/publish', () => ({ expectedVersion: campaign.version, previewToken: preview.previewToken, reason: campaign.draft.reason }), () => { surface.finish(); detail(campaign.id, 'Đã công bố chiến dịch.'); }, 'POST', () => { surface.finish(); detail(campaign.id, '', 'Dữ liệu đã thay đổi. Xem trước lại trước khi công bố.'); });
    surface.show(surface.cancel);
  }
  function cancel(data) {
    const { campaign } = data, surface = giftModal('Hủy chiến dịch'), form = node('form', null, { class: 'stack' }); surface.dialog.append(form);
    append(form, node('p', campaign.draft.titleVi), node('p', 'Hủy sẽ chặn phát mới và quà chưa nhận. Quà đã nhận giữ nguyên.', { class: 'muted' }));
    const reason = field(form, 'Lý do hủy', 'gift-cancel-reason', 'textarea', { required: '', maxlength: '500' }), notices = feedback(form), submit = node('button', 'Xác nhận hủy chiến dịch', { type: 'submit', class: 'primary' });
    form.append(append(node('div', null, { class: 'modal-actions' }), surface.cancel, submit));
    mutation(form, submit, notices, surface, path(campaign.id) + '/cancel', () => { const value = reason.value.trim(); if (!value || /[\u0000-\u001f\u007f]/.test(value)) throw new Error('Nhập lý do hủy trên một dòng.'); return { expectedVersion: campaign.version, reason: value }; }, () => { surface.finish(); detail(campaign.id, 'Đã hủy chiến dịch.'); }, 'POST', () => { surface.finish(); detail(campaign.id, '', 'Chiến dịch đã thay đổi. Kiểm tra báo cáo trước khi hủy lại.'); });
    surface.show(reason);
  }
  list();
}
