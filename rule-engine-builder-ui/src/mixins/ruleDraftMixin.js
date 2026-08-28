import { listRuleRevisions, saveContent } from '@/api/definition'
import * as definitionApi from '@/api/definition'
import {
  clearDraftRecovery,
  createDraftFingerprint,
  readDraftRecovery,
  saveDraftRecovery,
} from '@/utils/ruleDesignerDraft'
import { registerDesignerLeaveGuard } from '@/utils/designerLeaveGuard'

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
      draftGuardActive: true,
      draftGuardDefinitionId: null,
      draftGuardRouteKey: '',
      viewRefreshToken: 0,
      designerRevisions: [],
      designerVersions: [],
      designerSourcesLoading: false,
      designerActionState: 'CLEAN',
      designerBaselineFingerprint: '',
      designerCurrentFingerprint: '',
      designerCheckedFingerprint: '',
      designerValidationReport: null,
      designerCompileResult: null,
      designerRecoveryCandidate: null,
      designerDraftTrackingReady: false,
      designerCaptureQueued: false,
      designerRestoringRecovery: false,
      designerLeaveUnregister: null,
      designerLeaveApproved: false,
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
    designerCanTest() {
      return (
        this.canEditDraft &&
        this.designerActionState === 'READY_TO_TEST' &&
        Boolean(this.designerCheckedFingerprint) &&
        this.designerCheckedFingerprint === this.designerCurrentFingerprint
      )
    },
    designerHasUnsavedChanges() {
      return (
        this.designerActionState === 'SAVING' ||
        (
        this.designerDraftTrackingReady &&
        Boolean(this.designerCurrentFingerprint) &&
        this.designerCurrentFingerprint !== this.designerBaselineFingerprint
        )
      )
    },
  },
  watch: {
    '$route.query'() {
      if (!this.draftGuardActive || !this.isOwnDesignerRoute()) return
      const routeKey = this.currentDesignerRouteKey()
      if (!routeKey || routeKey === this.draftGuardRouteKey) return
      this.draftGuardRouteKey = routeKey
      this.startViewedRevisionRefresh(true)
    },
  },
  created() {
    this.draftGuardDefinitionId = String(this.$route?.params?.id || '')
    this.draftGuardRouteKey = this.currentDesignerRouteKey()
    this.startViewedRevisionRefresh(false)
  },
  mounted() {
    if (typeof window === 'undefined') return
    window.addEventListener('keydown', this.handleDesignerSaveShortcut)
    window.addEventListener('beforeunload', this.handleDesignerBeforeUnload)
    this.registerDesignerLeaveProtection()
  },
  updated() {
    this.queueDesignerDraftCapture()
  },
  beforeUnmount() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('keydown', this.handleDesignerSaveShortcut)
      window.removeEventListener('beforeunload', this.handleDesignerBeforeUnload)
    }
    if (this.designerLeaveUnregister) this.designerLeaveUnregister()
  },
  beforeRouteLeave(to, _from, next) {
    if (this.designerLeaveApproved) {
      this.designerLeaveApproved = false
      next()
      return
    }
    if (this.isOwnExpressionRoute(to)) {
      next()
      return
    }
    this.confirmDesignerLeave({ discardRecovery: true }).then((confirmed) => {
      if (confirmed) next()
      else next(false)
    })
  },
  activated() {
    this.draftGuardActive = true
    this.registerDesignerLeaveProtection()
    if (!this.draftGuardNeedsRefresh || !this.isOwnDesignerRoute()) return
    this.draftGuardNeedsRefresh = false
    this.startViewedRevisionRefresh(true)
  },
  deactivated() {
    this.draftGuardActive = false
    this.draftGuardNeedsRefresh = !this.isOwnExpressionRoute()
  },
  methods: {
    registerDesignerLeaveProtection() {
      if (!this.isOwnDesignerRoute()) return
      const path = this.$route?.fullPath || this.$route?.path
      if (!path) return
      if (this.designerLeaveUnregister) this.designerLeaveUnregister()
      this.designerLeaveUnregister = registerDesignerLeaveGuard(path, () =>
        this.confirmDesignerLeave({ discardRecovery: true })
      )
    },
    designerSessionStorage() {
      try {
        return typeof window !== 'undefined' ? window.sessionStorage : null
      } catch {
        return null
      }
    },
    designerDraftIdentity(revision = this.draftRevision) {
      return {
        definitionId: this.definitionId || this.$route?.params?.id,
        revisionId: revision?.id,
        lockVersion: revision?.lockVersion,
      }
    },
    initializeDesignerDraftTracking(modelJson) {
      if (this.designerRestoringRecovery) return
      if (!this.canEditDraft) {
        this.designerDraftTrackingReady = false
        this.designerRecoveryCandidate = null
        return
      }
      const serialized =
        typeof modelJson === 'string'
          ? modelJson
          : this.serializeDesignerDraft?.()
      if (typeof serialized !== 'string') return
      const fingerprint = createDraftFingerprint(serialized)
      this.designerBaselineFingerprint = fingerprint
      this.designerCurrentFingerprint = fingerprint
      this.designerCheckedFingerprint = ''
      this.designerValidationReport = null
      this.designerCompileResult = null
      this.designerActionState = 'CLEAN'
      this.designerDraftTrackingReady = true
      const identity = this.designerDraftIdentity()
      const recovery = readDraftRecovery(this.designerSessionStorage(), identity)
      this.designerRecoveryCandidate =
        recovery && recovery.fingerprint !== fingerprint ? recovery : null
      if (recovery && recovery.fingerprint === fingerprint) {
        clearDraftRecovery(this.designerSessionStorage(), identity)
      }
    },
    queueDesignerDraftCapture() {
      if (
        this.designerCaptureQueued ||
        !this.designerDraftTrackingReady ||
        typeof this.serializeDesignerDraft !== 'function'
      ) {
        return
      }
      this.designerCaptureQueued = true
      Promise.resolve().then(() => {
        this.designerCaptureQueued = false
        this.captureDesignerDraftState()
      })
    },
    captureDesignerDraftState() {
      if (
        !this.designerDraftTrackingReady ||
        !this.canEditDraft ||
        this.designerActionState === 'SAVING' ||
        typeof this.serializeDesignerDraft !== 'function'
      ) {
        return
      }
      let modelJson
      try {
        modelJson = this.serializeDesignerDraft()
      } catch {
        return
      }
      if (typeof modelJson !== 'string') return
      const fingerprint = createDraftFingerprint(modelJson)
      if (fingerprint === this.designerCurrentFingerprint) return
      this.designerCurrentFingerprint = fingerprint
      if (fingerprint === this.designerBaselineFingerprint) {
        this.designerActionState =
          this.designerCheckedFingerprint === fingerprint
            ? 'READY_TO_TEST'
            : 'CLEAN'
        clearDraftRecovery(
          this.designerSessionStorage(),
          this.designerDraftIdentity()
        )
        this.designerRecoveryCandidate = null
        return
      }
      if (this.designerActionState !== 'SAVE_CONFLICT') {
        this.designerActionState = 'DIRTY'
      }
      this.designerCheckedFingerprint = ''
      this.designerValidationReport = null
      this.designerCompileResult = null
      const recovery = {
        ...this.designerDraftIdentity(),
        modelJson,
        fingerprint,
        savedAt: new Date().toISOString(),
      }
      saveDraftRecovery(this.designerSessionStorage(), recovery)
    },
    markDesignerDraftSaved(modelJson) {
      const fingerprint = createDraftFingerprint(modelJson)
      this.designerBaselineFingerprint = fingerprint
      this.designerCurrentFingerprint = fingerprint
      this.designerCheckedFingerprint = ''
      this.designerValidationReport = null
      this.designerCompileResult = null
      this.designerDraftTrackingReady = true
      this.designerActionState = 'SAVED_UNCHECKED'
      clearDraftRecovery(
        this.designerSessionStorage(),
        this.designerDraftIdentity()
      )
      this.designerRecoveryCandidate = null
    },
    async restoreDesignerRecovery() {
      const recovery = this.designerRecoveryCandidate
      if (!recovery || typeof this.loadContent !== 'function') return
      const revision = this.viewRevision
      const serverModelJson = revision?.modelJson
      this.designerRestoringRecovery = true
      try {
        if (revision) revision.modelJson = recovery.modelJson
        await this.loadContent()
        await this.$nextTick()
      } finally {
        if (revision) revision.modelJson = serverModelJson
        this.designerRestoringRecovery = false
      }
      this.designerCurrentFingerprint = recovery.fingerprint
      this.designerActionState = 'DIRTY'
      this.designerCheckedFingerprint = ''
      this.designerValidationReport = null
      this.designerRecoveryCandidate = null
    },
    discardDesignerRecovery() {
      clearDraftRecovery(
        this.designerSessionStorage(),
        this.designerDraftIdentity()
      )
      this.designerRecoveryCandidate = null
    },
    async confirmDesignerLeave(options = {}) {
      this.captureDesignerDraftState()
      if (!this.designerHasUnsavedChanges) return true
      try {
        await this.$confirm(
          '当前设计有未保存修改，放弃修改并离开吗？',
          '未保存提醒',
          {
            type: 'warning',
            confirmButtonText: '放弃并离开',
            cancelButtonText: '继续编辑',
          }
        )
        if (options.discardRecovery) {
          this.discardDesignerRecovery()
          this.designerLeaveApproved = true
        }
        return true
      } catch {
        return false
      }
    },
    handleDesignerBeforeUnload(event) {
      this.captureDesignerDraftState()
      if (!this.designerHasUnsavedChanges) return
      event.preventDefault()
      event.returnValue = ''
    },
    handleDesignerSaveShortcut(event) {
      if (
        !this.canEditDraft ||
        !this.draftGuardActive ||
        !(event.ctrlKey || event.metaKey) ||
        String(event.key || '').toLowerCase() !== 's'
      ) {
        return
      }
      event.preventDefault()
      if (this.designerActionState !== 'SAVING') this.handleSave?.()
    },
    ensureDesignerReadyForTest() {
      this.captureDesignerDraftState()
      if (this.designerCanTest) return true
      this.$message.warning('请先保存并检查当前内容，通过后再进入测试')
      return false
    },
    currentDesignerRouteKey() {
      if (!this.isOwnDesignerRoute()) return ''
      return `${this.draftGuardDefinitionId}:${sourceKey(this.requestedSource)}`
    },
    isOwnDesignerRoute(route = this.$route) {
      const routeId = route?.params?.id
      return (
        routeId != null &&
        String(routeId) === String(this.draftGuardDefinitionId || '')
      )
    },
    isOwnExpressionRoute(route = this.$route) {
      return (
        route?.name === 'ExpressionEditor' &&
        String(route?.params?.ruleId || '') ===
          String(this.draftGuardDefinitionId || '')
      )
    },
    startViewedRevisionRefresh(reloadContent) {
      if (!this.isOwnDesignerRoute()) return Promise.resolve(null)
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
      const replaceSource = () =>
        this.$router.replace({
          query: {
            ...(this.$route?.query || {}),
            sourceType: match[1],
            sourceId: match[2],
          },
        })
      this.captureDesignerDraftState()
      if (!this.designerHasUnsavedChanges) return replaceSource()
      return this.confirmDesignerLeave({ discardRecovery: true }).then(
        (confirmed) => (confirmed ? replaceSource() : null)
      )
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
      this.designerActionState = 'SAVING'
      let response
      try {
        response = await saveContent({
          ...allowedExtra,
          definitionId,
          revisionId: this.draftRevision.id,
          lockVersion: this.draftRevision.lockVersion,
          modelJson,
        })
      } catch (error) {
        this.designerActionState =
          error?.response?.status === 409 ? 'SAVE_CONFLICT' : 'DIRTY'
        this.designerCurrentFingerprint = createDraftFingerprint(modelJson)
        const recovery = {
          ...this.designerDraftIdentity(),
          modelJson,
          fingerprint: this.designerCurrentFingerprint,
          savedAt: new Date().toISOString(),
        }
        saveDraftRecovery(this.designerSessionStorage(), recovery)
        throw error
      }
      const result = unwrap(response)
      if (!result?.revision) {
        this.designerActionState = 'DIRTY'
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
      this.markDesignerDraftSaved(modelJson)
      return result
    },
    async completeRuleCompile(result, options = {}) {
      const successMessage = options.successMessage || '编译成功'
      const errorPrefix = options.errorPrefix || '编译失败'
      this.designerCompileResult = result || null
      if (!result || !result.compileSuccess) {
        this.designerActionState = 'CHECK_FAILED'
        this.designerCheckedFingerprint = ''
        this.designerValidationReport = {
          valid: false,
          errors: (result?.issues || []).filter(
            (item) => item.severity !== 'WARNING'
          ),
          warnings: (result?.issues || []).filter(
            (item) => item.severity === 'WARNING'
          ),
        }
        this.$message.error(
          `${errorPrefix}: ${(result && result.compileMessage) || '未知错误'}`
        )
        return result
      }
      if (!this.canEditDraft || !result.revision?.id) return result
      if (typeof options.onSuccess === 'function') {
        await options.onSuccess()
      }
      if (!this.canEditDraft) return result
      const definitionId = this.definitionId || this.$route.params.id
      try {
        const response = await definitionApi.preflightRuleRevision(
          definitionId,
          this.draftRevision.id
        )
        this.designerValidationReport = unwrap(response)
      } catch (error) {
        const report = error?.response?.data?.data
        if (!report) {
          this.designerActionState = 'CHECK_FAILED'
          throw error
        }
        this.designerValidationReport = report
      }
      if (!this.designerValidationReport?.valid) {
        this.designerActionState = 'CHECK_FAILED'
        this.designerCheckedFingerprint = ''
        this.$message.warning('草稿已保存，但发布前检查存在阻断项')
        return result
      }
      this.designerCheckedFingerprint = this.designerBaselineFingerprint
      this.designerCurrentFingerprint = this.designerBaselineFingerprint
      this.designerActionState = 'READY_TO_TEST'
      this.$message.success(`${successMessage}，发布前检查已通过`)
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
