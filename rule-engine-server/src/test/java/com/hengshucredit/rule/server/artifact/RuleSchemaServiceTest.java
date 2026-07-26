package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleSchemaServiceTest {

    private final RuleSchemaService service = new RuleSchemaService();

    @Test
    public void nestedOverridesPreserveOpenShapeWhileTopLevelSchemaStaysExact() {
        RuleDefinitionInputField input = input("payload", "OBJECT");
        RuleDefinitionOutputField output = output("decision", "STRING");
        Map<String, Object> payloadOverride = new LinkedHashMap<>();
        payloadOverride.put("type", "object");
        payloadOverride.put("properties", Collections.singletonMap(
                "known", Collections.singletonMap("type", "integer")));
        payloadOverride.put("additionalProperties", true);

        RuleSchemaService.SchemaSnapshot snapshot = service.build(
                Collections.singletonList(input),
                Collections.singletonList(output),
                Collections.singletonMap("payload", payloadOverride),
                Collections.emptyMap());

        assertEquals(Boolean.FALSE, snapshot.getInputSchema().get("additionalProperties"));
        Map<String, Object> payload = property(snapshot.getInputSchema(), "payload");
        assertEquals(Boolean.TRUE, payload.get("additionalProperties"));
        assertTrue(properties(payload).containsKey("known"));
        assertFalse(properties(snapshot.getInputSchema()).containsKey("unknown"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void inputAndOutputOverridesAreDeepCopiedBeforeSnapshotUse() {
        RuleDefinitionInputField input = input("payload", "OBJECT");
        RuleDefinitionOutputField output = output("scores", "LIST");
        List<Object> enumValues = new ArrayList<>(List.of("A", "B"));
        Map<String, Object> inputProperties = new LinkedHashMap<>();
        inputProperties.put("grade",
                new LinkedHashMap<>(Map.of("type", "string", "enum", enumValues)));
        Map<String, Object> inputOverride = new LinkedHashMap<>();
        inputOverride.put("type", "object");
        inputOverride.put("properties", inputProperties);
        Map<String, Object> outputOverride = new LinkedHashMap<>();
        outputOverride.put("type", "array");
        outputOverride.put("items", new LinkedHashMap<>(Map.of("type", "number")));

        RuleSchemaService.SchemaSnapshot snapshot = service.build(
                Collections.singletonList(input),
                Collections.singletonList(output),
                Collections.singletonMap("payload", inputOverride),
                Collections.singletonMap("scores", outputOverride));
        enumValues.clear();
        inputProperties.clear();
        ((Map<String, Object>) outputOverride.get("items")).put("type", "string");

        Map<String, Object> payload = property(snapshot.getInputSchema(), "payload");
        Map<String, Object> grade = property(payload, "grade");
        assertEquals(List.of("A", "B"), grade.get("enum"));
        assertEquals("number", ((Map<?, ?>) property(
                snapshot.getOutputSchema(), "scores").get("items")).get("type"));
    }

    @Test
    public void fieldsWithoutOverridesStillUseDeclaredRuleType() {
        RuleSchemaService.SchemaSnapshot snapshot = service.build(
                Collections.singletonList(input("count", "INTEGER")),
                Collections.singletonList(output("approved", "BOOLEAN")),
                Collections.emptyMap(), Collections.emptyMap());

        assertEquals("integer", property(snapshot.getInputSchema(), "count").get("type"));
        assertEquals("boolean", property(snapshot.getOutputSchema(), "approved").get("type"));
    }

    private static RuleDefinitionInputField input(String name, String type) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setFieldName(name);
        field.setFieldLabel(name);
        field.setFieldType(type);
        field.setStatus(1);
        return field;
    }

    private static RuleDefinitionOutputField output(String name, String type) {
        RuleDefinitionOutputField field = new RuleDefinitionOutputField();
        field.setFieldName(name);
        field.setFieldLabel(name);
        field.setFieldType(type);
        field.setStatus(1);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(Map<String, Object> schema, String name) {
        return (Map<String, Object>) properties(schema).get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }
}
