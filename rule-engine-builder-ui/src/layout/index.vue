<template>
  <div class="layout-shell">
    <layout-sidebar
      :width="sidebarWidth"
      :compact="isSidebarCompact"
      :active-menu="activeMenuIndex"
      :menus="sidebarMenus"
      :login-enabled="loginEnabled"
      :username="username"
      :avatar-initial="avatarInitial"
      @navigate="navigateTo"
      @toggle-collapse="toggleSidebar"
      @resize="handleSidebarResize"
      @resize-end="handleSidebarResizeEnd"
      @logout="doLogout"
    />
    <section class="layout-workspace">
      <workspace-tabs
        :tabs="workspaceTabs"
        :active-path="activeTabPath"
        @activate="activateTab"
        @operate="handleTabOperation"
      />
      <project-context-bar />
      <main class="layout-main">
        <router-view v-slot="{ Component }">
          <keep-alive :max="12">
            <component
              :is="Component"
              v-if="$route.meta.keepAlive"
              :key="currentViewKey"
            />
          </keep-alive>
          <component
            :is="Component"
            v-if="!$route.meta.keepAlive"
            :key="currentViewKey"
          />
        </router-view>
      </main>
    </section>
    <button
      v-if="!themeDrawerOpen"
      type="button"
      class="theme-settings-trigger"
      aria-label="主题设置"
      title="主题设置"
      @click="openThemeSettings"
    >
      <app-icon name="Setting" />
    </button>
    <theme-settings-drawer
      v-model="themeDrawerOpen"
      :config="confirmedTheme"
      :saving="themeSaving"
      @preview="handleThemePreview"
      @save="saveTheme"
      @cancel="cancelThemePreview"
    />
  </div>
</template>

<script>
import { isNavigationFailure } from 'vue-router'
import LayoutSidebar from '@/layout/components/LayoutSidebar.vue'
import WorkspaceTabs from '@/layout/components/WorkspaceTabs.vue'
import ProjectContextBar from '@/components/ProjectContextBar.vue'
import ThemeSettingsDrawer from '@/components/theme/ThemeSettingsDrawer.vue'
import {
  SIDEBAR_COMPACT_THRESHOLD,
  SIDEBAR_MENUS,
  SIDEBAR_MIN_WIDTH,
  clampSidebarWidth,
  getActiveMenuIndex,
  getAvatarInitial,
  isWorkspaceRoute,
  readSidebarState,
  resolveWorkspaceShortcut,
  routeToTab,
  writeSidebarState,
} from '@/layout/layoutState'
import { readWorkspaceTabs } from '@/store/modules/workspaceTabs'
import {
  getConsoleAuthConfig,
  consoleLogout,
  getConsoleMe,
  getConsoleThemePreference,
  saveConsoleThemePreference,
} from '@/api/auth'
import { getProject } from '@/api/project'
import {
  normalizeProjectId,
  routeSupportsProjectContext,
  withProjectContext,
} from '@/utils/projectContext'
import {
  clearCurrentUser,
  filterMenus,
  setCurrentUser,
} from '@/security/permissionState'
import {
  DEFAULT_THEME_CONFIG,
  normalizeThemeConfig,
} from '@/theme/themeConfig'
import {
  applyTheme,
  readLocalTheme,
  writeLocalTheme,
} from '@/theme/themeRuntime'

function browserSessionStorage() {
  try {
    return typeof window !== 'undefined' ? window.sessionStorage : null
  } catch (e) {
    return null
  }
}

function browserLocalStorage() {
  try {
    return typeof window !== 'undefined' ? window.localStorage : null
  } catch (e) {
    return null
  }
}

