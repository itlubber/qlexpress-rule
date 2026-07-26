package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class RuleRevisionRepairRequest {
    private Long sourceRevisionId;
    private String previewDigest;
}
