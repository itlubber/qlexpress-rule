package com.hengshucredit.rule.core.script;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QLScriptAnalysis {

    private final List<String> directInputs;
    private final List<String> localSymbols;
    private final Map<String, String> localAssignments;
    private final List<OutputField> publicOutputs;
    private final List<Diagnostic> diagnostics;
    private final boolean explicitResult;

    public QLScriptAnalysis(List<String> directInputs,
                            List<String> localSymbols,
                            Map<String, String> localAssignments,
                            List<OutputField> publicOutputs,
                            List<Diagnostic> diagnostics,
                            boolean explicitResult) {
        this.directInputs = List.copyOf(directInputs);
        this.localSymbols = List.copyOf(localSymbols);
        this.localAssignments = Collections.unmodifiableMap(
                new LinkedHashMap<>(localAssignments));
        this.publicOutputs = List.copyOf(publicOutputs);
        this.diagnostics = List.copyOf(diagnostics);
        this.explicitResult = explicitResult;
    }

    public static QLScriptAnalysis parseError(String code, String path, String message) {
        return new QLScriptAnalysis(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.singletonList(new Diagnostic(code, "ERROR", path, message)),
                false);
    }

    public List<String> getDirectInputs() {
        return directInputs;
    }

    public List<String> getLocalSymbols() {
        return localSymbols;
    }

    public Map<String, String> getLocalAssignments() {
        return localAssignments;
    }

    public List<OutputField> getPublicOutputs() {
        return publicOutputs;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public boolean hasExplicitResult() {
        return explicitResult;
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(item -> "ERROR".equals(item.getSeverity()));
    }

    public static final class OutputField {

        private final String name;
        private final String sourceExpression;
        private final String valueType;

        public OutputField(String name, String sourceExpression, String valueType) {
            this.name = name;
            this.sourceExpression = sourceExpression;
            this.valueType = valueType;
        }

        public String getName() {
            return name;
        }

        public String getSourceExpression() {
            return sourceExpression;
        }

        public String getValueType() {
            return valueType;
        }
    }

    public static final class Diagnostic {

        private final String code;
        private final String severity;
        private final String path;
        private final String message;

        public Diagnostic(String code, String severity, String path, String message) {
            this.code = code;
            this.severity = severity;
            this.path = path;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getSeverity() {
            return severity;
        }

        public String getPath() {
            return path;
        }

        public String getMessage() {
            return message;
        }
    }
}
