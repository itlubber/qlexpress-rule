package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ProjectWorkbenchDTO;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleProject;
import org.junit.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ProjectWorkbenchServiceTest {

    @Test
    public void buildChecksExplainsIncompleteProjectWithoutBlockingOptionalResources() {
        RuleProject project = project(0);
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setRuleCount(2L);
        metrics.setDraftRuleCount(2L);
        metrics.setPendingApprovalCount(1L);

        Map<String, ProjectWorkbenchDTO.CheckItem> checks = checks(
                new ProjectWorkbenchService().buildChecks(project, metrics, null));

        assertEquals("BLOCKED", checks.get("PROJECT").getStatus());
        assertEquals("ACTION_REQUIRED", checks.get("FIELD").getStatus());
        assertEquals("OPTIONAL", checks.get("SOURCE").getStatus());
        assertEquals("OPTIONAL", checks.get("MODEL").getStatus());
        assertEquals("READY", checks.get("RULE").getStatus());
        assertEquals("ACTION_REQUIRED", checks.get("TEST").getStatus());
        assertEquals("ATTENTION", checks.get("APPROVAL").getStatus());
        assertEquals("ACTION_REQUIRED", checks.get("PUBLISH").getStatus());
        assertEquals("BLOCKED", checks.get("RUN").getStatus());
    }

    @Test
    public void buildChecksMarksCompleteProjectReadyAndUsesRealRunSummary() {
        RuleProject project = project(1);
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setFieldCount(5L);
        metrics.setDataSourceCount(2L);
        metrics.setEnabledDataSourceCount(2L);
        metrics.setModelCount(1L);
        metrics.setRuleCount(2L);
        metrics.setDraftRuleCount(1L);
        metrics.setPublishedRuleCount(1L);
        metrics.setTestScenarioCount(2L);
        metrics.setRecentExecutionCount(12L);
        metrics.setRecentSuccessCount(10L);
        RuleExecutionLog latest = new RuleExecutionLog();
        latest.setRuleCode("RISK_MAIN");
        latest.setSuccess(1);

        Map<String, ProjectWorkbenchDTO.CheckItem> checks = checks(
                new ProjectWorkbenchService().buildChecks(project, metrics, latest));

        assertEquals("READY", checks.get("PROJECT").getStatus());
        assertEquals("READY", checks.get("FIELD").getStatus());
        assertEquals("READY", checks.get("SOURCE").getStatus());
        assertEquals("READY", checks.get("MODEL").getStatus());
        assertEquals("READY", checks.get("PUBLISH").getStatus());
        assertEquals("READY", checks.get("RUN").getStatus());
        assertEquals("查看最近执行", checks.get("RUN").getActionLabel());
    }

    @Test
    public void buildChecksTreatsOfflineRulesAsExistingProjectRules() {
        RuleProject project = project(1);
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setRuleCount(1L);

        Map<String, ProjectWorkbenchDTO.CheckItem> checks = checks(
                new ProjectWorkbenchService().buildChecks(project, metrics, null));

        assertEquals("READY", checks.get("RULE").getStatus());
        assertEquals("ACTION_REQUIRED", checks.get("TEST").getStatus());
        assertEquals("ACTION_REQUIRED", checks.get("PUBLISH").getStatus());
    }

    @Test
    public void sourceCheckRoutesToTheDatasourceTypeThatNeedsAttention() {
        RuleProject project = project(1);
        ProjectWorkbenchDTO.Metrics metrics = metrics();
        metrics.setDataSourceCount(2L);
        metrics.setEnabledDataSourceCount(1L);
        metrics.setExternalDataSourceCount(1L);
        metrics.setEnabledExternalDataSourceCount(1L);
        metrics.setDatabaseDataSourceCount(1L);
        metrics.setEnabledDatabaseDataSourceCount(0L);

        Map<String, ProjectWorkbenchDTO.CheckItem> checks = checks(
                new ProjectWorkbenchService().buildChecks(project, metrics, null));

        assertEquals("ATTENTION", checks.get("SOURCE").getStatus());
        assertEquals("CONFIGURE_DATABASE_SOURCES",
                checks.get("SOURCE").getActionCode());
    }

    private RuleProject project(int status) {
        RuleProject project = new RuleProject();
        project.setId(9L);
        project.setProjectCode("RISK");
        project.setProjectName("风控项目");
        project.setStatus(status);
        return project;
    }

    private ProjectWorkbenchDTO.Metrics metrics() {
        ProjectWorkbenchDTO.Metrics metrics = new ProjectWorkbenchDTO.Metrics();
        metrics.setFieldCount(0L);
        metrics.setDataSourceCount(0L);
        metrics.setEnabledDataSourceCount(0L);
        metrics.setExternalDataSourceCount(0L);
        metrics.setEnabledExternalDataSourceCount(0L);
        metrics.setDatabaseDataSourceCount(0L);
        metrics.setEnabledDatabaseDataSourceCount(0L);
        metrics.setModelCount(0L);
        metrics.setRuleCount(0L);
        metrics.setDraftRuleCount(0L);
        metrics.setPublishedRuleCount(0L);
        metrics.setTestScenarioCount(0L);
        metrics.setPendingApprovalCount(0L);
        metrics.setRecentExecutionCount(0L);
        metrics.setRecentSuccessCount(0L);
        return metrics;
    }

    private Map<String, ProjectWorkbenchDTO.CheckItem> checks(
            java.util.List<ProjectWorkbenchDTO.CheckItem> rows) {
        return rows.stream().collect(Collectors.toMap(
                ProjectWorkbenchDTO.CheckItem::getCode, value -> value));
    }
}
