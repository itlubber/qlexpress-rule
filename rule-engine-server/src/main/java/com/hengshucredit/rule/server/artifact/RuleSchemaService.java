package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class RuleSchemaService {
    private static final String JSON_SCHEMA_DRAFT = "https://json-schema.org/draft/2020-12/schema";

    public SchemaSnapshot build(List<RuleDefinitionInputField> inputFields,
                                List<RuleDefinitionOutputField> outputFields) {
        return build(inputFields, outputFields,
                Collections.emptyMap(), Collections.emptyMap());
    }

    public SchemaSnapshot build(
            List<RuleDefinitionInputField> inputFields,
            List<RuleDefinitionOutputField> outputFields,
            Map<String, Map<String, Object>> inputOverrides,
            Map<String, Map<String, Object>> outputOverrides) {
        Map<String, Object> inputProperties = new TreeMap<>();
        List<String> requiredInputs = new ArrayList<>();
        if (inputFields != null) {
            for (RuleDefinitionInputField field : inputFields) {
                if (!active(field.getStatus())) {
                    continue;
                }
                putExactField(inputProperties, field.getFieldName(),
                        field.getFieldLabel(), field.getFieldType(),
                        override(inputOverrides, field.getFieldName()));
                if (field.getDefaultValue() == null || field.getDefaultValue().isBlank()) {
                    requiredInputs.add(field.getFieldName());
                }
            }
        }

        Map<String, Object> outputProperties = new TreeMap<>();
        List<String> requiredOutputs = new ArrayList<>();
        if (outputFields != null) {
            for (RuleDefinitionOutputField field : outputFields) {
                if (!active(field.getStatus())) {
                    continue;
                }
                putExactField(outputProperties, field.getFieldName(),
                        field.getFieldLabel(), field.getFieldType(),
                        override(outputOverrides, field.getFieldName()));
                requiredOutputs.add(field.getFieldName());
            }
        }
        requiredInputs.sort(String::compareTo);
        requiredOutputs.sort(String::compareTo);
        return new SchemaSnapshot(schema(inputProperties, requiredInputs),
                schema(outputProperties, requiredOutputs));
    }

    private void putExactField(Map<String, Object> properties, String fieldName,
                               String fieldLabel, String fieldType,
                               Map<String, Object> override) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("Schema 字段名不能为空");
        }
        Map<String, Object> property = override == null
                ? property(fieldType) : deepCopyMap(override);
        if (fieldLabel != null && !fieldLabel.isBlank()) {
            property.putIfAbsent("title", fieldLabel);
        }
        if (properties.put(fieldName, property) != null) {
            throw new IllegalArgumentException("Schema 字段名重复: " + fieldName);
        }
    }

    private Map<String, Object> override(
            Map<String, Map<String, Object>> overrides, String fieldName) {
        return overrides == null || fieldName == null ? null : overrides.get(fieldName);
    }

    private Map<String, Object> property(String fieldType) {
        String ruleType = fieldType == null || fieldType.isBlank() ? "OBJECT" : fieldType;
        String normalizedType = ruleType.toUpperCase(Locale.ROOT);
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", switch (normalizedType) {
            case "INTEGER", "LONG", "SHORT", "BYTE" -> "integer";
            case "NUMBER", "DOUBLE", "FLOAT", "DECIMAL", "BIGDECIMAL" -> "number";
            case "BOOLEAN", "BOOL" -> "boolean";
            case "ARRAY", "LIST", "SET" -> "array";
            case "OBJECT", "MAP" -> "object";
            default -> "string";
        });
        if ("DATE".equals(normalizedType)) {
            property.put("format", "date");
        } else if ("DATETIME".equals(normalizedType) || "LOCALDATETIME".equals(normalizedType)) {
            property.put("format", "date-time");
        }
        property.put("x-rule-type", ruleType);
        return property;
    }

    private Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", JSON_SCHEMA_DRAFT);
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private boolean active(Integer status) {
        return status == null || status != 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> T deepCopy(T value) {
        if (value instanceof Map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(entry.getKey(), deepCopy(entry.getValue()));
            }
            return (T) copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value) {
                copy.add(deepCopy(item));
            }
            return (T) copy;
        }
        return value;
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : deepCopy(value);
    }

    public static final class SchemaSnapshot {
        private final Map<String, Object> inputSchema;
        private final Map<String, Object> outputSchema;

        private SchemaSnapshot(Map<String, Object> inputSchema, Map<String, Object> outputSchema) {
            this.inputSchema = deepCopyMap(inputSchema);
            this.outputSchema = deepCopyMap(outputSchema);
        }

        public Map<String, Object> getInputSchema() {
            return deepCopyMap(inputSchema);
        }

        public Map<String, Object> getOutputSchema() {
            return deepCopyMap(outputSchema);
        }

        public String getInputSchemaJson() {
            return CanonicalJson.write(inputSchema);
        }

        public String getOutputSchemaJson() {
            return CanonicalJson.write(outputSchema);
        }
    }
}
