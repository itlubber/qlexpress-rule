import { listRuleRevisions, saveContent } from '@/api/definition'
import * as definitionApi from '@/api/definition'

function unwrap(response) {
  return response && response.data !== undefined ? response.data : response
}

function isSourceId(value) {
  return /^[1-9]\d*$/.test(String(value || ''))
}

function sourceKey(source) {
  return source ? `${source.sourceType}:${source.sourceId}` : ''
}

const REVISION_STATE_LABELS = {
  DRAFT: '待修改',
  REVIEW: '评审中',
  REJECTED: '已驳回',
  APPROVED: '已批准',
  PUBLISHED: '已发布',
  OFFLINE: '已下线',
}

export default {
  data() {
    return {
      draftRevision: null,
      viewRevision: null,
      draftGuardLoaded: false,
      draftGuardPromise: null,
      draftIssues: [],
      draftGuardError: null,
      draftGuardNeedsRefresh: false,
      viewRefreshToken: 0,
      designerRevisions: [],
      designerVersions: [],
      designerSourcesLoading: false,
    }
  },
  computed: {
    requestedSource() {
      const query = this.$route?.query || {}
      const sourceType = query.sourceType
      if (!['REVISION', 'VERSION'].includes(sourceType) || !isSourceId(query.sourceId)) {
        return null
      }
      return { sourceType, sourceId: String(query.sourceId) }
    },
    viewRevisionLabel() {
      if (!this.viewRevision) return ''
      if (this.viewRevision.sourceType === 'LEGACY_CONTENT') {
        return '历史生效内容'
      }
      const prefix = this.viewRevision.sourceType === 'VERSION' ? '版本' : '修订'
      return `${prefix} ${this.viewRevision.revisionNo || ''}`.trim()
    },
    canEditDraft() {
      return (
        this.draftGuardLoaded &&
        this.draftRevision?.state === 'DRAFT' &&
        this.viewRevision?.state === 'DRAFT' &&
        String(this.draftRevision.id) === String(this.viewRevision.id)
      )
    },
    canForkViewRevision() {
      return ['VERSION', 'APPROVED', 'PUBLISHED', 'OFFLINE', 'LEGACY'].includes(
        this.viewRevision?.state
      )
    },
    hasPendingDraft() {
      return this.draftRevision?.state === 'DRAFT'
    },
    selectedDesignerSource() {
      if (this.requestedSource) return sourceKey(this.requestedSource)
      if (!this.viewRevision || !isSourceId(this.viewRevision.id)) return ''
      return `REVISION:${String(this.viewRevision.id)}`
    },
    designerSourceOptions() {
      const revisions = this.designerRevisions
        .filter((item) => item && isSourceId(item.id))
        .map((item) => ({
          value: `REVISION:${String(item.id)}`,
          label: `${REVISION_STATE_LABELS[item.state] || '生命周期'}修订 v${item.revisionNo || '—'}`,
          group: 'REVISION',
        }))
      const versions = this.designerVersions
        .filter((item) => item && isSourceId(item.id))
        .map((item) => ({
          value: `VERSION:${String(item.id)}`,
          label: `发布版本 v${item.version || '—'}`,
          group: 'VERSION',
        }))

      if (this.viewRevision && this.viewRevision.state !== 'LEGACY') {
        const selectedValue = this.selectedDesignerSource
        const group = selectedValue.startsWith('VERSION:') ? 'VERSION' : 'REVISION'
        const options = group === 'VERSION' ? versions : revisions
        if (selectedValue && !options.some((item) => item.value === selectedValue)) {
          options.push({
            value: selectedValue,
            label:
              group === 'VERSION'
                ? `发布版本 v${this.viewRevision.revisionNo || '—'}`
                : `${REVISION_STATE_LABELS[this.viewRevision.state] || '生命周期'}修订 v${this.viewRevision.revisionNo || '—'}`,
            group,
          })
        }
      }
      return [...revisions, ...versions]
    },
  },
  watch: {
    '$route.query'() {
      this.startViewedRevisionRefresh(true)
    },
  },
  created() {
    this.startViewedRevisionRefresh(false)
  },
  activated() {
    if (!this.draftGuardNeedsRefresh) return
    this.draftGuardNeedsRefresh = false
    this.startViewedRevisionRefresh(true)
  },
  deactivated() {
    this.draftGuardNeedsRefresh = true
  },
  methods: {
    startViewedRevisionRefresh(reloadContent) {
      const refreshPromise = this.refreshViewedRevision()
      this.draftGuardPromise = refreshPromise
      if (reloadContent) {
        refreshPromise.then((result) => {
          if (
            result &&
            this.isCurrentSource(result.sourceKey, result.refreshToken) &&
            this.viewRevision &&
            typeof this.loadContent === 'function'
          ) {
            return this.loadContent()
          }
          return null
        })
      }
      return refreshPromise
    },
    isCurrentSource(requestedSourceKey, refreshToken) {
      return (
        refreshToken === this.viewRefreshToken &&
        requestedSourceKey === sourceKey(this.requestedSource)
      )
    },
    isCurrentViewAction(action) {
      return (
        this.isCurrentSource(action.sourceKey, action.refreshToken) &&
        String(this.viewRevision?.id) === action.viewId
      )
    },
    isCurrentDraftAction(action) {
      return (
        this.isCurrentViewAction(action) &&
        String(this.draftRevision?.id) === action.draftId
      )
    },
    async loadDraftRevision() {
      return this.startViewedRevisionRefresh(false)
    },
    async refreshViewedRevision() {
      const definitionId = this.definitionId || this.$route.params.id
      const source = this.requestedSource
      const requestedSourceKey = sourceKey(source)
      const refreshToken = ++this.viewRefreshToken
      this.draftGuardLoaded = false
      this.draftRevision = null
      this.viewRevision = null
      this.draftIssues = []
      this.draftGuardError = null
      this.designerSourcesLoading = true
      const listPromise = Promise.resolve().then(() => listRuleRevisions(definitionId))
      const versionsPromise = Promise.resolve().then(() =>
        definitionApi.listVersions(definitionId)
      )
      this.loadDesignerVersions(
        versionsPromise,
        requestedSourceKey,
        refreshToken
      )
      try {
        if (source) {
          const exactSourcePromise = Promise.resolve().then(() =>
            source.sourceType === 'REVISION'
              ? definitionApi.getRuleRevision(definitionId, source.sourceId)
              : definitionApi.getVersionById(definitionId, source.sourceId)
          )
          const [listResult, sourceResult] = await Promise.allSettled([
            listPromise,
            exactSourcePromise,
          ])
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null

          if (listResult.status === 'fulfilled') {
            const data = unwrap(listResult.value)
            const revisions = Array.isArray(data) ? [...data] : []
            revisions.sort(
              (left, right) =>
                Number(right.revisionNo || 0) - Number(left.revisionNo || 0)
            )
            this.draftRevision =
              revisions.find((item) => item.state === 'DRAFT') || null
            this.designerRevisions = revisions
          } else {
            this.designerRevisions = []
          }
          if (sourceResult.status !== 'fulfilled') {
            this.viewRevision = null
            this.draftGuardError = sourceResult.reason
            return null
          }

          const sourceData = unwrap(sourceResult.value)
          if (!sourceData) throw new Error('当前节点不存在')
          this.viewRevision =
            source.sourceType === 'VERSION'
              ? {
                  ...sourceData,
                  state: 'VERSION',
                  revisionNo: sourceData.version,
                  sourceType: 'VERSION',
                  sourceId: source.sourceId,
                }
              : sourceData
          return { refreshToken, sourceKey: requestedSourceKey }
        }

        const response = await listPromise
        if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null
        const data = unwrap(response)
        const revisions = Array.isArray(data) ? [...data] : []
        revisions.sort(
          (left, right) =>
            Number(right.revisionNo || 0) - Number(left.revisionNo || 0)
        )
        this.draftRevision =
          revisions.find((item) => item.state === 'DRAFT') || null
        this.designerRevisions = revisions
        this.viewRevision = this.draftRevision || revisions[0] || null
        if (!this.viewRevision) {
          const contentResponse = await definitionApi.getContent(definitionId)
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null
          const content = unwrap(contentResponse)
          const modelJson = content?.modelJson
          if (typeof modelJson !== 'string' || !modelJson.trim()) {
            throw new Error('当前规则没有可查看的历史内容')
          }
          try {
            JSON.parse(modelJson)
          } catch {
            throw new Error('当前规则的历史内容不是有效 JSON')
          }
          this.viewRevision = {
            id: `legacy-content:${definitionId}`,
            definitionId,
            state: 'LEGACY',
            sourceType: 'LEGACY_CONTENT',
            sourceId: String(definitionId),
            modelJson,
          }
        }
        return { refreshToken, sourceKey: requestedSourceKey }
      } catch (error) {
        if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null
        this.viewRevision = null
        this.draftGuardError = error
        return null
      } finally {
        if (this.isCurrentSource(requestedSourceKey, refreshToken)) {
          this.draftGuardLoaded = true
        }
      }
    },
    loadDesignerVersions(versionsPromise, requestedSourceKey, refreshToken) {
      return Promise.resolve(versionsPromise)
        .then((response) => {
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return
          this.designerVersions = this.normalizeDesignerVersions(response)
        })
        .catch(() => {
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return
          this.designerVersions = []
        })
        .finally(() => {
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return
          this.designerSourcesLoading = false
        })
    },
    normalizeDesignerVersions(response) {
      const data = unwrap(response)
      return (Array.isArray(data) ? [...data] : []).sort(
        (left, right) => Number(right.version || 0) - Number(left.version || 0)
      )
    },
    switchDesignerSource(value) {
      const match = /^(REVISION|VERSION):([1-9]\d*)$/.exec(String(value || ''))
      if (!match || value === this.selectedDesignerSource) return
      this.$router.replace({
        query: {
          ...(this.$route?.query || {}),
          sourceType: match[1],
          sourceId: match[2],
        },
      })
    },
    async forkViewRevision() {
      if (!this.canForkViewRevision) {
        throw new Error('当前节点不允许派生草稿')
      }
      const definitionId = this.definitionId || this.$route.params.id
      if (
        this.draftRevision?.state === 'DRAFT' &&
        String(this.draftRevision.id) !== String(this.viewRevision.id)
      ) {
        await this.$router.replace({
          query: {
            sourceType: 'REVISION',
            sourceId: String(this.draftRevision.id),
          },
        })
        if (this.$message?.info) {
          this.$message.info('规则已有待修改修订，已为你打开该版本')
        }
        return { revision: this.draftRevision }
      }
      const action = {
        sourceKey: sourceKey(this.requestedSource),
        refreshToken: this.viewRefreshToken,
        viewId: String(this.viewRevision.id),
      }
      try {
        let response
        if (this.requestedSource) {
          response = await definitionApi.createDraftFromSource(definitionId, {
            sourceType: this.requestedSource.sourceType,
            sourceId: this.requestedSource.sourceId,
          })
        } else if (this.viewRevision.state === 'LEGACY') {
          response = await definitionApi.createDraftRevision(definitionId)
        } else {
          response = await definitionApi.createDraftRevision(
            definitionId,
            this.viewRevision.id
          )
        }
        const result = unwrap(response)
        const revision = result?.revision || result
        if (!revision || revision.state !== 'DRAFT') {
          throw new Error('派生草稿响应缺少 DRAFT 修订')
        }
        if (!this.isCurrentViewAction(action)) return result
        this.draftRevision = revision
        this.viewRevision = revision
        this.draftIssues = Array.isArray(result?.issues) ? result.issues : []
        this.$router.replace({
          query: { sourceType: 'REVISION', sourceId: String(revision.id) },
        })
        return result
      } catch (error) {
        if (
          this.isCurrentViewAction(action) &&
          !error?.requestErrorNotified &&
          this.$message?.error
        ) {
          this.$message.error(error?.message || '创建草稿失败，请稍后重试')
        }
        throw error
      }
    },
    async saveDraftModel(modelJson, extra = {}) {
      if (this.draftGuardPromise) await this.draftGuardPromise
      if (!this.canEditDraft) {
        throw new Error(
          '当前规则没有可编辑草稿，请先进入生命周期创建或退回草稿'
        )
      }
      const definitionId = this.definitionId || this.$route.params.id
      const action = {
        sourceKey: sourceKey(this.requestedSource),
        refreshToken: this.viewRefreshToken,
        viewId: String(this.viewRevision.id),
        draftId: String(this.draftRevision.id),
      }
      const allowedExtra = {}
      ;['openApiConfigJson', 'updateOpenApiConfig'].forEach((field) => {
        if (Object.prototype.hasOwnProperty.call(extra, field)) {
          allowedExtra[field] = extra[field]
        }
      })
      const response = await saveContent({
        ...allowedExtra,
        definitionId,
        revisionId: this.draftRevision.id,
        lockVersion: this.draftRevision.lockVersion,
        modelJson,
      })
      const result = unwrap(response)
      if (!result?.revision) {
        if (this.isCurrentDraftAction(action)) {
          this.draftRevision = null
          this.viewRevision = null
        }
        throw new Error('草稿保存响应缺少 revision')
      }
      if (!this.isCurrentDraftAction(action)) return result
      this.draftRevision = result.revision
      this.viewRevision = result.revision
      this.draftIssues = Array.isArray(result.issues) ? result.issues : []
      return result
    },
    async completeRuleCompile(result, options = {}) {
      const successMessage = options.successMessage || '编译成功'
      const errorPrefix = options.errorPrefix || '编译失败'
      if (!result || !result.compileSuccess) {
        this.$message.error(
          `${errorPrefix}: ${(result && result.compileMessage) || '未知错误'}`
        )
        return result
      }
      this.$message.success(successMessage)
      if (typeof options.onSuccess === 'function') {
        await options.onSuccess()
      }
      this.goRuleLifecycle()
      return result
    },
    goRuleLifecycle() {
      this.$router.push({
        name: 'RuleDetail',
        params: { id: this.definitionId || this.$route.params.id },
        query: { focus: 'lifecycle' },
      })
    },
  },
}
