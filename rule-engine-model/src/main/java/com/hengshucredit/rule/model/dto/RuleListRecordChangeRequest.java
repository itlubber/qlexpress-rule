package com.hengshucredit.rule.model.dto;

import com.hengshucredit.rule.model.entity.RuleListRecord;
import lombok.Data;

@Data
public class RuleListRecordChangeRequest {
    private String operation;
    private RuleListRecord record;
}
