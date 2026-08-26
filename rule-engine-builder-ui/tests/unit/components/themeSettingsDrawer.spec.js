import { mount } from '@test-utils'
import ThemeSettingsDrawer from '@/components/theme/ThemeSettingsDrawer.vue'
import { DEFAULT_THEME_CONFIG } from '@/theme/themeConfig'

const drawerStub = {
  name: 'ElDrawer',
  props: ['modelValue', 'beforeClose'],
  template: `
    <section v-if="modelValue" class="drawer-stub">
      <slot />
      <slot name="footer" />
    </section>
  `,
}

function mountDrawer(overrides = {}) {
  return mount(ThemeSettingsDrawer, {
    props: {
      modelValue: true,
      config: { ...DEFAULT_THEME_CONFIG },
      saving: false,
      ...overrides,
    },
    stubs: {
      'el-drawer': drawerStub,
      'el-icon': { template: '<i><slot /></i>' },
      'el-switch': {
        props: ['modelValue', 'disabled'],
        template: '<button type="button" class="switch-stub" :disabled="disabled" />',
      },
    },
  })
}

describe('ThemeSettingsDrawer', () => {
  test('展示 8 个纯色和 4 个固定渐变色卡', () => {
    const wrapper = mountDrawer()

    expect(wrapper.findAll('[data-accent-kind="solid"]')).toHaveLength(8)
    expect(wrapper.findAll('[data-accent-kind="gradient"]')).toHaveLength(4)
    expect(wrapper.find('[data-accent="THEME_BLUE"]').attributes('aria-label'))
      .toBe('主题蓝')
    expect(wrapper.find('[data-accent="PEACH_PINK_GRADIENT"]').attributes('aria-label'))
      .toBe('桃粉渐变')
  })

  test('白天夜间和色卡选择只更新草稿并发送实时预览', async () => {
    const wrapper = mountDrawer()

    await wrapper.find('[data-theme-scheme="DARK"]').trigger('click')
    await wrapper.find('[data-accent="PEACH_PINK_GRADIENT"]').trigger('click')

    const preview = wrapper.emitted('preview').at(-1)[0]
    expect(preview.colorScheme).toBe('DARK')
    expect(preview.accentPreset).toBe('PEACH_PINK_GRADIENT')
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  test('取消和非保存关闭发送打开前快照并关闭抽屉', async () => {
    const opened = { ...DEFAULT_THEME_CONFIG, accentPreset: 'LIQUID_PURPLE' }
    const wrapper = mountDrawer({ config: opened })
    await wrapper.find('[data-theme-scheme="DARK"]').trigger('click')

    wrapper.vm.requestClose()

    expect(wrapper.emitted('cancel')[0]).toEqual([opened])
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([false])
  })

  test('恢复默认只预览默认草稿，仍需显式保存', async () => {
    const wrapper = mountDrawer({
      config: { ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' },
    })

    await wrapper.find('[data-action="restore-default"]').trigger('click')

    expect(wrapper.emitted('preview').at(-1)[0]).toEqual(DEFAULT_THEME_CONFIG)
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  test('保存发送完整草稿且保存中禁止重复提交', async () => {
    const wrapper = mountDrawer()
    await wrapper.find('[data-theme-scheme="DARK"]').trigger('click')
    await wrapper.find('[data-action="save"]').trigger('click')

    expect(wrapper.emitted('save').at(-1)[0]).toEqual({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
    })

    await wrapper.setProps({ saving: true })
    expect(wrapper.find('[data-action="save"]').attributes('disabled')).toBeDefined()
  })
})
