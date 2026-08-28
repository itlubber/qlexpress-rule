const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDesignerApiData } = require('./support/designerFixtures.cjs')

const designers = [
  {
    name: '决策表',
    path: '/designer/table/101',
    title: '决策表配置',
    action: '添加行',
    itemSelector: '.dt-rule-card',
    loadsVariables: true
  },
  {
    name: '决策树',
    path: '/designer/tree/102',
    title: '决策树设计器',
    action: '执行动作',
    itemSelector: '.tree-canvas .lf-node:not(.lf-mini-map .lf-node)',
    loadsVariables: true
  },
  {
    name: '决策流',
    path: '/designer/flow/103',
    title: '决策流设计器',
    action: '执行动作',
    itemSelector: '.flow-canvas .lf-node:not(.lf-mini-map .lf-node)',
    loadsVariables: true
  },
  {
    name: '规则集',
    path: '/designer/ruleset/104',
    title: '规则集配置',
    action: '添加规则',
    itemSelector: '.rs-rule-card',
    loadsVariables: true
  },
  {
    name: '交叉表',
    path: '/designer/cross/105',
    title: '交叉表设计器',
    action: '添加行',
    itemSelector: '.ct-matrix tbody tr'
  },
  {
    name: '评分卡',
    path: '/designer/score/106',
    title: '评分卡设计器',
    action: '添加评分项',
    itemSelector: '.score-item-card'
  },
  {
    name: '复杂交叉表',
    path: '/designer/cross-adv/107',
    title: '复杂交叉表设计器',
    action: '添加行维度',
    itemSelector: '.dim-config-card',
    loadsVariables: true
  },
  {
    name: '复杂评分卡',
    path: '/designer/score-adv/108',
    title: '复杂评分卡设计器',
    action: '添加维度组',
    itemSelector: '.asc-group',
    loadsVariables: true
  }
]

async function expectDesignerShell(page, title, saveButtonName) {
  const main = page.getByRole('main')
  await expect(main.getByText(title, { exact: true })).toBeVisible()
  const save = main.getByRole('button', { name: saveButtonName })
  await expect(save).toBeVisible()
  await expect(save).toBeInViewport({ ratio: 0.6 })
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth
  }))
  expect(metrics.document).toBeLessThanOrEqual(metrics.viewport + 1)
  expect(metrics.body).toBeLessThanOrEqual(metrics.viewport + 1)
}

function createConnectedGraphModel() {
  const logicflow = {
    nodes: [
      {
        id: 'start',
        type: 'start-event',
        x: 260,
        y: 300,
        properties: { nodeName: '开始', nodeCode: 'START', nodeDesc: '流程开始节点' }
      },
      {
        id: 'task',
        type: 'script-task',
        x: 560,
        y: 300,
        properties: { nodeName: '执行动作', nodeCode: 'TASK', actionData: [] }
      }
    ],
    edges: [
      {
        id: 'edge-1',
        type: 'polyline',
        sourceNodeId: 'start',
        targetNodeId: 'task',
        sourceAnchorId: 'start_1',
        targetAnchorId: 'task_3',
        properties: { conditionName: '已有分支', priority: 10 }
      }
    ]
  }
  return {
    nodes: [
      { id: 'start', type: 'start', name: '开始', x: 260, y: 300, actionData: [] },
      { id: 'task', type: 'task', name: '执行动作', x: 560, y: 300, actionData: [] }
    ],
    edges: [
      {
        id: 'edge-1',
        source: 'start',
        target: 'task',
        name: '已有分支',
        conditionExpression: '',
        priority: 10
      }
    ],
    defaultEdgeLineType: 'polyline',
    logicflow
  }
}

