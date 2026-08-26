import {
  DEFAULT_THEME_CONFIG,
  createPrimaryScale,
  mixThemeColors,
  normalizeThemeConfig,
  resolveAccentPreset,
} from './themeConfig'

export const LOCAL_THEME_STORAGE_KEY = 'tianshu-ui-theme-v1'

export function applyTheme(config, root = document.documentElement) {
  const normalized = normalizeThemeConfig(config)
  const preset = resolveAccentPreset(normalized.accentPreset)
  const scale = createPrimaryScale(preset.primary)
  const secondaryScale = createPrimaryScale(preset.secondary)
  const hintPalette = createHintPalette(
    preset.primary,
    preset.secondary,
    normalized.colorScheme
  )
  const actionPalette = createActionPalette(
    preset.primary,
    normalized.colorScheme
  )

  root.dataset.theme = normalized.colorScheme.toLowerCase()
  root.dataset.sidebarTheme = normalized.sidebarTheme.toLowerCase()
  root.classList.toggle(
    'theme-content--fixed',
    normalized.contentWidth === 'FIXED'
  )
  root.classList.remove('theme-sidebar--static')
  root.classList.toggle('theme-color-weak', normalized.colorWeak)

  setProperty(root, '--tianshu-brand-background', preset.background)
  setProperty(
    root,
    '--tianshu-brand-gradient',
    preset.kind === 'gradient' ? preset.background : 'none'
  )
  setProperty(root, '--tianshu-brand-foreground', preset.foreground)
  applyColorScale(root, '--el-color-primary', scale)
  applyColorScale(root, '--tianshu-color-secondary', secondaryScale)
  applyColorScale(root, '--el-color-info', hintPalette.info.scale)
  applyColorScale(root, '--el-color-success', hintPalette.success.scale)
  applyColorScale(root, '--el-color-warning', hintPalette.warning.scale)
  applyColorScale(root, '--el-color-danger', hintPalette.danger.scale)
  for (const [type, tone] of Object.entries(hintPalette)) {
    setProperty(root, `--tianshu-${type}-bg`, tone.bg)
    setProperty(root, `--tianshu-${type}-border`, tone.border)
    setProperty(root, `--tianshu-${type}-text`, tone.text)
    setProperty(root, `--tianshu-${type}-foreground`, tone.foreground)
  }
  for (const [role, tone] of Object.entries(actionPalette)) {
    setProperty(root, `--tianshu-action-${role}`, tone.color)
    setProperty(root, `--tianshu-action-${role}-rgb`, tone.rgb)
  }
  setProperty(root, '--tianshu-focus-ring', `rgba(${scale.rgb}, 0.18)`)
  setProperty(root, '--tianshu-primary-shadow', `rgba(${scale.rgb}, 0.16)`)

  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    window.dispatchEvent(new CustomEvent('tianshu-theme-change', {
      detail: { ...normalized },
    }))
  }
  return normalized
}

export function readLocalTheme(storage) {
  if (!storage || typeof storage.getItem !== 'function') {
    return { ...DEFAULT_THEME_CONFIG }
  }
  try {
    const raw = storage.getItem(LOCAL_THEME_STORAGE_KEY)
    return raw ? normalizeThemeConfig(JSON.parse(raw)) : { ...DEFAULT_THEME_CONFIG }
  } catch (error) {
    return { ...DEFAULT_THEME_CONFIG }
  }
}

export function writeLocalTheme(storage, config) {
  if (!storage || typeof storage.setItem !== 'function') return
  try {
    storage.setItem(
      LOCAL_THEME_STORAGE_KEY,
      JSON.stringify(normalizeThemeConfig(config))
    )
  } catch (error) {
    // 本地存储不可用时仍保持当前页面主题。
  }
}

export function bootstrapLocalTheme(
  storage,
  root = document.documentElement
) {
  return applyTheme(readLocalTheme(storage), root)
}

function setProperty(root, name, value) {
  root.style.setProperty(name, value)
}

