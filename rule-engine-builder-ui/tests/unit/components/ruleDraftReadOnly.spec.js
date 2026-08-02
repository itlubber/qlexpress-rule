import { flushPromises, mount } from '@test-utils'
import RuleDraftReadOnly from '@/components/rule/RuleDraftReadOnly.vue'

describe('RuleDraftReadOnly', () => {
  test('确认草稿状态期间立即覆盖编辑区并播报加载文案', () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: { visible: true, loading: true },
    })

    const overlay = wrapper.get('[data-testid="draft-read-only"]')
    expect(overlay.attributes('aria-live')).toBe('polite')
    expect(overlay.text()).toContain('正在确认草稿状态')
    expect(wrapper.find('[data-testid="go-rule-lifecycle"]').exists()).toBe(
      false
    )
  })

  test('只读遮罩保留返回操作', async () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: { visible: true, loading: true },
    })

    await wrapper.get('[data-testid="draft-read-only-back"]').trigger('click')

    expect(wrapper.emitted('go-back')).toHaveLength(1)
  })

  test('无草稿时说明只读原因并触发生命周期入口', async () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: { visible: true, loading: false },
    })

    expect(wrapper.text()).toContain('当前规则没有可编辑草稿')
    await wrapper.get('[data-testid="go-rule-lifecycle"]').trigger('click')
    expect(wrapper.emitted('go-lifecycle')).toHaveLength(1)
  })

  test('允许编辑时不渲染遮罩', () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: { visible: false, loading: false },
    })

    expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(false)
  })
})

describe('RuleDraftReadOnly source actions', () => {
  test('shows a stable source label and emits fork for derivable nodes', async () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: {
        visible: true,
        loading: false,
        revisionLabel: '版本 7',
        canFork: true,
      },
    })

    expect(wrapper.text()).toContain('版本 7')
    expect(wrapper.text()).toContain('基于此节点编辑')
    await wrapper.get('[data-testid="fork-view-revision"]').trigger('click')
    expect(wrapper.emitted('fork')).toHaveLength(1)
  })

  test('tells REVIEW nodes to return through lifecycle instead of forking', () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: { visible: true, loading: false, revisionState: 'REVIEW' },
    })

    expect(wrapper.text()).toContain('需前往规则生命周期退回')
    expect(wrapper.find('[data-testid="fork-view-revision"]').exists()).toBe(false)
  })

  test('明确说明旧版历史内容只读且必须显式创建草稿', () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: {
        visible: true,
        loading: false,
        revisionLabel: '历史生效内容',
        revisionState: 'LEGACY',
      },
    })

    expect(wrapper.text()).toContain('历史生效内容')
    expect(wrapper.text()).toContain('旧版历史内容，只读展示')
    expect(wrapper.text()).toContain('前往规则生命周期创建草稿')
    expect(wrapper.find('[data-testid="fork-view-revision"]').exists()).toBe(false)
  })

  test('distinguishes failed source loading from an ordinary read-only node', () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: { visible: true, loading: false, loadError: true },
    })

    expect(wrapper.text()).toContain('当前节点加载失败')
    expect(wrapper.text()).toContain('无法显示当前节点')
  })
})

describe('RuleDraftReadOnly interaction isolation', () => {
  test('marks the underlying designer content inert while the read-only layer is visible', async () => {
    const wrapper = mount({
      components: { RuleDraftReadOnly },
      data() {
        return { visible: true }
      },
      template: `
        <div>
          <rule-draft-read-only :visible="visible" />
          <section data-testid="designer-content">
            <button data-testid="designer-save">Save</button>
          </section>
        </div>
      `,
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="designer-content"]').attributes('inert')).toBe('')

    wrapper.vm.visible = false
    await flushPromises()

    expect(wrapper.get('[data-testid="designer-content"]').attributes('inert')).toBeUndefined()
  })
})
