package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModelGovernedResourceAdapter
        extends AggregateEntityGovernedResourceAdapter<RuleModel> {

    private final RuleModelInputFieldMapper inputMapper;
    private final RuleModelOutputFieldMapper outputMapper;

    public ModelGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleModel>
                    store,
            RuleModelInputFieldMapper inputMapper,
            RuleModelOutputFieldMapper outputMapper,
            GovernanceSecretCodec secretCodec) {
        super(new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.MODEL,
                RuleModel.class,
                store,
                RuleModel::getId,
                RuleModel::setId,
                RuleModel::getStatus,
                RuleModel::setStatus,
                Set.of("modelCode", "modelName", "modelType",
                        "modelFormat"),
                Set.of("modelContent"),
                secretCodec));
        this.inputMapper = inputMapper;
        this.outputMapper = outputMapper;
    }

    @Override
    protected void enrichSnapshot(Long resourceId,
                                  Map<String, Object> snapshot) {
        snapshot.put("inputFields", inputMapper.selectList(
                new LambdaQueryWrapper<RuleModelInputField>()
                        .eq(RuleModelInputField::getModelId, resourceId)
                        .orderByAsc(RuleModelInputField::getSortOrder)
                        .orderByAsc(RuleModelInputField::getId)));
        snapshot.put("outputFields", outputMapper.selectList(
                new LambdaQueryWrapper<RuleModelOutputField>()
                        .eq(RuleModelOutputField::getModelId, resourceId)
                        .orderByAsc(RuleModelOutputField::getSortOrder)
                        .orderByAsc(RuleModelOutputField::getId)));
    }

    @Override
    protected void applyAggregate(Long resourceId,
                                  Map<String, Object> snapshot) {
        inputMapper.delete(new LambdaQueryWrapper<RuleModelInputField>()
                .eq(RuleModelInputField::getModelId, resourceId));
        outputMapper.delete(new LambdaQueryWrapper<RuleModelOutputField>()
                .eq(RuleModelOutputField::getModelId, resourceId));
        List<RuleModelInputField> inputs = JSON.parseArray(
                JSON.toJSONString(snapshot.get("inputFields")),
                RuleModelInputField.class);
        if (inputs != null) {
            for (int index = 0; index < inputs.size(); index++) {
                RuleModelInputField input = inputs.get(index);
                input.setId(null);
                input.setModelId(resourceId);
                input.setSortOrder(index);
                inputMapper.insert(input);
            }
        }
        List<RuleModelOutputField> outputs = JSON.parseArray(
                JSON.toJSONString(snapshot.get("outputFields")),
                RuleModelOutputField.class);
        if (outputs != null) {
            for (int index = 0; index < outputs.size(); index++) {
                RuleModelOutputField output = outputs.get(index);
                output.setId(null);
                output.setModelId(resourceId);
                output.setSortOrder(index);
                outputMapper.insert(output);
            }
        }
    }
}
