import { flushPromises, mount } from '@test-utils'
import { reactive } from 'vue'
import * as definitionApi from '@/api/definition'
import ruleDraftMixin from '@/mixins/ruleDraftMixin'

function mountHost() {
  return mount({
    name: 'RuleDraftMixinHost',
    mixins: [ruleDraftMixin],
    template: '<div />',
  }, {
    mocks: {
      $route: { params: { id: 30 }, query: {} },
      $router: { push: vi.fn() },
    },
  })
}

function mountCachedHost() {
  return mount({
    name: 'RuleDraftMixinCacheHarness',
    components: {
      CachedHost: {
        name: 'CachedRuleDraftMixinHost',
        mixins: [ruleDraftMixin],
        template: '<div />',
      },
    },
    data() {
      return { active: true }
    },
    template:
      '<keep-alive><cached-host v-if="active" ref="host" /></keep-alive>',
  }, {
    mocks: {
      $route: { params: { id: 30 }, query: {} },
      $router: { push: vi.fn() },
    },
  })
}

describe('ruleDraftMixin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('保存编译成功后进入规则详情生命周期', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getContent.mockResolvedValueOnce({
      data: { modelJson: '{}' },
    })
    const wrapper = mountHost()
    await flushPromises()

    await wrapper.vm.completeRuleCompile(
      { compileSuccess: true },
      { successMessage: '编译成功' }
    )

    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle' },
    })
    wrapper.unmount()
  })

  test('有 DRAFT 时保存携带 revisionId 和 lockVersion 并更新乐观锁', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.saveContent.mockResolvedValueOnce({
      data: {
        revision: {
          id: 6,
          definitionId: 30,
          state: 'DRAFT',
          lockVersion: 5,
        },
        compileSuccess: true,
        issues: [],
      },
    })
    const wrapper = mountHost()
    await flushPromises()

    const result = await wrapper.vm.saveDraftModel(
      '{"script":"x = input.x"}'
    )

    expect(definitionApi.saveContent).toHaveBeenCalledWith({
      definitionId: 30,
      revisionId: 6,
      lockVersion: 4,
      modelJson: '{"script":"x = input.x"}',
    })
    expect(result.compileSuccess).toBe(true)
    expect(wrapper.vm.draftRevision.lockVersion).toBe(5)
    wrapper.unmount()
  })

  test('保存扩展字段仅透传 OpenAPI 白名单且不能覆盖草稿治理字段', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.saveContent.mockResolvedValueOnce({
      data: {
        revision: {
          id: 6,
          definitionId: 30,
          state: 'DRAFT',
          lockVersion: 5,
        },
        compileSuccess: true,
        issues: [],
      },
    })
    const wrapper = mountHost()
    await flushPromises()

    await wrapper.vm.saveDraftModel('{"script":"trusted"}', {
      definitionId: 999,
      revisionId: 999,
      lockVersion: 999,
      modelJson: '{"script":"malicious"}',
      openApiConfigJson: '{"enabled":true}',
      updateOpenApiConfig: true,
      unknownField: 'must-not-pass',
    })

    expect(definitionApi.saveContent).toHaveBeenCalledWith({
      definitionId: 30,
      revisionId: 6,
      lockVersion: 4,
      modelJson: '{"script":"trusted"}',
      openApiConfigJson: '{"enabled":true}',
      updateOpenApiConfig: true,
    })
    wrapper.unmount()
  })

  test('无 DRAFT 时设计器只读且保存不会隐式创建草稿', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 5,
          definitionId: 30,
          revisionNo: 1,
          state: 'PUBLISHED',
          modelJson: '{}',
        },
      ],
    })
    const wrapper = mountHost()
    await flushPromises()

    expect(wrapper.vm.canEditDraft).toBe(false)
    expect(wrapper.vm.viewRevision.id).toBe(5)
    await expect(wrapper.vm.saveDraftModel('{}')).rejects.toThrow(
      '当前规则没有可编辑草稿'
    )
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
    expect(definitionApi.ensureDraftRevision).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('没有治理修订时回退展示旧版生效内容且保持只读', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getContent.mockResolvedValueOnce({
      data: {
        id: 18,
        definitionId: 30,
        modelJson: '{"rules":[{"id":"legacy"}]}',
      },
    })
    const wrapper = mountHost()
    await flushPromises()

    expect(wrapper.vm.viewRevision).toMatchObject({
      id: 'legacy-content:30',
      definitionId: 30,
      state: 'LEGACY',
      sourceType: 'LEGACY_CONTENT',
      sourceId: '30',
      modelJson: '{"rules":[{"id":"legacy"}]}',
    })
    expect(wrapper.vm.viewRevisionLabel).toBe('历史生效内容')
    expect(wrapper.vm.canEditDraft).toBe(false)
    expect(wrapper.vm.canForkViewRevision).toBe(false)
    wrapper.unmount()
  })

  test('修订列表查询失败时不以旧内容掩盖治理服务错误', async () => {
    definitionApi.listRuleRevisions.mockRejectedValueOnce(
      new Error('revision query failed')
    )
    const wrapper = mountHost()
    await wrapper.vm.draftGuardPromise

    expect(definitionApi.getContent).not.toHaveBeenCalled()
    expect(wrapper.vm.viewRevision).toBeNull()
    expect(wrapper.vm.draftGuardError.message).toBe('revision query failed')
    wrapper.unmount()
  })

  test('旧版生效内容不是合法 JSON 时显示加载错误而不是空白设计器', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getContent.mockResolvedValueOnce({
      data: { definitionId: 30, modelJson: '{broken json' },
    })
    const wrapper = mountHost()
    await wrapper.vm.draftGuardPromise

    expect(wrapper.vm.viewRevision).toBeNull()
    expect(wrapper.vm.draftGuardError.message).toBe(
      '当前规则的历史内容不是有效 JSON'
    )
    wrapper.unmount()
  })

  test('旧版生效内容为空时显示明确错误', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getContent.mockResolvedValueOnce({
      data: { definitionId: 30, modelJson: '' },
    })
    const wrapper = mountHost()
    await wrapper.vm.draftGuardPromise

    expect(wrapper.vm.viewRevision).toBeNull()
    expect(wrapper.vm.draftGuardError.message).toBe(
      '当前规则没有可查看的历史内容'
    )
    wrapper.unmount()
  })

  test('编译失败仍更新锁与诊断且不调用旧编译接口', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
        },
      ],
    })
    definitionApi.saveContent.mockResolvedValueOnce({
      data: {
        revision: {
          id: 6,
          definitionId: 30,
          state: 'DRAFT',
          lockVersion: 5,
        },
        compileSuccess: false,
        compileMessage: '脚本解析失败',
        issues: [{ code: 'QL_PARSE_ERROR', severity: 'ERROR' }],
      },
    })
    const wrapper = mountHost()
    await flushPromises()

    const result = await wrapper.vm.saveDraftModel('invalid ql')

    expect(result.compileSuccess).toBe(false)
    expect(wrapper.vm.draftRevision.lockVersion).toBe(5)
    expect(wrapper.vm.draftIssues).toEqual([
      { code: 'QL_PARSE_ERROR', severity: 'ERROR' },
    ])
    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('保存响应缺少 revision 时拒绝继续沿用旧锁', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
        },
      ],
    })
    definitionApi.saveContent.mockResolvedValueOnce({
      data: { compileSuccess: true, issues: [] },
    })
    const wrapper = mountHost()
    await flushPromises()

    await expect(wrapper.vm.saveDraftModel('{}')).rejects.toThrow(
      '草稿保存响应缺少 revision'
    )
    expect(wrapper.vm.draftRevision).toBeNull()
    expect(wrapper.vm.canEditDraft).toBe(false)
    wrapper.unmount()
  })

  test('重载失败立即清空旧草稿并以只读状态完成守卫', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
          modelJson: '{"source":"old"}',
        },
      ],
    })
    const wrapper = mountHost()
    await wrapper.vm.draftGuardPromise
    wrapper.vm.draftIssues = [{ code: 'OLD_ISSUE' }]
    definitionApi.listRuleRevisions.mockRejectedValueOnce(
      new Error('revision query failed')
    )

    const reload = wrapper.vm.loadDraftRevision()
    const loadingState = {
      loaded: wrapper.vm.draftGuardLoaded,
      draft: wrapper.vm.draftRevision,
      view: wrapper.vm.viewRevision,
      issues: wrapper.vm.draftIssues,
    }
    const outcome = await reload.then(
      (value) => ({ resolved: true, value }),
      (error) => ({ resolved: false, error })
    )

    expect({
      loadingState,
      outcome,
      loaded: wrapper.vm.draftGuardLoaded,
      draft: wrapper.vm.draftRevision,
      view: wrapper.vm.viewRevision,
      canEdit: wrapper.vm.canEditDraft,
      errorMessage: wrapper.vm.draftGuardError?.message,
    }).toEqual({
      loadingState: {
        loaded: false,
        draft: null,
        view: null,
        issues: [],
      },
      outcome: { resolved: true, value: null },
      loaded: true,
      draft: null,
      view: null,
      canEdit: false,
      errorMessage: 'revision query failed',
    })
    await expect(wrapper.vm.saveDraftModel('{}')).rejects.toThrow(
      '当前规则没有可编辑草稿'
    )
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('初次草稿查询失败不会留下 rejected 守卫 Promise', async () => {
    definitionApi.listRuleRevisions.mockRejectedValueOnce(
      new Error('initial query failed')
    )

    const wrapper = mountHost()
    const outcome = await wrapper.vm.draftGuardPromise.then(
      (value) => ({ resolved: true, value }),
      (error) => ({ resolved: false, error })
    )

    expect({
      outcome,
      loaded: wrapper.vm.draftGuardLoaded,
      canEdit: wrapper.vm.canEditDraft,
      errorMessage: wrapper.vm.draftGuardError?.message,
    }).toEqual({
      outcome: { resolved: true, value: null },
      loaded: true,
      canEdit: false,
      errorMessage: 'initial query failed',
    })
    await expect(wrapper.vm.saveDraftModel('{}')).rejects.toThrow(
      '当前规则没有可编辑草稿'
    )
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('进入生命周期携带规则 ID 与定位参数', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    const wrapper = mountHost()
    await flushPromises()

    wrapper.vm.goRuleLifecycle()

    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle' },
    })
    wrapper.unmount()
  })

  test('设计器从工作区缓存恢复时重新校验 DRAFT 状态', async () => {
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({
        data: [
          {
            id: 6,
            definitionId: 30,
            revisionNo: 2,
            state: 'DRAFT',
            lockVersion: 4,
          },
        ],
      })
      .mockResolvedValueOnce({
        data: [
          {
            id: 6,
            definitionId: 30,
            revisionNo: 2,
            state: 'REVIEW',
            lockVersion: 5,
          },
        ],
      })
    const wrapper = mountCachedHost()
    await flushPromises()
    expect(wrapper.vm.$refs.host.canEditDraft).toBe(true)

    await wrapper.setData({ active: false })
    await wrapper.setData({ active: true })
    await flushPromises()

    expect(definitionApi.listRuleRevisions).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.$refs.host.canEditDraft).toBe(false)
    expect(wrapper.vm.$refs.host.viewRevision.state).toBe('REVIEW')
    wrapper.unmount()
  })
})

