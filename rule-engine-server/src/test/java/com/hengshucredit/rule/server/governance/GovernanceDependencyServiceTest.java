package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class GovernanceDependencyServiceTest {

    @Test
    public void missingRequiredDependencyBlocksSubmission() {
        TestService service = new TestService();
        ResourceDependencyRef dependency = new ResourceDependencyRef(
                "VARIABLE", 91L, "VARIABLE", "$.conditions[0].left",
                "REFERENCES", true);

        GovernancePreflightReport report = service.preflight(
                "RULE", 7L, List.of(dependency), List.of());

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_MISSING",
                report.errors().get(0).code());
        Assert.assertEquals("MISSING",
                report.dependencies().get(0).resolutionStatus());
    }

    @Test
    public void inactiveDependencyBlocksSubmission() {
        TestService service = new TestService();
        service.target = resource(91L, 15L, 4, "OFFLINE");
        ResourceDependencyRef dependency = new ResourceDependencyRef(
                "MODEL", 91L, "MODEL", "$.modelId",
                "REFERENCES", true);

        GovernancePreflightReport report = service.preflight(
                "RULE", 7L, List.of(dependency), List.of());

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_INACTIVE",
                report.errors().get(0).code());
    }

    @Test
    public void effectiveDependencyRecordsExactVersionAndDigest() {
        TestService service = new TestService();
        service.target = resource(91L, 15L, 4, "ACTIVE");
        service.version = new GovernedResourceVersion();
        service.version.setId(15L);
        service.version.setVersionNo(4);
        service.version.setSnapshotDigest("digest-v4");
        ResourceDependencyRef dependency = new ResourceDependencyRef(
                "MODEL", 91L, "MODEL", "$.modelId",
                "REFERENCES", true);

        GovernancePreflightReport report = service.preflight(
                "RULE", 7L, List.of(dependency), List.of());

        Assert.assertTrue(report.valid());
        Assert.assertEquals(Long.valueOf(15L),
                report.dependencies().get(0).targetVersionId());
        Assert.assertEquals("digest-v4",
                report.dependencies().get(0).targetDigest());
        Assert.assertNotNull(report.dependencyDigest());
    }

    @Test
    public void changedDependencyVersionConflictsAtApprovalTime() {
        TestService service = new TestService();
        service.target = resource(91L, 16L, 5, "ACTIVE");
        GovernancePreflightReport.ResolvedDependency submitted =
                new GovernancePreflightReport.ResolvedDependency(
                        "MODEL", 91L, 15L, 4, "$.modelId",
                        "REFERENCES", true, "RESOLVED", "digest-v4",
                        null, null);

        GovernancePreflightReport report = service.revalidate(
                List.of(submitted));

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_VERSION_CHANGED",
                report.errors().get(0).code());
    }

    @Test
    public void changedDigestOnSameVersionConflictsAtApprovalTime() {
        TestService service = new TestService();
        service.target = resource(91L, 15L, 4, "ACTIVE");
        service.version = new GovernedResourceVersion();
        service.version.setId(15L);
        service.version.setVersionNo(4);
        service.version.setSnapshotDigest("digest-tampered");
        GovernancePreflightReport.ResolvedDependency submitted =
                new GovernancePreflightReport.ResolvedDependency(
                        "MODEL", 91L, 15L, 4, "$.modelId",
                        "REFERENCES", true, "RESOLVED", "digest-v4",
                        null, null);

        GovernancePreflightReport report = service.revalidate(
                List.of(submitted));

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_DIGEST_CHANGED",
                report.errors().get(0).code());
    }

    @Test
    public void explicitReferenceTypeMismatchBlocksSubmission() {
        TestService service = new TestService();
        ResourceDependencyRef dependency = new ResourceDependencyRef(
                "MODEL", 91L, "VARIABLE", "$.modelId",
                "REFERENCES", true);

        GovernancePreflightReport report = service.preflight(
                "RULE", 7L, List.of(dependency), List.of());

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_TYPE_MISMATCH",
                report.errors().get(0).code());
    }

    private static GovernedResource resource(Long resourceId,
                                             Long versionId,
                                             Integer versionNo,
                                             String status) {
        GovernedResource resource = new GovernedResource();
        resource.setResourceType("MODEL");
        resource.setResourceId(resourceId);
        resource.setEffectiveVersionId(versionId);
        resource.setEffectiveVersionNo(versionNo);
        resource.setEffectiveStatus(status);
        return resource;
    }

    private static class TestService extends GovernanceDependencyService {
        private GovernedResource target;
        private GovernedResourceVersion version;

        @Override
        protected GovernedResource findTarget(String resourceType,
                                              Long resourceId) {
            return target;
        }

        @Override
        protected GovernedResourceVersion findVersion(Long versionId) {
            return version;
        }
    }
}
