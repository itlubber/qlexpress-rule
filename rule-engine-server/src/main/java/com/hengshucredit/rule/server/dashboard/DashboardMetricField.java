package com.hengshucredit.rule.server.dashboard;

import java.util.Set;

import static com.hengshucredit.rule.server.dashboard.DashboardMapping.JsonSource.INPUT;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.JsonSource.OUTPUT;

public enum DashboardMetricField {
    REQUEST_ID("请求编号", 3L, INPUT, false),
    DEVICE_ID("设备编号", 14L, INPUT, false),
    LONGITUDE("经度", 73L, INPUT, true),
    LATITUDE("纬度", 74L, INPUT, true),
    PERIOD("期数", 76L, INPUT, true),
    AMOUNT("金额", 77L, INPUT, true),
    DECISION_RESULT("决策结果", 141L, OUTPUT, false);

    private static final Set<String> NON_SCALAR_TYPES =
            Set.of("OBJECT", "LIST", "MAP");

    private final String displayName;
    private final long defaultRefId;
    private final DashboardMapping.JsonSource defaultJsonSource;
    private final boolean numeric;

    DashboardMetricField(String displayName,
                         long defaultRefId,
                         DashboardMapping.JsonSource defaultJsonSource,
                         boolean numeric) {
        this.displayName = displayName;
        this.defaultRefId = defaultRefId;
        this.defaultJsonSource = defaultJsonSource;
        this.numeric = numeric;
    }

    public String displayName() {
        return displayName;
    }

    public long defaultRefId() {
        return defaultRefId;
    }

    public DashboardMapping.JsonSource defaultJsonSource() {
        return defaultJsonSource;
    }

    public boolean acceptsType(String type) {
        if (type == null || type.isBlank()) return false;
        String normalized = type.trim().toUpperCase();
        return numeric ? "NUMBER".equals(normalized)
                : !NON_SCALAR_TYPES.contains(normalized);
    }
}
