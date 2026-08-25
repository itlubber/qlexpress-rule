<template>
  <div class="rule-draft-source-shell">
    <div
      v-if="visible"
      class="rule-draft-read-only"
      data-testid="draft-read-only"
      role="dialog"
      aria-modal="true"
      aria-label="规则只读提示"
      aria-live="polite"
    >
      <div class="rule-draft-read-only__card">
        <app-icon :name="loading ? 'Loading' : 'Lock'" />
        <strong>{{
          loading
            ? '正在确认草稿状态'
            : loadError
              ? '当前节点加载失败'
              : hasEditableDraft
                ? '当前正在查看历史版本'
                : '当前规则没有可编辑草稿'
        }}</strong>
        <span v-if="loading">正在确认草稿和当前查看节点。</span>
        <span v-else-if="loadError">无法显示当前节点，请返回后重试。</span>
        <span v-else>
          <template v-if="revisionLabel">{{ revisionLabel }}：</template>
          <template v-if="hasEditableDraft">
            当前内容只读；规则已有待修改修订，继续编辑将打开该修订。
          </template>
          <template v-else-if="canFork && revisionState === 'LEGACY'">
            该规则来自旧版历史内容，只读展示；点击“开始编辑”后创建草稿。
          </template>
          <template v-else-if="canFork">
            当前内容只读；点击“开始编辑”后基于此节点创建草稿。
          </template>
          <template v-else-if="revisionState === 'REVIEW'">
            需前往规则生命周期退回后编辑。
          </template>
          <template v-else>
            当前内容可查看；如需修改，请先在规则生命周期中创建或退回草稿。
          </template>
        </span>
        <rule-designer-version-select
          v-if="sourceOptions.length"
          :options="sourceOptions"
          :model-value="selectedSource"
          :loading="sourceLoading"
          @change="$emit('change-source', $event)"
        />
        <div class="rule-draft-read-only__actions">
          <el-button
            size="small"
            data-testid="draft-read-only-back"
            @click="$emit('go-back')"
          >
            返回
          </el-button>
          <el-button
            v-if="canFork && !loading && !loadError"
            type="primary"
            size="small"
            data-testid="fork-view-revision"
            @click="$emit('fork')"
          >
            {{ hasEditableDraft ? '打开待修改版本' : '开始编辑' }}
          </el-button>
          <el-button
            v-else-if="!loading"
            type="primary"
            size="small"
            data-testid="go-rule-lifecycle"
            @click="$emit('go-lifecycle')"
          >
            前往规则生命周期
          </el-button>
        </div>
      </div>
    </div>
    <div v-else-if="sourceOptions.length" class="rule-draft-current-source">
      <rule-designer-version-select
        :options="sourceOptions"
        :model-value="selectedSource"
        :loading="sourceLoading"
        @change="$emit('change-source', $event)"
      />
    </div>
  </div>
</template>

<script>
import RuleDesignerVersionSelect from './RuleDesignerVersionSelect.vue'

export default {
  name: 'RuleDraftReadOnly',
  components: { RuleDesignerVersionSelect },
  data() {
    return {
      inertedSiblings: [],
    }
  },
  props: {
    visible: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    loadError: { type: Boolean, default: false },
    revisionLabel: { type: String, default: '' },
    revisionState: { type: String, default: '' },
    canFork: { type: Boolean, default: false },
    hasEditableDraft: { type: Boolean, default: false },
    sourceOptions: { type: Array, default: () => [] },
    selectedSource: { type: String, default: '' },
    sourceLoading: { type: Boolean, default: false },
  },
  emits: ['change-source', 'fork', 'go-back', 'go-lifecycle'],
  mounted() {
    this.syncInertSiblings()
  },
  updated() {
    this.syncInertSiblings()
  },
  beforeUnmount() {
    this.clearInertSiblings()
  },
  methods: {
    clearInertSiblings() {
      this.inertedSiblings.forEach(({ element, added }) => {
        if (added) element.removeAttribute('inert')
      })
      this.inertedSiblings = []
    },
    syncInertSiblings() {
      this.clearInertSiblings()
      if (!this.visible || !this.$el?.parentElement) return
      this.inertedSiblings = Array.from(this.$el.parentElement.children)
        .filter((element) => element !== this.$el)
        .map((element) => {
          const added = !element.hasAttribute('inert')
          if (added) element.setAttribute('inert', '')
          return { element, added }
        })
    },
  },
}
</script>

<style lang="scss" scoped>
.rule-draft-source-shell {
  display: contents;
}

.rule-draft-read-only {
  position: absolute;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 72px;
  background: rgba(248, 250, 252, 0.52);
}

.rule-draft-read-only__card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  max-width: min(960px, calc(100vw - 48px));
  padding: 14px 18px;
  color: #475569;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);

  strong {
    color: #1e293b;
    white-space: nowrap;
  }

  span {
    font-size: 13px;
  }
}

.rule-draft-current-source {
  display: flex;
  justify-content: flex-end;
  padding: 8px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.rule-draft-read-only__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
</style>
