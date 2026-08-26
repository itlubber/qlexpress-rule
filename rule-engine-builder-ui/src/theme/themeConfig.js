export const DEFAULT_THEME_CONFIG = Object.freeze({
  schemaVersion: 1,
  colorScheme: 'LIGHT',
  accentPreset: 'THEME_BLUE',
  sidebarTheme: 'DARK',
  contentWidth: 'FLUID',
  fixedSidebar: true,
  colorWeak: false,
})

export const ACCENT_PRESETS = Object.freeze([
  solid('THEME_BLUE', '主题蓝', '#2639E9', '#F76E6C', '#FFFFFF'),
  solid('LIQUID_PURPLE', '凝液紫', '#873FF2', '#2CC7B8', '#FFFFFF'),
  solid('PEACH_PINK', '桃粉红', '#FF6B9D', '#5B6CFF', '#19103B'),
  solid('NEON_PINK', '霓虹粉', '#E14ECA', '#3A9BFF', '#19103B'),
  solid('MIDNIGHT_PURPLE', '深夜紫', '#19103B', '#F2B35D', '#FFFFFF'),
  solid('INDIGO_PURPLE', '靛青紫', '#5B3DF5', '#2BBEC4', '#FFFFFF'),
  solid('NEBULA_BLUE', '星云蓝', '#536DFE', '#D96BB3', '#FFFFFF'),
  solid('CRYSTAL_BLUE', '晶石蓝', '#2F80ED', '#22B8A7', '#FFFFFF'),
  gradient(
    'THEME_BLUE_GRADIENT',
    '主题蓝渐变',
    '#2639E9',
    '#E66AB1',
    'linear-gradient(135deg, #2639E9 0%, #536DFE 100%)'
  ),
  gradient(
    'LIQUID_PURPLE_GRADIENT',
    '凝液紫渐变',
    '#873FF2',
    '#2CC7B8',
    'linear-gradient(135deg, #19103B 0%, #873FF2 100%)'
  ),
  gradient(
    'PEACH_PINK_GRADIENT',
    '桃粉渐变',
    '#873FF2',
    '#E14ECA',
    'linear-gradient(135deg, #873FF2 0%, #E14ECA 100%)'
  ),
  gradient(
    'NEBULA_GRADIENT',
    '星云渐变',
    '#536DFE',
    '#E14ECA',
    'linear-gradient(135deg, #536DFE 0%, #E14ECA 100%)'
  ),
])

const PRESET_BY_ID = new Map(ACCENT_PRESETS.map(item => [item.id, item]))
const CONFIG_KEYS = Object.keys(DEFAULT_THEME_CONFIG)

function solid(id, name, primary, secondary, foreground) {
  return Object.freeze({
    id,
    name,
    kind: 'solid',
    primary,
    secondary,
    foreground,
    background: primary,
  })
}

function gradient(id, name, primary, secondary, background) {
  return Object.freeze({
    id,
    name,
    kind: 'gradient',
    primary,
    secondary,
    foreground: '#FFFFFF',
    background,
  })
}

function defaultConfig() {
  return { ...DEFAULT_THEME_CONFIG }
}

export function normalizeThemeConfig(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return defaultConfig()
  }
  const keys = Object.keys(value)
  if (
    keys.length !== CONFIG_KEYS.length ||
    keys.some(key => !CONFIG_KEYS.includes(key)) ||
    value.schemaVersion !== 1 ||
    !['LIGHT', 'DARK'].includes(value.colorScheme) ||
    !PRESET_BY_ID.has(value.accentPreset) ||
    !['LIGHT', 'DARK'].includes(value.sidebarTheme) ||
    !['FLUID', 'FIXED'].includes(value.contentWidth) ||
    typeof value.fixedSidebar !== 'boolean' ||
    typeof value.colorWeak !== 'boolean'
  ) {
    return defaultConfig()
  }
  return CONFIG_KEYS.reduce((normalized, key) => {
    normalized[key] = value[key]
    return normalized
  }, {})
}

export function resolveAccentPreset(id) {
  return PRESET_BY_ID.get(id) || ACCENT_PRESETS[0]
}

export function createPrimaryScale(primary) {
  const rgb = hexToRgb(primary)
  const scale = {
    rgb: `${rgb.r}, ${rgb.g}, ${rgb.b}`,
    primary: rgbToHex(rgb),
  }
  for (let index = 1; index <= 9; index += 1) {
    scale[`light${index}`] = mixRgb(rgb, { r: 255, g: 255, b: 255 }, index / 10)
  }
  scale.dark1 = mixRgb(rgb, { r: 0, g: 0, b: 0 }, 0.1)
  scale.dark2 = mixRgb(rgb, { r: 0, g: 0, b: 0 }, 0.2)
  return scale
}

export function mixThemeColors(source, target, weight) {
  return mixRgb(hexToRgb(source), hexToRgb(target), weight)
}

function hexToRgb(value) {
  const normalized = String(value || '').replace('#', '')
  if (!/^[0-9a-f]{6}$/i.test(normalized)) {
    throw new Error('主题预设包含无效色值')
  }
  return {
    r: Number.parseInt(normalized.slice(0, 2), 16),
    g: Number.parseInt(normalized.slice(2, 4), 16),
    b: Number.parseInt(normalized.slice(4, 6), 16),
  }
}

function mixRgb(source, target, weight) {
  return rgbToHex({
    r: Math.round(source.r + (target.r - source.r) * weight),
    g: Math.round(source.g + (target.g - source.g) * weight),
    b: Math.round(source.b + (target.b - source.b) * weight),
  })
}

function rgbToHex({ r, g, b }) {
  const channel = value => Math.max(0, Math.min(255, value))
    .toString(16)
    .padStart(2, '0')
    .toUpperCase()
  return `#${channel(r)}${channel(g)}${channel(b)}`
}
