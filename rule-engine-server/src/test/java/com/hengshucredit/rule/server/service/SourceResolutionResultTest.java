package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class SourceResolutionResultTest {

    @Test
    public void snapshotsWorkerStateAndTraceEvents() {
        Map<String, Map<String, Object>> states = new LinkedHashMap<>();
        states.put("VARIABLE:1", singletonMap("OUTCOME", "SUCCESS"));
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(singletonMap("resourceCode", "first"));

        SourceResolutionResult result = new SourceResolutionResult(
                3, "riskScore", true, 88, states, events);
        states.get("VARIABLE:1").put("OUTCOME", "ERROR");
        events.get(0).put("resourceCode", "changed");
        events.add(singletonMap("resourceCode", "second"));

        assertEquals("SUCCESS", result.getSourceStates().get("VARIABLE:1").get("OUTCOME"));
        assertEquals("first", result.getTraceEvents().get(0).get("resourceCode"));
        assertEquals(1, result.getTraceEvents().size());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getSourceStates().put("VARIABLE:2", Collections.emptyMap()));
    }

    @Test
    public void optionsMergeSourceStatesPreservesExistingAndIncomingOrder() {
        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setStatusReferenceKeys(new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                "VARIABLE:1", "VARIABLE:2")));
        options.recordSourceState("VARIABLE", 1L, "OUTCOME", "SUCCESS");
        Map<String, Map<String, Object>> incoming = new LinkedHashMap<>();
        incoming.put("VARIABLE:1", singletonMap("PRESENCE", "PRESENT"));
        incoming.put("VARIABLE:2", singletonMap("OUTCOME", "TIMEOUT"));

        options.mergeSourceStates(incoming);

        assertEquals(java.util.Arrays.asList("VARIABLE:1", "VARIABLE:2"),
                new ArrayList<>(options.getSourceStates().keySet()));
        assertEquals(java.util.Arrays.asList("OUTCOME", "PRESENCE"),
                new ArrayList<>(options.getSourceStates().get("VARIABLE:1").keySet()));
        assertEquals("TIMEOUT", options.getSourceStates().get("VARIABLE:2").get("OUTCOME"));
        assertFalse(options.getSourceStates().isEmpty());
    }

    private static Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }
}
