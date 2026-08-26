package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MybatisDashboardMappingRepository
        implements DashboardMappingRepository {

    private final RuleVariableMapper variableMapper;
    private final RuleDataObjectFieldMapper dataObjectFieldMapper;
    private final RuleDataObjectMapper dataObjectMapper;

    public MybatisDashboardMappingRepository(
            RuleVariableMapper variableMapper,
            RuleDataObjectFieldMapper dataObjectFieldMapper,
            RuleDataObjectMapper dataObjectMapper) {
        this.variableMapper = variableMapper;
        this.dataObjectFieldMapper = dataObjectFieldMapper;
        this.dataObjectMapper = dataObjectMapper;
    }

    @Override
    public RuleVariable findVariable(Long id) {
        return variableMapper.selectById(id);
    }

    @Override
    public RuleDataObjectField findDataObjectField(Long id) {
        return dataObjectFieldMapper.selectById(id);
    }

    @Override
    public RuleDataObject findDataObject(Long id) {
        return dataObjectMapper.selectById(id);
    }

    @Override
    public List<RuleVariable> listVariables(Long projectId) {
        LambdaQueryWrapper<RuleVariable> query =
                new LambdaQueryWrapper<RuleVariable>()
                        .eq(RuleVariable::getStatus, 1)
                        .orderByAsc(RuleVariable::getScope,
                                RuleVariable::getSortOrder,
                                RuleVariable::getId);
        if (projectId == null) {
            query.and(item -> item.eq(RuleVariable::getScope, "GLOBAL")
                    .or().eq(RuleVariable::getProjectId, 0L));
        } else {
            query.and(item -> item.eq(RuleVariable::getScope, "GLOBAL")
                    .or().eq(RuleVariable::getProjectId, 0L)
                    .or().eq(RuleVariable::getProjectId, projectId));
        }
        return variableMapper.selectList(query);
    }

    @Override
    public List<RuleDataObjectField> listDataObjectFields(Long projectId) {
        LambdaQueryWrapper<RuleDataObjectField> query =
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getStatus, 1)
                        .orderByAsc(RuleDataObjectField::getObjectId,
                                RuleDataObjectField::getSortOrder,
                                RuleDataObjectField::getId);
        if (projectId == null) {
            query.and(item -> item.eq(RuleDataObjectField::getScope, "GLOBAL")
                    .or().eq(RuleDataObjectField::getProjectId, 0L));
        } else {
            query.and(item -> item.eq(RuleDataObjectField::getScope, "GLOBAL")
                    .or().eq(RuleDataObjectField::getProjectId, 0L)
                    .or().eq(RuleDataObjectField::getProjectId, projectId));
        }
        return dataObjectFieldMapper.selectList(query);
    }
}
