import { flushPromises, mount } from '@test-utils'
import * as definitionApi from '@/api/definition'
import * as variableApi from '@/api/variable'
import * as dataObjectApi from '@/api/dataObject'
import * as functionApi from '@/api/function'
import * as modelApi from '@/api/model'
import * as ruleListApi from '@/api/ruleList'

vi.mock('@logicflow/core', () => {
  class NodeModel {
    getNodeStyle() {
      return {}
    }
    getConnectedSourceRules() {
      return []
    }
  }
  class LogicFlow {
    constructor() {
      this.extension = {
        miniMap: { show: vi.fn(), hide: vi.fn() },
        selectionSelect: { open: vi.fn(), close: vi.fn() },
      }
      this.graphModel = { edges: [] }
      this.graph = { nodes: [], edges: [] }
    }
    register() {}
    addMenuConfig() {}
    on() {}
    render(graph) {
      this.graph = graph || { nodes: [], edges: [] }
    }
    addNode(node) {
      this.graph.nodes.push(node)
    }
    setDefaultEdgeType() {}
    getGraphData() {
      return this.graph
    }
    getTransform() {
      return { SCALE_X: 1, TRANSLATE_X: 0, TRANSLATE_Y: 0 }
    }
    destroy() {}
  }
  return {
    default: LogicFlow,
    h: vi.fn(),
    CircleNode: class {},
    CircleNodeModel: NodeModel,
    RectNode: class {},
    RectNodeModel: NodeModel,
    DiamondNode: class {},
    DiamondNodeModel: NodeModel,
    SelectionSelect: class {},
    Menu: class {},
    Snapshot: class {},
    DynamicGroup: class {},
    MiniMap: class {},
    Dagre: class {},
  }
})

vi.unmock('@/components/rule/RuleDraftReadOnly.vue')

const [
  { default: ScriptEditor },
  { default: DecisionTable },
  { default: DecisionTree },
  { default: DecisionFlow },
  { default: RuleSet },
  { default: CrossTable },
  { default: Scorecard },
  { default: AdvancedCrossTable },
  { default: AdvancedScorecard },
] = await Promise.all([
  import('@/views/designer/ScriptEditor.vue'),
  import('@/views/designer/DecisionTable.vue'),
  import('@/views/designer/DecisionTree.vue'),
  import('@/views/designer/DecisionFlow.vue'),
  import('@/views/designer/RuleSet.vue'),
  import('@/views/designer/CrossTable.vue'),
  import('@/views/designer/Scorecard.vue'),
  import('@/views/designer/AdvancedCrossTable.vue'),
  import('@/views/designer/AdvancedScorecard.vue'),
])

const { default: ScriptPanel } = await vi.importActual(
  '@/components/common/ScriptPanel.vue'
)

const designers = [
  ['ScriptEditor', ScriptEditor],
  ['DecisionTable', DecisionTable],
  ['DecisionTree', DecisionTree],
  ['DecisionFlow', DecisionFlow],
  ['RuleSet', RuleSet],
  ['CrossTable', CrossTable],
  ['Scorecard', Scorecard],
  ['AdvancedCrossTable', AdvancedCrossTable],
  ['AdvancedScorecard', AdvancedScorecard],
]

