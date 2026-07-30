import { shallowMount } from '@test-utils'

import request from '@/api/request'
import * as projectApi from '@/api/project'
import ProjectDetail from '@/views/project/ProjectDetail.vue'

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountPage() {
  projectApi.getProject.mockResolvedValue({
    data: { id: 9, projectCode: 'risk', projectName: '风控项目' },
  })
  request.mockResolvedValue({
    data: { records: [], total: 0 },
  })
  const router = { push: vi.fn(), replace: vi.fn() }
  const wrapper = shallowMount(ProjectDetail, {
    mocks: {
      $route: { params: { id: 9 }, query: {} },
      $router: router,
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
  return { wrapper, router }
}

describe('ProjectDetail 规则生命周期入口', () => {
  afterEach(() => {
    vi.clearAllMocks()
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
})
