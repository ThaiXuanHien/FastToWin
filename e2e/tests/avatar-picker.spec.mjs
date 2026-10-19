import { createHash } from 'node:crypto';
import { test, expect, tag, click, login } from '../support/game.mjs';

// Removing the browser's white matte must fail the decoded JPEG pixel checks,
// even though the backend still saves and serves a valid image successfully.
async function fixture(page, alpha) {
  const encoded = await page.evaluate(alpha => {
    const canvas = document.createElement('canvas');
    canvas.width = 1024;
    canvas.height = 768;
    const context = canvas.getContext('2d');
    if (alpha) {
      context.fillStyle = 'rgba(0, 0, 255, 0.5)';
      context.fillRect(1024 / 3, 0, 1024 / 3, 768);
      context.fillStyle = 'rgb(255, 128, 0)';
      context.fillRect(2048 / 3, 0, 1024 / 3, 768);
    } else {
      context.fillStyle = 'rgb(32, 160, 64)';
      context.fillRect(0, 0, 1024, 768);
    }
    return canvas.toDataURL(alpha ? 'image/png' : 'image/jpeg', 1).split(',')[1];
  }, alpha);
  return { name: alpha ? 'alpha.png' : 'opaque.jpg',
    mimeType: alpha ? 'image/png' : 'image/jpeg', buffer: Buffer.from(encoded, 'base64') };
}

async function choose(page, file) {
  const pending = page.waitForEvent('filechooser');
  await click(page, tag(page, 'profile_avatar_picker'));
  await (await pending).setFiles(file);
  await expect(tag(page, 'profile_avatar_preview')).toBeAttached();
}

async function pixels(page, bytes) {
  return page.evaluate(async encoded => {
    const image = new Image();
    image.src = 'data:image/jpeg;base64,' + encoded;
    await image.decode();
    const canvas = document.createElement('canvas');
    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;
    const context = canvas.getContext('2d');
    context.drawImage(image, 0, 0);
    return { width: canvas.width, height: canvas.height,
      samples: [1 / 6, 1 / 2, 5 / 6].map(fraction =>
        Array.from(context.getImageData(Math.floor(canvas.width * fraction),
          Math.floor(canvas.height / 2), 1, 1).data).slice(0, 3)) };
  }, bytes.toString('base64'));
}

for (const alpha of [true, false]) {
  test(alpha ? 'avatar PNG alpha is composited on white before JPEG upload'
    : 'opaque JPEG avatar preserves color through picker, save and reload', async ({ actors, request }, testInfo) => {
    test.skip(!process.env.E2E_POSTGRES_SETTINGS, 'Avatar persistence requires the isolated PostgreSQL fixture');
    const player = await actors('Avatar');
    const page = player.page;
    await page.setViewportSize({ width: 1280, height: 900 });
    const loginResponse = page.waitForResponse(response =>
      response.url() === `${process.env.E2E_API_URL}/auth/login` && response.request().method() === 'POST');
    await login(player);
    const userId = (await (await loginResponse).json()).userId;
    const avatarUrl = `${process.env.E2E_API_URL}/api/avatar/${userId}`;
    await click(page, tag(page, 'bottom_tab:account'));
    await expect(tag(page, 'profile_identity_card')).toBeAttached();
    const file = await fixture(page, alpha);
    await click(page, tag(page, 'profile_edit'));
    await choose(page, file);
    await page.screenshot({ path: testInfo.outputPath('avatar-preview-desktop.png') });
    await click(page, page.getByRole('button', { name: 'Hủy', exact: true }));
    await expect(tag(page, 'profile_display_name')).not.toBeAttached();
    expect((await request.get(avatarUrl)).status(), 'Cancel does not upload the draft').toBe(404);
    await click(page, tag(page, 'profile_edit'));
    await expect(tag(page, 'profile_avatar_preview')).not.toBeAttached();
    await choose(page, file);
    await click(page, page.getByRole('button', { name: 'Lưu', exact: true }));
    await expect(tag(page, 'profile_display_name')).not.toBeAttached();
    let response;
    await expect.poll(async () => {
      response = await request.get(avatarUrl);
      return response.status();
    }).toBe(200);
    expect(response.headers()['content-type']).toContain('image/jpeg');
    expect(response.headers()['cache-control']).toContain('no-store');
    const bytes = await response.body();
    expect(bytes.length).toBeLessThanOrEqual(45 * 1024);
    const decoded = await pixels(page, bytes);
    expect(decoded.width).toBe(512);
    expect(decoded.height).toBe(384);
    const expected = alpha ? [[255, 255, 255], [127, 127, 255], [255, 128, 0]]
      : [[32, 160, 64], [32, 160, 64], [32, 160, 64]];
    for (const [index, rgb] of expected.entries()) {
      for (const [channel, value] of rgb.entries()) {
        expect(Math.abs(decoded.samples[index][channel] - value),
          `JPEG sample ${index}, channel ${channel}`).toBeLessThanOrEqual(5);
      }
    }
    await player.navigate(() => page.reload());
    await expect(tag(page, 'profile_identity_card')).toBeAttached();
    const afterReload = await request.get(avatarUrl);
    expect(afterReload.status()).toBe(200);
    expect(createHash('sha256').update(await afterReload.body()).digest('hex'))
      .toBe(createHash('sha256').update(bytes).digest('hex'));
    await page.screenshot({ path: testInfo.outputPath('avatar-saved-desktop.png') });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: testInfo.outputPath('avatar-saved-mobile-layout.png') });
    await testInfo.attach('avatar-pixels', { body: Buffer.from(JSON.stringify(decoded)), contentType: 'application/json' });
  });
}
