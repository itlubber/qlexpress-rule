package com.hengshucredit.rule.model.dto;

import com.hengshucredit.rule.model.enums.RuleDraftSourceType;
import lombok.Data;

@Data
public class RuleDraftSourceRequest {
    private RuleDraftSourceType sourceType;
    private Long sourceId;
}
