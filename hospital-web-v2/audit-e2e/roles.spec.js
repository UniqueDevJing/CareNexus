import { test, expect } from '@playwright/test'
for (const [role, destination] of [['patient','patient/pre'],['nurse','nurse/triage'],['doctor','doctor/console'],['admin','admin/dashboard']]) {
  test(`${role} login mounts real workspace without failed APIs`, async ({ page }) => {
    const failures = []
    page.on('response', response => { if (response.url().includes('/v2/api/') && response.status() >= 400) failures.push(`${response.status()} ${new URL(response.url()).pathname}`) })
    await page.goto('./login')
    await page.getByPlaceholder('输入用户名').fill(role)
    await page.getByPlaceholder('输入密码', { exact: true }).fill('123456')
    await page.locator('button[type=submit]').click()
    await expect(page).toHaveURL(new RegExp(destination))
    await expect(page.locator('main')).toBeVisible()
    await page.waitForLoadState('networkidle')
    expect(failures).toEqual([])
    if (role === 'patient') {
      await page.goto('./admin/dashboard')
      await expect(page).toHaveURL(/patient\/pre/)
      await expect(page.getByRole('button', { name: '发送', exact: false })).toBeDisabled()
    }
  })
}

test('multiline SSE preserves text without protocol prefixes', async ({ page }) => {
  await page.goto('./login')
  await page.getByPlaceholder('输入用户名').fill('patient')
  await page.getByPlaceholder('输入密码', { exact: true }).fill('123456')
  await page.locator('button[type=submit]').click()
  await expect(page).toHaveURL(/patient\/pre/)
  await page.getByRole('button', { name: '开始对话', exact: true }).click()
  await page.route('**/v2/api/patient/chat', route => route.fulfill({ status: 200, contentType: 'text/event-stream', body: 'data:第一行\ndata:第二行\n\ndata:[DONE]\n\n' }))
  await page.locator('textarea').first().fill('咳嗽两天')
  await page.getByRole('button', { name: '发送', exact: false }).click()
  await expect(page.locator('.message-row.bot .message-content p').last()).toHaveText('第一行\n第二行')
})
