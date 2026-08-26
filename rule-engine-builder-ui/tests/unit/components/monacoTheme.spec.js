import { mount } from '@test-utils'
import * as monaco from 'monaco-editor'
import MonacoEditor from '@/components/MonacoEditor.vue'
import MonacoDiffEditor from '@/components/rule/versionDiff/MonacoDiffEditor.vue'

describe('Monaco 全局日夜主题同步', () => {
  beforeEach(() => {
    window.monaco = monaco
    document.documentElement.dataset.theme = 'light'
    monaco.editor.setTheme.mockClear()
  })

  afterEach(() => {
    delete window.monaco
    document.documentElement.removeAttribute('data-theme')
    vi.clearAllMocks()
  })

  test('普通编辑器挂载时读取根主题并响应后续切换', async () => {
    document.documentElement.dataset.theme = 'dark'
    const wrapper = mount(MonacoEditor, { props: { value: 'result = true' } })
    await wrapper.vm.$nextTick()

    expect(monaco.editor.setTheme).toHaveBeenCalledWith('vs-dark')

    window.dispatchEvent(new CustomEvent('tianshu-theme-change', {
      detail: { colorScheme: 'LIGHT' },
    }))
    expect(monaco.editor.setTheme).toHaveBeenLastCalledWith('vs')
    wrapper.unmount()
  })

  test('差异编辑器和普通编辑器使用同一全局主题事件', async () => {
    const wrapper = mount(MonacoDiffEditor, {
      props: { original: 'a = 1', modified: 'a = 2', language: 'json' },
    })
    await wrapper.vm.$nextTick()

    window.dispatchEvent(new CustomEvent('tianshu-theme-change', {
      detail: { colorScheme: 'DARK' },
    }))

    expect(monaco.editor.setTheme).toHaveBeenCalledWith('vs-dark')
    wrapper.unmount()
  })

  test('组件卸载时清理主题事件监听', async () => {
    const removeSpy = vi.spyOn(window, 'removeEventListener')
    const wrapper = mount(MonacoEditor)
    await wrapper.vm.$nextTick()

    wrapper.unmount()

    expect(removeSpy).toHaveBeenCalledWith(
      'tianshu-theme-change',
      wrapper.vm.handleGlobalThemeChange
    )
    removeSpy.mockRestore()
  })
})
