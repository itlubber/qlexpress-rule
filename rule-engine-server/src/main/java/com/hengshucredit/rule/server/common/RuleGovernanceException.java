package com.hengshucredit.rule.server.common;

import com.hengshucredit.rule.model.dto.RuleValidationIssue;

import java.util.Collections;
import java.util.List;

public final class RuleGovernanceException extends RuntimeException {
    private final int httpStatus;
    private final String code;
    private final List<RuleValidationIssue> issues;

    public RuleGovernanceException(int httpStatus, String code, String message,
                                   List<RuleValidationIssue> issues) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.issues = issues == null
                ? Collections.emptyList() : List.copyOf(issues);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public List<RuleValidationIssue> getIssues() {
        return issues;
    }
}
