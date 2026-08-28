package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DataObjectFieldReferenceResolverTest {

    private final DataObjectFieldReferenceResolver resolver =
            new DataObjectFieldReferenceResolver();
    private final ExecutionParameterBinder binder = new ExecutionParameterBinder();

    @Test
    public void sourceVariableIsAssembledIntoMappedObjectPath() {
        DataObjectFieldReferenceResolver.ReferencePlan plan = plan();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("age", "18");
        Set<String> explicitTargets = plan.captureExplicitTargets(input);

        Map<String, Object> values = binder.bindRuleInputs(
                plan.mergeBindingFields(Collections.emptyList()), input);
        plan.apply(values, explicitTargets);

        assertEquals(Double.valueOf(18D),
                ((Map<?, ?>) values.get("request")).get("age"));
        assertTrue(plan.requiredSourceNames().contains("age"));
    }

    @Test
    public void explicitObjectPathWinsOverReferencedVariable() {
        DataObjectFieldReferenceResolver.ReferencePlan plan = plan();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("age", "21");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("request", request);
        input.put("age", "18");
        Set<String> explicitTargets = plan.captureExplicitTargets(input);

        Map<String, Object> values = binder.bindRuleInputs(
                plan.mergeBindingFields(Collections.emptyList()), input);
        plan.apply(values, explicitTargets);

        assertEquals(Double.valueOf(21D),
                ((Map<?, ?>) values.get("request")).get("age"));
    }

    @Test
    public void explicitNullObjectPathAlsoWinsOverReferencedVariable() {
        DataObjectFieldReferenceResolver.ReferencePlan plan = plan();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("age", null);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("request", request);
        input.put("age", "18");
        Set<String> explicitTargets = plan.captureExplicitTargets(input);

        Map<String, Object> values = binder.bindRuleInputs(
                plan.mergeBindingFields(Collections.emptyList()), input);
        plan.apply(values, explicitTargets);

        assertNull(((Map<?, ?>) values.get("request")).get("age"));
    }

    @Test
    public void flatExplicitObjectPathIsNormalizedAndStillWins() {
        DataObjectFieldReferenceResolver.ReferencePlan plan = plan();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("request.age", "21");
        input.put("age", "18");
        Set<String> explicitTargets = plan.captureExplicitTargets(input);

        Map<String, Object> values = binder.bindRuleInputs(
                plan.mergeBindingFields(Collections.emptyList()), input);
        plan.apply(values, explicitTargets);

        assertEquals(Double.valueOf(21D),
                ((Map<?, ?>) values.get("request")).get("age"));
        assertTrue(!values.containsKey("request.age"));
    }

    private DataObjectFieldReferenceResolver.ReferencePlan plan() {
        RuleDefinitionInputField target = new RuleDefinitionInputField();
        target.setVarId(30L);
        target.setRefType("DATA_OBJECT");
        target.setFieldName("age");
        target.setScriptName("request.age");
        target.setFieldType("NUMBER");

        RuleDataObjectField mappedField = new RuleDataObjectField();
        mappedField.setId(30L);
        mappedField.setRefVariableId(9L);

        RuleVariable source = new RuleVariable();
        source.setId(9L);
        source.setVarCode("age");
        source.setVarLabel("年龄");
        source.setScriptName("age");
        source.setVarType("NUMBER");
        source.setVarSource("INPUT");
        source.setStatus(1);

        return resolver.resolveSnapshot(
                Collections.singletonList(target),
                Collections.singletonList(mappedField),
                Collections.singletonList(source));
    }
}
