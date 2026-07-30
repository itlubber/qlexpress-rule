vi.mock('@/api/governance', () => ({
  getGovernanceRequest: vi.fn().mockResolvedValue({
    data: {
      request: {
        id: 12,
        requestNo: 'GOV-12',
        resourceType: 'DATABASE',
        resourceId: 7,
        action: 'UPDATE',
        status: 'PENDING',
        applicant: 'same-user',
        baseVersionNo: 2
      },
      diff: {
        summary: '共 2 项变更',
        fields: [
          {
            key: '$.host',
            label: 'host',
            leftValue: 'db-old',
            rightValue: 'db-new',
            changeType: 'MODIFIED',
            sensitive: false,
            changed: true
          },
          {
            key: '$.credentials/password',
            label: '凭据 /password',
            leftValue: '已配置',
            rightValue: '已配置',
            changeType: 'MODIFIED',
            sensitive: true,
            changed: true
          }
        ]
      },
      events: [{
        id: 1,
        action: 'SUBMIT',
        actor: 'same-user',
        toStatus: 'PENDING'
      }],
      dependencies: [],
      versions: [{ id: 3, versionNo: 2 }]
    }
  }),
  preflightGovernanceRequest: vi.fn(),
  submitGovernanceRequest: vi.fn(),
  approveGovernanceRequest: vi.fn(),
  rejectGovernanceRequest: vi.fn(),
  cancelGovernanceRequest: vi.fn(),
  restoreGovernanceVersion: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '12' } }),
  useRouter: () => ({ push: vi.fn(), back: vi.fn() })
}))

import { flushPromises, shallowMount } from '@test-utils'
import { clearCurrentUser, setCurrentUser } from '@/security/permissionState'
import ApprovalDetail from '@/views/approval/ApprovalDetail.vue'

describe('统一审批详情', () => {
  afterEach(() => {
    clearCurrentUser()
  })

  test('左右展示基准和提交版本差异', async() => {
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('基准版本')
    expect(wrapper.text()).toContain('提交版本')
    expect(wrapper.text()).toContain('db-old')
    expect(wrapper.text()).toContain('db-new')
  })

  test('敏感字段只显示配置状态且审批历史保留', async() => {
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('敏感内容受保护')
    expect(wrapper.text()).toContain('same-user')
    expect(wrapper.text()).not.toContain('top-secret')
  })

  test('非申请人不显示草稿预检提交和撤回操作', async() => {
    setCurrentUser({
      username: 'other-user',
      permissions: ['approval:submit', 'approval:approve']
    })
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()
    wrapper.vm.detail = {
      ...wrapper.vm.detail,
      request: {
        ...wrapper.vm.detail.request,
        status: 'EDITING'
      }
    }
    await wrapper.vm.$nextTick()
    const buttonLabels = wrapper.findAll('el-button-stub')
      .map(button => button.text().trim())

    expect(buttonLabels).not.toContain('依赖预检')
    expect(buttonLabels).not.toContain('提交审批')
    expect(buttonLabels).not.toContain('取消申请')
  })
})
