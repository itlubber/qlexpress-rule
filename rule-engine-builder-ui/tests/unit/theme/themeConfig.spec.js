import {
  ACCENT_PRESETS,
  DEFAULT_THEME_CONFIG,
  normalizeThemeConfig,
  resolveAccentPreset,
} from '@/theme/themeConfig'

describe('themeConfig — 配置白名单和固定色卡', () => {
  test('损坏、缺字段或未来版本配置整体回退默认值', () => {
    expect(normalizeThemeConfig(null)).toEqual(DEFAULT_THEME_CONFIG)
    expect(normalizeThemeConfig({
      schemaVersion: 2,
      colorScheme: 'DARK',
      accentPreset: 'url(evil)',
    })).toEqual(DEFAULT_THEME_CONFIG)
    expect(normalizeThemeConfig({
      ...DEFAULT_THEME_CONFIG,
      unknown: true,
    })).toEqual(DEFAULT_THEME_CONFIG)
    expect(normalizeThemeConfig(null)).not.toBe(DEFAULT_THEME_CONFIG)
  })

  test('有效配置只返回白名单字段且保持布尔值', () => {
    const value = {
      schemaVersion: 1,
      colorScheme: 'DARK',
      accentPreset: 'PEACH_PINK_GRADIENT',
      sidebarTheme: 'LIGHT',
      contentWidth: 'FIXED',
      fixedSidebar: false,
      colorWeak: true,
    }

    expect(normalizeThemeConfig(value)).toEqual(value)
  })

  test('包含 8 个纯色和 4 个固定渐变预设且主题蓝排首位', () => {
    expect(ACCENT_PRESETS.filter(item => item.kind === 'solid')).toHaveLength(8)
    expect(ACCENT_PRESETS.filter(item => item.kind === 'gradient')).toHaveLength(4)
    expect(ACCENT_PRESETS[0]).toMatchObject({
      id: 'THEME_BLUE',
      name: '主题蓝',
      primary: '#2639E9',
      background: '#2639E9',
    })
  })

  test('渐变预设同时提供实色主色和固定渐变背景', () => {
    expect(resolveAccentPreset('LIQUID_PURPLE_GRADIENT')).toEqual(
      expect.objectContaining({
        primary: '#873FF2',
        foreground: '#FFFFFF',
        background: 'linear-gradient(135deg, #19103B 0%, #873FF2 100%)',
      })
    )
  })
})
