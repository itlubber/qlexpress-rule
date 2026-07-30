package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernanceApprovalEvent;

import java.util.List;

public record GovernanceRequestDetail(
        GovernanceRequestView request,
        ResourceDiff diff,
        List<GovernanceApprovalEvent> events,
        List<GovernancePreflightReport.ResolvedDependency> dependencies,
        List<GovernanceVersionView> versions,
        boolean credentialChanged) {
}
