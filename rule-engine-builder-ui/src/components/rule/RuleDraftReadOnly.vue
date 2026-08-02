<template>
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
            : '当前规则没有可编辑草稿'
      }}</strong>
      <span v-if="loading">正在确认草稿和当前查看节点。</span>
      <span v-else-if="loadError">无法显示当前节点，请返回后重试。</span>
      <span v-else>
        <template v-if="revisionLabel">{{ revisionLabel }}：</template>
        <template v-if="canFork">当前内容只读，可基于此节点编辑。</template>
        <template v-else-if="revisionState === 'LEGACY'">
          该规则来自旧版历史内容，只读展示；如需修改，请先前往规则生命周期创建草稿。
        </template>
        <template v-else-if="revisionState === 'REVIEW'">需前往规则生命周期退回后编辑。</template>
        <template v-else>当前内容可查看；如需修改，请先在规则生命周期中创建或退回草稿。</template>
      </span>
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
          基于此节点编辑
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
</template>

<script>
export default {
  name: 'RuleDraftReadOnly',
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
  },
  emits: ['fork', 'go-back', 'go-lifecycle'],
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
  gap: 10px;
  max-width: 560px;
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

.rule-draft-read-only__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
</style>
