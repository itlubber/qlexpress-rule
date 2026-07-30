package com.hengshucredit.rule.server.governance;

public record ResourceSnapshot(String snapshotJson,
                               String effectiveStatus,
                               String secretPayloadCiphertext,
                               String secretDigest) {
    public ResourceSnapshot {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("资源快照不能为空");
        }
        effectiveStatus = effectiveStatus == null
                ? "ACTIVE" : effectiveStatus;
    }

    public static ResourceSnapshot ofJson(String snapshotJson) {
        return new ResourceSnapshot(snapshotJson, "ACTIVE", null, null);
    }
}
