import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './audit-e2e',
  use: { baseURL: 'http://127.0.0.1:5194/CareNexus/', trace: 'retain-on-failure' },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1280, height: 900 } } },
    { name: 'tablet', testMatch: '**/roles.spec.js', use: { viewport: { width: 768, height: 1024 } } },
    { name: 'mobile', testMatch: '**/roles.spec.js', use: { viewport: { width: 375, height: 812 } } }
  ],
  webServer: { command: 'npm run dev -- --host 127.0.0.1 --port 5194', url: 'http://127.0.0.1:5194/CareNexus/login' }
})
