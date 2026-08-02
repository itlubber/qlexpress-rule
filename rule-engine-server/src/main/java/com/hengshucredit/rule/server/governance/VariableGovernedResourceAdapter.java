package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVariableOption;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableOptionMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class VariableGovernedResourceAdapter
        extends AggregateEntityGovernedResourceAdapter<RuleVariable> {

    private final RuleVariableOptionMapper optionMapper;

    public VariableGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleVariable>
                    store,
            RuleVariableOptionMapper optionMapper,
            GovernanceSecretCodec secretCodec) {
        super(new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.VARIABLE,
                RuleVariable.class,
                store,
                RuleVariable::getId,
                RuleVariable::setId,
                RuleVariable::getStatus,
                RuleVariable::setStatus,
                Set.of("varCode", "varLabel", "varType"),
                Set.of(),
                secretCodec));
        this.optionMapper = optionMapper;
    }

    @Override
    protected void enrichSnapshot(Long resourceId,
                                  Map<String, Object> snapshot) {
        snapshot.put("options", optionMapper.selectList(
                new LambdaQueryWrapper<RuleVariableOption>()
                        .eq(RuleVariableOption::getVariableId,
                                resourceId)
                        .orderByAsc(RuleVariableOption::getSortOrder)
                        .orderByAsc(RuleVariableOption::getId)));
    }

    @Override
    protected boolean isOwnershipReference(
            ResourceDependencyRef dependency) {
        return isCollectionOwnershipReference(
                dependency, "options", "variableId");
    }

    @Override
    protected void applyAggregate(Long resourceId,
                                  Map<String, Object> snapshot) {
        optionMapper.delete(new LambdaQueryWrapper<RuleVariableOption>()
                .eq(RuleVariableOption::getVariableId, resourceId));
        List<RuleVariableOption> options = JSON.parseArray(
                JSON.toJSONString(snapshot.get("options")),
                RuleVariableOption.class);
        if (options == null) {
            return;
        }
        for (int index = 0; index < options.size(); index++) {
            RuleVariableOption option = options.get(index);
            option.setId(null);
            option.setVariableId(resourceId);
            option.setSortOrder(index);
            optionMapper.insert(option);
        }
    }
}
