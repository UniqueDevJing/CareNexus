import { expect, test } from '@playwright/test'

test('product shell exposes the patient, clinical and security boundaries', async ({ page }) => {
  const errors = []
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await page.goto('/CareNexus/login')
  await expect(page).toHaveTitle(/CareNexus/)
  await expect(page.getByRole('heading', { name: '进入工作台' })).toBeVisible()
  await expect(page.getByText('临床复核', { exact: true })).toBeVisible()
  await expect(page.getByText('本地演示账号', { exact: true })).toBeVisible()
  await expect(page.getByText('护士', { exact: true })).toHaveCount(0)
  await expect(page.getByText('比赛', { exact: true })).toHaveCount(0)
  expect(errors).toEqual([])
})
