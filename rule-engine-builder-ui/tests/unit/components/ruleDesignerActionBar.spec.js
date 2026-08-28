import { mount } from '@test-utils'
import RuleDesignerActionBar from '@/components/rule/RuleDesignerActionBar.vue'

describe('RuleDesignerActionBar', () => {
  test('只保留一个主动作且未检查内容不能进入测试', async () => {
    const wrapper = mount(RuleDesignerActionBar, {
      props: {
        canEdit: true,
        state: 'DIRTY',
        canTest: false,
      },
    })

    const primaryButtons = wrapper.findAll('.rule-designer-actions__primary')
    expect(primaryButtons).toHaveLength(1)
    expect(wrapper.text()).toContain('保存并检查')
    expect(wrapper.text()).toContain('仅保存草稿')
    expect(wrapper.text()).toContain('进入测试')
    expect(wrapper.get('[data-action="test"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-action="save-check"]').trigger('click')
    expect(wrapper.emitted('save-check')).toHaveLength(1)
  })

  test('检查通过后启用测试并以文字链接呈现生命周期入口', async () => {
    const wrapper = mount(RuleDesignerActionBar, {
      props: {
        canEdit: true,
        state: 'READY_TO_TEST',
        canTest: true,
      },
    })

    expect(wrapper.get('[data-action="test"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-action="lifecycle"]').classes()).toContain(
      'rule-designer-actions__link'
    )
    await wrapper.get('[data-action="lifecycle"]').trigger('click')
    expect(wrapper.emitted('lifecycle')).toHaveLength(1)
  })
})
