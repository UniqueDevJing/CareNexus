import { expect, test } from '@playwright/test'

const workspaces = [
  ['doctor', '/doctor/console', '医生工作台'],
  ['nurse', '/nurse/triage', '护士工作台'],
  ['admin', '/admin/agent-runs', 'Agent执行时间线']
]

for (const [role, path, heading] of workspaces) {
  test(`${role} workspace keeps a clean empty state when APIs return no records`, async ({ page }) => {
    const errors = []
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
    await page.addInitScript(({ role }) => localStorage.setItem('hospital_user', JSON.stringify({ username: role, name: role, role })), { role })
    await page.route('**/v2/api/**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data: [] }) }))
    await page.goto(`/CareNexus${path}`)
    await expect(page.getByText(heading, { exact: false }).first()).toBeVisible()
    await expect(page.locator('main')).toBeVisible()
    await page.waitForLoadState('networkidle')
    expect(errors).toEqual([])
  })
}
