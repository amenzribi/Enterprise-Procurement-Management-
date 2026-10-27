import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests against servers that are already running (backend with the `dev,demo` profiles on :8080,
 * `npm start` on :4200). They use the locally installed Microsoft Edge by default (PW_CHANNEL=chrome for Chrome,
 * or PW_CHANNEL=chromium after `npx playwright install chromium`, e.g. on CI). The browser used is printed in the
 * report so QA evidence records it (S1DR-25).
 *
 * Tests share the demo database, so they run one at a time and create their own uniquely named requests.
 */
const channel = process.env['PW_CHANNEL'] ?? 'msedge';

export default defineConfig({
  testDir: '.',
  outputDir: '.results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { outputFolder: '.report', open: 'never' }]],
  metadata: { browserChannel: channel },
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    channel: channel === 'chromium' ? undefined : channel,
    viewport: { width: 1280, height: 900 },
    locale: 'en-GB',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
