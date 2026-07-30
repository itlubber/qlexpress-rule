package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ConsoleUserPermissionRequest {
    private List<Long> roleIds;
    private Map<String, String> permissionOverrides;
}