for (const designer of designers) {
  test(`${designer.name}设计器可加载变量、显示工具栏并新增配置项`, async ({ page }) => {
    const pageErrors = []
    const consoleErrors = []
    page.on('pageerror', error => pageErrors.push(error.message))
    page.on('console', message => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })
    await page.setViewportSize({ width: 1440, height: 900 })
    const { requests, assertClean } = await installDistRoutes(page, {
      apiData: createDesignerApiData()
    })
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)

    await expectDesignerShell(page, designer.title, '保存并检查')
    await expect(page.getByRole('button', { name: '仅保存草稿' })).toBeVisible()
    await expect(page.getByRole('button', { name: '进入测试' })).toBeDisabled()
    if (designer.loadsVariables) {
      await expect.poll(() => requests.some(request =>
        new URL(request.url).pathname === '/api/rule/variable/project/1'
      )).toBe(true)
    }
    const items = page.locator(designer.itemSelector)
    const before = await items.count()
    await page.getByRole('button', { name: designer.action, exact: true }).first().click()
    await expect(items).toHaveCount(before + 1)
    expect(pageErrors).toEqual([])
    expect(consoleErrors).toEqual([])
    assertClean()
  })
}

for (const designer of designers.filter(item => ['决策树', '决策流'].includes(item.name))) {
  test(`${designer.name}节点拖动后吸附到 20px 网格`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    const { assertClean } = await installDistRoutes(page, {
      apiData: createDesignerApiData()
    })
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)

    await expect(page.getByRole('button', { name: '开始', exact: true })).toBeDisabled()
    await page.getByRole('button', { name: '执行动作', exact: true }).click()
    const canvasClass = designer.name === '决策树' ? '.tree-canvas' : '.flow-canvas'
    const node = page.locator(
      `${canvasClass} .lf-node:not(.lf-mini-map .lf-node)`
    ).last()
    await expect(node).toBeVisible()
    const before = await node.boundingBox()
    expect(before).toBeTruthy()

    await page.mouse.move(before.x + before.width / 2, before.y + before.height / 2)
    await page.mouse.down()
    await page.mouse.move(
      before.x + before.width / 2 + 47,
      before.y + before.height / 2 + 43,
      { steps: 10 }
    )
    await page.mouse.up()

    const after = await node.boundingBox()
    expect(after).toBeTruthy()
    const deltaX = Math.round(after.x - before.x)
    const deltaY = Math.round(after.y - before.y)
    expect(Math.abs(deltaX) + Math.abs(deltaY)).toBeGreaterThan(0)
    expect(deltaX % 20).toBe(0)
    expect(deltaY % 20).toBe(0)
    assertClean()
  })
}

for (const designer of designers.filter(item => ['决策树', '决策流'].includes(item.name))) {
  test(`${designer.name}可调整已有连线的目标锚点并持久化`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    const definitionId = designer.name === '决策树' ? 102 : 103
    const canvasClass = designer.name === '决策树' ? '.tree-canvas' : '.flow-canvas'
    let modelJson = JSON.stringify(createConnectedGraphModel())
    let savedPayload = null
    const apiData = createDesignerApiData()
    apiData.set(`/api/rule/definition/content/${definitionId}`, () => ({
      definitionId,
      modelJson,
      scriptMode: 'visual'
    }))
    apiData.set(`/api/rule/definition/${definitionId}/revisions`, () => [
      {
        id: 2000 + definitionId,
        definitionId,
        revisionNo: 1,
        state: 'DRAFT',
        lockVersion: 0,
        modelJson,
        createTime: '2026-07-23 12:00:00'
      }
    ])
    apiData.set('POST /api/rule/definition/save', async ({ request }) => {
      savedPayload = JSON.parse(request.postData())
      modelJson = savedPayload.modelJson
      return {
        revision: {
          id: 2000 + definitionId,
          definitionId,
          revisionNo: 1,
          state: 'DRAFT',
          lockVersion: 1,
          modelJson: savedPayload.modelJson
        },
        issues: []
      }
    })
    const { assertClean } = await installDistRoutes(page, { apiData })
    await page.goto(`http://tianshu.local/index.html#${designer.path}`)

    const edge = page.locator(`${canvasClass} .lf-edge:not(.lf-mini-map .lf-edge)`).first()
    await expect(edge).toBeVisible()
    await edge.locator('.lf-edge-append').click({ force: true })

    const adjustPoints = page.locator(`${canvasClass} .lf-edge-adjust-point`)
    await expect(adjustPoints).toHaveCount(2)
    await expect(page.getByText('拖动连线两端的圆点，可切换节点的连接锚点')).toBeVisible()
    const targetNode = page.locator(`${canvasClass} .lf-node:not(.lf-mini-map .lf-node)`).filter({ hasText: '执行动作' })
    const targetBox = await targetNode.boundingBox()
    const targetHandleBox = await adjustPoints.nth(1).boundingBox()
    expect(targetBox).toBeTruthy()
    expect(targetHandleBox).toBeTruthy()

    await page.mouse.move(
      targetHandleBox.x + targetHandleBox.width / 2,
      targetHandleBox.y + targetHandleBox.height / 2
    )
    await page.mouse.down()
    await page.mouse.move(targetBox.x + targetBox.width / 2, targetBox.y, { steps: 10 })
    await page.mouse.up()

    await page.getByRole('button', { name: '仅保存草稿' }).click()
    await expect.poll(() => savedPayload).not.toBeNull()
    const savedModel = JSON.parse(savedPayload.modelJson)
    expect(savedModel.logicflow.edges[0]).toMatchObject({
      id: 'edge-1',
      sourceAnchorId: 'start_1',
      targetAnchorId: 'task_0',
      properties: { conditionName: '已有分支', priority: 10 }
    })

    await page.reload()
    const reloadedEdge = page.locator(`${canvasClass} .lf-edge:not(.lf-mini-map .lf-edge)`).first()
    await expect(reloadedEdge).toBeVisible()
    await reloadedEdge.locator('.lf-edge-append path').first().click({ force: true })
    const reloadedTargetHandle = page.locator(`${canvasClass} .lf-edge-adjust-point`).nth(1)
    await expect(reloadedTargetHandle).toHaveAttribute('cx', '560')
    await expect(reloadedTargetHandle).toHaveAttribute('cy', '279')
    assertClean()
  })
}

