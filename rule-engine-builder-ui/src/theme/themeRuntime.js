import {
  DEFAULT_THEME_CONFIG,
  createPrimaryScale,
  normalizeThemeConfig,
  resolveAccentPreset,
} from './themeConfig'

export const LOCAL_THEME_STORAGE_KEY = 'tianshu-ui-theme-v1'

export function applyTheme(config, root = document.documentElement) {
  const normalized = normalizeThemeConfig(config)
  const preset = resolveAccentPreset(normalized.accentPreset)
  const scale = createPrimaryScale(preset.primary)

  root.dataset.theme = normalized.colorScheme.toLowerCase()
  root.dataset.sidebarTheme = normalized.sidebarTheme.toLowerCase()
  root.classList.toggle(
    'theme-content--fixed',
    normalized.contentWidth === 'FIXED'
  )
  root.classList.toggle('theme-sidebar--static', !normalized.fixedSidebar)
  root.classList.toggle('theme-color-weak', normalized.colorWeak)

  setProperty(root, '--tianshu-brand-background', preset.background)
  setProperty(
    root,
    '--tianshu-brand-gradient',
    preset.kind === 'gradient' ? preset.background : 'none'
  )
  setProperty(root, '--tianshu-brand-foreground', preset.foreground)
  setProperty(root, '--el-color-primary-rgb', scale.rgb)
  setProperty(root, '--el-color-primary', scale.primary)
  for (let index = 1; index <= 9; index += 1) {
    setProperty(root, `--el-color-primary-light-${index}`, scale[`light${index}`])
  }
  setProperty(root, '--el-color-primary-dark-1', scale.dark1)
  setProperty(root, '--el-color-primary-dark-2', scale.dark2)
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
