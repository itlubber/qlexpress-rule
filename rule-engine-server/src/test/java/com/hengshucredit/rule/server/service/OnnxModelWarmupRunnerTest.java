package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.health.OnnxWarmupState;
import com.hengshucredit.rule.server.health.OnnxWarmupStatus;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class OnnxModelWarmupRunnerTest {

    @Test
    public void warmsOnlyEnabledOnnxModelsConfiguredForStartup() throws Exception {
        AtomicInteger preloads = new AtomicInteger();
        AtomicInteger detailLoads = new AtomicInteger();
        java.util.List<RuleModel> models = Arrays.asList(model("ONNX", 1, 1), model("ONNX", 1, 0),
                model("PMML", 1, 1), model("ONNX", 0, 1));
        OnnxModelExecutionService executionService = new OnnxModelExecutionService(null) {
            @Override
            public PreloadOutcome preloadWithOutcome(byte[] modelBytes, String configJson) {
                preloads.incrementAndGet();
                return PreloadOutcome.READY;
            }
        };
        RuleModelMapper mapper = (RuleModelMapper) Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) return models;
                    if ("selectById".equals(method.getName())) {
                        detailLoads.incrementAndGet();
                        Long id = ((Number) args[0]).longValue();
                        return models.stream().filter(item -> item.getId().equals(id)).findFirst().orElse(null);
                    }
                    return null;
                });

        OnnxWarmupStatus status = new OnnxWarmupStatus();
        OnnxModelWarmupRunner runner = new OnnxModelWarmupRunner(mapper, executionService, status);

        runner.run(null);

        assertEquals(1, preloads.get());
        assertEquals(1, detailLoads.get());
        assertEquals(OnnxWarmupState.READY, status.getState());
        assertEquals(1, status.details().get("successCount"));
    }

    @Test
    public void continuesWhenOneModelHasNativeLibraryLoadFailure() throws Exception {
        RuleModel first = model("ONNX", 1, 1);
        first.setId(1L);
        RuleModel second = model("ONNX", 1, 1);
        second.setId(2L);
        AtomicInteger preloads = new AtomicInteger();
        OnnxModelExecutionService executionService = new OnnxModelExecutionService(null) {
            @Override
            public PreloadOutcome preloadWithOutcome(byte[] modelBytes, String configJson) {
                if (preloads.getAndIncrement() == 0) {
                    throw new UnsatisfiedLinkError("opencv native library missing");
                }
                return PreloadOutcome.READY;
            }
        };
        java.util.List<RuleModel> models = Arrays.asList(first, second);
        RuleModelMapper mapper = (RuleModelMapper) Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) return models;
                    if ("selectById".equals(method.getName())) {
                        Long id = ((Number) args[0]).longValue();
                        return models.stream().filter(item -> item.getId().equals(id)).findFirst().orElse(null);
                    }
                    return null;
                });

        OnnxWarmupStatus status = new OnnxWarmupStatus();
        new OnnxModelWarmupRunner(mapper, executionService, status).run(null);

        assertEquals(2, preloads.get());
        assertEquals(OnnxWarmupState.FAILED, status.getState());
        assertEquals(1, status.details().get("failureCount"));
    }

    @Test
    public void backfillsMissingDigestForHistoricalModelWithoutPreloading() throws Exception {
        RuleModel historical = model("ONNX", 1, 0);
        historical.setModelDigest(null);
        AtomicReference<RuleModel> updated = new AtomicReference<>();
        AtomicInteger preloads = new AtomicInteger();
        OnnxModelExecutionService executionService = new OnnxModelExecutionService(null) {
            @Override
            public void preload(byte[] modelBytes, String configJson) {
                preloads.incrementAndGet();
            }
        };
        RuleModelMapper mapper = (RuleModelMapper) Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) return java.util.Collections.singletonList(historical);
                    if ("selectById".equals(method.getName())) return historical;
                    if ("updateById".equals(method.getName())) {
                        updated.set((RuleModel) args[0]);
                        return 1;
                    }
                    return null;
                });

        OnnxWarmupStatus status = new OnnxWarmupStatus();
        new OnnxModelWarmupRunner(mapper, executionService, status).run(null);

        assertEquals(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                updated.get().getModelDigest());
        assertEquals(0, preloads.get());
        assertEquals(OnnxWarmupState.READY, status.getState());
    }

    @Test
    public void failedDigestBackfillKeepsReadinessFailed() throws Exception {
        RuleModel historical = model("ONNX", 1, 0);
        historical.setModelDigest(null);
        RuleModelMapper mapper = (RuleModelMapper) Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return java.util.Collections.singletonList(historical);
                    }
                    if ("selectById".equals(method.getName())) {
                        return historical;
                    }
                    if ("updateById".equals(method.getName())) {
                        return 0;
                    }
                    return null;
                });
        OnnxWarmupStatus status = new OnnxWarmupStatus();

        new OnnxModelWarmupRunner(
                mapper, new OnnxModelExecutionService(null), status).run(null);

        assertEquals(OnnxWarmupState.FAILED, status.getState());
        assertEquals(1, status.details().get("failureCount"));
    }

    @Test
    public void emptyCandidateListMarksWarmupReady() throws Exception {
        RuleModelMapper mapper = (RuleModelMapper) Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? java.util.Collections.emptyList() : null);
        OnnxWarmupStatus status = new OnnxWarmupStatus();

        new OnnxModelWarmupRunner(mapper, new OnnxModelExecutionService(null), status).run(null);

        assertEquals(OnnxWarmupState.READY, status.getState());
        assertEquals(0, status.details().get("targetCount"));
    }

    @Test
    public void detailLoadFailureMarksReadinessFailedWithoutAbortingRunner() throws Exception {
        RuleModel candidate = model("ONNX", 1, 1);
        RuleModelMapper mapper = (RuleModelMapper) Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return java.util.Collections.singletonList(candidate);
                    }
                    if ("selectById".equals(method.getName())) {
                        throw new IllegalStateException("database unavailable");
                    }
                    return null;
                });
        OnnxWarmupStatus status = new OnnxWarmupStatus();

        new OnnxModelWarmupRunner(mapper, new OnnxModelExecutionService(null), status).run(null);

        assertEquals(OnnxWarmupState.FAILED, status.getState());
        assertEquals(1, status.details().get("failureCount"));
    }

    @Test
    public void successfulCpuFallbackIsReadyAndCounted() throws Exception {
        RuleModel candidate = model("ONNX", 1, 1);
        RuleModelMapper mapper = (RuleModelMapper) Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return java.util.Collections.singletonList(candidate);
                    }
                    if ("selectById".equals(method.getName())) return candidate;
                    return null;
                });
        OnnxModelExecutionService executionService = new OnnxModelExecutionService(null) {
            @Override
            public PreloadOutcome preloadWithOutcome(byte[] modelBytes, String configJson) {
                return PreloadOutcome.CPU_FALLBACK;
            }
        };
        OnnxWarmupStatus status = new OnnxWarmupStatus();

        new OnnxModelWarmupRunner(mapper, executionService, status).run(null);

        assertEquals(OnnxWarmupState.READY, status.getState());
        assertEquals(1, status.details().get("cpuFallbackCount"));
    }

    private static RuleModel model(String format, int status, int preload) {
        RuleModel model = new RuleModel();
        model.setId((long) (format.hashCode() + status + preload));
        model.setModelCode(format + status + preload);
        model.setModelFormat(format);
        model.setStatus(status);
        model.setPreloadOnStartup(preload);
        model.setModelContent(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        model.setModelDigest("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        model.setModelConfig("{\"onnxTaskType\":\"MN3_ANTISPOOF\"}");
        return model;
    }
}
