import { defineConfig } from 'playwright/test';

const reuse = process.env.E2E_REUSE_SERVERS === '1';
const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:18081';
const apiURL = process.env.E2E_API_URL || 'http://127.0.0.1:18080';

// These tests create/delete disposable accounts. Never point them at production.
for (const value of [baseURL, apiURL]) {
  const url = new URL(value);
  if (url.protocol !== 'http:' || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) {
    throw new Error('Web E2E only permits loopback HTTP servers.');
  }
}
process.env.E2E_API_URL = apiURL;

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    viewport: { width: 430, height: 932 },
    locale: 'vi-VN',
    serviceWorkers: 'block',
    testIdAttribute: 'id', // Compose Web maps SemanticsProperties.TestTag to DOM id.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 60_000,
  },
  projects: [
    {
      name: 'chromium',
      testMatch: /game\.spec\.mjs/,
      use: { browserName: 'chromium' },
    },
    {
      name: 'chromium-small-phone',
      testMatch: /responsive\.spec\.mjs/,
      use: { browserName: 'chromium', viewport: { width: 320, height: 568 } },
    },
    {
      name: 'chromium-large-phone',
      testMatch: /responsive\.spec\.mjs/,
      use: { browserName: 'chromium', viewport: { width: 430, height: 932 } },
    },
    {
      name: 'chromium-tablet',
      testMatch: /responsive\.spec\.mjs/,
      use: { browserName: 'chromium', viewport: { width: 834, height: 1112 } },
    },
    {
      name: 'chromium-landscape',
      testMatch: /responsive\.spec\.mjs/,
      use: { browserName: 'chromium', viewport: { width: 932, height: 430 } },
    },
    {
      name: 'firefox-smoke',
      testMatch: /browser-smoke\.spec\.mjs/,
      use: { browserName: 'firefox', viewport: { width: 390, height: 844 } },
    },
    {
      name: 'webkit-smoke',
      testMatch: /browser-smoke\.spec\.mjs/,
      use: { browserName: 'webkit', viewport: { width: 390, height: 844 } },
    },
  ],
  webServer: reuse ? undefined : {
    command: 'node support/serve.mjs',
    url: `${baseURL}/__e2e/ready`,
    reuseExistingServer: false,
    timeout: 60_000,
    stdout: 'pipe',
    stderr: 'pipe',
    env: { E2E_BASE_URL: baseURL, E2E_API_URL: apiURL },
  },
});
