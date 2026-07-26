package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class RuleDraftSaveRequest {
    private Long definitionId;
    private Long revisionId;
    private Integer lockVersion;
    private String modelJson;
    private String openApiConfigJson;
    private Boolean updateOpenApiConfig;
}
