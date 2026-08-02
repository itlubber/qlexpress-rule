<template>
  <div class="approval-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">LIFECYCLE GOVERNANCE</span>
        <h1>审批管理</h1>
        <p>集中处理引擎各模块的变更、依赖校验、版本生效和历史恢复。</p>
      </div>
      <div class="header-stat">
        <strong>{{ total }}</strong>
        <span>当前筛选申请</span>
      </div>
    </header>

    <section class="approval-card">
      <el-tabs v-model="activeTab" class="module-tabs" @tab-change="changeTab">
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        />
      </el-tabs>

      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="审批单号或变更说明"
          class="keyword-filter"
          @keyup.enter="search"
        />
        <el-select v-model="filters.status" clearable placeholder="申请状态">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="filters.action" clearable placeholder="生命周期动作">
          <el-option
            v-for="item in actionOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        row-key="id"
        class="approval-table"
        @row-click="openDetail"
      >
        <el-table-column label="审批申请" min-width="250">
          <template #default="{ row }">
            <div class="request-cell">
              <span class="request-icon">{{ moduleInitial(row.resourceType) }}</span>
              <span>
                <strong>{{ resourceTitle(row) }}</strong>
                <small>{{ row.requestNo }}</small>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            {{ resourceTypeLabel(row.resourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="动作" width="100">
          <template #default="{ row }">
            <span class="action-label">{{ actionLabel(row.action) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applicant" label="申请人" width="130" />
        <el-table-column label="提交时间" width="172">
          <template #default="{ row }">
            {{ formatTime(row.submitTime || row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <strong>当前模块暂无审批申请</strong>
            <span>从对应业务模块发起生命周期变更后会显示在这里。</span>
          </div>
        </template>
      </el-table>

      <footer class="pagination-row">
        <span>共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          layout="prev, pager, next"
          :total="total"
          @current-change="loadRequests"
        />
      </footer>
    </section>
  </div>
</template>

<script>
import router from '@/router'
import { listGovernanceRequests } from '@/api/governance'

const STATUS_OPTIONS = [
  { value: 'EDITING', label: '编辑中' },
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CONFLICT', label: '存在冲突' },
  { value: 'CANCELLED', label: '已取消' }
]

const ACTION_OPTIONS = [
  { value: 'CREATE', label: '新增' },
  { value: 'UPDATE', label: '修改' },
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
  { value: 'DELETE', label: '删除' },
  { value: 'RESTORE', label: '恢复版本' }
]

export default {
  name: 'ApprovalList',
  data() {
    return {
      tabs: [
        { name: 'FIELD', label: '字段' },
        { name: 'MODEL', label: '模型' },
        { name: 'DATASOURCE', label: '外数' },
        { name: 'DATABASE', label: '数据库' },
        { name: 'FUNCTION', label: '函数' },
        { name: 'RULE', label: '规则' },
        { name: 'EXPERIMENT', label: '分流' },
        { name: 'PROJECT', label: '项目' }
      ],
      statusOptions: STATUS_OPTIONS,
      actionOptions: ACTION_OPTIONS,
      activeTab: 'FIELD',
      filters: {
        keyword: '',
        status: '',
        action: ''
      },
      records: [],
      pageNum: 1,
      pageSize: 20,
      total: 0,
      loading: false
    }
  },
  created() {
    this.loadRequests()
  },
  methods: {
    async loadRequests() {
      this.loading = true
      try {
        const response = await listGovernanceRequests({
          tab: this.activeTab,
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined,
          action: this.filters.action || undefined,
          pageNum: this.pageNum,
          pageSize: this.pageSize
        })
        const page = response.data || {}
        this.records = page.records || []
        this.total = Number(page.total || 0)
      } finally {
        this.loading = false
      }
    },
    changeTab() {
      this.pageNum = 1
      this.loadRequests()
    },
    search() {
      this.pageNum = 1
      this.loadRequests()
    },
    resetFilters() {
      this.filters = { keyword: '', status: '', action: '' }
      this.search()
    },
    openDetail(row) {
      router.push(`/approval/${row.id}`)
    },
    snapshot(row) {
      const text = row.submittedSnapshotJson || row.draftSnapshotJson
      if (!text) return {}
      try {
        return JSON.parse(text)
      } catch (error) {
        return {}
      }
    },
    resourceTitle(row) {
      const value = this.snapshot(row)
      if (
        row.resourceType === 'RULE_PROJECT_BINDING' &&
        value.definitionId &&
        value.projectId
      ) {
        const action = row.action === 'DELETE' ? '移出' : '加入'
        const rule = value.ruleName
          ? `“${value.ruleName}”`
          : `#${value.definitionId}`
        const project = value.projectName
          ? `“${value.projectName}”`
          : `#${value.projectId}`
        return `将规则${rule}${action}项目${project}`
      }
      return value.ruleName || value.modelName || value.funcName ||
        value.datasourceName || value.projectName || value.varLabel ||
        value.objectLabel || value.experimentName ||
        `${this.resourceTypeLabel(row.resourceType)} #${row.resourceId}`
    },
    moduleInitial(type) {
      return this.resourceTypeLabel(type).slice(0, 1)
    },
    resourceTypeLabel(type) {
      return {
        VARIABLE: '字段',
        DATA_OBJECT: '数据对象',
        MODEL: '模型',
        EXTERNAL_DATASOURCE: '外数源',
        EXTERNAL_API: '外数 API',
        DATABASE: '数据库',
        FUNCTION: '函数',
        RULE: '规则',
        RULE_PROJECT_BINDING: '项目规则关联',
        EXPERIMENT: '分流',
        PROJECT: '项目'
      }[type] || type || '资源'
    },
    actionLabel(action) {
      return (ACTION_OPTIONS.find(item => item.value === action) || {}).label || action
    },
    statusLabel(status) {
      return (STATUS_OPTIONS.find(item => item.value === status) || {}).label || status
    },
    statusType(status) {
      return {
        EDITING: 'info',
        PENDING: 'warning',
        APPROVED: 'success',
        REJECTED: 'danger',
        CONFLICT: 'danger',
        CANCELLED: 'info'
      }[status] || 'info'
    },
    formatTime(value) {
      return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
    }
  }
}
</script>

<style scoped>
.approval-page {
  min-height: 100%;
  padding: 28px;
  background: #f4f6f8;
  color: #18212f;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  max-width: 1440px;
  margin: 0 auto 22px;
}

.page-eyebrow {
  display: block;
  margin-bottom: 7px;
  color: #2763d5;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.page-header h1 {
  margin: 0;
  font-size: 30px;
  letter-spacing: -0.04em;
}

.page-header p {
  margin: 9px 0 0;
  color: #6b7585;
}

.header-stat {
  display: flex;
  align-items: baseline;
  gap: 9px;
  color: #6b7585;
}

.header-stat strong {
  color: #18212f;
  font-size: 30px;
}

.approval-card {
  max-width: 1440px;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid #e4e8ee;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 12px 34px rgb(25 40 65 / 7%);
}

.module-tabs {
  padding: 0 22px;
  border-bottom: 1px solid #edf0f4;
}

.filter-bar {
  display: flex;
  gap: 10px;
  padding: 18px 22px;
  background: #fafbfc;
}

.filter-bar .keyword-filter {
  width: min(360px, 34vw);
}

.filter-bar :deep(.el-select) {
  width: 160px;
}

.approval-table {
  cursor: pointer;
}

.request-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.request-cell > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.request-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request-cell small {
  margin-top: 3px;
  color: #8a94a3;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.request-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border: 1px solid #cbd9f5;
  border-radius: 9px;
  background: #eef4ff;
  color: #2763d5;
  font-weight: 700;
}

.action-label {
  color: #354052;
  font-weight: 600;
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 22px;
  border-top: 1px solid #edf0f4;
  color: #7d8797;
  font-size: 13px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 46px 0;
  color: #8a94a3;
}

.empty-state strong {
  color: #4f5969;
}

@media (max-width: 900px) {
  .approval-page {
    padding: 18px 12px;
  }

  .header-stat {
    display: none;
  }

  .filter-bar {
    flex-wrap: wrap;
  }

  .filter-bar .keyword-filter {
    width: 100%;
  }
}
</style>
