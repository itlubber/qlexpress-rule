import { shallowMount } from '@test-utils'

import request from '@/api/request'
import * as definitionApi from '@/api/definition'
import * as projectApi from '@/api/project'
import ProjectDetail from '@/views/project/ProjectDetail.vue'

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountPage() {
  projectApi.getProject.mockResolvedValue({
    data: { id: 9, projectCode: 'risk', projectName: '风控项目' },
  })
  projectApi.getProjectWorkbench.mockResolvedValue({
    data: {
      metrics: { fieldCount: 3, publishedRuleCount: 1 },
      checks: [
        {
          code: 'FIELD',
          status: 'READY',
          title: '定义业务字段',
          actionCode: 'CONFIGURE_FIELDS',
          actionLabel: '查看字段',
        },
      ],
    },
  })
  request.mockResolvedValue({
    data: { records: [], total: 0 },
  })
  const router = { push: vi.fn(), replace: vi.fn() }
  const store = {
    state: { currentProject: null },
    commit: vi.fn(),
  }
  const wrapper = shallowMount(ProjectDetail, {
    mocks: {
      $route: { params: { id: 9 }, query: {} },
      $router: router,
      $store: store,
      $confirm: vi.fn().mockResolvedValue('confirm'),
      $message: {
        success: vi.fn(),
        warning: vi.fn(),
        error: vi.fn(),
      },
    },
  })
  await flushPromises()
  request.mockClear()
  router.push.mockClear()
  return { wrapper, router, store }
}

describe('ProjectDetail 规则生命周期入口', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  test('项目工作台和项目规则使用独立 Tab', async () => {
    const { wrapper } = await mountPage()

    expect(wrapper.vm.activeProjectTab).toBe('workbench')
    wrapper.vm.activeProjectTab = 'rules'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.activeProjectTab).toBe('rules')
    wrapper.unmount()
  })

  test('项目规则设计入口复用统一命名路由', async () => {
    const { wrapper, router } = await mountPage()

    wrapper.vm.go({ id: 30, modelType: 'DECISION_FLOW' })

    expect(router.push).toHaveBeenCalledWith({
      name: 'DecisionFlow',
      params: { id: '30' },
    })
    wrapper.unmount()
  })

  test('发布入口只聚焦生命周期且不调用旧发布接口', async () => {
    const { wrapper, router } = await mountPage()

    await wrapper.vm.pub({ id: 30, status: 0 })

    expect(request).not.toHaveBeenCalled()
    expect(router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle' },
    })
    wrapper.unmount()
  })

  test('下线入口只聚焦生命周期且不自动执行下线', async () => {
    const { wrapper, router } = await mountPage()

    await wrapper.vm.unpub({ id: 30, status: 1 })

    expect(request).not.toHaveBeenCalled()
    expect(router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle', action: 'offline' },
    })
    wrapper.unmount()
  })

  test('加载项目工作台并写入稳定项目上下文', async () => {
    const { wrapper, store } = await mountPage()

    expect(projectApi.getProjectWorkbench).toHaveBeenCalledWith(9)
    expect(wrapper.vm.workbench.metrics.fieldCount).toBe(3)
    expect(store.commit).toHaveBeenCalledWith(
      'SET_CURRENT_PROJECT',
      expect.objectContaining({ id: 9, projectCode: 'risk' })
    )
    wrapper.unmount()
  })

  test('工作台动作通过项目 ID 进入对应模块', async () => {
    const { wrapper, router } = await mountPage()

    wrapper.vm.openWorkbenchAction({ actionCode: 'CONFIGURE_FIELDS' })

    expect(router.push).toHaveBeenCalledWith({
      path: '/variable',
      query: { projectId: 9 },
    })
    wrapper.unmount()
  })

  test('数据库数据源异常会进入数据库管理而不是外数管理', async () => {
    const { wrapper, router } = await mountPage()

    wrapper.vm.openWorkbenchAction({
      actionCode: 'CONFIGURE_DATABASE_SOURCES',
    })

    expect(router.push).toHaveBeenCalledWith({
      path: '/database',
      query: { projectId: 9 },
    })
    wrapper.unmount()
  })

  test('添加全局规则只生成关联审批草稿并进入审批详情', async () => {
    definitionApi.createProjectBinding.mockResolvedValueOnce({
      data: { id: 81, resourceType: 'RULE_PROJECT_BINDING' },
    })
    const { wrapper, router } = await mountPage()

    await wrapper.vm.addGlobalToProject({ id: 30, scope: 'GLOBAL' })

    expect(definitionApi.createProjectBinding).toHaveBeenCalledWith(30, 9)
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith(
      '已生成加入项目审批草稿'
    )
    expect(router.push).toHaveBeenCalledWith('/approval/81')
    expect(request).not.toHaveBeenCalledWith(
      expect.objectContaining({ url: '/rule/definition/add-global-to-project' })
    )
    wrapper.unmount()
  })

  test('移出全局规则只删除关联且确认不会删除共享规则', async () => {
    definitionApi.deleteProjectBinding.mockResolvedValueOnce({
      data: { id: 82, resourceType: 'RULE_PROJECT_BINDING' },
    })
    const { wrapper, router } = await mountPage()

    await wrapper.vm.del({
      id: 30,
      scope: 'GLOBAL',
      projectBindingId: 15,
      ruleName: '共享评分规则',
    })

    expect(wrapper.vm.$confirm).toHaveBeenCalledWith(
      '确定将全局规则“共享评分规则”移出当前项目吗？共享规则本身不会被删除。'
    )
    expect(definitionApi.deleteProjectBinding).toHaveBeenCalledWith(15, 9)
    expect(definitionApi.deleteDefinition).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith(
      '已生成移出项目审批草稿'
    )
    expect(router.push).toHaveBeenCalledWith('/approval/82')
    wrapper.unmount()
  })

  test('缺少关联 ID 时禁止猜测关联并提示刷新', async () => {
    const { wrapper } = await mountPage()

    await wrapper.vm.del({
      id: 30,
      scope: 'GLOBAL',
      projectBindingId: null,
      ruleName: '共享评分规则',
    })

    expect(wrapper.vm.$confirm).not.toHaveBeenCalled()
    expect(definitionApi.deleteProjectBinding).not.toHaveBeenCalled()
    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith(
      '未找到项目关联记录，请刷新页面后重试'
    )
    wrapper.unmount()
  })

  test('项目自有规则删除只生成规则审批草稿且不宣称已删除', async () => {
    definitionApi.deleteDefinition.mockResolvedValueOnce({
      data: { id: 83, resourceType: 'RULE' },
    })
    const { wrapper, router } = await mountPage()

    await wrapper.vm.del({
      id: 31,
      scope: 'PROJECT',
      ruleName: '项目专属规则',
    })

    expect(wrapper.vm.$confirm).toHaveBeenCalledWith(
      '确定为项目规则“项目专属规则”发起删除审批吗？审批通过后规则才会删除。'
    )
    expect(definitionApi.deleteDefinition).toHaveBeenCalledWith(31)
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith(
      '已生成规则删除审批草稿'
    )
    expect(router.push).toHaveBeenCalledWith('/approval/83')
    wrapper.unmount()
  })
})
