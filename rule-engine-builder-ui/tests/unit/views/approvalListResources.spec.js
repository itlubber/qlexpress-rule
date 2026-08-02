vi.mock('@/api/governance', () => ({
  listGovernanceRequests: vi.fn().mockResolvedValue({
    data: { records: [], total: 0 }
  }),
  getGovernanceRequest: vi.fn().mockResolvedValue({
    data: { request: null, diff: { fields: [] } }
  }),
  preflightGovernanceRequest: vi.fn(),
  submitGovernanceRequest: vi.fn(),
  approveGovernanceRequest: vi.fn(),
  rejectGovernanceRequest: vi.fn(),
  cancelGovernanceRequest: vi.fn(),
  restoreGovernanceVersion: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '91' } }),
  useRouter: () => ({ push: vi.fn(), back: vi.fn() })
}))

import { flushPromises, shallowMount } from '@test-utils'
import ApprovalDetail from '@/views/approval/ApprovalDetail.vue'
import ApprovalList from '@/views/approval/ApprovalList.vue'

const batchRequest = {
  id: 91,
  resourceType: 'LIST_RECORD_BATCH',
  resourceId: 0,
  action: 'CREATE',
  status: 'EDITING',
  draftSnapshotJson: JSON.stringify({
    batchId: 101,
    listId: 9,
    listName: 'Mobile blacklist',
    addCount: 2,
    updateCount: 1,
    deleteCount: 1
  })
}

describe('approval labels for list resources', () => {
  test('list view explains a record batch using business counts', async () => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()

    expect(wrapper.vm.resourceTypeLabel('LIST_LIBRARY'))
      .toBe('\u540d\u5355\u5e93')
    expect(wrapper.vm.resourceTypeLabel('LIST_RECORD_BATCH'))
      .toBe('\u540d\u5355\u5185\u5bb9\u6279\u6b21')
    expect(wrapper.vm.resourceTitle(batchRequest))
      .toBe('Mobile blacklist \u00b7 4 \u6761\u5185\u5bb9\u53d8\u66f4')
  })

  test('detail view uses the same readable batch title', async () => {
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()
    wrapper.vm.detail = {
      request: batchRequest,
      diff: { fields: [] }
    }
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.resourceTitle)
      .toBe('Mobile blacklist \u00b7 4 \u6761\u5185\u5bb9\u53d8\u66f4')
  })
})
