package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleLifecycleEvent;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RulePublishOutbox;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.enums.RuleRevisionState;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.DecisionArtifactService;
import com.hengshucredit.rule.server.artifact.RulePreflightValidationService;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleLifecycleEventMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RulePublishOutboxMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RuleLifecycleService {
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    private RuleDefinitionMapper definitionMapper;
    @Resource
    private RuleLifecycleEventMapper eventMapper;
    @Resource
    private RulePublishedMapper publishedMapper;
    @Resource
    private RulePublishOutboxMapper outboxMapper;
    @Resource
    private RuleDefinitionVersionMapper versionMapper;
    @Resource
    private RuleDefinitionService definitionService;
    @Resource
    private RuleProjectService projectService;
    @Resource
    private RulePreflightValidationService preflightService;
    @Resource
    private DecisionArtifactService artifactService;
    @Resource
    private ConsoleOperatorResolver operatorResolver;

    @Transactional
    public RuleRevision ensureDraft(Long definitionId) {
        return createDraft(definitionId, null);
    }

    @Transactional
    public RuleRevision createDraft(Long definitionId, Long baseRevisionId) {
        RuleDefinition definition = lockDefinition(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("规则定义不存在");
        }
        RuleRevision existing = findDraft(definitionId);
        if (existing != null) {
            if (baseRevisionId == null
                    || baseRevisionId.equals(existing.getBaseRevisionId())) {
                return existing;
            }
            throw governance(409, "DRAFT_BASE_MISMATCH",
                    "已有草稿的基线与本次请求不一致");
        }
        RuleRevision pending = findPendingRevision(definitionId);
        if (pending != null) {
            throw governance(409, "DRAFT_CREATION_BLOCKED",
                    "规则已有 REVIEW 修订，请先完成或退回该修订");
        }
        RuleDefinitionContent content = loadContent(definitionId);
        RuleRevision base = baseRevisionId == null
                ? findLatestRevision(definitionId, RuleRevisionState.APPROVED)
                : loadRevision(baseRevisionId);
        if (baseRevisionId == null && base == null) {
            base = findPublishedRevision(definitionId);
        }
        if (baseRevisionId != null) {
            validateDraftBase(definitionId, baseRevisionId, base);
        } else if (base == null && findLatestAnyRevision(definitionId) != null) {
            throw governance(409, "BASE_REVISION_REQUIRED",
                    "规则已有历史修订，请显式指定草稿基线");
        }
        RuleRevision draft = new RuleRevision();
        draft.setDefinitionId(definitionId);
        draft.setRevisionNo(nextRevisionNo(definitionId));
        draft.setState(RuleRevisionState.DRAFT.name());
        draft.setBaseRevisionId(base == null ? null : base.getId());
        draft.setBaseArtifactId(base == null ? null : base.getArtifactId());
        draft.setModelJson(base != null ? base.getModelJson() : content == null ? "{}" : content.getModelJson());
        draft.setCompiledScript(base != null ? base.getCompiledScript()
                : content == null ? null : content.getCompiledScript());
        draft.setCompiledType(base != null ? base.getCompiledType()
                : content == null ? null : content.getCompiledType());
        draft.setOpenApiConfigJson(base != null ? base.getOpenApiConfigJson()
                : content == null ? null : content.getOpenApiConfigJson());
        draft.setInputSchemaJson(base == null ? null : base.getInputSchemaJson());
        draft.setOutputSchemaJson(base == null ? null : base.getOutputSchemaJson());
        draft.setLockVersion(0);
        draft.setCreateBy(actor());
        draft.setCreateTime(LocalDateTime.now());
        draft.setUpdateBy(actor());
        draft.setUpdateTime(LocalDateTime.now());
        try {
            insertRevision(draft);
        } catch (DuplicateKeyException conflict) {
            RuleRevision concurrent = findDraft(definitionId);
            if (concurrent != null
                    && (baseRevisionId == null
                    || baseRevisionId.equals(
                    concurrent.getBaseRevisionId()))) {
                return concurrent;
            }
            throw governance(409, "DRAFT_BASE_MISMATCH",
                    "并发创建的草稿基线与本次请求不一致");
        }
        RuleLifecycleEvent created =
                event(draft, "CREATE_DRAFT", null, "DRAFT", null);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("baseRevisionId", draft.getBaseRevisionId());
        created.setDetailsJson(JSON.toJSONString(details));
        insertEvent(created);
        return draft;
    }

    @Transactional
    public RuleRevision submit(Long revisionId, RuleLifecycleActionRequest request) {
        RuleRevision revision = requireState(revisionId, RuleRevisionState.DRAFT);
        RulePreflightReport report = preflight(revisionId);
        assertPreflightValid(report);
        applyPreflight(revision, report);
        revision.setSubmitBy(actor());
        revision.setSubmitTime(LocalDateTime.now());
        return transition(revision, RuleRevisionState.DRAFT, RuleRevisionState.REVIEW,
                "SUBMIT", comment(request));
    }

    @Transactional
    public RuleRevision returnToDraft(Long revisionId, RuleLifecycleActionRequest request) {
        String comment = comment(request);
        if (comment == null) throw new IllegalArgumentException("退回 DRAFT 必须填写原因");
        RuleRevision revision = requireState(revisionId, RuleRevisionState.REVIEW);
        return transition(revision, RuleRevisionState.REVIEW, RuleRevisionState.DRAFT,
                "RETURN_TO_DRAFT", comment);
    }

    @Transactional
    public RuleRevision approve(Long revisionId, RuleLifecycleActionRequest request) {
        RuleRevision revision = requireState(revisionId, RuleRevisionState.REVIEW);
        RulePreflightReport report = preflight(revisionId);
        assertPreflightValid(report);
        if (revision.getContentDigest() == null
                || !revision.getContentDigest().equals(report.getContentDigest())) {
            throw new IllegalStateException("REVIEW 内容或依赖已变化，请退回 DRAFT 后重新提交");
        }
        String forceReason = request == null ? null : trim(request.getForcePublishReason());
        if (report.isBreakingSchemaChange() && forceReason == null) {
            throw new IllegalArgumentException("破坏性 Schema 变更必须填写批准原因");
        }
        if (forceReason != null) revision.setForcePublishReason(forceReason);
        applyPreflight(revision, report);
        persistRevisionSnapshot(revision);
        DecisionArtifact artifact = buildArtifact(revisionId, actor());
        revision.setArtifactId(artifact.getId());
        revision.setApproveBy(actor());
        revision.setApproveTime(LocalDateTime.now());
        return transition(revision, RuleRevisionState.REVIEW, RuleRevisionState.APPROVED,
                "APPROVE", comment(request), artifact.getArtifactDigest());
    }

    @Transactional
    public RuleRevision publish(Long revisionId, RuleLifecycleActionRequest request) {
        RuleRevision revision = requireState(revisionId, RuleRevisionState.APPROVED);
        if (revision.getArtifactId() == null) {
            throw new IllegalStateException("APPROVED 修订缺少不可变制品");
        }
        RuleRevision prior = findPublishedRevision(revision.getDefinitionId());
        if (prior != null && !prior.getId().equals(revision.getId())) {
            transition(prior, RuleRevisionState.PUBLISHED, RuleRevisionState.OFFLINE,
                    "AUTO_OFFLINE", "发布新修订自动下线旧修订");
        }
        RuleRevision published = transition(revision, RuleRevisionState.APPROVED,
                RuleRevisionState.PUBLISHED, "PUBLISH", comment(request));
        published.setPublishBy(actor());
        published.setPublishTime(LocalDateTime.now());
        persistRevisionSnapshot(published);
        activateArtifact(published, actor());
        return published;
    }

    @Transactional
    public RuleRevision offline(Long revisionId, RuleLifecycleActionRequest request) {
        RuleRevision revision = requireState(revisionId, RuleRevisionState.PUBLISHED);
        RuleRevision offline = transition(revision, RuleRevisionState.PUBLISHED,
                RuleRevisionState.OFFLINE, "OFFLINE", comment(request));
        offline.setOfflineBy(actor());
        offline.setOfflineTime(LocalDateTime.now());
        persistRevisionSnapshot(offline);
        deactivateArtifact(offline, actor());
        return offline;
    }

    public List<RuleLifecycleEvent> timeline(Long definitionId) {
        return eventMapper.selectList(new LambdaQueryWrapper<RuleLifecycleEvent>()
                .eq(RuleLifecycleEvent::getDefinitionId, definitionId)
                .orderByAsc(RuleLifecycleEvent::getCreateTime)
                .orderByAsc(RuleLifecycleEvent::getId));
    }

    public List<RuleRevision> listRevisions(Long definitionId) {
        return revisionMapper.selectList(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .orderByDesc(RuleRevision::getRevisionNo));
    }

    public RuleRevision getRevision(Long definitionId, Long revisionId) {
        RuleRevision revision = loadRevision(revisionId);
        if (revision == null || !definitionId.equals(revision.getDefinitionId())) {
            throw new IllegalArgumentException("规则修订不存在");
        }
        return revision;
    }

    public RuleRevision currentDraft(Long definitionId) {
        return findDraft(definitionId);
    }

    public RuleRevision requireEditableDraft(Long definitionId) {
        RuleRevision draft = findDraft(definitionId);
        if (draft == null) {
            throw governance(409, "FROZEN_REVISION_WRITE_REJECTED",
                    "规则没有可编辑的 DRAFT 修订");
        }
        return requireEditableDraft(definitionId, draft.getId());
    }

    public RuleRevision requireEditableDraft(Long definitionId, Long revisionId) {
        RuleRevision revision = loadRevision(revisionId);
        if (revision == null) {
            throw governance(409, "FROZEN_REVISION_WRITE_REJECTED",
                    "指定修订不存在或已冻结");
        }
        if (!definitionId.equals(revision.getDefinitionId())) {
            throw governance(400, "REVISION_DEFINITION_MISMATCH",
                    "修订不属于指定规则");
        }
        if (!RuleRevisionState.DRAFT.name().equals(revision.getState())) {
            throw governance(409, "FROZEN_REVISION_WRITE_REJECTED",
                    "只能修改 DRAFT 状态的规则修订");
        }
        return revision;
    }

    public RulePreflightReport preflightReport(Long definitionId, Long revisionId) {
        getRevision(definitionId, revisionId);
        return preflight(revisionId);
    }

    @Transactional
    public RuleRevision publishApproved(Long definitionId, RuleLifecycleActionRequest request) {
        RuleRevision approved = findLatestRevision(definitionId, RuleRevisionState.APPROVED);
        if (approved == null) throw new IllegalStateException("规则没有可发布的 APPROVED 修订");
        return publish(approved.getId(), request);
    }

    @Transactional
    public RuleRevision offlinePublished(Long definitionId, RuleLifecycleActionRequest request) {
        RuleRevision published = findPublishedRevision(definitionId);
        if (published == null) throw new IllegalStateException("规则没有 PUBLISHED 修订");
        return offline(published.getId(), request);
    }

    @Transactional
    public RuleRevision activateImportedArtifact(Long artifactId, Long definitionId, String importActor) {
        DecisionArtifact artifact = artifactService.getById(artifactId);
        if (artifact == null) throw new IllegalArgumentException("导入制品不存在");
        DecisionArtifactService.RuntimeProjection projection =
                artifactService.loadRuntimeProjection(artifactId);
        RuleRevision draft = ensureDraft(definitionId);
        draft.setModelJson(projection.getModelJson());
        draft.setCompiledScript(projection.getCompiledScript());
        draft.setCompiledType("QLEXPRESS");
        draft.setOpenApiConfigJson(projection.getOpenApiConfigJson());
        draft.setInputSchemaJson(projection.getInputSchemaJson());
        draft.setOutputSchemaJson(projection.getOutputSchemaJson());
        draft.setContentDigest(artifact.getArtifactDigest());
        draft.setArtifactId(artifactId);
        draft.setApproveBy(importActor);
        draft.setApproveTime(LocalDateTime.now());
        draft.setForcePublishReason("跨环境导入已批准决策制品");
        if (!compareAndSetState(draft, RuleRevisionState.DRAFT.name(),
                RuleRevisionState.APPROVED.name())) {
            throw new IllegalStateException("目标规则草稿已被其他会话修改");
        }
        draft.setState(RuleRevisionState.APPROVED.name());
        persistRevisionSnapshot(draft);
        RuleLifecycleEvent imported = event(draft, "IMPORT_APPROVED_ARTIFACT",
                RuleRevisionState.DRAFT.name(), RuleRevisionState.APPROVED.name(),
                "导入不可变制品 " + artifact.getArtifactDigest());
        imported.setArtifactDigest(artifact.getArtifactDigest());
        insertEvent(imported);
        RuleLifecycleActionRequest request = new RuleLifecycleActionRequest();
        request.setComment("跨环境部署");
        return publish(draft.getId(), request);
    }

    private RuleRevision transition(RuleRevision revision, RuleRevisionState expected,
                                    RuleRevisionState target, String action, String comment) {
        return transition(revision, expected, target, action, comment, null);
    }

    private RuleRevision transition(RuleRevision revision, RuleRevisionState expected,
                                    RuleRevisionState target, String action, String comment,
                                    String artifactDigest) {
        if (!expected.canTransitionTo(target)) {
            throw new IllegalStateException("不允许的生命周期变更: " + expected + " -> " + target);
        }
        if (!compareAndSetState(revision, expected.name(), target.name())) {
            throw new IllegalStateException("规则修订已被其他会话修改，请刷新后重试");
        }
        revision.setState(target.name());
        revision.setUpdateBy(actor());
        revision.setUpdateTime(LocalDateTime.now());
        if (target == RuleRevisionState.OFFLINE) {
            revision.setOfflineBy(actor());
            revision.setOfflineTime(LocalDateTime.now());
        }
        persistRevisionSnapshot(revision);
        RuleLifecycleEvent event = event(revision, action, expected.name(), target.name(), comment);
        event.setArtifactDigest(artifactDigest);
        insertEvent(event);
        return revision;
    }

    private void applyPreflight(RuleRevision revision, RulePreflightReport report) {
        revision.setCompiledScript(report.getCompiledScript());
        revision.setCompiledType(report.getCompiledType());
        revision.setInputSchemaJson(report.getInputSchemaJson());
        revision.setOutputSchemaJson(report.getOutputSchemaJson());
        revision.setContentDigest(report.getContentDigest());
        revision.setValidationReportDigest(Sha256Digests.text(CanonicalJson.write(report)));
    }

    private void assertPreflightValid(RulePreflightReport report) {
        if (!report.isValid()) {
            throw new IllegalStateException("发布前验证未通过: "
                    + (report.getErrors().isEmpty() ? "未知错误" : report.getErrors().get(0).getMessage()));
        }
    }

    private RuleRevision requireState(Long revisionId, RuleRevisionState state) {
        RuleRevision revision = loadRevision(revisionId);
        if (revision == null) throw new IllegalArgumentException("规则修订不存在");
        if (!state.name().equals(revision.getState())) {
            throw new IllegalStateException("当前操作要求修订状态为 " + state
                    + "，实际为 " + revision.getState());
        }
        return revision;
    }

    private RuleLifecycleEvent event(RuleRevision revision, String action, String from,
                                     String to, String comment) {
        RuleLifecycleEvent event = new RuleLifecycleEvent();
        event.setDefinitionId(revision.getDefinitionId());
        event.setRevisionId(revision.getId());
        event.setAction(action);
        event.setFromState(from);
        event.setToState(to);
        event.setActor(actor());
        event.setComment(comment);
        event.setContentDigest(revision.getContentDigest());
        event.setValidationReportDigest(revision.getValidationReportDigest());
        event.setRequestSource("CONSOLE");
        event.setCreateTime(LocalDateTime.now());
        return event;
    }

    private String comment(RuleLifecycleActionRequest request) {
        return request == null ? null : trim(request.getComment());
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void validateDraftBase(Long definitionId, Long baseRevisionId,
                                   RuleRevision base) {
        if (base == null || !definitionId.equals(base.getDefinitionId())) {
            throw governance(400, "BASE_REVISION_INVALID",
                    "基线修订不存在或不属于当前规则");
        }
        if (!RuleRevisionState.APPROVED.name().equals(base.getState())
                && !RuleRevisionState.PUBLISHED.name().equals(base.getState())
                && !RuleRevisionState.OFFLINE.name().equals(base.getState())) {
            throw governance(400, "BASE_REVISION_INVALID",
                    "基线修订必须为 APPROVED、PUBLISHED 或 OFFLINE");
        }
        if (!baseRevisionId.equals(base.getId())) {
            throw governance(400, "BASE_REVISION_INVALID",
                    "基线修订身份不匹配");
        }
    }

    private RuleGovernanceException governance(int httpStatus, String code,
                                               String message) {
        return new RuleGovernanceException(httpStatus, code, message,
                Collections.singletonList(
                        RuleValidationIssue.error(code, "$", message)));
    }

    protected String actor() {
        return operatorResolver.resolve();
    }

    protected RuleDefinition loadDefinition(Long definitionId) {
        return definitionService.getById(definitionId);
    }

    protected RuleDefinition lockDefinition(Long definitionId) {
        return definitionMapper.selectOne(
                new LambdaQueryWrapper<RuleDefinition>()
                        .eq(RuleDefinition::getId, definitionId)
                        .last("FOR UPDATE"));
    }

    protected RuleDefinitionContent loadContent(Long definitionId) {
        return definitionService.getContent(definitionId);
    }

    protected RuleRevision loadRevision(Long revisionId) {
        return revisionMapper.selectById(revisionId);
    }

    protected RuleRevision findDraft(Long definitionId) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .eq(RuleRevision::getState, RuleRevisionState.DRAFT.name())
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
    }

    protected RuleRevision findPublishedRevision(Long definitionId) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .eq(RuleRevision::getState, RuleRevisionState.PUBLISHED.name())
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
    }

    protected RuleRevision findPendingRevision(Long definitionId) {
        if (revisionMapper == null) return null;
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .eq(RuleRevision::getState, RuleRevisionState.REVIEW.name())
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
    }

    protected RuleRevision findLatestRevision(Long definitionId, RuleRevisionState state) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .eq(RuleRevision::getState, state.name())
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
    }

    protected RuleRevision findLatestAnyRevision(Long definitionId) {
        if (revisionMapper == null) return null;
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
    }

    protected int nextRevisionNo(Long definitionId) {
        RuleRevision latest = revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
        return latest == null || latest.getRevisionNo() == null ? 1 : latest.getRevisionNo() + 1;
    }

    protected void insertRevision(RuleRevision revision) {
        if (revisionMapper.insert(revision) != 1) {
            throw new IllegalStateException("创建规则草稿失败");
        }
    }

    protected void persistRevisionSnapshot(RuleRevision revision) {
        revisionMapper.updateById(revision);
    }

    protected boolean compareAndSetState(RuleRevision revision, String expected, String target) {
        int lockVersion = revision.getLockVersion() == null ? 0 : revision.getLockVersion();
        int updated = revisionMapper.update(null, new LambdaUpdateWrapper<RuleRevision>()
                .eq(RuleRevision::getId, revision.getId())
                .eq(RuleRevision::getState, expected)
                .eq(RuleRevision::getLockVersion, lockVersion)
                .set(RuleRevision::getState, target)
                .set(RuleRevision::getLockVersion, lockVersion + 1)
                .set(RuleRevision::getUpdateBy, actor())
                .set(RuleRevision::getUpdateTime, LocalDateTime.now()));
        if (updated == 1) revision.setLockVersion(lockVersion + 1);
        return updated == 1;
    }

    protected RulePreflightReport preflight(Long revisionId) {
        return preflightService.validate(revisionId);
    }

    protected DecisionArtifact buildArtifact(Long revisionId, String actor) {
        return artifactService.buildApprovedArtifact(revisionId, actor);
    }

    protected void activateArtifact(RuleRevision revision, String actor) {
        RuleDefinition definition = loadDefinition(revision.getDefinitionId());
        DecisionArtifact artifact = artifactService.getById(revision.getArtifactId());
        if (definition == null || artifact == null) {
            throw new IllegalStateException("发布激活所需的规则或制品不存在");
        }
        DecisionArtifactService.RuntimeProjection projection =
                artifactService.loadRuntimeProjection(artifact.getId());
        String projectCode = definition.getProjectCode();
        if ((projectCode == null || projectCode.isBlank()) && definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            projectCode = project == null ? null : project.getProjectCode();
        }
        RulePublished published = publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, definition.getId()).last("LIMIT 1"));
        boolean insert = published == null;
        if (insert) published = new RulePublished();
        published.setRuleCode(definition.getRuleCode());
        published.setDefinitionId(definition.getId());
        published.setRevisionId(revision.getId());
        published.setArtifactId(artifact.getId());
        published.setArtifactDigest(artifact.getArtifactDigest());
        published.setProjectCode(projectCode);
        published.setVersion(revision.getRevisionNo());
        published.setModelType(definition.getModelType());
        published.setCompiledScript(projection.getCompiledScript());
        published.setCompiledType(revision.getCompiledType());
        published.setModelJson(projection.getModelJson());
        published.setStatus(1);
        published.setPublishBy(actor);
        published.setPublishTime(LocalDateTime.now());
        published.setOfflineTime(null);
        if (insert) publishedMapper.insert(published); else publishedMapper.updateById(published);
        publishedMapper.updateOpenApiConfigByDefinitionId(definition.getId(),
                projection.getOpenApiConfigJson());

        RuleDefinitionVersion version = new RuleDefinitionVersion();
        version.setDefinitionId(definition.getId());
        version.setVersion(revision.getRevisionNo());
        version.setModelJson(projection.getModelJson());
        version.setCompiledScript(projection.getCompiledScript());
        version.setCompiledType(revision.getCompiledType());
        version.setOpenApiConfigJson(projection.getOpenApiConfigJson());
        version.setChangeLog(revision.getForcePublishReason());
        version.setPublishBy(actor);
        version.setPublishTime(LocalDateTime.now());
        versionMapper.insert(version);

        definition.setPublishedVersion(revision.getRevisionNo());
        definition.setStatus(1);
        definitionService.updateById(definition);

        RulePushMessage message = pushMessage(definition, revision, artifact, projection,
                projectCode, "PUBLISH");
        insertOutbox(definition, revision, artifact, message);
    }

    protected void deactivateArtifact(RuleRevision revision, String actor) {
        RuleDefinition definition = loadDefinition(revision.getDefinitionId());
        RulePublished published = publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, revision.getDefinitionId()).last("LIMIT 1"));
        if (published != null && revision.getId().equals(published.getRevisionId())) {
            published.setStatus(0);
            published.setOfflineTime(LocalDateTime.now());
            publishedMapper.updateById(published);
        }
        if (definition != null) {
            definition.setStatus(2);
            definitionService.updateById(definition);
            RulePushMessage message = new RulePushMessage();
            message.setRuleCode(definition.getRuleCode());
            message.setRevisionId(revision.getId());
            message.setArtifactDigest(published == null ? null : published.getArtifactDigest());
            message.setProjectCode(published == null ? definition.getProjectCode() : published.getProjectCode());
            message.setAction("UNPUBLISH");
            message.setPublishTime(System.currentTimeMillis());
            insertOutbox(definition, revision, null, message);
        }
    }

    private RulePushMessage pushMessage(RuleDefinition definition, RuleRevision revision,
                                        DecisionArtifact artifact,
                                        DecisionArtifactService.RuntimeProjection projection,
                                        String projectCode, String action) {
        RulePushMessage message = new RulePushMessage();
        message.setRuleCode(definition.getRuleCode());
        message.setVersion(revision.getRevisionNo());
        message.setRevisionId(revision.getId());
        message.setArtifactDigest(artifact.getArtifactDigest());
        message.setModelType(definition.getModelType());
        message.setCompiledScript(projection.getCompiledScript());
        message.setCompiledType(revision.getCompiledType());
        message.setModelJson(projection.getModelJson());
        List<RuleDefinitionOutputField> fields = definitionService.listOutputFields(definition.getId());
        if (fields != null) {
            message.setOutputScriptNames(fields.stream().map(RuleDefinitionOutputField::getScriptName)
                    .filter(name -> name != null && !name.isBlank()).collect(Collectors.toList()));
        }
        message.setProjectCode(projectCode);
        message.setPublishTime(System.currentTimeMillis());
        message.setAction(action);
        return message;
    }

    private void insertOutbox(RuleDefinition definition, RuleRevision revision,
                              DecisionArtifact artifact, RulePushMessage message) {
        RulePublishOutbox outbox = new RulePublishOutbox();
        outbox.setOperationId(UUID.randomUUID().toString());
        outbox.setDefinitionId(definition.getId());
        outbox.setRevisionId(revision.getId());
        outbox.setArtifactId(artifact == null ? revision.getArtifactId() : artifact.getId());
        outbox.setMessageJson(JSON.toJSONString(message));
        outbox.setDeliveryStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setCreateTime(LocalDateTime.now());
        outbox.setUpdateTime(LocalDateTime.now());
        outboxMapper.insert(outbox);
    }

    protected void insertEvent(RuleLifecycleEvent event) {
        eventMapper.insert(event);
    }
}
