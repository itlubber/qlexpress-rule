package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RuleListChangeBatchResult {
    private Long batchId;
    private Long approvalRequestId;
    private boolean submittable;
    private Integer totalCount;
    private Integer addCount;
    private Integer updateCount;
    private Integer deleteCount;
    private Integer duplicateCount;
    private Integer invalidCount;
    private String contentDigest;
    private List<String> errors = new ArrayList<>();
}
