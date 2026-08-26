import {
  DEFAULT_THEME_CONFIG,
} from '@/theme/themeConfig'
import {
  LOCAL_THEME_STORAGE_KEY,
  applyTheme,
  bootstrapLocalTheme,
  readLocalTheme,
  writeLocalTheme,
} from '@/theme/themeRuntime'

describe('themeRuntime — 全局主题应用和本地降级', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.removeAttribute('data-sidebar-theme')
    document.documentElement.className = ''
    document.documentElement.removeAttribute('style')
  })

  test('夜间渐变主题一次更新根属性、布局状态和 Element Plus 主色', () => {
    const theme = applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
      accentPreset: 'PEACH_PINK_GRADIENT',
      sidebarTheme: 'LIGHT',
      contentWidth: 'FIXED',
      fixedSidebar: false,
      colorWeak: true,
    })

    expect(theme.colorScheme).toBe('DARK')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.documentElement.dataset.sidebarTheme).toBe('light')
    expect(document.documentElement.classList.contains('theme-content--fixed')).toBe(true)
    expect(document.documentElement.classList.contains('theme-sidebar--static')).toBe(true)
    expect(document.documentElement.classList.contains('theme-color-weak')).toBe(true)
    expect(document.documentElement.style.getPropertyValue('--tianshu-brand-gradient'))
      .toContain('#E14ECA')
    expect(document.documentElement.style.getPropertyValue('--el-color-primary'))
      .toBe('#873FF2')
  })

  test('每次应用主题都发送完整的 Monaco 可消费事件', () => {
    const listener = vi.fn()
    window.addEventListener('tianshu-theme-change', listener)

    applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' })

    expect(listener).toHaveBeenCalledTimes(1)
    expect(listener.mock.calls[0][0].detail).toEqual({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
    })
    window.removeEventListener('tianshu-theme-change', listener)
  })

  test('本地配置可往返，损坏配置回退默认且存储异常不阻断', () => {
    const dark = { ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' }
    writeLocalTheme(window.localStorage, dark)
    expect(readLocalTheme(window.localStorage)).toEqual(dark)

    window.localStorage.setItem(LOCAL_THEME_STORAGE_KEY, 'not-json')
    expect(readLocalTheme(window.localStorage)).toEqual(DEFAULT_THEME_CONFIG)

    const deniedStorage = {
      getItem() { throw new Error('denied') },
      setItem() { throw new Error('denied') },
    }
    expect(readLocalTheme(deniedStorage)).toEqual(DEFAULT_THEME_CONFIG)
    expect(() => writeLocalTheme(deniedStorage, dark)).not.toThrow()
  })

  test('启动阶段读取本地配置并立即应用', () => {
    window.localStorage.setItem(LOCAL_THEME_STORAGE_KEY, JSON.stringify({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
    }))

    bootstrapLocalTheme(window.localStorage)

    expect(document.documentElement.dataset.theme).toBe('dark')
  })
})
