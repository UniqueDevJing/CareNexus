import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  use: {
    baseURL: process.env.BASE_URL || 'http://127.0.0.1:5192',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --mode contest --host 127.0.0.1 --port 5192',
    url: 'http://127.0.0.1:5192/contest',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['iPhone 13'], browserName: 'chromium' } },
  ],
})
