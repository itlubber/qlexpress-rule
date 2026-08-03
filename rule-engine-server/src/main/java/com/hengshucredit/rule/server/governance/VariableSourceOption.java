package com.hengshucredit.rule.server.governance;

public record VariableSourceOption(
        Long id,
        String code,
        String name,
        String scope,
        Long projectId,
        Long parentId,
        String parentName) {
}
