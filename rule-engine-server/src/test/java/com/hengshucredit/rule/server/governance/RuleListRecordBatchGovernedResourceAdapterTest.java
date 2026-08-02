package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.service.RuleListChangeBatchService;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class RuleListRecordBatchGovernedResourceAdapterTest {

    @Test
    public void normalizationAlwaysReloadsTrustedServerSnapshot() {
        TestBatchService service = new TestBatchService();
        RuleListRecordBatchGovernedResourceAdapter adapter =
                new RuleListRecordBatchGovernedResourceAdapter(service);

        ResourceSnapshot normalized = adapter.normalizeDraft(
                ResourceSnapshot.ofJson("{\"batchId\":101,"
                        + "\"addCount\":999,\"samples\":["
                        + "{\"itemContent\":\"plain-secret\"}]}"));

        Map<String, Object> value = CanonicalJson.readMap(
                normalized.snapshotJson());
        Assert.assertEquals(1,
                ((Number) value.get("addCount")).intValue());
        Assert.assertFalse(normalized.snapshotJson()
                .contains("plain-secret"));
        Assert.assertEquals(Long.valueOf(101L), service.loadedBatchId);
    }

    @Test
    public void validateAndDependencyUseBatchDigestAndStableListId() {
        TestBatchService service = new TestBatchService();
        service.issues = List.of(GovernanceIssue.error(
                "LIST_BATCH_BASELINE_CHANGED", "baseline changed",
                GovernanceResourceTypes.LIST_RECORD_BATCH,
                101L, "$.items[0]"));
        RuleListRecordBatchGovernedResourceAdapter adapter =
                new RuleListRecordBatchGovernedResourceAdapter(service);
        ResourceSnapshot snapshot = service.serverSnapshot;

        List<GovernanceIssue> issues = adapter.validate(snapshot);

        Assert.assertEquals("LIST_BATCH_BASELINE_CHANGED",
                issues.get(0).code());
        Assert.assertEquals("batch-digest", service.validatedDigest);
        Assert.assertEquals(List.of(new ResourceDependencyRef(
                        GovernanceResourceTypes.LIST_LIBRARY, 9L,
                        GovernanceResourceTypes.LIST_LIBRARY,
                        "$.listId", "BELONGS_TO", true)),
                adapter.collectDependencies(snapshot));
    }

    @Test
    public void approvalAppliesExactBatchAndTerminationSealsIt() {
        TestBatchService service = new TestBatchService();
        RuleListRecordBatchGovernedResourceAdapter adapter =
                new RuleListRecordBatchGovernedResourceAdapter(service);

        AppliedResource applied = adapter.apply(
                new ApprovalApplyContext(81L, null, 1,
                        "CREATE", service.serverSnapshot,
                        "reviewer", null));
        adapter.onCreateApprovalTerminated(service.serverSnapshot,
                "reviewer", "not accepted", "REJECTED");

        Assert.assertEquals(Long.valueOf(101L), applied.resourceId());
        Assert.assertEquals("ACTIVE", applied.effectiveStatus());
        Assert.assertEquals(Long.valueOf(101L), service.appliedBatchId);
        Assert.assertEquals("batch-digest", service.appliedDigest);
        Assert.assertEquals("reviewer", service.appliedBy);
        Assert.assertEquals("REJECTED", service.terminalStatus);
        Assert.assertEquals("not accepted", service.terminalMessage);
    }

    private static class TestBatchService
            extends RuleListChangeBatchService {
        private final ResourceSnapshot serverSnapshot =
                ResourceSnapshot.ofJson("{\"batchId\":101,"
                        + "\"listId\":9,\"listCode\":"
                        + "\"mobile_black\",\"listName\":"
                        + "\"mobile blacklist\",\"addCount\":1,"
                        + "\"contentDigest\":\"batch-digest\","
                        + "\"samples\":[{\"itemContent\":"
                        + "\"138****8000\"}]}");
        private List<GovernanceIssue> issues = List.of();
        private Long loadedBatchId;
        private String validatedDigest;
        private Long appliedBatchId;
        private String appliedDigest;
        private String appliedBy;
        private String terminalStatus;
        private String terminalMessage;

        @Override
        public ResourceSnapshot loadBatchSnapshot(Long batchId) {
            loadedBatchId = batchId;
            return serverSnapshot;
        }

        @Override
        public List<GovernanceIssue> validateBatch(
                Long batchId, String expectedDigest) {
            loadedBatchId = batchId;
            validatedDigest = expectedDigest;
            return issues;
        }

        @Override
        public Long applyBatch(Long batchId, String expectedDigest,
                               String actor) {
            appliedBatchId = batchId;
            appliedDigest = expectedDigest;
            appliedBy = actor;
            return batchId;
        }

        @Override
        public void terminateBatch(Long batchId, String terminalStatus,
                                   String message) {
            loadedBatchId = batchId;
            this.terminalStatus = terminalStatus;
            terminalMessage = message;
        }
    }
}
