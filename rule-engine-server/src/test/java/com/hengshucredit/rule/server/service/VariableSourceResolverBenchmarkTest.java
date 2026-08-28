package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VariableSourceResolverBenchmarkTest {

    private static final int SOURCE_COUNT = 8;
    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 9;
    private static final long SOURCE_DELAY_MILLIS = 30L;

    @Test
    public void parallelWaveImprovesMedianForIndependentBlockingSources() {
        Assume.assumeTrue("enable with -Dtianshu.source-resolution.benchmark=true",
                Boolean.getBoolean("tianshu.source-resolution.benchmark"));
        List<RuleVariable> variables = variables();
        SourceResolutionExecutor sequentialExecutor = new SourceResolutionExecutor(1);
        SourceResolutionExecutor parallelExecutor = new SourceResolutionExecutor(4);
        try {
            VariableSourceResolver sequential = resolver(variables, sequentialExecutor);
            VariableSourceResolver parallel = resolver(variables, parallelExecutor);
            for (int i = 0; i < WARMUP_RUNS; i++) {
                verify(sequential.resolve(1L, Collections.emptyMap()));
                verify(parallel.resolve(1L, Collections.emptyMap()));
            }

            List<Long> sequentialDurations = measure(sequential);
            List<Long> parallelDurations = measure(parallel);
            long sequentialMedian = median(sequentialDurations);
            long parallelMedian = median(parallelDurations);
            double improvement = (sequentialMedian - parallelMedian) / (double) sequentialMedian;

            System.out.println("VARIABLE_SOURCE_BENCHMARK sequentialMedianMs="
                    + millis(sequentialMedian) + " parallelMedianMs=" + millis(parallelMedian)
                    + " improvement=" + String.format(java.util.Locale.ROOT, "%.3f", improvement)
                    + " sequentialP95Ms=" + millis(percentile95(sequentialDurations))
                    + " parallelP95Ms=" + millis(percentile95(parallelDurations)));
            assertTrue("parallel median improvement must be at least 20% but was " + improvement,
                    improvement >= 0.20d);
        } finally {
            sequentialExecutor.close();
            parallelExecutor.close();
        }
    }

    private List<Long> measure(VariableSourceResolver resolver) {
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long started = System.nanoTime();
            verify(resolver.resolve(1L, Collections.emptyMap()));
            durations.add(System.nanoTime() - started);
        }
        return durations;
    }

    private void verify(Map<String, Object> resolved) {
        for (int i = 1; i <= SOURCE_COUNT; i++) {
            assertEquals((long) i, ((Number) resolved.get("score" + i)).longValue());
        }
    }

    private VariableSourceResolver resolver(List<RuleVariable> variables,
                                            SourceResolutionExecutor executor) {
        VariableSourceResolver resolver = new VariableSourceResolver();
        ReflectionTestUtils.setField(resolver, "variableService", new RuleVariableService() {
            @Override
            public List<RuleVariable> listByProject(Long projectId, String varSource) {
                return variables;
            }
        });
        ReflectionTestUtils.setField(resolver, "externalApiInvokeService", new ExternalApiInvokeService() {
            @Override
            public Map<String, Object> invoke(Long apiConfigId, Map<String, Object> params) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(SOURCE_DELAY_MILLIS));
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("value", apiConfigId);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("body", body);
                return response;
            }
        });
        ReflectionTestUtils.setField(resolver, "sourceResolutionExecutor", executor);
        return resolver;
    }

    private List<RuleVariable> variables() {
        List<RuleVariable> variables = new ArrayList<>();
        for (int i = 1; i <= SOURCE_COUNT; i++) {
            RuleVariable variable = new RuleVariable();
            variable.setId((long) i);
            variable.setProjectId(1L);
            variable.setScope("PROJECT");
            variable.setVarCode("score" + i);
            variable.setVarLabel("score" + i);
            variable.setScriptName("score" + i);
            variable.setVarType("NUMBER");
            variable.setVarSource("API");
            variable.setSourceConfig("{\"apiConfigId\":" + i
                    + ",\"resultPath\":\"body.value\"}");
            variable.setStatus(1);
            variables.add(variable);
        }
        return variables;
    }

    private long median(List<Long> durations) {
        List<Long> sorted = new ArrayList<>(durations);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private long percentile95(List<Long> durations) {
        List<Long> sorted = new ArrayList<>(durations);
        Collections.sort(sorted);
        int index = (int) Math.ceil(sorted.size() * 0.95d) - 1;
        return sorted.get(Math.max(0, index));
    }

    private long millis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }
}
