import {
  ACCENT_PRESETS,
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
    document.documentElement.removeAttribute('data-navigation-layout')
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
    expect(document.documentElement.classList.contains('theme-sidebar--static')).toBe(false)
    expect(document.documentElement.classList.contains('theme-color-weak')).toBe(true)
    expect(document.documentElement.style.getPropertyValue('--tianshu-brand-gradient'))
      .toContain('#E14ECA')
    expect(document.documentElement.style.getPropertyValue('--el-color-primary'))
      .toBe('#873FF2')
  })

  test('自定义三色渐变同步驱动品牌、主辅色和科技感滚动条令牌', () => {
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      accentMode: 'CUSTOM_GRADIENT',
      customGradientColors: ['#125DFF', '#6C3BFF', '#E94FC7'],
      customGradientType: 'LINEAR',
      customGradientAngle: 120,
      navigationLayout: 'TOP',
    })

    const style = document.documentElement.style
    expect(document.documentElement.dataset.navigationLayout).toBe('top')
    expect(style.getPropertyValue('--tianshu-brand-background')).toBe(
      'linear-gradient(120deg, #125DFF 0%, #6C3BFF 50%, #E94FC7 100%)'
    )
    expect(style.getPropertyValue('--el-color-primary')).toBe('#125DFF')
    expect(style.getPropertyValue('--tianshu-color-secondary')).toBe('#E94FC7')
    expect(style.getPropertyValue('--tianshu-scrollbar-thumb')).toContain(
      'linear-gradient(90deg, #125DFF 0%, #E94FC7 100%)'
    )
    expect(style.getPropertyValue('--tianshu-scrollbar-glow')).toBe(
      'rgba(18, 93, 255, 0.42)'
    )
  })

  test('切换主题色时同步更新辅助色和四类提示层级色', () => {
    applyTheme(DEFAULT_THEME_CONFIG)
    const bluePalette = readAppliedPalette()

    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      accentPreset: 'LIQUID_PURPLE',
    })
    const purplePalette = readAppliedPalette()

    expect(bluePalette.secondary).toBe('#F76E6C')
    expect(purplePalette.secondary).toBe('#2CC7B8')
    expect(purplePalette.infoBg).toBe('#F3ECFE')
    expect(purplePalette.successBg).toBe('#EAF9F8')
    expect(purplePalette).not.toEqual(bluePalette)
    expect(new Set(Object.values(purplePalette).filter(Boolean)).size)
      .toBeGreaterThanOrEqual(8)
  })

  test('夜间模式使用当前主题色生成更深的提示背景', () => {
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
      accentPreset: 'CRYSTAL_BLUE',
    })

    const palette = readAppliedPalette()
    expect(palette.secondary).toBe('#22B8A7')
    expect(palette.infoBg).toBe('rgba(47, 128, 237, 0.18)')
    expect(palette.successBg).toBe('rgba(34, 184, 167, 0.18)')
    expect(palette.dangerBorder).toBe('rgba(31, 166, 150, 0.6)')
  })

  test('管理列表操作色随主题切换且编辑始终使用当前主题色', () => {
    applyTheme(DEFAULT_THEME_CONFIG)
    const blueActions = readAppliedActions()

    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      accentPreset: 'LIQUID_PURPLE',
    })
    const purpleActions = readAppliedActions()

    expect(blueActions.edit).toBe('#2639E9')
    expect(purpleActions.edit).toBe('#873FF2')
    expect(new Set(Object.values(blueActions)).size).toBe(actionRoles.length)
    expect(new Set(Object.values(purpleActions)).size).toBe(actionRoles.length)
    expect(purpleActions).not.toEqual(blueActions)
    expect(isRedTone(blueActions.delete)).toBe(true)
    expect(isRedTone(purpleActions.delete)).toBe(true)
  })

  test.each(ACCENT_PRESETS.flatMap(preset => [
    ['LIGHT', preset.id],
    ['DARK', preset.id],
  ]))('%s / %s 的辅助操作使用高饱和亮色且避开黄灰绿青', (colorScheme, accentPreset) => {
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme,
      accentPreset,
    })

    const forbiddenActions = Object.entries(readAppliedActions())
      .filter(([role, color]) => role !== 'edit' && !isAllowedBrightActionTone(color))
      .map(([role]) => role)

    expect(forbiddenActions).toEqual([])
  })

  test.each(ACCENT_PRESETS.flatMap(preset => [
    ['LIGHT', preset.id],
    ['DARK', preset.id],
  ]))('%s / %s 的同一行辅助操作保持明显感知色差', (colorScheme, accentPreset) => {
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme,
      accentPreset,
    })

    const actions = readAppliedActions()
    for (const roles of coexistingSecondaryActionGroups) {
      for (let first = 0; first < roles.length; first += 1) {
        for (let second = first + 1; second < roles.length; second += 1) {
          const firstRole = roles[first]
          const secondRole = roles[second]
          expect(
            colorDistance(actions[firstRole], actions[secondRole]),
            `${firstRole} / ${secondRole}`
          ).toBeGreaterThanOrEqual(18)
        }
      }
    }
  })

  test.each(ACCENT_PRESETS.flatMap(preset => [
    ['LIGHT', preset.id, '#FFFFFF'],
    ['DARK', preset.id, '#151D31'],
  ]))('%s / %s 的操作色互不重复且在表格背景上保持可读', (colorScheme, accentPreset, surface) => {
    applyTheme({
      ...DEFAULT_THEME_CONFIG,
      colorScheme,
      accentPreset,
    })

    const actions = readAppliedActions()
    expect(new Set(Object.values(actions)).size).toBe(actionRoles.length)
    for (const [role, color] of Object.entries(actions)) {
      expect(contrastRatio(color, surface), role).toBeGreaterThanOrEqual(4.5)
    }
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

function readAppliedPalette() {
  const style = document.documentElement.style
  return {
    secondary: style.getPropertyValue('--tianshu-color-secondary'),
    infoBg: style.getPropertyValue('--tianshu-info-bg'),
    infoBorder: style.getPropertyValue('--tianshu-info-border'),
    infoText: style.getPropertyValue('--tianshu-info-text'),
    successBg: style.getPropertyValue('--tianshu-success-bg'),
    successBorder: style.getPropertyValue('--tianshu-success-border'),
    successText: style.getPropertyValue('--tianshu-success-text'),
    warningBg: style.getPropertyValue('--tianshu-warning-bg'),
    warningBorder: style.getPropertyValue('--tianshu-warning-border'),
    warningText: style.getPropertyValue('--tianshu-warning-text'),
    dangerBg: style.getPropertyValue('--tianshu-danger-bg'),
    dangerBorder: style.getPropertyValue('--tianshu-danger-border'),
    dangerText: style.getPropertyValue('--tianshu-danger-text'),
  }
}

const actionRoles = [
  'edit',
  'detail',
  'execute',
  'inspect',
  'configure',
  'version',
  'compare',
  'rollback',
  'global',
  'publish',
  'reset',
  'disable',
  'add',
  'docs',
  'delete',
]

const coexistingSecondaryActionGroups = [
  ['configure', 'reset', 'publish'],
  ['configure', 'reset', 'disable'],
  ['detail', 'execute'],
  ['execute', 'version'],
  ['inspect', 'compare', 'rollback'],
  ['detail', 'publish', 'global'],
  ['detail', 'disable', 'global'],
  ['execute', 'inspect'],
  ['execute', 'add'],
  ['detail', 'execute', 'configure', 'global'],
  ['detail', 'configure', 'docs'],
]

function readAppliedActions() {
  const style = document.documentElement.style
  return Object.fromEntries(actionRoles.map(role => [
    role,
    style.getPropertyValue(`--tianshu-action-${role}`).trim(),
  ]))
}

function isRedTone(color) {
  const { r, g, b } = rgb(color)
  return r >= 150 && r > g * 1.8 && r > b * 1.4
}

function contrastRatio(foreground, background) {
  const foregroundLuminance = relativeLuminance(rgb(foreground))
  const backgroundLuminance = relativeLuminance(rgb(background))
  const lighter = Math.max(foregroundLuminance, backgroundLuminance)
  const darker = Math.min(foregroundLuminance, backgroundLuminance)
  return (lighter + 0.05) / (darker + 0.05)
}

function relativeLuminance({ r, g, b }) {
  return [r, g, b]
    .map(value => {
      const channel = value / 255
      return channel <= 0.03928
        ? channel / 12.92
        : ((channel + 0.055) / 1.055) ** 2.4
    })
    .reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0)
}

function rgb(color) {
  const normalized = color.replace('#', '')
  return {
    r: Number.parseInt(normalized.slice(0, 2), 16),
    g: Number.parseInt(normalized.slice(2, 4), 16),
    b: Number.parseInt(normalized.slice(4, 6), 16),
  }
}

function isAllowedBrightActionTone(color) {
  const { hue, saturation, lightness } = hsl(color)
  const forbiddenHue = hue >= 35 && hue <= 205
  return !forbiddenHue && saturation >= 0.6 && lightness >= 0.32
}

function hsl(color) {
  const channels = Object.values(rgb(color)).map(value => value / 255)
  const maximum = Math.max(...channels)
  const minimum = Math.min(...channels)
  const delta = maximum - minimum
  const lightness = (maximum + minimum) / 2
  let hue = 0

  if (delta > 0) {
    if (maximum === channels[0]) {
      hue = 60 * (((channels[1] - channels[2]) / delta) % 6)
    } else if (maximum === channels[1]) {
      hue = 60 * ((channels[2] - channels[0]) / delta + 2)
    } else {
      hue = 60 * ((channels[0] - channels[1]) / delta + 4)
    }
  }

  return {
    hue: hue < 0 ? hue + 360 : hue,
    saturation: delta === 0
      ? 0
      : delta / (1 - Math.abs(2 * lightness - 1)),
    lightness,
  }
}

function colorDistance(first, second) {
  const firstLab = lab(first)
  const secondLab = lab(second)
  return Math.sqrt(
    (firstLab.lightness - secondLab.lightness) ** 2 +
    (firstLab.a - secondLab.a) ** 2 +
    (firstLab.b - secondLab.b) ** 2
  )
}

function lab(color) {
  const { r, g, b } = rgb(color)
  const [red, green, blue] = [r, g, b].map(value => {
    const channel = value / 255
    return channel <= 0.04045
      ? channel / 12.92
      : ((channel + 0.055) / 1.055) ** 2.4
  })
  const [x, y, z] = [
    (red * 0.4124 + green * 0.3576 + blue * 0.1805) / 0.95047,
    red * 0.2126 + green * 0.7152 + blue * 0.0722,
    (red * 0.0193 + green * 0.1192 + blue * 0.9505) / 1.08883,
  ].map(value => value > 0.008856
    ? Math.cbrt(value)
    : 7.787 * value + 16 / 116)

  return {
    lightness: 116 * y - 16,
    a: 500 * (x - y),
    b: 200 * (y - z),
  }
}
