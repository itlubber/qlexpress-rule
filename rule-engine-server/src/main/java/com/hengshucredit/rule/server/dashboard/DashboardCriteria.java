package com.hengshucredit.rule.server.dashboard;

import java.time.LocalDateTime;

public record DashboardCriteria(Long projectId,
                                String projectCode,
                                String ruleCode,
                                String ruleName,
                                LocalDateTime startTime,
                                LocalDateTime endTime) {

    public DashboardCriteria(Long projectId,
                             String projectCode,
                             LocalDateTime startTime,
                             LocalDateTime endTime) {
        this(projectId, projectCode, null, null, startTime, endTime);
    }

    public boolean hasRuleFilter() {
        return ruleCode != null || ruleName != null;
    }
}
