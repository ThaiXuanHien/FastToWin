const path = '/avatar-frames';
const maxPngBytes = 256 * 1024;

function readPngBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener('load', () => resolve(reader.result.split(',')[1]), { once: true });
    reader.addEventListener('error', () => reject(new Error('Không thể đọc tệp PNG.')), { once: true });
    reader.readAsDataURL(file);
  });
}

export function openAvatarFrames({ node, append, button, field, modal, feedback, shell, channel, api }) {
  const main = shell('Khung avatar', 'avatarFrames');
  const notices = feedback(main);
  const list = node('div', null, { class: 'frame-list' });
  const request = channel();
  const create = button('Thêm khung', () => editFrame(null, create), { class: 'primary', disabled: '' });
  main.append(append(node('div', null, { class: 'toolbar' }), create), list);

  function frameImage(frame, size) {
    return node('img', null, { class: 'frame-image', src: frame.imagePath, alt: '',
      width: size, height: size, loading: 'lazy' });
  }

  async function load(message = '', focusId = null) {
    const task = request();
    create.disabled = true;
    notices.clear();
    notices.status.textContent = 'Đang tải danh mục khung…';
    try {
      const frames = await api(path, task);
      if (!task.current()) return;
      const rows = frames.map(item => {
        const frame = item.definition;
        const edit = button('Sửa', () => editFrame(item, edit), { 'aria-label': `Sửa ${frame.nameVi}` });
        edit.dataset.frameId = frame.id;
        const name = append(node('div', null, { class: 'frame-name' }), node('strong', frame.nameVi),
          node('span', frame.nameEn), node('code', frame.id));
        return append(node('article', null, { class: 'frame-row' }), frameImage(frame, 56), name, edit);
      });
      list.replaceChildren(...rows);
      if (!rows.length) list.append(node('p', 'Chưa có khung avatar.', { class: 'muted' }));
      notices.status.textContent = message;
      if (focusId) list.querySelector(`button[data-frame-id="${CSS.escape(focusId)}"]`)?.focus();
    } catch (error) {
      if (task.current()) notices.fail(error);
    } finally {
      if (task.current()) create.disabled = false;
    }
  }

  function editFrame(item, opener) {
    const frame = item?.definition;
    const surface = modal(frame ? 'Sửa khung avatar' : 'Thêm khung avatar', opener);
    const form = node('form', null, { class: 'stack' });
    if (frame) form.append(node('code', frame.id));
    const id = frame ? null : field(form, 'Mã khung', 'avatar-frame-id', 'text', {
      required: '', maxlength: '32', pattern: 'frame_[a-z0-9]+(_[a-z0-9]+)*',
      'aria-describedby': 'avatar-frame-id-help', autocapitalize: 'none', spellcheck: 'false',
    });
    if (id) form.append(node('p', 'Ví dụ frame_rong_bac. Khung mới dùng được cho mọi tài khoản.',
      { id: 'avatar-frame-id-help', class: 'muted frame-field-help' }));
    const names = node('div', null, { class: 'gift-field-pair' });
    form.append(names);
    const nameVi = field(names, 'Tên tiếng Việt', 'avatar-frame-name-vi', 'text', { required: '', maxlength: '80' });
    const nameEn = field(names, 'Tên tiếng Anh', 'avatar-frame-name-en', 'text', { required: '', maxlength: '80' });
    nameVi.value = frame?.nameVi ?? '';
    nameEn.value = frame?.nameEn ?? '';
    const file = field(form, frame ? 'Thay ảnh PNG (tùy chọn)' : 'Ảnh khung PNG', 'avatar-frame-png', 'file', {
      accept: 'image/png,.png', 'aria-describedby': 'avatar-frame-png-help', ...(frame ? {} : { required: '' }),
    });
    form.append(node('p', 'Ảnh vuông 64–512 px, có kênh trong suốt và tối đa 256 KB.',
      { id: 'avatar-frame-png-help', class: 'muted frame-field-help' }));
    const canvas = node('canvas', null, { class: 'frame-image', role: 'img', 'aria-label': 'Xem trước ảnh khung mới',
      width: '80', height: '80', hidden: '' });
    const currentImage = frame ? frameImage(frame, 80) : null;
    if (currentImage) {
      currentImage.alt = 'Ảnh khung hiện tại';
      form.append(currentImage);
    }
    form.append(canvas);

    const reason = field(form, 'Lý do thay đổi', 'avatar-frame-reason', 'text', { required: '', maxlength: '500' });
    const submit = node('button', 'Lưu khung', { type: 'submit', class: 'primary' });
    const errors = feedback(form);
    form.append(append(node('div', null, { class: 'toolbar' }), submit, surface.cancel));
    surface.dialog.append(form);
    surface.show(id ?? nameVi);

    file.addEventListener('change', async () => {
      const selected = file.files[0];
      canvas.hidden = true;
      if (currentImage) currentImage.hidden = false;
      file.setCustomValidity('');
      errors.clear();
      submit.disabled = Boolean(selected);
      if (!selected) return;
      try {
        if (selected.size > maxPngBytes) throw new Error('Tệp PNG phải nhỏ hơn hoặc bằng 256 KB.');
        const bitmap = await createImageBitmap(selected);
        try {
          if (file.files[0] !== selected) return;
          if (bitmap.width < 64 || bitmap.width > 512 || bitmap.width !== bitmap.height) {
            throw new Error('Chọn ảnh PNG vuông từ 64 đến 512 px.');
          }
          const context = canvas.getContext('2d');
          context.clearRect(0, 0, canvas.width, canvas.height);
          context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
          if (currentImage) currentImage.hidden = true;
          canvas.hidden = false;
        } finally { bitmap.close(); }
      } catch (error) {
        if (file.files[0] !== selected) return;
        const message = error.name === 'InvalidStateError' ? 'Không thể mở ảnh PNG.' : error.message;
        file.setCustomValidity(message);
        errors.fail(new Error(message));
      } finally {
        if (file.files[0] === selected) submit.disabled = false;
      }
    });

    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (submit.disabled) return;
      const frameId = frame?.id ?? id.value.trim();
      const task = channel()();
      submit.disabled = true;
      surface.pending(true);
      errors.clear();
      errors.status.textContent = 'Đang lưu khung…';
      try {
        const pngBase64 = file.files[0] ? await readPngBase64(file.files[0]) : null;
        if (!task.current()) return;
        await api(`${path}/${encodeURIComponent(frameId)}`, task, {
          nameVi: nameVi.value.trim(), nameEn: nameEn.value.trim(), reason: reason.value.trim(),
          version: item?.version ?? null, pngBase64,
        }, 'PUT');
        if (!task.current()) return;
        surface.finish();
        await load('Đã lưu khung avatar.', frameId);
      } catch (error) {
        if (!task.current()) return;
        if (error.status === 409) {
          errors.fail(new Error('Mã khung đã tồn tại hoặc khung đã thay đổi. Đóng hộp thoại để tải dữ liệu mới.'), true);
          surface.dialog.addEventListener('close', () => load(), { once: true });
        } else {
          errors.fail(error, true);
        }
      } finally {
        submit.disabled = false;
        if (surface.dialog.open) surface.pending(false);
      }
    });
  }

  load();
}
