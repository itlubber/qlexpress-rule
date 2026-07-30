package com.hengshucredit.rule.server.governance;

import java.util.List;
import java.util.Map;

public final class GovernanceResourceTypes {
    public static final String VARIABLE = "VARIABLE";
    public static final String DATA_OBJECT = "DATA_OBJECT";
    public static final String MODEL = "MODEL";
    public static final String EXTERNAL_DATASOURCE =
            "EXTERNAL_DATASOURCE";
    public static final String EXTERNAL_API = "EXTERNAL_API";
    public static final String DATABASE = "DATABASE";
    public static final String FUNCTION = "FUNCTION";
    public static final String RULE = "RULE";
    public static final String EXPERIMENT = "EXPERIMENT";
    public static final String PROJECT = "PROJECT";

    private static final Map<String, List<String>> TAB_TYPES = Map.of(
            "FIELD", List.of(VARIABLE, DATA_OBJECT),
            "MODEL", List.of(MODEL),
            "DATASOURCE", List.of(
                    EXTERNAL_DATASOURCE, EXTERNAL_API),
            "DATABASE", List.of(DATABASE),
            "FUNCTION", List.of(FUNCTION),
            "RULE", List.of(RULE),
            "EXPERIMENT", List.of(EXPERIMENT),
            "PROJECT", List.of(PROJECT));

    private GovernanceResourceTypes() {
    }

    public static List<String> forTab(String tab) {
        return TAB_TYPES.getOrDefault(
                tab == null ? "" : tab.toUpperCase(),
                List.of());
    }
}
