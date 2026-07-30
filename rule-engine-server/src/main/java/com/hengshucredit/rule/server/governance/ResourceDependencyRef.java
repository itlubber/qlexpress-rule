package com.hengshucredit.rule.server.governance;

public record ResourceDependencyRef(String targetResourceType,
                                    Long targetResourceId,
                                    String refType,
                                    String referencePath,
                                    String relationType,
                                    boolean required) {
    public ResourceDependencyRef {
        if (targetResourceType == null || targetResourceType.isBlank()) {
            throw new IllegalArgumentException("依赖资源类型不能为空");
        }
        if (targetResourceId == null) {
            throw new IllegalArgumentException("依赖资源ID不能为空");
        }
    }
}
