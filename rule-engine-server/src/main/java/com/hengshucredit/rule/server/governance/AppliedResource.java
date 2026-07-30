package com.hengshucredit.rule.server.governance;

public record AppliedResource(Long resourceId,
                              Integer versionNo,
                              String effectiveStatus,
                              Long artifactId) {
}
