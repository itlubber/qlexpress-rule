package com.hengshucredit.rule.server.governance;

public record GovernanceApprovalSummary(
        long pendingCount,
        long myDraftCount,
        long myRequestCount,
        long completedCount) {
}
