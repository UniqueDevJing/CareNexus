import { expect, test } from '@playwright/test'

test('public contest page exposes the real workflow and evidence sections', async ({ page }, testInfo) => {
  const errors = []
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })

  await page.goto('/contest')
  await expect(page.getByRole('heading', { name: '把一次预问诊，变成可核验的就诊流程。' })).toBeVisible()
  await expect(page.getByText('CareNexus 2026 Qoder 高校创造力大赛作品', { exact: false })).toBeVisible()
  await expect(page.getByRole('link', { name: '进入真实工作台' })).toHaveAttribute('href', /114\.215\.186\.113\/CareNexus/)

  await page.getByRole('link', { name: '四端链路' }).click()
  await expect(page.locator('#workflow')).toBeInViewport()
  await expect(page.getByRole('heading', { name: '四个工作区，合成一条就诊链路。' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '患者端' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '护士端' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '医生端' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '管理端' })).toBeVisible()

  await page.getByRole('link', { name: '知识来源' }).click()
  await expect(page.locator('#knowledge')).toBeInViewport()
  await expect(page.getByRole('heading', { name: '知识库先审来源，再进入检索。' })).toBeVisible()
  await expect(page.getByText('SOURCE_VERIFIED', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('无法确认', { exact: true })).toBeVisible()

  await page.screenshot({ path: testInfo.outputPath('contest-page.png'), fullPage: true })
  expect(errors).toEqual([])
})
