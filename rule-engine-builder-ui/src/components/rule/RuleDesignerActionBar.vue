<template>
  <section class="rule-designer-actions" aria-label="规则草稿操作">
    <div class="rule-designer-actions__status" role="status">
      <span class="rule-designer-actions__status-dot" :class="statusClass" />
      <span>{{ statusLabel }}</span>
    </div>
    <div class="rule-designer-actions__buttons">
      <button
        v-permission="'rule:edit'"
        type="button"
        class="rule-designer-actions__button rule-designer-actions__secondary"
        data-action="save"
        :disabled="!canEdit || busy"
        @click="$emit('save')"
      >
        仅保存草稿
      </button>
      <button
        v-permission="'rule:edit'"
        type="button"
        class="rule-designer-actions__button rule-designer-actions__primary"
        data-action="save-check"
        :disabled="!canEdit || busy"
        @click="$emit('save-check')"
      >
        {{ busy ? '处理中…' : '保存并检查' }}
      </button>
      <button
        v-permission="'rule:edit'"
        type="button"
        class="rule-designer-actions__button rule-designer-actions__secondary"
        data-action="test"
        :disabled="!canEdit || !canTest || busy"
        :title="canTest ? '使用已检查的最新草稿进行测试' : '请先保存并检查当前内容'"
        @click="$emit('test')"
      >
        进入测试
      </button>
      <button
        type="button"
        class="rule-designer-actions__link"
        data-action="lifecycle"
        aria-label="前往规则生命周期审核发布"
        title="前往规则生命周期审核发布"
        @click="$emit('lifecycle')"
      >
        生命周期
      </button>
    </div>
    <el-alert
      v-if="recovery"
      class="rule-designer-actions__recovery"
      type="warning"
      :closable="false"
      title="检测到本次浏览器会话中尚未保存的内容"
    >
      <template #default>
        <span>恢复时间：{{ recovery.savedAt || '未知' }}</span>
        <button type="button" data-action="restore" @click="$emit('restore')">
          恢复内容
        </button>
        <button type="button" data-action="discard-recovery" @click="$emit('discard-recovery')">
          忽略
        </button>
      </template>
    </el-alert>
    <rule-validation-report
      v-if="report"
      class="rule-designer-actions__report"
      :report="report"
    />
  </section>
</template>

<script>
import RuleValidationReport from '@/components/rule/RuleValidationReport.vue'

const STATUS_LABELS = {
  CLEAN: '已保存',
  DIRTY: '有未保存修改',
  SAVING: '正在保存',
  SAVED_UNCHECKED: '已保存，等待检查',
  CHECK_FAILED: '检查未通过',
  READY_TO_TEST: '已检查，可测试',
  SAVE_CONFLICT: '保存冲突，请重新加载后处理',
}

export default {
  name: 'RuleDesignerActionBar',
  components: { RuleValidationReport },
  props: {
    canEdit: { type: Boolean, default: false },
    canTest: { type: Boolean, default: false },
    state: { type: String, default: 'CLEAN' },
    recovery: { type: Object, default: null },
    report: { type: Object, default: null },
  },
  emits: [
    'save',
    'save-check',
    'test',
    'lifecycle',
    'restore',
    'discard-recovery',
  ],
  computed: {
    busy() {
      return this.state === 'SAVING'
    },
    statusLabel() {
      return STATUS_LABELS[this.state] || STATUS_LABELS.CLEAN
    },
    statusClass() {
      return `is-${String(this.state || 'CLEAN').toLowerCase()}`
    },
  },
}
</script>

<style scoped>
.rule-designer-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.rule-designer-actions__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--tianshu-text-secondary, #64748b);
  font-size: 12px;
  white-space: nowrap;
}

.rule-designer-actions__status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
}

.rule-designer-actions__status-dot.is-dirty,
.rule-designer-actions__status-dot.is-saved_unchecked {
  background: #d97706;
}

.rule-designer-actions__status-dot.is-ready_to_test {
  background: #16a34a;
}

.rule-designer-actions__status-dot.is-check_failed,
.rule-designer-actions__status-dot.is-save_conflict {
  background: #dc2626;
}

.rule-designer-actions__buttons {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.rule-designer-actions__button,
.rule-designer-actions__link {
  min-height: 32px;
  border-radius: 6px;
  font: inherit;
  cursor: pointer;
}

.rule-designer-actions__button {
  padding: 0 14px;
  border: 1px solid var(--el-border-color, #d1d5db);
  background: var(--el-bg-color, #fff);
  color: var(--tianshu-text-primary, #1f2937);
}

.rule-designer-actions__primary {
  border-color: var(--el-color-primary, #2563eb);
  background: var(--el-color-primary, #2563eb);
  color: #fff;
  font-weight: 600;
}

.rule-designer-actions__link {
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: var(--el-color-primary, #2563eb);
}

.rule-designer-actions__button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.rule-designer-actions__recovery,
.rule-designer-actions__report {
  flex: 1 0 100%;
}

.rule-designer-actions__recovery button {
  margin-left: 12px;
  border: 0;
  background: transparent;
  color: var(--el-color-primary, #2563eb);
  cursor: pointer;
}
</style>
