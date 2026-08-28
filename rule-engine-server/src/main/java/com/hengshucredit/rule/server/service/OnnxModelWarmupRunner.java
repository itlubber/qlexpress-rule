package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.server.health.OnnxWarmupStatus;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class OnnxModelWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OnnxModelWarmupRunner.class);

    private final RuleModelMapper modelMapper;
    private final OnnxModelExecutionService executionService;
    private final OnnxWarmupStatus warmupStatus;

    public OnnxModelWarmupRunner(RuleModelMapper modelMapper,
                                 OnnxModelExecutionService executionService,
                                 OnnxWarmupStatus warmupStatus) {
        this.modelMapper = modelMapper;
        this.executionService = executionService;
        this.warmupStatus = warmupStatus;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<RuleModel> candidates;
        try {
            candidates = modelMapper.selectList(new QueryWrapper<RuleModel>()
                    .select("id", "model_code", "model_name", "model_format", "status",
                            "preload_on_startup", "model_digest")
                    .eq("model_format", "ONNX")
                    .ne("status", -1));
        } catch (RuntimeException | LinkageError e) {
            warmupStatus.fail(e);
            log.error("查询 ONNX 启动预热候选失败", e);
            return;
        }
        List<RuleModel> targets = new ArrayList<>();
        for (RuleModel candidate : candidates == null
                ? Collections.<RuleModel>emptyList() : candidates) {
            if (requiresWarmup(candidate)) {
                targets.add(candidate);
            }
        }
        warmupStatus.start(targets.size());
        for (RuleModel candidate : targets) {
            RuleModel model = null;
            try {
                model = modelMapper.selectById(candidate.getId());
                if (model == null || !"ONNX".equals(model.getModelFormat())
                        || Integer.valueOf(-1).equals(model.getStatus())) {
                    throw new IllegalStateException("ONNX warmup target is unavailable");
                }
                byte[] modelBytes = Base64.getDecoder().decode(model.getModelContent());
                if (model.getModelDigest() == null || model.getModelDigest().trim().isEmpty()) {
                    RuleModel digestUpdate = new RuleModel();
                    digestUpdate.setId(model.getId());
                    digestUpdate.setModelDigest(Sha256Digests.bytes(modelBytes));
                    int updated = modelMapper.updateById(digestUpdate);
                    if (updated != 1) {
                        throw new IllegalStateException("ONNX model digest backfill did not update one row");
                    }
                    model.setModelDigest(digestUpdate.getModelDigest());
                }
                boolean cpuFallback = false;
                if (Integer.valueOf(1).equals(model.getStatus())
                        && Integer.valueOf(1).equals(model.getPreloadOnStartup())) {
                    OnnxModelExecutionService.PreloadOutcome outcome =
                            executionService.preloadWithOutcome(modelBytes, model.getModelConfig());
                    cpuFallback = outcome == OnnxModelExecutionService.PreloadOutcome.CPU_FALLBACK;
                    log.info("ONNX 模型启动预加载成功: {}({})", model.getModelName(), model.getModelCode());
                }
                warmupStatus.recordSuccess(cpuFallback);
            } catch (RuntimeException | LinkageError e) {
                warmupStatus.recordFailure(e);
                RuleModel failedModel = model == null ? candidate : model;
                log.error("ONNX 模型摘要修复或启动预加载失败: {}({})",
                        failedModel.getModelName(), failedModel.getModelCode(), e);
            }
        }
        warmupStatus.complete();
    }

    private boolean requiresWarmup(RuleModel candidate) {
        if (candidate == null || !"ONNX".equals(candidate.getModelFormat())
                || Integer.valueOf(-1).equals(candidate.getStatus())) {
            return false;
        }
        boolean missingDigest = candidate.getModelDigest() == null
                || candidate.getModelDigest().trim().isEmpty();
        boolean shouldPreload = Integer.valueOf(1).equals(candidate.getStatus())
                && Integer.valueOf(1).equals(candidate.getPreloadOnStartup());
        return missingDigest || shouldPreload;
    }
}