const explicitRevisionSources = [
  {
    name: 'ScriptEditor',
    component: ScriptEditor,
    model: { script: "result = 'revision-script-41'" },
    assertLoaded: (vm) => expect(vm.script).toBe("result = 'revision-script-41'"),
  },
  {
    name: 'DecisionTable',
    component: DecisionTable,
    model: { sourceMarker: 'revision-table-41', rules: [] },
    assertLoaded: (vm) => expect(vm.model.sourceMarker).toBe('revision-table-41'),
  },
  {
    name: 'DecisionTree',
    component: DecisionTree,
    model: {
      defaultEdgeLineType: 'polyline',
      logicflow: {
        nodes: [
          {
            id: 'revision-tree-41',
            type: 'script-task',
            x: 160,
            y: 120,
            properties: { actionData: [], sourceMarker: 'revision-tree-41' },
          },
        ],
        edges: [],
      },
    },
    assertLoaded: (vm) =>
      expect(vm.lf.getGraphData().nodes[0].properties.sourceMarker).toBe(
        'revision-tree-41'
      ),
  },
  {
    name: 'DecisionFlow',
    component: DecisionFlow,
    model: {
      defaultEdgeLineType: 'polyline',
      logicflow: {
        nodes: [
          {
            id: 'revision-flow-41',
            type: 'script-task',
            x: 160,
            y: 120,
            properties: { actionData: [], sourceMarker: 'revision-flow-41' },
          },
        ],
        edges: [],
      },
    },
    assertLoaded: (vm) =>
      expect(vm.lf.getGraphData().nodes[0].properties.sourceMarker).toBe(
        'revision-flow-41'
      ),
  },
  {
    name: 'RuleSet',
    component: RuleSet,
    model: { sourceMarker: 'revision-ruleset-41', executionMode: 'SERIAL', rules: [] },
    assertLoaded: (vm) => expect(vm.model.sourceMarker).toBe('revision-ruleset-41'),
  },
  {
    name: 'CrossTable',
    component: CrossTable,
    model: {
      sourceMarker: 'revision-cross-41',
      rowDimensions: [],
      colDimensions: [],
      cells: [],
    },
    assertLoaded: (vm) => expect(vm.model.sourceMarker).toBe('revision-cross-41'),
  },
  {
    name: 'Scorecard',
    component: Scorecard,
    model: { sourceMarker: 'revision-score-41', scoreItems: [], thresholds: [] },
    assertLoaded: (vm) => expect(vm.model.sourceMarker).toBe('revision-score-41'),
  },
  {
    name: 'AdvancedCrossTable',
    component: AdvancedCrossTable,
    model: {
      sourceMarker: 'revision-advanced-cross-41',
      rowDimensions: [],
      colDimensions: [],
      cells: [],
    },
    assertLoaded: (vm) =>
      expect(vm.model.sourceMarker).toBe('revision-advanced-cross-41'),
  },
  {
    name: 'AdvancedScorecard',
    component: AdvancedScorecard,
    model: {
      sourceMarker: 'revision-advanced-score-41',
      initialScore: 0,
      dimensionGroups: [],
      thresholds: [],
    },
    assertLoaded: (vm) =>
      expect(vm.model.sourceMarker).toBe('revision-advanced-score-41'),
  },
]

function configureQueryApis() {
  definitionApi.getDefinition.mockResolvedValue({
    data: { id: 30, projectId: null, scope: 'PROJECT' },
  })
  definitionApi.listProjectDefinitions.mockResolvedValue({ data: [] })
  variableApi.listVariablesByProject.mockResolvedValue({ data: [] })
  dataObjectApi.getVariableTree.mockResolvedValue({ data: [] })
  functionApi.listAllFunctionsByProject.mockResolvedValue({ data: [] })
  modelApi.listAllModelsByProject.mockResolvedValue({ data: [] })
  ruleListApi.listLibraries.mockResolvedValue({ data: [] })
}

function mountDesigner(component, query = {}, options = {}) {
  return mount(component, {
    mocks: {
      $route: { params: { id: 30 }, query },
      $router: {
        push: vi.fn(),
        replace: vi.fn(),
        back: vi.fn(),
      },
      $alert: vi.fn(),
    },
    stubs: {
      MonacoEditor: true,
      DesignerTestDialog: true,
      OperandPicker: true,
      ConditionGroupEditor: true,
      ActionBlockEditor: true,
      EndNodeScopeDialog: true,
      FlowNodeAddMenu: true,
      GraphDesignerNavigator: true,
      ScriptPanel: true,
      ...(options.stubs || {}),
    },
  })
}

