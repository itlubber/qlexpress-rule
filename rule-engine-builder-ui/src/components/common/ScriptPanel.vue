<template>
  <div class="script-panel" :class="{ 'is-collapsed': !expanded }">
    <div class="sp-header" @click="toggleExpand">
      <div class="sp-header-left">
        <el-icon class="sp-icon"><el-icon-s-promotion /></el-icon>
        <span class="sp-title">编译脚本预览</span>
        <el-tag :type="statusTag.type" size="small" class="sp-status-tag">
          {{ statusTag.text }}
        </el-tag>
        <span class="sp-lifecycle-guidance" data-testid="designer-lifecycle-guidance">
          保存并编译仅更新草稿，审核发布请
          <button
            type="button"
            class="sp-lifecycle-link"
            aria-label="前往规则生命周期审核发布"
            title="前往规则生命周期审核发布"
            @click.stop="$emit('go-lifecycle')"
          >
            前往规则生命周期
          </button>
          。
        </span>
      </div>
      <div class="sp-header-right" @click.stop>
        <el-tooltip
          :content="expanded ? '收起脚本面板' : '展开脚本面板'"
          placement="top"
        >
          <el-button
            size="small"
            circle
            :icon="expandIcon"
            class="sp-toggle-btn"
            :aria-label="expanded ? '收起脚本面板' : '展开脚本面板'"
            :title="expanded ? '收起脚本面板' : '展开脚本面板'"
            @click.stop="toggleExpand"
          />
        </el-tooltip>
      </div>
    </div>

    <transition name="sp-slide">
      <div v-show="expanded" class="sp-body">
        <div class="sp-statusbar">
          <span
            v-if="content.compileMessage"
            class="sp-statusbar-item"
            :class="{ 'sp-error': content.compileStatus === 2 }"
          >
            <el-icon v-if="content.compileStatus === 2">
              <el-icon-close-circle />
            </el-icon>
            {{ content.compileMessage }}
          </span>
          <div class="sp-statusbar-spacer" />
          <el-button-group>
            <el-button
              size="small"
              :icon="ElIconRefresh"
              :loading="compiling"
              @click="handleCompile"
            >
              保存并编译
            </el-button>
            <el-button
              size="small"
              :icon="ElIconDocumentCopy"
              @click="copyScript"
            >
              复制
            </el-button>
          </el-button-group>
        </div>

        <div class="sp-editor-container">
          <div class="sp-line-numbers" aria-hidden="true">
            <div v-for="n in lineCount" :key="n" class="sp-line-num">
              {{ n }}
            </div>
          </div>
          <textarea
            ref="editorRef"
            v-model="editScript"
            class="sp-editor readonly"
            readonly
            placeholder="请先点击「保存并编译」生成脚本"
            spellcheck="false"
            autocomplete="off"
            autocorrect="off"
            autocapitalize="off"
            @scroll="syncScroll"
          />
        </div>

        <div class="sp-footer">
          <span class="sp-footer-tip">
            <el-icon><el-icon-info /></el-icon>
            此处仅预览由可视化配置编译生成的 QLExpress 脚本。
          </span>
          <span class="sp-line-info">
            {{ lineCount }} 行 / {{ editScript.length }} 字符
          </span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  Promotion as ElIconSPromotion,
  CircleClose as ElIconCloseCircle,
  InfoFilled as ElIconInfo,
  Refresh as ElIconRefresh,
  DocumentCopy as ElIconDocumentCopy,
  ArrowDown as ElIconArrowDown,
  ArrowUp as ElIconArrowUp,
} from '@element-plus/icons-vue'

