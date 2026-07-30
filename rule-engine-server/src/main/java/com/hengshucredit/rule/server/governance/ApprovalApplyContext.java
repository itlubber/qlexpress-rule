package com.hengshucredit.rule.server.governance;

public record ApprovalApplyContext(Long requestId,
                                   Long resourceId,
                                   Integer nextVersionNo,
                                   String action,
                                   ResourceSnapshot snapshot,
                                   String actor,
                                   Long sourceVersionId) {
}