test('结束节点范围弹窗完整容纳两项说明且不发生内容重叠', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })
  await page.goto('http://tianshu.local/index.html#/designer/tree/102')

  await page.getByRole('button', { name: '结束', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '添加结束节点' })
  await expect(dialog).toBeVisible()
  const metrics = await dialog.locator('.scope-option').evaluateAll(options =>
    options.map(option => {
      const optionRect = option.getBoundingClientRect()
      const descriptionRect = option.querySelector('.scope-description').getBoundingClientRect()
      return {
        optionHeight: optionRect.height,
        contentFits: descriptionRect.bottom <= optionRect.bottom + 1
      }
    })
  )

  expect(metrics).toHaveLength(2)
  metrics.forEach(metric => {
    expect(metric.optionHeight).toBeGreaterThanOrEqual(72)
    expect(metric.contentFits).toBe(true)
  })
  const bodyOverflow = await dialog.locator('.el-dialog__body').evaluate(body => ({
    clientHeight: body.clientHeight,
    scrollHeight: body.scrollHeight
  }))
  expect(bodyOverflow.scrollHeight).toBeLessThanOrEqual(bodyOverflow.clientHeight + 1)
  assertClean()
})

test('判断节点只在菱形内部显示一次名称', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })
  await page.goto('http://tianshu.local/index.html#/designer/tree/102')

  await page.getByRole('button', { name: '条件判断', exact: true }).click()
  const gateway = page.locator('.lf-node').filter({ hasText: '条件判断' })
  await expect(gateway).toBeVisible()
  const labels = (await gateway.locator('text').allTextContents())
    .map(text => text.trim())
    .filter(Boolean)

  expect(labels).toEqual(['条件判断'])
  assertClean()
})

test('QL 脚本编辑器可加载变量、插入字段并保持工具栏可操作', async ({ page }) => {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDesignerApiData()
  })
  await page.goto('http://tianshu.local/index.html#/designer/script/109')

  await expectDesignerShell(page, 'QL脚本编辑器', '保存并检查')
  await expect(page.getByRole('button', { name: '仅保存草稿' })).toBeVisible()
  await expect(page.getByRole('button', { name: '进入测试' })).toBeDisabled()
  const variable = page.locator('.se-var-item').filter({ hasText: 'age' }).first()
  await expect(variable).toBeVisible()
  await variable.dblclick()
  await expect(page.locator('.monaco-editor-container')).toBeVisible()
  await expect.poll(async () => page.locator('.view-lines').textContent()).toContain('age')
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
  assertClean()
})
