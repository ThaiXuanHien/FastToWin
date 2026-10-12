import { defineConfig } from 'playwright/test';

process.env.E2E_BASE_URL ||= 'http://127.0.0.1:18181';
process.env.E2E_API_URL ||= 'http://127.0.0.1:18180';
// Reuse the normal loopback safety checks and owned backend lifecycle.
const { default: base } = await import('./playwright.config.mjs');
export default defineConfig({
  ...base,
  testMatch: /clan-keyboard\.spec\.mjs/,
  timeout: 120_000,
  expect: { timeout: 10_000 },
  outputDir: '../.artifacts/clan-keyboard/results',
  reporter: 'list',
  use: {
    ...base.use,
    viewport: { width: 375, height: 812 },
    hasTouch: true,
  },
  projects: [
    { name: 'chromium-clan-keyboard', use: { browserName: 'chromium' } },
    { name: 'webkit-clan-keyboard', use: { browserName: 'webkit' } },
  ],
});