function applyColorScale(root, prefix, scale) {
  setProperty(root, `${prefix}-rgb`, scale.rgb)
  setProperty(root, prefix, scale.primary)
  for (let index = 1; index <= 9; index += 1) {
    setProperty(root, `${prefix}-light-${index}`, scale[`light${index}`])
  }
  setProperty(root, `${prefix}-dark-1`, scale.dark1)
  setProperty(root, `${prefix}-dark-2`, scale.dark2)
}

function createHintPalette(primary, secondary, colorScheme) {
  const info = createPrimaryScale(primary)
  const success = createPrimaryScale(secondary)
  const warning = createPrimaryScale(mixThemeColors(primary, secondary, 0.5))
  const danger = createPrimaryScale(mixThemeColors(secondary, '#000000', 0.1))
  const dark = colorScheme === 'DARK'

  return {
    info: createHintTone(info, dark, 'soft'),
    success: createHintTone(success, dark, 'soft'),
    warning: createHintTone(warning, dark, 'medium'),
    danger: createHintTone(danger, dark, 'strong'),
  }
}

function createHintTone(scale, dark, strength) {
  const alpha = {
    soft: { bg: 0.18, border: 0.42 },
    medium: { bg: 0.22, border: 0.48 },
    strong: { bg: 0.28, border: 0.6 },
  }[strength]

  if (dark) {
    return {
      scale,
      bg: `rgba(${scale.rgb}, ${alpha.bg})`,
      border: `rgba(${scale.rgb}, ${alpha.border})`,
      text: mixThemeColors(scale.primary, '#FFFFFF', 0.7),
      foreground: readableForeground(scale.primary),
    }
  }
  return {
    scale,
    bg: strength === 'strong' ? scale.light8 : scale.light9,
    border: strength === 'soft' ? scale.light7 : scale.light6,
    text: mixThemeColors(scale.primary, '#111827', 0.55),
    foreground: readableForeground(scale.primary),
  }
}

function createActionPalette(primary, colorScheme) {
  const themedBases = {
    edit: primary,
    detail: '#0B6BFF',
    execute: '#FF5A00',
    inspect: '#E000C5',
    configure: '#7A18FF',
    version: '#3154F4',
    compare: '#F04400',
    rollback: '#F0006B',
    global: '#F00083',
    publish: '#FF7900',
    reset: '#0052FF',
    disable: '#D500E6',
    add: '#A600FF',
    docs: '#D800B6',
    delete: '#F00024',
  }
  const dark = colorScheme === 'DARK'

  return Object.fromEntries(Object.entries(themedBases).map(([role, base]) => {
    const color = ensureActionContrast(base, dark)
    return [role, {
      color,
      rgb: createPrimaryScale(color).rgb,
    }]
  }))
}

function ensureActionContrast(color, dark) {
  const background = dark ? '#151D31' : '#FFFFFF'
  const target = dark ? '#FFFFFF' : '#111827'
  if (contrastRatio(color, background) >= 4.5) return color

  for (let weight = 0.08; weight <= 0.8; weight += 0.08) {
    const candidate = mixThemeColors(color, target, weight)
    if (contrastRatio(candidate, background) >= 4.5) return candidate
  }
  return target
}

function contrastRatio(first, second) {
  const firstLuminance = relativeLuminance(first)
  const secondLuminance = relativeLuminance(second)
  const lighter = Math.max(firstLuminance, secondLuminance)
  const darker = Math.min(firstLuminance, secondLuminance)
  return (lighter + 0.05) / (darker + 0.05)
}

function relativeLuminance(color) {
  return String(color)
    .replace('#', '')
    .match(/.{2}/g)
    .map(value => Number.parseInt(value, 16) / 255)
    .map(channel => channel <= 0.03928
      ? channel / 12.92
      : ((channel + 0.055) / 1.055) ** 2.4)
    .reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0)
}

function readableForeground(background) {
  const luminance = relativeLuminance(background)
  const whiteContrast = 1.05 / (luminance + 0.05)
  const darkContrast = (luminance + 0.05) / 0.058
  return whiteContrast >= darkContrast ? '#FFFFFF' : '#19103B'
}