export default {
  name: 'Layout',
  components: {
    LayoutSidebar,
    WorkspaceTabs,
    ProjectContextBar,
    ThemeSettingsDrawer,
  },
  data() {
    const sidebarState = readSidebarState(browserSessionStorage())
    const initialTheme = readLocalTheme(browserLocalStorage())
    return {
      sidebarWidth: sidebarState.width,
      lastExpandedWidth: sidebarState.lastExpandedWidth,
      sidebarMenus: SIDEBAR_MENUS,
      loginEnabled: false,
      username: '',
      confirmedTheme: initialTheme,
      themeDrawerOpen: false,
      themeSaving: false,
    }
  },
  computed: {
    isSidebarCompact() {
      return this.sidebarWidth < SIDEBAR_COMPACT_THRESHOLD
    },
    activeMenuIndex() {
      return getActiveMenuIndex(this.$route.path)
    },
    workspaceTabs() {
      return this.$store.getters['workspaceTabs/tabs'] || []
    },
    activeTabPath() {
      return this.$store.getters['workspaceTabs/activePath'] || ''
    },
    currentViewKey() {
      const viewKey = this.$store.getters['workspaceTabs/viewKey']
      const baseKey = viewKey
        ? viewKey(this.$route.fullPath)
        : this.$route.fullPath
      const current = this.$store.state.currentProject
      const projectId = normalizeProjectId(current && current.id)
      return routeSupportsProjectContext(this.$route.path) && projectId
        ? `${baseKey}::project:${projectId}`
        : baseKey
    },
    avatarInitial() {
      return getAvatarInitial(this.username)
    },
  },
  watch: {
    '$route.fullPath'() {
      this.openCurrentRoute()
      this.syncProjectContext()
    },
  },
  created() {
    applyTheme(this.confirmedTheme)
    this.restoreWorkspaceTabs()
  },
  async mounted() {
    window.addEventListener('keydown', this.handleWorkspaceShortcut, true)
    await this.refreshAuthBar()
    await this.syncProjectContext()
  },
  beforeUnmount() {
    window.removeEventListener('keydown', this.handleWorkspaceShortcut, true)
  },
  methods: {
    routeTab(route) {
      const tab = routeToTab(route)
      if (!route || route.name !== 'ExpressionEditor') return tab
      const getter = this.$store.getters['expressionSessions/sessionById']
      const session =
        typeof getter === 'function' ? getter(route.params.sessionId) : null
      return {
        ...tab,
        title: session && session.title ? session.title : tab.title,
      }
    },
    restoreWorkspaceTabs() {
      if (!isWorkspaceRoute(this.$route)) return
      const cached = readWorkspaceTabs(browserSessionStorage())
      const cachedTabs = cached.tabs
        .map((tab) => {
          try {
            const resolved = this.$router.resolve(tab.fullPath)
            return isWorkspaceRoute(resolved) ? this.routeTab(resolved) : null
          } catch (e) {
            return null
          }
        })
        .filter(Boolean)
      this.$store.dispatch('workspaceTabs/restore', {
        cachedTabs,
        currentTab: this.routeTab(this.$route),
      })
    },
    openCurrentRoute() {
      if (!isWorkspaceRoute(this.$route)) return
      this.$store.dispatch('workspaceTabs/open', this.routeTab(this.$route))
    },
    navigateTo(fullPath) {
      if (!fullPath || fullPath === this.$route.fullPath) return
      const target = withProjectContext(
        fullPath,
        this.$store.state.currentProject && this.$store.state.currentProject.id
      )
      return this.$router.push(target).catch((error) => {
        if (isNavigationFailure(error)) return
        throw error
      })
    },
    async syncProjectContext() {
      this.$store.commit('INVALIDATE_PROJECT_CONTEXT_REQUESTS')
      const generation = this.$store.state.projectContextGeneration
      if (this.$route.path === '/project') {
        if (this.$store.state.currentProject) {
          this.$store.commit('SET_CURRENT_PROJECT', null)
        }
        return
      }
      if (!routeSupportsProjectContext(this.$route.path)) return
      const explicitProjectId = normalizeProjectId(
        this.$route && this.$route.query && this.$route.query.projectId
      )
      const current = this.$store.state.currentProject
      const projectId =
        explicitProjectId || normalizeProjectId(current && current.id)
      if (!projectId) return
      try {
        const response = await getProject(projectId)
        if (generation !== this.$store.state.projectContextGeneration) return
        if (!response.data) throw new Error('项目不存在')
        this.$store.commit('SET_CURRENT_PROJECT', response.data)
      } catch (e) {
        if (generation !== this.$store.state.projectContextGeneration) return
        this.$store.commit('SET_CURRENT_PROJECT', null)
        this.$message.error(e.message || '加载项目上下文失败')
      }
    },
    activateTab(fullPath) {
      if (!fullPath) return
      this.$store.dispatch('workspaceTabs/activate', fullPath)
      return this.navigateTo(fullPath)
    },
    handleWorkspaceShortcut(event) {
      const command = resolveWorkspaceShortcut(
        event,
        this.workspaceTabs,
        this.activeTabPath
      )
      if (!command) return
      if (event && typeof event.preventDefault === 'function')
        event.preventDefault()
      if (command.type === 'activate')
        return this.activateTab(command.targetPath)
      return this.handleTabOperation(command)
    },
    async handleTabOperation({ operation, targetPath }) {
      if (operation === 'refresh') {
        if (targetPath !== this.$route.fullPath) {
          this.$store.dispatch('workspaceTabs/activate', targetPath)
          await this.navigateTo(targetPath)
        }
        await this.$store.dispatch('workspaceTabs/refresh', targetPath)
        return
      }

      const result = await this.$store.dispatch('workspaceTabs/close', {
        operation,
        targetPath,
      })
      if (result.nextPath !== this.$route.fullPath) {
        await this.navigateTo(result.nextPath)
      }
    },
    handleSidebarResize(width) {
      const nextWidth = clampSidebarWidth(width)
      this.sidebarWidth = nextWidth
      if (nextWidth >= SIDEBAR_COMPACT_THRESHOLD) {
        this.lastExpandedWidth = nextWidth
      }
    },
    handleSidebarResizeEnd(width) {
      this.handleSidebarResize(width)
      this.persistSidebarState()
    },
    toggleSidebar() {
      if (this.isSidebarCompact) {
        this.sidebarWidth = Math.max(
          SIDEBAR_COMPACT_THRESHOLD,
          this.lastExpandedWidth
        )
      } else {
        this.lastExpandedWidth = this.sidebarWidth
        this.sidebarWidth = SIDEBAR_MIN_WIDTH
      }
      this.persistSidebarState()
    },
    persistSidebarState() {
      writeSidebarState(browserSessionStorage(), {
        width: this.sidebarWidth,
        lastExpandedWidth: this.lastExpandedWidth,
      })
    },
    openThemeSettings() {
      this.themeDrawerOpen = true
    },
    handleThemePreview(theme) {
      applyTheme(theme)
    },
    cancelThemePreview(snapshot) {
      const restored = normalizeThemeConfig(snapshot || this.confirmedTheme)
      this.confirmedTheme = restored
      applyTheme(restored)
    },
    confirmTheme(theme) {
      const confirmed = normalizeThemeConfig(theme)
      this.confirmedTheme = confirmed
      applyTheme(confirmed)
      writeLocalTheme(browserLocalStorage(), confirmed)
      return confirmed
    },
    async saveTheme(theme) {
      if (this.themeSaving) return
      const candidate = normalizeThemeConfig(theme)
      this.themeSaving = true
      try {
        let saved = candidate
        if (this.loginEnabled && this.username) {
          const response = await saveConsoleThemePreference(candidate)
          saved = normalizeThemeConfig(response && response.data)
        }
        this.confirmTheme(saved)
        this.themeDrawerOpen = false
        this.$message.success('主题设置已保存')
      } catch (e) {
        this.$message.error(e.message || '保存主题设置失败')
      } finally {
        this.themeSaving = false
      }
    },
    async refreshAuthBar() {
      try {
        const cfg = await getConsoleAuthConfig()
        this.loginEnabled = !!(cfg.data && cfg.data.loginEnabled)
        if (!this.loginEnabled) {
          this.username = ''
          clearCurrentUser()
          this.sidebarMenus = filterMenus(SIDEBAR_MENUS)
          this.confirmTheme(readLocalTheme(browserLocalStorage()))
          return
        }
        const me = await getConsoleMe()
        this.username = (me.data && me.data.username) || ''
        if (me.data && Array.isArray(me.data.permissions)) {
          setCurrentUser(me.data)
        } else {
          clearCurrentUser()
        }
        this.sidebarMenus = filterMenus(SIDEBAR_MENUS)
        try {
          const theme = await getConsoleThemePreference()
          this.confirmTheme(theme && theme.data
            ? theme.data
            : DEFAULT_THEME_CONFIG)
        } catch (themeError) {
          this.confirmTheme(DEFAULT_THEME_CONFIG)
          this.$message.warning(
            themeError.message || '加载主题设置失败，已使用默认主题'
          )
        }
      } catch (e) {
        this.loginEnabled = false
        this.username = ''
        clearCurrentUser()
        this.$store.commit('SET_CURRENT_PROJECT', null)
        this.sidebarMenus = filterMenus(SIDEBAR_MENUS)
      }
    },
    async doLogout() {
      try {
        await consoleLogout()
      } finally {
        clearCurrentUser()
        this.$store.commit('SET_CURRENT_PROJECT', null)
        this.$router.replace({ path: '/login' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.layout-shell {
  display: flex;
  width: 100%;
  height: 100vh;
  min-width: 0;
  overflow: hidden;
  background: var(--tianshu-bg-page);
}
.layout-workspace {
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex: 1;
  flex-direction: column;
}
.layout-main {
  min-width: 0;
  min-height: 0;
  padding: 16px;
  overflow-x: hidden;
  overflow-y: auto;
  flex: 1;
  box-sizing: border-box;
  background: var(--tianshu-bg-page);
}
.theme-settings-trigger {
  position: fixed;
  z-index: 1900;
  top: 50%;
  right: 0;
  display: flex;
  width: 42px;
  height: 42px;
  padding: 0;
  align-items: center;
  justify-content: center;
  color: var(--tianshu-brand-foreground, #ffffff);
  font-size: 18px;
  background: var(--tianshu-brand-background, #2639e9);
  border: 0;
  border-radius: 8px 0 0 8px;
  box-shadow: var(--tianshu-shadow-medium, 0 8px 22px rgba(15, 23, 42, 0.18));
  cursor: pointer;
  transform: translateY(-50%);

  &:hover,
  &:focus-visible {
    width: 46px;
    outline: 2px solid var(--tianshu-focus-ring, rgba(38, 57, 233, 0.18));
    outline-offset: 2px;
  }
}
</style>
