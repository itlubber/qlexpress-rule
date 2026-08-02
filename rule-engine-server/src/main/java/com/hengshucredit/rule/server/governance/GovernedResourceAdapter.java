package com.hengshucredit.rule.server.governance;

import java.util.List;

public interface GovernedResourceAdapter {
    String resourceType();

    ResourceSnapshot loadEffective(Long resourceId);

    ResourceSnapshot normalizeDraft(ResourceSnapshot draft);

    List<GovernanceIssue> validate(ResourceSnapshot draft);

    /**
     * Validates a draft with its requested lifecycle action. Adapters that
     * only validate snapshot content keep the original implementation;
     * action-sensitive resources can additionally protect disable/delete.
     */
    default List<GovernanceIssue> validate(ResourceSnapshot draft,
                                           String action) {
        return validate(draft);
    }

    List<ResourceDependencyRef> collectDependencies(ResourceSnapshot draft);

    ResourceDiff diff(ResourceSnapshot left, ResourceSnapshot right);

    AppliedResource apply(ApprovalApplyContext context);

    /**
     * Called when an approval ends without applying its submitted snapshot.
     * Most resources do not need projection cleanup; adapters with a legacy
     * staging workflow can close that workflow here.
     */
    default void onApprovalTerminated(Long resourceId,
                                      ResourceSnapshot effectiveSnapshot,
                                      String actor,
                                      String comment,
                                      String terminalStatus) {
    }

    /**
     * Called for create approvals that own server-side staging data.
     * The request snapshot was normalized before it was persisted.
     */
    default void onCreateApprovalTerminated(
            ResourceSnapshot requestSnapshot,
            String actor,
            String comment,
            String terminalStatus) {
    }
}
