package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class GovernanceApprovalQuery {
    private int pageNum = 1;
    private int pageSize = 20;
    private String taskScope;
    private String tab;
    private String resourceType;
    private String status;
    private String action;
    private Long projectId;
    private String applicant;
    private String keyword;
}
