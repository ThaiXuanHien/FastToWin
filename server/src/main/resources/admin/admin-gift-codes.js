const path = '/gift-codes';
const rewardFields = { gold: 'Vàng', xp: 'XP', gems: 'Gem', plays: 'Lượt tích lũy' };
const timeZone = 'Asia/Bangkok';
const minuteFormat = new Intl.DateTimeFormat('vi-VN', { timeZone, dateStyle: 'short', timeStyle: 'short' });
const secondFormat = new Intl.DateTimeFormat('vi-VN', { timeZone, dateStyle: 'short', timeStyle: 'medium' });
const millisecondFormat = new Intl.DateTimeFormat('vi-VN', {
  timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  second: '2-digit', fractionalSecondDigits: 3, hourCycle: 'h23',
});
function expiryLabel(value) {
  const formatter = value % 1000 ? millisecondFormat : value % 60000 ? secondFormat : minuteFormat;
  return `${formatter.format(value)} · Giờ Việt Nam (UTC+7)`;
}

function rewardLabel(reward) {
  return Object.entries(rewardFields)
    .filter(([key]) => reward[key] > 0)
    .map(([key, label]) => `${reward[key].toLocaleString('vi-VN')} ${label}`)
    .join(' · ');
}

export function openGiftCodes(ui) {
  const { node, append, button, field, modal, feedback, shell, channel, api, pagination } = ui;
  const main = shell('Mã quà tặng', 'giftCodes');
  const notices = feedback(main);
  const list = node('div', null, { class: 'stack' });
  const request = channel();
  const create = button('Tạo mã', () => editCode(create), { class: 'primary' });
  main.append(append(node('div', null, { class: 'toolbar' }), create), list);

  async function load(page = 0) {
    const task = request();
    notices.clear();
    notices.status.textContent = 'Đang tải mã quà tặng…';
    try {
      const response = await api(`${path}?page=${page}`, task);
      if (!task.current()) return;
      const now = response.serverNowEpochMillis ?? Date.now();
      notices.clear();
      list.replaceChildren();
      for (const item of response.items) {
        const status = item.stopped ? 'Đã ngừng' : item.expiresAt <= now
          ? 'Hết hạn' : item.maximumClaims != null && item.claims >= item.maximumClaims
            ? 'Hết lượt' : 'Đang hoạt động';
        const row = append(node('article', null, { class: 'gift-code-row' }),
          append(node('div', null, { class: 'row-info' }),
            node('strong', item.code),
            node('p', rewardLabel(item.reward)),
            node('p', `${status} · ${item.claims}/${item.maximumClaims ?? '∞'} lượt · Hết hạn ${expiryLabel(item.expiresAt)}`)));
        if (status === 'Đang hoạt động') {
          const stop = button('Ngừng mã', () => stopCode(item, stop, page));
          row.append(stop);
        }
        list.append(row);
      }
      if (!response.items.length) list.append(node('p', 'Chưa có mã quà tặng.'));
      pagination(list, response, load);
    } catch (error) {
      if (task.current()) notices.fail(error);
    }
  }

  function editCode(opener) {
    const surface = modal('Tạo mã quà tặng', opener);
    surface.dialog.classList.add('gift-modal');
    const form = node('form', null, { class: 'stack' });
    const code = field(form, 'Mã quà tặng', 'gift-code', 'text', { required: '', minlength: '4', maxlength: '24', pattern: '[A-Za-z0-9_\\-]{4,24}' });
    const expiry = field(form, 'Hết hạn (giờ Việt Nam)', 'gift-code-expiry', 'datetime-local', { required: '', step: '0.001' });
    const limit = field(form, 'Tổng lượt nhận (tùy chọn)', 'gift-code-limit', 'number', { min: '1', max: '2147483647' });
    const grid = node('div', null, { class: 'gift-field-pair gift-reward-fields' });
    form.append(grid);
    const rewards = Object.fromEntries(Object.entries(rewardFields).map(([key, label]) => {
      const control = field(grid, label, 'gift-code-' + key, 'number', { min: '0', max: '2147483647', required: '' });
      control.value = '0';
      return [key, control];
    }));
    const reason = field(form, 'Lý do phát hành', 'gift-code-reason', 'text', { required: '', maxlength: '500' });
    const preview = node('div', null, { class: 'stack', hidden: '' });
    const previewButton = node('button', 'Xem trước', { type: 'submit', class: 'primary' });
    form.append(previewButton);
    surface.dialog.append(form, preview);
    const errors = feedback(surface.dialog);
    surface.show(code);
    form.addEventListener('submit', event => {
      event.preventDefault();
      const reward = Object.fromEntries(Object.entries(rewards).map(([key, input]) => [key, Number(input.value)]));
      const expiresAt = expiry.valueAsNumber - 7 * 3600000;
      if (!Object.values(reward).some(value => value > 0) || !Number.isFinite(expiresAt)) {
        errors.fail(new Error('Chọn ít nhất một phần thưởng và nhập thời gian hết hạn.'), true);
        return;
      }
      const payload = {
        code: code.value.trim().toUpperCase(), reward, expiresAt,
        maximumClaims: limit.value ? Number(limit.value) : null,
        reason: reason.value.trim(), requestId: crypto.randomUUID(),
      };
      form.hidden = true;
      preview.hidden = false;
      const back = button('Quay lại chỉnh sửa', () => {
        preview.hidden = true;
        form.hidden = false;
        code.focus();
      });
      const publish = button('Phát hành', async () => {
        back.disabled = true;
        try { await mutate(path, payload, publish, surface, errors, 0); }
        finally { back.disabled = false; }
      }, { class: 'primary' });
      preview.replaceChildren(node('h3', payload.code), node('p', rewardLabel(reward)),
        node('p', `Hết hạn ${expiryLabel(expiresAt)} · ${payload.maximumClaims ?? 'Không giới hạn'} lượt`),
        publish, back);
      back.focus();
    });
  }

  function stopCode(item, opener, page) {
    const surface = modal('Ngừng mã ' + item.code, opener);
    const form = node('form', null, { class: 'stack' });
    const reason = field(form, 'Lý do ngừng mã', 'gift-code-stop', 'text', { required: '', maxlength: '500' });
    const submit = node('button', 'Ngừng nhận mã', { type: 'submit', class: 'primary' });
    form.append(submit);
    surface.dialog.append(form);
    const errors = feedback(surface.dialog);
    surface.show(reason);
    const requestId = crypto.randomUUID();
    form.addEventListener('submit', event => {
      event.preventDefault();
      mutate(`${path}/${encodeURIComponent(item.code)}/stop`, { reason: reason.value.trim(), requestId },
        submit, surface, errors, page);
    });
  }

  async function mutate(target, payload, submit, surface, errors, page) {
    if (submit.disabled) return;
    submit.disabled = true;
    surface.pending(true);
    errors.clear();
    const operation = channel()();
    try {
      await api(target, operation, payload, 'POST');
      if (!operation.current()) return;
      surface.finish();
      await load(page);
    } catch (error) {
      if (operation.current()) errors.fail(error, true);
    } finally {
      submit.disabled = false;
      if (surface.dialog.open) surface.pending(false);
    }
  }
  load();
}
