package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernanceResourceTypes;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Bridges the rule designer revision workflow into the unified approval
 * workflow. The legacy revision submit and the unified request creation are
 * committed atomically.
 */
@Service
public class RuleGovernanceSubmissionService {

    private final RuleLifecycleService lifecycleService;
    private final GovernanceApprovalService approvalService;
    private final GovernedResourceAdapterRegistry adapterRegistry;
    private final ConsoleOperatorResolver operatorResolver;
    private final RuleRevisionMapper revisionMapper;

    public RuleGovernanceSubmissionService(
            RuleLifecycleService lifecycleService,
            GovernanceApprovalService approvalService,
            GovernedResourceAdapterRegistry adapterRegistry,
            ConsoleOperatorResolver operatorResolver,
            RuleRevisionMapper revisionMapper) {
        this.lifecycleService = lifecycleService;
        this.approvalService = approvalService;
        this.adapterRegistry = adapterRegistry;
        this.operatorResolver = operatorResolver;
        this.revisionMapper = revisionMapper;
    }

    @Transactional
    public RuleRevision submit(
            Long revisionId,
            RuleLifecycleActionRequest action) {
        RuleRevision revision =
                lifecycleService.submit(revisionId, action);
        String actor = operatorResolver.resolve();
        ResourceSnapshot snapshot = adapterRegistry
                .require(GovernanceResourceTypes.RULE)
                .loadEffective(revision.getDefinitionId());
        Map<String, Object> value =
                CanonicalJson.readMap(snapshot.snapshotJson());

        GovernanceDraftRequest draft =
                new GovernanceDraftRequest();
        draft.setResourceType(GovernanceResourceTypes.RULE);
        draft.setResourceId(revision.getDefinitionId());
        draft.setProjectId(longValue(value.get("projectId")));
        draft.setAction("UPDATE");
        draft.setSnapshotJson(snapshot.snapshotJson());
        draft.setEffectiveStatus(snapshot.effectiveStatus());
        draft.setSecretPayloadCiphertext(
                snapshot.secretPayloadCiphertext());
        draft.setSecretDigest(snapshot.secretDigest());
        draft.setChangeSummary(comment(action,
                "提交规则修订 v" + revision.getRevisionNo()));

        GovernanceApprovalRequest request =
                approvalService.createDraft(draft, actor);
        GovernanceSubmitRequest submit =
                new GovernanceSubmitRequest();
        submit.setComment(comment(action, "提交规则审批"));
        request = approvalService.submit(
                request.getId(), submit, actor);
        revision.setGovernanceRequestId(request.getId());
        if (revisionMapper.updateById(revision) != 1) {
            throw new IllegalStateException(
                    "规则修订绑定统一审批申请失败");
        }
        return revision;
    }

    private String comment(RuleLifecycleActionRequest action,
                           String fallback) {
        return action == null || action.getComment() == null
                || action.getComment().isBlank()
                ? fallback : action.getComment().trim();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