export default {
  name: 'ScriptPanel',
  emits: ['go-lifecycle'],
  components: {
    ElIconSPromotion,
    ElIconCloseCircle,
    ElIconInfo,
  },
  props: {
    definitionId: { type: [String, Number], required: true },
    onBeforeCompile: { type: Function, default: null },
  },
  data() {
    return {
      expanded: false,
      content: {},
      editScript: '',
      compiling: false,
      ElIconRefresh: markRaw(ElIconRefresh),
      ElIconDocumentCopy: markRaw(ElIconDocumentCopy),
    }
  },
  computed: {
    lineCount() {
      return (this.editScript.match(/\n/g) || []).length + 1
    },
    statusTag() {
      const status = this.content.compileStatus
      if (status === 1) return { type: 'success', text: '已编译' }
      if (status === 2) return { type: 'danger', text: '编译失败' }
      return { type: 'info', text: '未编译' }
    },
    expandIcon() {
      return this.expanded ? ElIconArrowDown : ElIconArrowUp
    },
  },
  methods: {
    toggleExpand() {
      this.expanded = !this.expanded
    },
    async handleCompile() {
      if (!this.onBeforeCompile) {
        this.$message.warning('当前设计器未提供保存编译能力')
        return null
      }
      this.compiling = true
      try {
        const response = await this.onBeforeCompile()
        if (response === false) return false
        const result =
          response && response.data !== undefined ? response.data : response
        this.content = {
          compileStatus: result?.compileSuccess ? 1 : 2,
          compileMessage: result?.compileMessage || '',
        }
        this.editScript = result?.revision?.compiledScript || ''
        if (result?.compileSuccess) {
          this.$message.success('编译成功')
        } else {
          this.$message.error(
            '编译失败: ' + (result?.compileMessage || '未知错误')
          )
        }
        return result
      } catch (e) {
        this.$message.error('保存并编译失败: ' + (e.message || '未知错误'))
        throw e
      } finally {
        this.compiling = false
      }
    },
    copyScript() {
      if (!this.editScript) {
        this.$message.warning('暂无脚本，请先编译')
        return
      }
      if (navigator.clipboard) {
        navigator.clipboard.writeText(this.editScript).then(() => {
          this.$message.success('脚本已复制到剪贴板')
        })
      } else {
        const editor = this.$refs.editorRef
        editor.select()
        document.execCommand('copy')
        this.$message.success('脚本已复制')
      }
    },
    syncScroll(e) {
      const lineNumbers = this.$el.querySelector('.sp-line-numbers')
      if (lineNumbers) lineNumbers.scrollTop = e.target.scrollTop
    },
  },
}
</script>

<style lang="scss" scoped>
$editor-bg: #1e1e2e;
$editor-text: #cdd6f4;
$editor-line-bg: #181825;
$editor-line-text: #9399b2;
$editor-border: #313244;

.script-panel {
  margin-top: 16px;
  overflow: hidden;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
}

.sp-header,
.sp-header-left,
.sp-header-right,
.sp-statusbar,
.sp-footer {
  display: flex;
  align-items: center;
}

.sp-header {
  justify-content: space-between;
  padding: 8px 14px;
  cursor: pointer;
  background: #fafafa;
}

.sp-header-left,
.sp-header-right,
.sp-statusbar {
  gap: 8px;
}

.sp-header-left {
  min-width: 0;
  flex-wrap: wrap;
}

.sp-icon {
  color: var(--el-color-primary);
}

.sp-title {
  font-size: 13px;
  font-weight: 700;
  color: #333333;
}

.sp-lifecycle-guidance {
  color: #64748b;
  font-size: 12px;
}

.sp-lifecycle-link {
  padding: 0;
  color: var(--el-color-primary);
  font: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;

  &:hover,
  &:focus-visible {
    text-decoration: underline;
  }
}

.sp-toggle-btn {
  border: none;
}

.sp-body {
  background: $editor-bg;
}

.sp-statusbar {
  padding: 6px 12px;
  background: #11111b;
  border-bottom: 1px solid $editor-border;
}

.sp-statusbar-item {
  font-size: 11px;
  color: #9399b2;
}

.sp-error {
  color: #ff6b6b;
}

.sp-statusbar-spacer {
  flex: 1;
}

.sp-statusbar :deep(.el-button) {
  min-height: 26px;
  padding: 6px 12px;
  color: #1d39c4 !important;
  font-weight: 600;
  background: #f8faff;
  border-color: #adc6ff !important;
}

.sp-statusbar :deep(.el-button:hover) {
  color: #1d39c4 !important;
  background: #eaf2ff;
  border-color: #85a5ff !important;
}

.sp-editor-container {
  display: flex;
  min-height: 200px;
  max-height: 420px;
  overflow: hidden;
}

.sp-line-numbers {
  min-width: 42px;
  padding: 12px 8px 12px 12px;
  overflow: hidden;
  color: $editor-line-text;
  text-align: right;
  background: $editor-line-bg;
  border-right: 1px solid $editor-border;
}

.sp-line-num,
.sp-editor,
.sp-line-info {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

.sp-line-num,
.sp-editor {
  font-size: 13px;
  line-height: 1.6;
}

.sp-editor {
  flex: 1;
  width: 100%;
  min-height: 200px;
  max-height: 420px;
  padding: 12px 16px;
  overflow: auto;
  color: $editor-text;
  resize: none;
  background: $editor-bg;
  border: none;
  outline: none;
}

.sp-footer {
  justify-content: space-between;
  padding: 5px 12px;
  color: $editor-line-text;
  background: #11111b;
  border-top: 1px solid $editor-border;
}

.sp-footer-tip,
.sp-line-info {
  font-size: 11px;
}

.sp-slide-enter-active,
.sp-slide-leave-active {
  overflow: hidden;
  transition: max-height 0.25s ease;
}

.sp-slide-enter-from,
.sp-slide-leave-to {
  max-height: 0;
}

.sp-slide-enter-to,
.sp-slide-leave-from {
  max-height: 600px;
}
</style>
