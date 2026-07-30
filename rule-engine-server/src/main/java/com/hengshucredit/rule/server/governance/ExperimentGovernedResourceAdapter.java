package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
import com.hengshucredit.rule.server.mapper.RuleExperimentGroupMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExperimentGovernedResourceAdapter
        extends AggregateEntityGovernedResourceAdapter<RuleExperiment> {

    private final RuleExperimentGroupMapper groupMapper;

    public ExperimentGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleExperiment>
                    store,
            RuleExperimentGroupMapper groupMapper,
            GovernanceSecretCodec secretCodec) {
        super(new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.EXPERIMENT,
                RuleExperiment.class,
                store,
                RuleExperiment::getId,
                RuleExperiment::setId,
                RuleExperiment::getStatus,
                RuleExperiment::setStatus,
                Set.of("experimentCode", "experimentName",
                        "routingMode"),
                Set.of(),
                secretCodec));
        this.groupMapper = groupMapper;
    }

    @Override
    protected void enrichSnapshot(Long resourceId,
                                  Map<String, Object> snapshot) {
        snapshot.put("groups", groupMapper.selectList(
                new LambdaQueryWrapper<RuleExperimentGroup>()
                        .eq(RuleExperimentGroup::getExperimentId,
                                resourceId)
                        .orderByAsc(RuleExperimentGroup::getSortOrder)
                        .orderByAsc(RuleExperimentGroup::getId)));
    }

    @Override
    protected void applyAggregate(Long resourceId,
                                  Map<String, Object> snapshot) {
        groupMapper.delete(new LambdaQueryWrapper<RuleExperimentGroup>()
                .eq(RuleExperimentGroup::getExperimentId, resourceId));
        List<RuleExperimentGroup> groups = groups(snapshot);
        for (int index = 0; index < groups.size(); index++) {
            RuleExperimentGroup group = groups.get(index);
            group.setId(null);
            group.setExperimentId(resourceId);
            group.setSortOrder(index);
            groupMapper.insert(group);
        }
    }

    @Override
    protected void validateAggregate(Map<String, Object> snapshot,
                                     List<GovernanceIssue> issues) {
        List<RuleExperimentGroup> groups = groups(snapshot);
        long champions = groups.stream()
                .filter(group -> Integer.valueOf(1).equals(
                        group.getStatus()))
                .filter(group -> "CHAMPION".equalsIgnoreCase(
                        group.getGroupType()))
                .count();
        if (champions != 1) {
            issues.add(GovernanceIssue.error(
                    "EXPERIMENT_CHAMPION_REQUIRED",
                    "分流实验必须且只能配置一个启用的冠军组",
                    GovernanceResourceTypes.EXPERIMENT,
                    longValue(snapshot.get("id")),
                    "$.groups"));
        }
        BigDecimal total = groups.stream()
                .filter(group -> Integer.valueOf(1).equals(
                        group.getStatus()))
                .filter(group -> !"TEST".equalsIgnoreCase(
                        group.getGroupType()))
                .map(RuleExperimentGroup::getTrafficRatio)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if ("RATIO".equalsIgnoreCase(
                String.valueOf(snapshot.get("routingMode")))
                && total.compareTo(new BigDecimal("100")) != 0
                && total.compareTo(BigDecimal.ONE) != 0) {
            issues.add(GovernanceIssue.error(
                    "EXPERIMENT_RATIO_INVALID",
                    "比例分流的启用生产组流量总和必须为 100%（或 1）",
                    GovernanceResourceTypes.EXPERIMENT,
                    longValue(snapshot.get("id")),
                    "$.groups[*].trafficRatio"));
        }
    }

    private List<RuleExperimentGroup> groups(
            Map<String, Object> snapshot) {
        List<RuleExperimentGroup> groups = JSON.parseArray(
                JSON.toJSONString(snapshot.get("groups")),
                RuleExperimentGroup.class);
        return groups == null ? List.of() : groups;
    }

    private Long longValue(Object value) {
        return value instanceof Number number
                ? number.longValue() : null;
    }
}
