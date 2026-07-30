package com.hengshucredit.rule.server.governance;

import java.util.Collections;
import java.util.List;

public record ResourceDiff(String summary,
                           List<FieldDiff> fields,
                           String semanticJson) {
    public ResourceDiff {
        fields = fields == null
                ? Collections.emptyList() : List.copyOf(fields);
    }

    public record FieldDiff(String key,
                            String label,
                            Object leftValue,
                            Object rightValue,
                            String changeType,
                            boolean sensitive,
                            boolean changed) {
    }
}
