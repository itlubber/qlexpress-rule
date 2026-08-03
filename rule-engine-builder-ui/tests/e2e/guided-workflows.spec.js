const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')

function workflowFixtures() {
  const routes = createDetailApiData()
  routes.set('/api/rule/governance/requests/summary', {
    pendingCount: 3,
    myDraftCount: 1,
    myRequestCount: 6,
    completedCount: 9
  })
  routes.set('/api/rule/governance/requests', {
    records: [{
      id: 91,
      requestNo: 'GOV-E2E-91',
      resourceType: 'LIST_RECORD_BATCH',
      resourceId: 0,
      action: 'CREATE',
      status: 'PENDING',
      applicant: 'e2e-applicant',
      submitTime: '2026-08-03T08:30:00',
      submittedSnapshotJson: JSON.stringify({
        batchId: 101,
        listName: '手机号黑名单',
        addCount: 2,
        updateCount: 1,
        deleteCount: 1
      })
    }],
    total: 1
  })
  return routes
}

test('审批任务中心默认跨模块展示待处理事项并覆盖全部资源筛选', async ({
  page
}) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: workflowFixtures()
  })

  await page.goto('http://tianshu.local/index.html#/approval')

  await expect(page.getByRole('heading', { name: '审批任务中心' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '待处理', exact: true }))
    .toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText('手机号黑名单 · 4 条内容变更', { exact: true }))
    .toBeVisible()
  await page.locator('.filter-bar .el-select').first().click()
  await expect(page.getByText('名单内容批次', { exact: true }).last()).toBeVisible()
  await expect(page.getByText('计费配置', { exact: true }).last()).toBeVisible()
  await page.keyboard.press('Escape')
  assertClean()
})

test('外数 API 按业务、稳定性和高级能力分层且保留完整配置入口', async ({
  page
}) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: workflowFixtures()
  })

  await page.goto('http://tianshu.local/index.html#/datasource/api/new?projectId=1')

  await expect(page.getByText('配置检查', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /业务配置/ })).toBeVisible()
  await expect(page.getByText('接口鉴权', { exact: true }).last()).toBeVisible()
  await page.getByRole('button', { name: /稳定性策略/ }).click()
  await expect(page.getByText('连接&流控', { exact: true })).toBeVisible()
  await expect(page.getByText('异常&重试', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: /高级能力/ }).click()
  await expect(page.getByText('脚本处理', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '生成审批草稿', exact: true }))
    .toBeVisible()
  assertClean()
})