describe('九类设计器草稿保护', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    configureQueryApis()
  })

  test('可视化设计器从共享提示点击进入当前规则生命周期', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{
        id: 6,
        definitionId: 30,
        revisionNo: 2,
        state: 'DRAFT',
        lockVersion: 0,
        modelJson: JSON.stringify({ rules: [] }),
      }],
    })
    const wrapper = mountDesigner(DecisionTable, {}, {
      stubs: { ScriptPanel },
    })
    await flushPromises()

    await wrapper
      .get('[aria-label="前往规则生命周期审核发布"]')
      .trigger('click')

    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle' },
    })
    wrapper.unmount()
  })

  test('脚本设计器从自身提示点击进入当前规则生命周期', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{
        id: 6,
        definitionId: 30,
        revisionNo: 2,
        state: 'DRAFT',
        lockVersion: 0,
        modelJson: JSON.stringify({ script: '', scriptVarRefs: [] }),
      }],
    })
    const wrapper = mountDesigner(ScriptEditor)
    await flushPromises()

    await wrapper
      .get('[aria-label="前往规则生命周期审核发布"]')
      .trigger('click')

    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle' },
    })
    wrapper.unmount()
  })

  test.each(designers)('%s 在无 DRAFT 时只读展示且点击开始编辑后才创建草稿', async (
    _name,
    component
  ) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 5,
          definitionId: 30,
          revisionNo: 1,
          state: 'PUBLISHED',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.createDraftRevision.mockResolvedValueOnce({
      data: {
        id: 6,
        definitionId: 30,
        revisionNo: 2,
        state: 'DRAFT',
        lockVersion: 0,
        modelJson: '{}',
      },
    })

    const wrapper = mountDesigner(component)
    await flushPromises()

    expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(true)
    expect(wrapper.vm.canEditDraft).toBe(false)
    expect(wrapper.vm.draftRevision).toBeNull()
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 5, state: 'PUBLISHED' })

    await wrapper.findComponent({ name: 'RuleDraftReadOnly' }).vm.$emit('fork')
    await flushPromises()

    expect(definitionApi.createDraftRevision).toHaveBeenCalledWith(30, 5)
    expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(false)
    expect(wrapper.vm.canEditDraft).toBe(true)
    expect(wrapper.vm.draftRevision).toMatchObject({ id: 6, state: 'DRAFT' })
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    expect(definitionApi.ensureDraftRevision).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test.each(designers)('%s 在 DRAFT 就绪后解除只读遮罩', async (
    _name,
    component
  ) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })

    const wrapper = mountDesigner(component)
    await flushPromises()

    expect({
      overlay: wrapper.find('[data-testid="draft-read-only"]').exists(),
      canEditDraft: wrapper.vm.canEditDraft,
      draftRevision: wrapper.vm.draftRevision,
      draftGuardLoaded: wrapper.vm.draftGuardLoaded,
    }).toEqual({
      overlay: false,
      canEditDraft: true,
      draftRevision: expect.objectContaining({ id: 6, state: 'DRAFT' }),
      draftGuardLoaded: true,
    })
    wrapper.unmount()
  })

  test.each(explicitRevisionSources)(
    '$name 在没有治理修订时只读加载旧版内容且点击开始编辑后才创建草稿',
    async ({ component, model, assertLoaded }) => {
      definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
      definitionApi.getContent.mockResolvedValueOnce({
        data: {
          id: 18,
          definitionId: 30,
          modelJson: JSON.stringify(model),
        },
      })
      definitionApi.createDraftRevision.mockResolvedValueOnce({
        data: {
          id: 6,
          definitionId: 30,
          revisionNo: 1,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: JSON.stringify(model),
        },
      })

      const wrapper = mountDesigner(component)
      await flushPromises()

      expect(wrapper.vm.viewRevision).toMatchObject({
        id: 'legacy-content:30',
        state: 'LEGACY',
      })
      assertLoaded(wrapper.vm)
      expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
      expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(true)
      expect(wrapper.vm.canEditDraft).toBe(false)

      await wrapper.findComponent({ name: 'RuleDraftReadOnly' }).vm.$emit('fork')
      await flushPromises()

      expect(definitionApi.createDraftRevision).toHaveBeenCalledWith(30)
      expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(false)
      expect(wrapper.vm.canEditDraft).toBe(true)
      expect(definitionApi.saveContent).not.toHaveBeenCalled()
      wrapper.unmount()
    }
  )

  test.each(designers)('%s 将历史节点上下文和派生事件交给真实只读遮罩', async (
    _name,
    component
  ) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: {
        id: 41,
        definitionId: 30,
        revisionNo: 7,
        state: 'PUBLISHED',
        lockVersion: 3,
        modelJson: '{"sourceMarker":"revision-41"}',
      },
    })
    definitionApi.createDraftFromSource.mockResolvedValueOnce({
      data: {
        revision: {
          id: 52,
          definitionId: 30,
          revisionNo: 8,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{"sourceMarker":"revision-41"}',
        },
        compileSuccess: true,
        issues: [],
      },
    })

    const wrapper = mountDesigner(component, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await flushPromises()

    const readOnly = wrapper.findComponent({ name: 'RuleDraftReadOnly' })
    expect(readOnly.props()).toMatchObject({
      visible: true,
      loading: false,
      loadError: false,
      revisionLabel: '修订 7',
      revisionState: 'PUBLISHED',
      canFork: true,
    })

    await readOnly.vm.$emit('fork')
    await flushPromises()

    expect(definitionApi.createDraftFromSource).toHaveBeenCalledWith(30, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 52, state: 'DRAFT' })
    wrapper.unmount()
  })

  test.each(explicitRevisionSources)(
    '$name 从显式 REVISION 的 modelJson 加载自身可观测状态，而非当前 DRAFT',
    async ({ component, model, assertLoaded }) => {
      definitionApi.listRuleRevisions.mockResolvedValueOnce({
        data: [
          {
            id: 6,
            definitionId: 30,
            revisionNo: 2,
            state: 'DRAFT',
            lockVersion: 0,
            modelJson: JSON.stringify({ sourceMarker: 'draft-6' }),
          },
        ],
      })
      definitionApi.getRuleRevision.mockResolvedValueOnce({
        data: {
          id: 41,
          definitionId: 30,
          revisionNo: 7,
          state: 'PUBLISHED',
          lockVersion: 3,
          modelJson: JSON.stringify(model),
        },
      })

      const wrapper = mountDesigner(component, {
        sourceType: 'REVISION',
        sourceId: '41',
      })
      await flushPromises()

      expect(wrapper.vm.viewRevision).toMatchObject({ id: 41, state: 'PUBLISHED' })
      assertLoaded(wrapper.vm)
      wrapper.unmount()
    }
  )

  test.each(designers)('%s 历史只读时执行保存入口仍由共享守卫阻止', async (
    _name,
    component
  ) => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: {
        id: 41,
        definitionId: 30,
        revisionNo: 7,
        state: 'PUBLISHED',
        lockVersion: 3,
        modelJson: '{}',
      },
    })
    const wrapper = mountDesigner(component, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await flushPromises()

    await expect(wrapper.vm.handleSave()).rejects.toThrow('没有可编辑草稿')
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('DecisionTable 的结构编辑后编译入口不能绕过历史只读保存守卫', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: {
        id: 41,
        definitionId: 30,
        revisionNo: 7,
        state: 'PUBLISHED',
        lockVersion: 3,
        modelJson: JSON.stringify({ rules: [] }),
      },
    })
    const wrapper = mountDesigner(DecisionTable, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await flushPromises()

    wrapper.vm.addRule()
    expect(wrapper.vm.model.rules).toHaveLength(1)
    await expect(wrapper.vm.handleCompile()).rejects.toThrow('没有可编辑草稿')
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('精确来源加载失败后，脚本测试入口不能保存当前 DRAFT', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: JSON.stringify({ script: "result = 'draft-6'" }),
        },
      ],
    })
    definitionApi.getRuleRevision.mockRejectedValueOnce(new Error('修订 41 不存在'))
    const wrapper = mountDesigner(ScriptEditor, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await flushPromises()

    expect(wrapper.vm.draftGuardError).toBeInstanceOf(Error)
    await expect(wrapper.vm.handleTest()).rejects.toThrow('没有可编辑草稿')
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('精确来源加载中时，决策流结构编辑后的保存会等待守卫并最终被阻止', async () => {
    let resolveSource
    const sourcePromise = new Promise((resolve) => {
      resolveSource = resolve
    })
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 0,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.getRuleRevision.mockReturnValueOnce(sourcePromise)
    const wrapper = mountDesigner(DecisionFlow, {
      sourceType: 'REVISION',
      sourceId: '41',
    })
    await Promise.resolve()

    expect(wrapper.vm.draftGuardLoaded).toBe(false)
    wrapper.vm.addNode('script-task')
    const pendingSave = wrapper.vm.handleSave()
    await Promise.resolve()
    expect(definitionApi.saveContent).not.toHaveBeenCalled()

    resolveSource({
      data: {
        id: 41,
        definitionId: 30,
        revisionNo: 7,
        state: 'PUBLISHED',
        lockVersion: 3,
        modelJson: '{}',
      },
    })
    await expect(pendingSave).rejects.toThrow('没有可编辑草稿')
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('ScriptEditor 从非空 DRAFT 加载稳定引用并在编译失败后推进锁且不打开测试', async () => {
    const scriptModel = {
      script: 'credit_score = profile.score;',
      scriptVarRefs: [
        {
          refCode: 'profile.score',
          varId: 88,
          refType: 'DATA_OBJECT',
        },
      ],
    }
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
          modelJson: JSON.stringify(scriptModel),
        },
      ],
    })
    definitionApi.saveContent.mockResolvedValueOnce({
      data: {
        revision: {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 5,
          modelJson: JSON.stringify(scriptModel),
        },
        compileSuccess: false,
        compileMessage: '脚本解析失败',
        issues: [{ code: 'QL_PARSE_ERROR', severity: 'ERROR' }],
      },
    })
    const wrapper = mountDesigner(ScriptEditor)
    await flushPromises()

    expect({
      script: wrapper.vm.script,
      scriptVarRefs: wrapper.vm.scriptVarRefs,
    }).toEqual(scriptModel)
    await wrapper.vm.handleTest()

    const payload = definitionApi.saveContent.mock.calls[0][0]
    expect({
      definitionId: payload.definitionId,
      revisionId: payload.revisionId,
      lockVersion: payload.lockVersion,
      savedModel: JSON.parse(payload.modelJson),
      nextLockVersion: wrapper.vm.draftRevision.lockVersion,
      issues: wrapper.vm.draftIssues,
      testVisible: wrapper.vm.testVisible,
    }).toEqual({
      definitionId: 30,
      revisionId: 6,
      lockVersion: 4,
      savedModel: scriptModel,
      nextLockVersion: 5,
      issues: [{ code: 'QL_PARSE_ERROR', severity: 'ERROR' }],
      testVisible: false,
    })
    wrapper.unmount()
  })

  test('DecisionFlow 从非空 DRAFT 加载图结构并按稳定引用原子保存', async () => {
    const flowModel = {
      defaultEdgeLineType: 'polyline',
      nodes: [
        {
          id: 'task_1',
          type: 'task',
          name: '评分任务',
          x: 240,
          y: 180,
          actionData: [],
          leftVarId: 81,
          leftRefType: 'VARIABLE',
        },
      ],
      edges: [],
      logicflow: {
        nodes: [
          {
            id: 'task_1',
            type: 'script-task',
            x: 240,
            y: 180,
            properties: {
              nodeName: '评分任务',
              actionData: [],
              leftVarId: 81,
              leftRefType: 'VARIABLE',
            },
          },
        ],
        edges: [],
      },
    }
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 7,
          definitionId: 30,
          revisionNo: 3,
          state: 'DRAFT',
          lockVersion: 2,
          modelJson: JSON.stringify(flowModel),
        },
      ],
    })
    definitionApi.saveContent.mockResolvedValueOnce({
      data: {
        revision: {
          id: 7,
          definitionId: 30,
          revisionNo: 3,
          state: 'DRAFT',
          lockVersion: 3,
        },
        compileSuccess: true,
        issues: [],
      },
    })
    const wrapper = mountDesigner(DecisionFlow)
    await flushPromises()

    expect(wrapper.vm.lf.getGraphData().nodes[0]).toMatchObject({
      id: 'task_1',
      properties: {
        leftVarId: 81,
        leftRefType: 'VARIABLE',
      },
    })
    await wrapper.vm.handleSave()

    const payload = definitionApi.saveContent.mock.calls[0][0]
    const savedModel = JSON.parse(payload.modelJson)
    expect({
      revisionId: payload.revisionId,
      lockVersion: payload.lockVersion,
      backendNode: savedModel.nodes[0],
      logicflowNode: savedModel.logicflow.nodes[0],
    }).toEqual({
      revisionId: 7,
      lockVersion: 2,
      backendNode: expect.objectContaining({
        id: 'task_1',
        leftVarId: 81,
        leftRefType: 'VARIABLE',
      }),
      logicflowNode: expect.objectContaining({
        id: 'task_1',
        properties: expect.objectContaining({
          leftVarId: 81,
          leftRefType: 'VARIABLE',
        }),
      }),
    })
    wrapper.unmount()
  })

  test('AdvancedCrossTable 从非空 DRAFT 保留维度 ID 与单元格结构', async () => {
    const referenceOperand = {
      kind: 'REFERENCE',
      value: 'customer.segment',
      code: 'customer.segment',
      label: '客户分群',
      valueType: 'STRING',
      refId: 71,
      refType: 'DATA_OBJECT',
      resolved: true,
    }
    const resultOperand = {
      kind: 'REFERENCE',
      value: 'decision.result',
      code: 'decision.result',
      label: '决策结果',
      valueType: 'NUMBER',
      refId: 73,
      refType: 'DATA_OBJECT',
      resolved: true,
    }
    const literal = {
      kind: 'LITERAL',
      value: 'A',
      valueType: 'STRING',
    }
    const advancedModel = {
      rowDimensions: [
        {
          varCode: 'customer.segment',
          varLabel: '客户分群',
          varType: 'STRING',
          _varId: 71,
          _refType: 'DATA_OBJECT',
          operand: referenceOperand,
          segments: [
            {
              label: 'A',
              operator: '==',
              value: 'A',
              valueOperand: literal,
            },
          ],
        },
      ],
      colDimensions: [
        {
          varCode: 'customer.level',
          varLabel: '客户等级',
          varType: 'STRING',
          _varId: 72,
          _refType: 'DATA_OBJECT',
          operand: {
            ...referenceOperand,
            value: 'customer.level',
            code: 'customer.level',
            label: '客户等级',
            refId: 72,
          },
          segments: [
            {
              label: 'VIP',
              operator: '==',
              value: 'VIP',
              valueOperand: {
                ...literal,
                value: 'VIP',
              },
            },
          ],
        },
      ],
      resultVar: {
        varCode: 'decision.result',
        varLabel: '决策结果',
        varType: 'NUMBER',
        _varId: 73,
        _refType: 'DATA_OBJECT',
        operand: resultOperand,
      },
      cells: [[{ kind: 'LITERAL', value: 100, valueType: 'NUMBER' }]],
    }
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 8,
          definitionId: 30,
          revisionNo: 4,
          state: 'DRAFT',
          lockVersion: 1,
          modelJson: JSON.stringify(advancedModel),
        },
      ],
    })
    const wrapper = mountDesigner(AdvancedCrossTable)
    await flushPromises()

    expect({
      rowVarId: wrapper.vm.model.rowDimensions[0]._varId,
      colVarId: wrapper.vm.model.colDimensions[0]._varId,
      resultVarId: wrapper.vm.model.resultVar._varId,
      cell: wrapper.vm.cellData[0][0],
    }).toEqual({
      rowVarId: 71,
      colVarId: 72,
      resultVarId: 73,
      cell: { kind: 'LITERAL', value: 100, valueType: 'NUMBER' },
    })
    wrapper.unmount()
  })

  test('RuleSet 回滚后重载服务端 DRAFT 模型与锁并用于下一次保存', async () => {
    const oldModel = {
      executionMode: 'SERIAL',
      rules: [{ code: 'OLD_RULE', enabled: true }],
    }
    const restoredModel = {
      executionMode: 'PARALLEL',
      rules: [{ code: 'RESTORED_RULE', enabled: true }],
    }
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({
        data: [
          {
            id: 6,
            definitionId: 30,
            revisionNo: 2,
            state: 'DRAFT',
            lockVersion: 4,
            modelJson: JSON.stringify(oldModel),
          },
        ],
      })
      .mockResolvedValueOnce({
        data: [
          {
            id: 6,
            definitionId: 30,
            revisionNo: 2,
            state: 'DRAFT',
            lockVersion: 5,
            modelJson: JSON.stringify(restoredModel),
          },
        ],
      })
    definitionApi.rollbackVersion.mockResolvedValueOnce({})
    definitionApi.listVersions.mockResolvedValueOnce({ data: [] })
    definitionApi.saveContent.mockResolvedValueOnce({
      data: {
        revision: {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 6,
          modelJson: JSON.stringify(restoredModel),
        },
        compileSuccess: true,
        issues: [],
      },
    })
    const wrapper = mountDesigner(RuleSet)
    await flushPromises()

    expect(wrapper.vm.model.rules[0].code).toBe('OLD_RULE')
    await wrapper.vm.rollbackDraft({ version: 1 })
    await wrapper.vm.handleSave()

    const payload = definitionApi.saveContent.mock.calls[0][0]
    expect({
      revisionQueries: definitionApi.listRuleRevisions.mock.calls.length,
      modelCode: wrapper.vm.model.rules[0].code,
      lockVersion: payload.lockVersion,
      savedModelCode: JSON.parse(payload.modelJson).rules[0].code,
    }).toEqual({
      revisionQueries: 2,
      modelCode: 'RESTORED_RULE',
      lockVersion: 5,
      savedModelCode: 'RESTORED_RULE',
    })
    wrapper.unmount()
  })
})
