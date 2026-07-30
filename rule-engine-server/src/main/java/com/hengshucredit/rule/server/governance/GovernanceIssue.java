package com.hengshucredit.rule.server.governance;

public record GovernanceIssue(String severity,
                              String code,
                              String message,
                              String resourceType,
                              Long resourceId,
                              String referencePath,
                              String fixPath) {
    public boolean isError() {
        return "ERROR".equalsIgnoreCase(severity);
    }

    public static GovernanceIssue error(String code, String message,
                                        String resourceType, Long resourceId,
                                        String referencePath) {
        return new GovernanceIssue(
                "ERROR", code, message, resourceType, resourceId,
                referencePath, null);
    }
}
