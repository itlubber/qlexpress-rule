package com.hengshucredit.rule.model.dto;

import com.hengshucredit.rule.model.entity.RuleRevision;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RuleDraftSaveResponse {
    private RuleRevision revision;
    private Integer designVersion;
    private boolean compileSuccess;
    private String compileMessage;
    private List<RuleValidationIssue> issues = new ArrayList<>();
}
