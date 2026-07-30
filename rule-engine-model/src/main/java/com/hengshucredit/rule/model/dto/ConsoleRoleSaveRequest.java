package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ConsoleRoleSaveRequest {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
    private List<String> permissionCodes;
}
