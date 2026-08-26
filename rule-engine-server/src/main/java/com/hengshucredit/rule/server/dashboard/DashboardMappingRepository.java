package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVariable;

import java.util.Collections;
import java.util.List;

public interface DashboardMappingRepository {

    RuleVariable findVariable(Long id);

    default RuleDataObjectField findDataObjectField(Long id) {
        return null;
    }

    default RuleDataObject findDataObject(Long id) {
        return null;
    }

    default List<RuleVariable> listVariables(Long projectId) {
        return Collections.emptyList();
    }

    default List<RuleDataObjectField> listDataObjectFields(Long projectId) {
        return Collections.emptyList();
    }
}
