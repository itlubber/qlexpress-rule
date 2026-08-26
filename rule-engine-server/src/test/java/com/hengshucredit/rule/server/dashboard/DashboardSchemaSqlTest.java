package com.hengshucredit.rule.server.dashboard;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DashboardSchemaSqlTest {

    @Test
    public void dashboardIndexesExistInCanonicalSchema() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/sql/schema.sql");
        assertNotNull(stream);
        String schema = new String(stream.readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(schema.contains("idx_dashboard_execution_time_project"));
        assertTrue(schema.contains(
                "KEY `idx_dashboard_execution_rule_time` "
                        + "(`project_code`, `rule_code`, `create_time`)"));
        assertTrue(schema.contains(
                "idx_dashboard_runtime_project_module_action_time"));
        assertTrue(schema.contains(
                "KEY `idx_dashboard_billing_rule_time` "
                        + "(`rule_code`, `occur_time`, `project_code`)"));
        assertTrue(schema.contains(
                "idx_dashboard_definition_project_status_scope"));
    }
}
