import { defineConfig, devices } from '@playwright/test'

/**
 * Browser end-to-end tests against a RUNNING StockFlow:
 *   docker compose up -d --build      (UI + API on http://localhost:3000, the default here)
 *   or the Vite dev server:           E2E_BASE_URL=http://localhost:5173 npm run e2e
 * The tests create their own uniquely named data, so any database works (see e2e/global-setup.ts).
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  // No automatic retries: a test that only passes on the second try is hiding a real problem.
  retries: 0,
  forbidOnly: process.env.CI !== undefined,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI !== undefined ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
