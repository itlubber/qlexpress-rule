package com.hengshucredit.rule.server.governance;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class GovernedResourceAdapterRegistryTest {

    @Test(expected = IllegalStateException.class)
    public void duplicateResourceTypeIsRejected() {
        new GovernedResourceAdapterRegistry(
                List.of(adapter("RULE"), adapter("rule")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownResourceTypeIsRejected() {
        new GovernedResourceAdapterRegistry(
                List.of(adapter("RULE"))).require("MODEL");
    }

    @Test
    public void lookupIsCaseInsensitiveAndDeterministic() {
        GovernedResourceAdapter rule = adapter("RULE");
        GovernedResourceAdapterRegistry registry =
                new GovernedResourceAdapterRegistry(List.of(rule));

        Assert.assertSame(rule, registry.require("rule"));
        Assert.assertEquals(Collections.singleton("RULE"),
                registry.resourceTypes());
    }

    @Test
    public void projectRuleBindingsBelongToTheRuleApprovalTab() {
        Assert.assertEquals(
                List.of(GovernanceResourceTypes.RULE,
                        GovernanceResourceTypes.RULE_PROJECT_BINDING),
                GovernanceResourceTypes.forTab("RULE"));
    }

    @Test
    public void fieldValidationBelongsToTheFieldApprovalTab() {
        Assert.assertEquals(
                List.of(GovernanceResourceTypes.VARIABLE,
                        GovernanceResourceTypes.DATA_OBJECT,
                        GovernanceResourceTypes.FIELD_VALIDATION),
                GovernanceResourceTypes.forTab("FIELD"));
    }

    @Test
    public void billingConfigurationBelongsToTheProjectApprovalTab() {
        Assert.assertEquals(
                List.of(GovernanceResourceTypes.PROJECT,
                        GovernanceResourceTypes.BILLING_CONFIG),
                GovernanceResourceTypes.forTab("PROJECT"));
    }

    private static GovernedResourceAdapter adapter(String resourceType) {
        return new GovernedResourceAdapter() {
            @Override
            public String resourceType() {
                return resourceType;
            }

            @Override
            public ResourceSnapshot loadEffective(Long resourceId) {
                return null;
            }

            @Override
            public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
                return draft;
            }

            @Override
            public List<GovernanceIssue> validate(ResourceSnapshot draft) {
                return Collections.emptyList();
            }

            @Override
            public List<ResourceDependencyRef> collectDependencies(
                    ResourceSnapshot draft) {
                return Collections.emptyList();
            }

            @Override
            public ResourceDiff diff(
                    ResourceSnapshot left, ResourceSnapshot right) {
                return null;
            }

            @Override
            public AppliedResource apply(ApprovalApplyContext context) {
                return null;
            }
        };
    }
}
