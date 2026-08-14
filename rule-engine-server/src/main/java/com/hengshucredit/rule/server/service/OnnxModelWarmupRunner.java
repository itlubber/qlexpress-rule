package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

@Component
public class OnnxModelWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OnnxModelWarmupRunner.class);

    private final RuleModelMapper modelMapper;
    private final OnnxModelExecutionService executionService;

    public OnnxModelWarmupRunner(RuleModelMapper modelMapper, OnnxModelExecutionService executionService) {
        this.modelMapper = modelMapper;
        this.executionService = executionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<RuleModel> candidates = modelMapper.selectList(new QueryWrapper<RuleModel>()
                .select("id", "model_code", "model_name", "model_format", "status",
                        "preload_on_startup", "model_digest")
                .eq("model_format", "ONNX")
                .ne("status", -1));
        if (candidates == null) return;
        for (RuleModel candidate : candidates) {
            boolean missingDigest = candidate.getModelDigest() == null
                    || candidate.getModelDigest().trim().isEmpty();
            boolean shouldPreload = Integer.valueOf(1).equals(candidate.getStatus())
                    && Integer.valueOf(1).equals(candidate.getPreloadOnStartup());
            if (!"ONNX".equals(candidate.getModelFormat())
                    || Integer.valueOf(-1).equals(candidate.getStatus())
                    || (!missingDigest && !shouldPreload)) {
                continue;
            }
            RuleModel model = modelMapper.selectById(candidate.getId());
            if (model == null || !"ONNX".equals(model.getModelFormat())
                    || Integer.valueOf(-1).equals(model.getStatus())) continue;
            try {
                byte[] modelBytes = Base64.getDecoder().decode(model.getModelContent());
                if (model.getModelDigest() == null || model.getModelDigest().trim().isEmpty()) {
                    RuleModel digestUpdate = new RuleModel();
                    digestUpdate.setId(model.getId());
                    digestUpdate.setModelDigest(Sha256Digests.bytes(modelBytes));
                    modelMapper.updateById(digestUpdate);
                    model.setModelDigest(digestUpdate.getModelDigest());
                }
                if (Integer.valueOf(1).equals(model.getStatus())
                        && Integer.valueOf(1).equals(model.getPreloadOnStartup())) {
                    executionService.preload(modelBytes, model.getModelConfig());
                    log.info("ONNX 模型启动预加载成功: {}({})", model.getModelName(), model.getModelCode());
                }
            } catch (RuntimeException | LinkageError e) {
                log.error("ONNX 模型摘要修复或启动预加载失败: {}({})",
                        model.getModelName(), model.getModelCode(), e);
            }
        }
    }
}
