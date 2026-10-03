import { defineConfig } from 'playwright/test';

export default defineConfig({
  testDir: './tests', testMatch: ['admin.spec.mjs', 'admin-worker.spec.mjs', 'admin-gifts.spec.mjs', 'gift-worker.spec.mjs'],
  fullyParallel: false, workers: 1, timeout: 15000,
  expect: { timeout: 3000 },
  outputDir: '../.artifacts/admin-playwright',
  reporter: [['list']],
  use: { browserName: 'chromium', viewport: { width: 1440, height: 1000 }, trace: process.env.ADMIN_LIVE_SETTINGS ? 'off' : 'retain-on-failure' },
});
