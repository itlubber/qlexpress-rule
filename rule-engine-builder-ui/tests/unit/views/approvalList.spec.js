vi.mock('@/api/governance', () => ({
  listGovernanceRequests: vi.fn().mockResolvedValue({
    data: {
      records: [{
        id: 12,
        requestNo: 'GOV-12',
        resourceType: 'RULE',
        resourceId: 7,
        action: 'UPDATE',
        status: 'PENDING',
        applicant: 'same-user',
        submittedSnapshotJson: '{"ruleName":"授信规则"}'
      }],
      total: 1
    }
  })
}))

import { flushPromises, shallowMount } from '@test-utils'
import ApprovalList from '@/views/approval/ApprovalList.vue'
import router from '@/router'

describe('统一审批列表', () => {
  test('提供字段到项目共八个生命周期标签页', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()

    expect(wrapper.vm.tabs.map(tab => tab.label)).toEqual([
      '字段', '模型', '外数', '数据库',
      '函数', '规则', '分流', '项目'
    ])
  })

  test('点击申请进入统一审批详情', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()

    wrapper.vm.openDetail({ id: 12 })

    expect(router.push).toHaveBeenCalledWith('/approval/12')
  })

  test('项目规则关联归入规则页签并显示业务动作标题', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()
    const request = {
      resourceType: 'RULE_PROJECT_BINDING',
      resourceId: 0,
      action: 'CREATE',
      draftSnapshotJson: '{"definitionId":30,"projectId":9,' +
        '"ruleName":"共享评分规则","projectName":"风控项目"}'
    }

    expect(wrapper.vm.resourceTypeLabel(request.resourceType)).toBe(
      '项目规则关联'
    )
    expect(wrapper.vm.resourceTitle(request)).toBe(
      '将规则“共享评分规则”加入项目“风控项目”'
    )
  })
})
