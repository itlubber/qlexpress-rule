import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'
import LineageGraph from '@/views/lineage/LineageGraph.vue'
import * as lineageApi from '@/api/lineage'



function graphResponse() {
  return {
    startNode: {
      id: 'VARIABLE:1', refId: 1, type: 'VARIABLE', code: 'score', label: '评分',
      hasUpstream: true, hasDownstream: true
    },
    nodes: [
      { id: 'DATASOURCE:8', refId: 8, type: 'DATASOURCE', code: 'credit', label: '征信源', hasUpstream: true, hasDownstream: true },
      { id: 'API:7', refId: 7, type: 'API', code: 'score_api', label: '评分API', hasUpstream: true, hasDownstream: true },
      { id: 'VARIABLE:1', refId: 1, type: 'VARIABLE', code: 'score', label: '评分', hasUpstream: true, hasDownstream: true },
      { id: 'RULE:9', refId: 9, type: 'RULE', code: 'approve', label: '审批规则', hasUpstream: true, hasDownstream: true },
      { id: 'VARIABLE:2', refId: 2, type: 'VARIABLE', code: 'result', label: '结果', hasUpstream: true, hasDownstream: true }
    ],
    edges: [
      { from: 'DATASOURCE:8', to: 'API:7', label: '包含API' },
      { from: 'API:7', to: 'VARIABLE:1', label: '接口取数' },
      { from: 'VARIABLE:1', to: 'RULE:9', label: '规则输入' },
      { from: 'RULE:9', to: 'VARIABLE:2', label: '规则输出' }
    ]
  }
}

function mountPage() {
  lineageApi.listLineageOptions.mockResolvedValue({
    data: [{ type: 'VARIABLE', id: 1, displayName: '风险分 (riskScore)' }]
  })
  return shallowMount(LineageGraph, {
    mocks: {
      $message: { warning: vi.fn(), error: vi.fn() }
    },
    stubs: {
      'el-form': true,
      'el-form-item': true,
      'el-select': true,
      'el-option': true,
      'el-radio-group': true,
      'el-radio-button': true,
      'el-button': true
    }
  })
}

afterEach(() => vi.clearAllMocks())

