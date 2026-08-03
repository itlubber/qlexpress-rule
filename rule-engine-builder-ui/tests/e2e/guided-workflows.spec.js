const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createManagementApiData } = require('./support/managementFixtures.cjs')

function workflowFixtures() {
  const routes = new Map([
    ...createDetailApiData(),
    ...createManagementApiData()
  ])
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
  routes.set('/api/rule/variable/source-options', {
    apiOptions: [{
      id: 22,
      code: 'credit_query',
      name: '征信查询',
      scope: 'PROJECT',
      projectId: 1,
      parentId: 21,
      parentName: '征信供应商'
    }],
    databaseOptions: [{
      id: 31,
      code: 'risk_mysql',
      name: '风控只读库',
      scope: 'PROJECT',
      projectId: 1
    }],
    listOptions: [{
      id: 9,
      code: 'mobile_black',
      name: '手机号黑名单',
      scope: 'PROJECT',
      projectId: 1
    }]
  })
  routes.set('POST /api/rule/variable/preview', {
    varCode: 'riskScore',
    varSource: 'API',
    resolvedValue: 88,
    resolvedParams: { riskScore: 88 }
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

test('新建字段按业务取值方式引导并可在送审前预览外数结果', async ({
  page
}) => {
  const { requests, assertClean } = await installDistRoutes(page, {
    apiData: workflowFixtures()
  })

  await page.goto('http://tianshu.local/index.html#/variable?projectId=1')
  await page.getByRole('button', { name: '新建字段', exact: true }).click()

  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog.getByText('按业务取值方式完成字段配置', { exact: true }))
    .toBeVisible()
  await expect(dialog.getByText('3 个可用', { exact: true })).toHaveCount(0)
  await expect(dialog.getByText('1 个可用', { exact: true })).toHaveCount(3)
  await dialog.getByRole('button', { name: /外数接口/ }).click()
  await expect(dialog.getByText('保存前验证取值', { exact: true })).toBeVisible()

  await dialog.locator('.el-form-item').filter({ hasText: '字段编码' })
    .locator('input').fill('riskScore')
  await dialog.locator('.el-form-item').filter({ hasText: '字段名称' })
    .locator('input').fill('风险分')
  await dialog.locator('.el-form-item').filter({ hasText: '接口配置' })
    .locator('.el-select').click()
  await page.getByText('征信查询 / credit_query', { exact: true }).last().click()

  await expect(dialog.getByText('3 / 4 已就绪', { exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '预览取值', exact: true }).click()
  await expect(dialog.getByText('4 / 4 已就绪', { exact: true })).toBeVisible()
  await expect(dialog.locator('.draft-preview-result')).toContainText('88')
  await expect(dialog.getByRole('button', {
    name: '生成审批草稿', exact: true
  })).toBeVisible()
  expect(requests.some(request =>
    request.method === 'POST' &&
    new URL(request.url).pathname === '/api/rule/variable/preview'
  )).toBe(true)
  assertClean()
})

test('数据库只读查询按 SQL 占位符引导填写参数并区分结果状态', async ({
  page
}) => {
  let queryPayload
  const fixtures = workflowFixtures()
  fixtures.set('POST /api/rule/database/31/query', async ({ request }) => {
    queryPayload = request.postDataJSON()
    return [{ score: queryPayload.params[0] }]
  })
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })

  await page.goto('http://tianshu.local/index.html#/database')
  const datasourceRow = page.getByRole('row').filter({ hasText: 'risk_mysql' })
  await datasourceRow.getByRole('button', { name: '查询', exact: true }).click()

  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog.getByText('只读查询', { exact: true })).toBeVisible()
  const editor = dialog.locator('.monaco-editor-container')
  await editor.click()
  await page.keyboard.insertText('SELECT ? AS score')
  await expect(dialog.getByText('已识别 1 个有效 ? 占位符', { exact: true }))
    .toBeVisible()

  await dialog.locator('.query-param-type').click()
  await page.getByRole('option', { name: '数值', exact: true }).last().click()
  await dialog.getByLabel('参数 1 值').fill('88')
  await dialog.getByRole('button', { name: '执行查询', exact: true }).click()

  await expect(dialog.getByText('查询成功', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('cell', { name: '88', exact: true })).toBeVisible()
  expect(queryPayload).toEqual({
    sql: 'SELECT ? AS score',
    params: [88],
    maxRows: 100
  })
  assertClean()
})

test('模型测试明确参数来源、降级原因并只发送当前表单参数', async ({
  page
}) => {
  let executePayload
  const fixtures = workflowFixtures()
  fixtures.set('POST /api/rule/test-schema', {
    inputs: [{
      refId: 1,
      refType: 'VARIABLE',
      scriptName: 'age',
      label: '年龄',
      valueType: 'INTEGER'
    }],
    sampleParams: { age: 28 }
  })
  fixtures.set('/api/rule/model/41', {
    ...fixtures.get('/api/rule/model/41'),
    modelConfig: '{invalid json'
  })
  fixtures.set('POST /api/rule/model/execute/41', async ({ request }) => {
    executePayload = request.postDataJSON()
    return {
      success: true,
      outputs: { riskScore: executePayload.age * 2 },
      executeTimeMs: 12
    }
  })
  const { assertClean } = await installDistRoutes(page, { apiData: fixtures })

  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('http://tianshu.local/index.html#/model/41')
  await page.getByRole('button', { name: '模型测试', exact: true }).click()

  const dialog = page.getByRole('dialog', { name: '模型测试' })
  await expect(dialog.getByText('测试配置已降级加载', { exact: true }))
    .toBeVisible()
  await expect(dialog.getByText(/参数来源：测试 Schema 样例/)).toBeVisible()
  await expect(dialog.getByText(/模型配置样例解析失败/)).toBeVisible()

  const ageField = dialog.locator('.test-field-cell').filter({ hasText: '年龄' })
  await ageField.locator('input').fill('35')
  await dialog.getByRole('button', { name: '执行测试', exact: true }).click()

  await expect(dialog.getByText('执行成功', { exact: true })).toBeVisible()
  await expect(dialog.locator('pre')).toContainText('70')
  expect(executePayload).toEqual({ age: 35 })
  const dialogBox = await dialog.boundingBox()
  const viewport = page.viewportSize()
  expect(dialogBox.x).toBeGreaterThanOrEqual(0)
  expect(dialogBox.y).toBeGreaterThanOrEqual(0)
  expect(dialogBox.x + dialogBox.width).toBeLessThanOrEqual(viewport.width)
  expect(dialogBox.y + dialogBox.height).toBeLessThanOrEqual(viewport.height)
  assertClean()
})
