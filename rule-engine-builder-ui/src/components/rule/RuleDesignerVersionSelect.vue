<template>
  <div class="rule-designer-version-select" data-testid="designer-version-control">
    <span>规则版本</span>
    <el-select
      :model-value="modelValue"
      :loading="loading"
      size="small"
      aria-label="选择规则版本"
      data-testid="designer-version-select"
      placeholder="选择规则版本"
      @change="$emit('change', $event)"
    >
      <el-option-group v-if="revisionOptions.length" label="生命周期修订">
        <el-option
          v-for="item in revisionOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-option-group>
      <el-option-group v-if="versionOptions.length" label="已发布版本">
        <el-option
          v-for="item in versionOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-option-group>
    </el-select>
  </div>
</template>

<script>
export default {
  name: 'RuleDesignerVersionSelect',
  props: {
    options: { type: Array, default: () => [] },
    modelValue: { type: String, default: '' },
    loading: { type: Boolean, default: false },
  },
  emits: ['change'],
  computed: {
    revisionOptions() {
      return this.options.filter((item) => item.group === 'REVISION')
    },
    versionOptions() {
      return this.options.filter((item) => item.group === 'VERSION')
    },
  },
}
</script>

<style scoped>
.rule-designer-version-select {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tianshu-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.rule-designer-version-select .el-select {
  width: 184px;
}
</style>