describe('ruleDraftMixin stable source loading', () => {
  function mountWithRoute(query) {
    const route = reactive({ params: { id: 30 }, query })
    return mount({
      name: 'RuleDraftMixinStableSourceHost',
      mixins: [ruleDraftMixin],
      template: '<div />',
    }, {
      mocks: {
        $route: route,
        $router: { push: vi.fn(), replace: vi.fn() },
      },
    })
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('reads an explicit revision exactly and keeps historical view read-only', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        { id: 6, revisionNo: 3, state: 'DRAFT', lockVersion: 4 },
        { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' },
      ],
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"exact"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    expect(definitionApi.getRuleRevision).toHaveBeenCalledWith(30, '4')
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 4, modelJson: '{"source":"exact"}' })
    expect(wrapper.vm.draftRevision.id).toBe(6)
    expect(wrapper.vm.canEditDraft).toBe(false)
    wrapper.unmount()
  })

  test('wraps a VERSION only for display and does not fall back to draft', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, revisionNo: 3, state: 'DRAFT' }],
    })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    expect(definitionApi.getVersionById).toHaveBeenCalledWith(30, '9')
    expect(wrapper.vm.viewRevision).toMatchObject({
      id: 9,
      state: 'VERSION',
      revisionNo: 7,
      sourceType: 'VERSION',
      sourceId: '9',
      modelJson: '{"source":"snapshot"}',
    })
    expect(wrapper.vm.canEditDraft).toBe(false)
    wrapper.unmount()
  })

  test('clears the viewed node when its exact source fails instead of falling back', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, revisionNo: 3, state: 'DRAFT' }],
    })
    definitionApi.getRuleRevision.mockRejectedValueOnce(new Error('source missing'))
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    expect(wrapper.vm.draftRevision.id).toBe(6)
    expect(wrapper.vm.viewRevision).toBeNull()
    expect(wrapper.vm.canEditDraft).toBe(false)
    expect(wrapper.vm.draftGuardError.message).toBe('source missing')
    expect(definitionApi.getContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('retains DRAFT-first behavior when route query is absent or invalid', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        { id: 4, revisionNo: 2, state: 'PUBLISHED' },
        { id: 6, revisionNo: 3, state: 'DRAFT' },
      ],
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: 'not-an-id' })
    await flushPromises()

    expect(definitionApi.getRuleRevision).not.toHaveBeenCalled()
    expect(definitionApi.getVersionById).not.toHaveBeenCalled()
    expect(wrapper.vm.viewRevision.id).toBe(6)
    expect(wrapper.vm.canEditDraft).toBe(true)
    wrapper.unmount()
  })

  test('forks a stable VERSION source and replaces route with the new draft', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    definitionApi.createDraftFromSource.mockResolvedValueOnce({
      data: {
        revision: { id: 12, revisionNo: 8, state: 'DRAFT', lockVersion: 1 },
        issues: [{ code: 'WARN' }],
      },
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    await wrapper.vm.forkViewRevision()

    expect(definitionApi.createDraftFromSource).toHaveBeenCalledWith(30, {
      sourceType: 'VERSION', sourceId: '9',
    })
    expect(wrapper.vm.draftRevision).toMatchObject({ id: 12, state: 'DRAFT' })
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 12, state: 'DRAFT' })
    expect(wrapper.vm.draftIssues).toEqual([{ code: 'WARN' }])
    expect(wrapper.vm.$router.replace).toHaveBeenCalledWith({
      query: { sourceType: 'REVISION', sourceId: '12' },
    })
    wrapper.unmount()
  })

  test('preserves the current node when fork conflicts and never saves its model', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    definitionApi.createDraftFromSource.mockRejectedValueOnce(
      Object.assign(new Error('conflict'), { response: { status: 409 } })
    )
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()
    const viewed = wrapper.vm.viewRevision

    await expect(wrapper.vm.forkViewRevision()).rejects.toThrow('conflict')

    expect(wrapper.vm.viewRevision).toBe(viewed)
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('does not allow REVIEW nodes to fork', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'REVIEW' },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    expect(wrapper.vm.canForkViewRevision).toBe(false)
    await expect(wrapper.vm.forkViewRevision()).rejects.toThrow('当前节点不允许派生草稿')
    expect(definitionApi.createDraftFromSource).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('routes query changes through the shared viewed-revision refresh entry', async () => {
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [{ id: 6, revisionNo: 3, state: 'DRAFT' }] })
      .mockResolvedValueOnce({ data: [{ id: 6, revisionNo: 3, state: 'DRAFT' }] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED' },
    })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    wrapper.vm.$route.query.sourceType = 'VERSION'
    wrapper.vm.$route.query.sourceId = '9'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise

    expect(definitionApi.getVersionById).toHaveBeenCalledWith(30, '9')
    expect(wrapper.vm.viewRevision).toMatchObject({ state: 'VERSION', sourceId: '9' })
    wrapper.unmount()
  })

  test('ignores an older source response after a newer source refresh wins', async () => {
    let resolveOldRevision
    const oldRevision = new Promise((resolve) => {
      resolveOldRevision = resolve
    })
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockReturnValueOnce(oldRevision)
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"new"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    wrapper.vm.$route.query.sourceType = 'VERSION'
    wrapper.vm.$route.query.sourceId = '9'
    const current = wrapper.vm.refreshViewedRevision()
    await current
    resolveOldRevision({ data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' } })
    await flushPromises()

    expect(wrapper.vm.viewRevision).toMatchObject({
      state: 'VERSION',
      sourceId: '9',
      modelJson: '{"source":"new"}',
    })
    wrapper.unmount()
  })

  test('preserves a large exact source ID without Number coercion', async () => {
    const sourceId = '9007199254740993'
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: sourceId, version: 7, modelJson: '{"source":"large"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId })
    await flushPromises()

    expect(definitionApi.getVersionById).toHaveBeenCalledWith(30, sourceId)
    expect(wrapper.vm.viewRevision.sourceId).toBe(sourceId)
    wrapper.unmount()
  })

  test('forks a large REVISION source using the exact query ID instead of a rounded response ID', async () => {
    const sourceId = '9007199254740993'
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 9007199254740992, revisionNo: 2, state: 'PUBLISHED' },
    })
    definitionApi.createDraftFromSource.mockResolvedValueOnce({
      data: { revision: { id: 12, state: 'DRAFT', lockVersion: 1 }, issues: [] },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId })
    await flushPromises()

    await wrapper.vm.forkViewRevision()

    expect(definitionApi.createDraftFromSource).toHaveBeenCalledWith(30, {
      sourceType: 'REVISION',
      sourceId,
    })
    wrapper.unmount()
  })

  test('starts exact source loading before the revision list settles and still displays it if list fails', async () => {
    let rejectList
    let resolveVersion
    definitionApi.listRuleRevisions.mockReturnValueOnce(new Promise((_resolve, reject) => {
      rejectList = reject
    }))
    definitionApi.getVersionById.mockReturnValueOnce(new Promise((resolve) => {
      resolveVersion = resolve
    }))
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    expect(definitionApi.getVersionById).toHaveBeenCalledWith(30, '9')
    resolveVersion({ data: { id: 9, version: 7, modelJson: '{"source":"exact"}' } })
    rejectList(new Error('list unavailable'))
    await wrapper.vm.draftGuardPromise

    expect(wrapper.vm.viewRevision).toMatchObject({
      state: 'VERSION',
      sourceId: '9',
      modelJson: '{"source":"exact"}',
    })
    expect(wrapper.vm.draftRevision).toBeNull()
    expect(wrapper.vm.canEditDraft).toBe(false)
    wrapper.unmount()
  })

  test('query refresh reloads host content only after its guard promise resolves', async () => {
    const loadContent = vi.fn(async function () {
      await this.draftGuardPromise
    })
    const route = reactive({
      params: { id: 30 },
      query: { sourceType: 'REVISION', sourceId: '4' },
    })
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' },
    })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"new"}' },
    })
    const wrapper = mount({
      name: 'RuleDraftMixinContentReloadHost',
      mixins: [ruleDraftMixin],
      methods: { loadContent },
      template: '<div />',
    }, {
      mocks: { $route: route, $router: { push: vi.fn(), replace: vi.fn() } },
    })
    await flushPromises()
    expect(loadContent).not.toHaveBeenCalled()

    route.query.sourceType = 'VERSION'
    route.query.sourceId = '9'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    await flushPromises()

    expect(wrapper.vm.viewRevision.modelJson).toBe('{"source":"new"}')
    expect(loadContent).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  test('activated refresh reloads host content after the new exact source is ready', async () => {
    const loadContent = vi.fn()
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision
      .mockResolvedValueOnce({ data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' } })
      .mockResolvedValueOnce({ data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"fresh"}' } })
    const wrapper = mount({
      name: 'RuleDraftMixinActivatedReloadHost',
      mixins: [ruleDraftMixin],
      methods: { loadContent },
      template: '<div />',
    }, {
      mocks: {
        $route: reactive({ params: { id: 30 }, query: { sourceType: 'REVISION', sourceId: '4' } }),
        $router: { push: vi.fn(), replace: vi.fn() },
      },
    })
    await flushPromises()
    wrapper.vm.draftGuardNeedsRefresh = true

    ruleDraftMixin.activated.call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    await flushPromises()

    expect(wrapper.vm.viewRevision.modelJson).toBe('{"source":"fresh"}')
    expect(loadContent).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  test('does not reload host content when an old source request resolves after a new source', async () => {
    let resolveOldRevision
    const loadContent = vi.fn()
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockReturnValueOnce(new Promise((resolve) => {
      resolveOldRevision = resolve
    }))
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"new"}' },
    })
    const route = reactive({ params: { id: 30 }, query: { sourceType: 'REVISION', sourceId: '4' } })
    const wrapper = mount({
      name: 'RuleDraftMixinStaleReloadHost',
      mixins: [ruleDraftMixin],
      methods: { loadContent },
      template: '<div />',
    }, {
      mocks: { $route: route, $router: { push: vi.fn(), replace: vi.fn() } },
    })
    await flushPromises()

    route.query.sourceType = 'VERSION'
    route.query.sourceId = '9'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    resolveOldRevision({ data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' } })
    await flushPromises()

    expect(loadContent).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.viewRevision.modelJson).toBe('{"source":"new"}')
    wrapper.unmount()
  })

  test('keeps a newer viewed source when an older fork response returns', async () => {
    let resolveFork
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"version"}' },
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"current"}' },
    })
    definitionApi.createDraftFromSource.mockReturnValueOnce(new Promise((resolve) => {
      resolveFork = resolve
    }))
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    const fork = wrapper.vm.forkViewRevision()
    wrapper.vm.$route.query.sourceType = 'REVISION'
    wrapper.vm.$route.query.sourceId = '4'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    resolveFork({ data: { revision: { id: 12, state: 'DRAFT', lockVersion: 1 }, issues: [] } })
    await fork

    expect(wrapper.vm.viewRevision).toMatchObject({ id: 4, modelJson: '{"source":"current"}' })
    expect(wrapper.vm.$router.replace).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('keeps a newer viewed source when an older save response returns', async () => {
    let resolveSave
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [{ id: 6, revisionNo: 3, state: 'DRAFT', lockVersion: 4 }] })
      .mockResolvedValueOnce({ data: [{ id: 6, revisionNo: 3, state: 'DRAFT', lockVersion: 4 }] })
    definitionApi.saveContent.mockReturnValueOnce(new Promise((resolve) => {
      resolveSave = resolve
    }))
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"current"}' },
    })
    const wrapper = mountWithRoute({})
    await flushPromises()

    const save = wrapper.vm.saveDraftModel('{"source":"save"}')
    await flushPromises()
    wrapper.vm.$route.query.sourceType = 'REVISION'
    wrapper.vm.$route.query.sourceId = '4'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    resolveSave({ data: { revision: { id: 6, state: 'DRAFT', lockVersion: 5 }, issues: [{ code: 'OLD' }] } })
    await save

    expect(wrapper.vm.viewRevision).toMatchObject({ id: 4, modelJson: '{"source":"current"}' })
    expect(wrapper.vm.draftRevision).toMatchObject({ id: 6, lockVersion: 4 })
    expect(wrapper.vm.draftIssues).toEqual([])
    wrapper.unmount()
  })
})
