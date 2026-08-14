package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class OnnxModelWarmupRunnerTest {

    @Test
    public void warmsOnlyEnabledOnnxModelsConfiguredForStartup() throws Exception {
        AtomicInteger preloads = new AtomicInteger();
        AtomicInteger detailLoads = new AtomicInteger();
        java.util.List<RuleModel> models = Arrays.asList(model("ONNX", 1, 1), model("ONNX", 1, 0),
                model("PMML", 1, 1), model("ONNX", 0, 1));
        OnnxModelExecutionService executionService = new OnnxModelExecutionService(null) {
            @Override
            public void preload(byte[] modelBytes, String configJson) {
                preloads.incrementAndGet();
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

        OnnxModelWarmupRunner runner = new OnnxModelWarmupRunner(mapper, executionService);

        runner.run(null);

        assertEquals(1, preloads.get());
        assertEquals(1, detailLoads.get());
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
            public void preload(byte[] modelBytes, String configJson) {
                if (preloads.getAndIncrement() == 0) {
                    throw new UnsatisfiedLinkError("opencv native library missing");
                }
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

        new OnnxModelWarmupRunner(mapper, executionService).run(null);

        assertEquals(2, preloads.get());
    }

    @Test
    public void backfillsMissingDigestBeforePreloadingHistoricalModel() throws Exception {
        RuleModel historical = model("ONNX", 1, 1);
        historical.setModelDigest(null);
        AtomicReference<RuleModel> updated = new AtomicReference<>();
        OnnxModelExecutionService executionService = new OnnxModelExecutionService(null) {
            @Override
            public void preload(byte[] modelBytes, String configJson) {
                assertNotNull(updated.get());
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

        new OnnxModelWarmupRunner(mapper, executionService).run(null);

        assertEquals(
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                updated.get().getModelDigest());
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
