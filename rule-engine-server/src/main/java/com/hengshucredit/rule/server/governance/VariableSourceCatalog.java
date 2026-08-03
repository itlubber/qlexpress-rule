package com.hengshucredit.rule.server.governance;

import java.util.List;

public record VariableSourceCatalog(
        List<VariableSourceOption> apiOptions,
        List<VariableSourceOption> databaseOptions,
        List<VariableSourceOption> listOptions) {
}
