package com.hengshucredit.rule.model.dto;

import com.hengshucredit.rule.model.entity.RuleVariable;
import lombok.Data;

import java.util.Map;

@Data
public class VariableSourcePreviewRequest {
    private RuleVariable variable;
    private Map<String, Object> params;
}
