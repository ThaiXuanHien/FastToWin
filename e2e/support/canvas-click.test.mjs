import test from 'node:test';
import assert from 'node:assert/strict';
import { chromium, selectors } from 'playwright';
import { click } from './game.mjs';

// Model the CI trace where selector resolution takes longer than 500 ms.
await selectors.register('delayed', () => ({
  query(root, selector) {
    const until = performance.now() + 650;
    while (performance.now() < until) { /* Simulate a busy browser main thread. */ }
    return root.querySelector(selector);
  },
  queryAll(root, selector) {
    const until = performance.now() + 650;
    while (performance.now() < until) { /* Simulate a busy browser main thread. */ }
    return [...root.querySelectorAll(selector)];
  },
}));

test('canvas click reaches an attached checkbox when selector resolution exceeds 500 ms', async () => {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    await page.setContent(`
      <div id="arcade_dialog" style="position:absolute;left:430px;top:238px;width:420px;height:425px">
        <button id="agreement" role="checkbox" aria-checked="false"
          style="position:absolute;left:18px;top:232px;width:384px;height:48px"
          onclick="this.setAttribute('aria-checked', 'true')">Agree</button>
      </div>
    `);
    await click(page, page.locator('delayed=#agreement'));
    assert.equal(await page.locator('#agreement').getAttribute('aria-checked'), 'true');
  } finally {
    await browser.close();
  }
});
