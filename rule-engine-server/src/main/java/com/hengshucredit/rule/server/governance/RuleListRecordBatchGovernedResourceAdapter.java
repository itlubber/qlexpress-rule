package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.service.RuleListChangeBatchService;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RuleListRecordBatchGovernedResourceAdapter
        implements GovernedResourceAdapter {

    private final RuleListChangeBatchService batchService;

    public RuleListRecordBatchGovernedResourceAdapter(
            RuleListChangeBatchService batchService) {
        this.batchService = batchService;
    }

    @Override
    public String resourceType() {
        return GovernanceResourceTypes.LIST_RECORD_BATCH;
    }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        return batchService.loadBatchSnapshot(resourceId);
    }

    @Override
    public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
        return batchService.loadBatchSnapshot(batchId(draft));
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        Map<String, Object> value = value(draft);
        return batchService.validateBatch(longValue(value.get("batchId")),
                stringValue(value.get("contentDigest")));
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        Long listId = longValue(value(draft).get("listId"));
        if (listId == null || listId <= 0) {
            return List.of();
        }
        return List.of(new ResourceDependencyRef(
                GovernanceResourceTypes.LIST_LIBRARY, listId,
                GovernanceResourceTypes.LIST_LIBRARY, "$.listId",
                "BELONGS_TO", true));
    }

    @Override
    public ResourceDiff diff(ResourceSnapshot left,
                             ResourceSnapshot right) {
        return JsonResourceDiff.compare(left, right);
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        if (!"CREATE".equals(normalize(context.action()))) {
            throw new IllegalArgumentException(
                    "名单记录批次只支持创建审批");
        }
        Map<String, Object> value = value(context.snapshot());
        Long batchId = longValue(value.get("batchId"));
        Long appliedId = batchService.applyBatch(batchId,
                stringValue(value.get("contentDigest")),
                context.actor());
        return new AppliedResource(appliedId,
                context.nextVersionNo(), "ACTIVE", null);
    }

    @Override
    public void onCreateApprovalTerminated(
            ResourceSnapshot requestSnapshot,
            String actor,
            String comment,
            String terminalStatus) {
        batchService.terminateBatch(batchId(requestSnapshot),
                terminalStatus, comment);
    }

    private Long batchId(ResourceSnapshot snapshot) {
        Long batchId = longValue(value(snapshot).get("batchId"));
        if (batchId == null || batchId <= 0) {
            throw new IllegalArgumentException(
                    "名单变更批次 ID 不能为空");
        }
        return batchId;
    }

    private Map<String, Object> value(ResourceSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "名单变更批次快照不能为空");
        }
        return CanonicalJson.readMap(snapshot.snapshotJson());
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