describe('LineageGraph', () => {
  test('created 后按默认变量类型加载起点选项', async () => {
    const wrapper = mountPage()
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(lineageApi.listLineageOptions).toHaveBeenCalledWith({ nodeType: 'VARIABLE', keyword: '' })
    expect(wrapper.vm.options[0].displayName).toBe('风险分 (riskScore)')
    wrapper.unmount()
  })

  test('首次生成在中间节点两侧展示上下游各两跳', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })

    await wrapper.vm.loadGraph()

    expect(lineageApi.getLineageGraph).toHaveBeenCalledWith({
      nodeType: 'VARIABLE', nodeId: 1, direction: 'ALL', maxDepth: 2
    })
    expect(wrapper.vm.upstreamRoots[0].node.id).toBe('API:7')
    expect(wrapper.vm.upstreamRoots[0].children[0].node.id).toBe('DATASOURCE:8')
    expect(wrapper.vm.downstreamRoots[0].node.id).toBe('RULE:9')
    expect(wrapper.vm.downstreamRoots[0].children[0].node.id).toBe('VARIABLE:2')
    expect(wrapper.vm.visibleBranches).toHaveLength(4)

    const current = wrapper.vm.mindMapLayout.positions.CURRENT
    const upstream = wrapper.vm.mindMapLayout.positions[wrapper.vm.upstreamRoots[0].instanceId]
    const downstream = wrapper.vm.mindMapLayout.positions[wrapper.vm.downstreamRoots[0].instanceId]
    expect(upstream.left).toBeLessThan(current.left)
    expect(downstream.left).toBeGreaterThan(current.left)
    expect(wrapper.vm.edgeLines[0].toId).toBe('CURRENT')
    expect(wrapper.vm.edgeLines[2].fromId).toBe('CURRENT')
    wrapper.unmount()
  })

  test('首次生成后将血缘图适配到可视区域', async () => {
    const wrapper = mountPage()
    const graphWrap = wrapper.find('.graph-wrap').element
    Object.defineProperty(graphWrap, 'clientWidth', { value: 800 })
    Object.defineProperty(graphWrap, 'clientHeight', { value: 440 })
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })

    await wrapper.vm.loadGraph()
    await nextTick()

    expect(wrapper.vm.viewport.scale).toBeLessThanOrEqual(1)
    expect(wrapper.vm.viewport.x).toBeGreaterThanOrEqual(0)
    expect(wrapper.vm.viewport.y).toBeGreaterThanOrEqual(0)
    wrapper.unmount()
  })

  test('支持以画布中心缩放并限制缩放范围', () => {
    const wrapper = mountPage()
    const graphWrap = wrapper.find('.graph-wrap').element
    Object.defineProperty(graphWrap, 'clientWidth', { value: 1000 })
    Object.defineProperty(graphWrap, 'clientHeight', { value: 500 })
    graphWrap.getBoundingClientRect = () => ({ left: 0, top: 0, width: 1000, height: 500 })

    wrapper.vm.viewport = { x: 100, y: 50, scale: 1 }
    wrapper.vm.setZoom(1.25)

    expect(wrapper.vm.viewport.scale).toBe(1.25)
    expect(wrapper.vm.viewport.x).toBe(0)
    expect(wrapper.vm.viewport.y).toBe(0)

    wrapper.vm.setZoom(99)
    expect(wrapper.vm.viewport.scale).toBe(2)
    wrapper.vm.setZoom(0)
    expect(wrapper.vm.viewport.scale).toBe(0.4)
    wrapper.unmount()
  })

  test('节点拖动只覆盖当前组件实例坐标并同步更新连线', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()
    const branch = wrapper.vm.downstreamRoots[0]
    const instanceId = branch.instanceId
    const originalPosition = wrapper.vm.nodePosition(instanceId)
    const originalPath = wrapper.vm.edgeLines.find(edge => edge.toId === instanceId).path

    wrapper.vm.beginNodeDrag({ clientX: 200, clientY: 160, currentTarget: { setPointerCapture: vi.fn() } }, instanceId)
    wrapper.vm.onPointerMove({ clientX: 264, clientY: 200 })
    wrapper.vm.endPointerInteraction()

    expect(wrapper.vm.positionOverrides[instanceId]).toEqual({
      left: originalPosition.left + 64 / wrapper.vm.viewport.scale,
      top: originalPosition.top + 40 / wrapper.vm.viewport.scale
    })
    expect(wrapper.vm.edgeLines.find(edge => edge.toId === instanceId).path).not.toBe(originalPath)

    const anotherWrapper = mountPage()
    expect(anotherWrapper.vm.positionOverrides).toEqual({})
    anotherWrapper.unmount()
    wrapper.unmount()
  })

  test('可拖动画布且一键最佳分布会清除手工坐标并重新适配', async () => {
    const wrapper = mountPage()
    const graphWrap = wrapper.find('.graph-wrap').element
    Object.defineProperty(graphWrap, 'clientWidth', { value: 1000 })
    Object.defineProperty(graphWrap, 'clientHeight', { value: 500 })
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()

    const originalViewport = { ...wrapper.vm.viewport }
    wrapper.vm.beginCanvasPan({ clientX: 100, clientY: 100, currentTarget: { setPointerCapture: vi.fn() } })
    wrapper.vm.onPointerMove({ clientX: 145, clientY: 125 })
    wrapper.vm.endPointerInteraction()
    expect(wrapper.vm.viewport.x).toBe(originalViewport.x + 45)
    expect(wrapper.vm.viewport.y).toBe(originalViewport.y + 25)

    wrapper.vm.positionOverrides = { CURRENT: { left: 16, top: 24 } }
    wrapper.vm.resetToBestLayout()
    expect(wrapper.vm.positionOverrides).toEqual({})
    expect(wrapper.vm.viewport.scale).toBeLessThanOrEqual(1)
    expect(wrapper.find('[aria-label="一键回到最佳分布"]').exists()).toBe(true)
    wrapper.unmount()
  })

  test('第二跳节点按需展开并在收起后复用缓存', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()
    const branch = wrapper.vm.upstreamRoots[0].children[0]
    lineageApi.getLineageGraph.mockResolvedValueOnce({
      data: {
        startNode: graphResponse().nodes[0],
        nodes: [
          graphResponse().nodes[0],
          { id: 'PROJECT:5', refId: 5, type: 'PROJECT', code: 'global', label: '全局项目', hasUpstream: false, hasDownstream: true }
        ],
        edges: [{ from: 'PROJECT:5', to: 'DATASOURCE:8', label: '项目包含' }]
      }
    })

    await wrapper.vm.toggleBranch(branch)

    expect(lineageApi.getLineageGraph).toHaveBeenLastCalledWith({
      nodeType: 'DATASOURCE', nodeId: 8, direction: 'UPSTREAM', maxDepth: 1
    })
    expect(branch.children[0].node.id).toBe('PROJECT:5')
    expect(branch.expanded).toBe(true)

    await wrapper.vm.toggleBranch(branch)
    expect(branch.expanded).toBe(false)
    await wrapper.vm.toggleBranch(branch)
    expect(branch.expanded).toBe(true)
    expect(lineageApi.getLineageGraph).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  test('收起父节点会隐藏整条后代分支', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()

    await wrapper.vm.toggleBranch(wrapper.vm.upstreamRoots[0])

    expect(wrapper.vm.visibleBranches.map(item => item.branch.node.id)).toEqual([
      'API:7', 'RULE:9', 'VARIABLE:2'
    ])
    wrapper.unmount()
  })

  test('共享业务节点在不同路径生成独立展示实例', () => {
    const wrapper = mountPage()
    const data = graphResponse()
    data.nodes.splice(2, 0,
      { id: 'API:6', refId: 6, type: 'API', code: 'backup_api', label: '备用API', hasUpstream: true, hasDownstream: true })
    data.edges.splice(1, 0,
      { from: 'DATASOURCE:8', to: 'API:6', label: '包含API' },
      { from: 'API:6', to: 'VARIABLE:1', label: '接口取数' })

    const roots = wrapper.vm.buildBranches(data, 'UPSTREAM', 2)

    expect(roots).toHaveLength(2)
    expect(roots[0].children[0].node.id).toBe('DATASOURCE:8')
    expect(roots[1].children[0].node.id).toBe('DATASOURCE:8')
    expect(roots[0].children[0].instanceId).not.toBe(roots[1].children[0].instanceId)
    wrapper.unmount()
  })

  test('当前路径出现循环引用时生成不可继续展开的终止节点', async () => {
    const wrapper = mountPage()
    wrapper.vm.query.nodeId = 1
    lineageApi.getLineageGraph.mockResolvedValueOnce({ data: graphResponse() })
    await wrapper.vm.loadGraph()
    const branch = wrapper.vm.downstreamRoots[0].children[0]
    lineageApi.getLineageGraph.mockResolvedValueOnce({
      data: {
        startNode: graphResponse().nodes[4],
        nodes: [graphResponse().nodes[3], graphResponse().nodes[4]],
        edges: [{ from: 'VARIABLE:2', to: 'RULE:9', label: '规则输入' }]
      }
    })

    await wrapper.vm.toggleBranch(branch)

    expect(branch.children[0].node.id).toBe('RULE:9')
    expect(branch.children[0].cycle).toBe(true)
    expect(wrapper.vm.canToggle(branch.children[0])).toBe(false)
    wrapper.unmount()
  })

  test('提供两跳展开和静态分析边界说明', () => {
    const wrapper = mountPage()

    expect(wrapper.vm.lineageGuideCards.map(item => item.title)).toEqual([
      '选择起点',
      '两跳展开',
      '静态分析边界'
    ])
    expect(wrapper.vm.lineageGuideCards[1].text).toContain('两层')
    expect(wrapper.vm.lineageGuideCards[2].text).toContain('静态分析')
    wrapper.unmount()
  })
})
