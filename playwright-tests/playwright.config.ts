import { defineConfig, devices } from '@playwright/test';

/**
 * Namma Journey full-stack E2E suite.
 *
 * Runs serially against a locally running stack (backend :8080, Vite :5173) and a live
 * Postgres. Serial + single worker is deliberate: the specs share two freshly created
 * accounts and walk a booking through its status machine, so ordering matters.
 */
export default defineConfig({
  testDir: './tests',
  globalSetup: require.resolve('./global-setup'),
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    ['json', { outputFile: 'reports/results.json' }],
  ],
  use: {
    baseURL: process.env.E2E_WEB_BASE || 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    permissions: ['geolocation'],
    geolocation: { latitude: 11.0168, longitude: 76.9558 }, // Coimbatore
    viewport: { width: 1440, height: 900 },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
