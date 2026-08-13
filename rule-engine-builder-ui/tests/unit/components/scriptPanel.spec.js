import { mount } from '@test-utils'
import * as definitionApi from '@/api/definition'

vi.unmock('@/components/common/ScriptPanel.vue')

const ScriptPanel = (
  await vi.importActual('@/components/common/ScriptPanel.vue')
).default

function mountPanel(onBeforeCompile) {
  const messages = {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  }
  const wrapper = mount(ScriptPanel, {
    props: {
      definitionId: 12,
      onBeforeCompile,
    },
    mocks: {
      $message: messages,
    },
  })
  return { wrapper, messages }
}

describe('ScriptPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('前置保存正常返回编译失败时展示编译诊断且不调用旧编译接口', async () => {
    const onBeforeCompile = vi.fn().mockResolvedValue({
      compileSuccess: false,
      compileMessage: '脚本解析失败',
      issues: [{ code: 'QL_PARSE_ERROR' }],
    })
    const { wrapper, messages } = mountPanel(onBeforeCompile)

    const result = await wrapper.vm.handleCompile()

    expect(onBeforeCompile).toHaveBeenCalledTimes(1)
    expect(result.compileSuccess).toBe(false)
    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    expect(definitionApi.saveScript).not.toHaveBeenCalled()
    expect(definitionApi.updateScriptMode).not.toHaveBeenCalled()
    expect(definitionApi.validateScript).not.toHaveBeenCalled()
    expect(messages.error).toHaveBeenCalledWith('编译失败: 脚本解析失败')
    expect(wrapper.vm.compiling).toBe(false)
    wrapper.unmount()
  })

  test('前置保存返回编译成功时刷新只读脚本预览', async () => {
    const onBeforeCompile = vi.fn().mockResolvedValue({
      compileSuccess: true,
      revision: { compiledScript: 'result = input.score;' },
      issues: [],
    })
    const { wrapper, messages } = mountPanel(onBeforeCompile)

    const result = await wrapper.vm.handleCompile()

    expect(result.compileSuccess).toBe(true)
    expect(wrapper.vm.editScript).toBe('result = input.score;')
    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    expect(messages.success).toHaveBeenCalledWith('编译成功')
    expect(wrapper.vm.compiling).toBe(false)
    wrapper.unmount()
  })

  test('前置保存异常继续向上传播且不误标为纯编译失败', async () => {
    const onBeforeCompile = vi.fn().mockRejectedValue(new Error('save failed'))
    const { wrapper, messages } = mountPanel(onBeforeCompile)

    await expect(wrapper.vm.handleCompile()).rejects.toThrow('save failed')

    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    expect(messages.error).toHaveBeenCalledWith(
      '保存并编译失败: save failed'
    )
    expect(messages.error).not.toHaveBeenCalledWith('编译失败: save failed')
    expect(wrapper.vm.compiling).toBe(false)
    wrapper.unmount()
  })

  test('真实渲染的脚本编辑器始终只读且不暴露旧写入方法', () => {
    const { wrapper } = mountPanel(vi.fn())

    expect(wrapper.find('textarea').element.readOnly).toBe(true)
    expect(wrapper.vm.handleSaveScript).toBeUndefined()
    expect(wrapper.vm.handleValidateScript).toBeUndefined()
    expect(wrapper.vm.onModeChange).toBeUndefined()
    expect(wrapper.vm.switchToVisual).toBeUndefined()
    wrapper.unmount()
  })

  test('始终说明保存编译不会自动发布及下一步入口', () => {
    const { wrapper } = mountPanel(vi.fn())

    const guidance = wrapper.get('[data-testid="designer-lifecycle-guidance"]')
    expect(guidance.text()).toContain('保存并编译仅更新草稿')
    expect(guidance.text()).toContain('规则生命周期')
    wrapper.unmount()
  })

  test('点击生命周期入口只发出导航事件而不折叠脚本面板', async () => {
    const { wrapper } = mountPanel(vi.fn())
    wrapper.vm.expanded = true
    await wrapper.vm.$nextTick()

    await wrapper
      .get('[aria-label="前往规则生命周期审核发布"]')
      .trigger('click')

    expect(wrapper.emitted('go-lifecycle')).toHaveLength(1)
    expect(wrapper.vm.expanded).toBe(true)
    wrapper.unmount()
  })
})
