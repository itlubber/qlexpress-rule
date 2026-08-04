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
      return ['VERSION', 'APPROVED', 'PUBLISHED', 'OFFLINE'].includes(
        this.viewRevision?.state
      )
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
      const listPromise = Promise.resolve().then(() => listRuleRevisions(definitionId))
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
    async forkViewRevision() {
      if (!this.canForkViewRevision) {
        throw new Error('当前节点不允许派生草稿')
      }
      const definitionId = this.definitionId || this.$route.params.id
      const sourceType = this.viewRevision.sourceType === 'VERSION' ? 'VERSION' : 'REVISION'
      const sourceId =
        this.requestedSource?.sourceType === sourceType
          ? this.requestedSource.sourceId
          : this.viewRevision.sourceId ?? this.viewRevision.id
      const action = {
        sourceKey: sourceKey(this.requestedSource),
        refreshToken: this.viewRefreshToken,
        viewId: String(this.viewRevision.id),
      }
      const response = await definitionApi.createDraftFromSource(definitionId, {
        sourceType,
        sourceId,
      })
      const result = unwrap(response)
      const revision = result?.revision || result
      if (!revision || revision.state !== 'DRAFT') {
        if (this.isCurrentViewAction(action)) {
          this.draftRevision = null
          this.viewRevision = null
        }
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
