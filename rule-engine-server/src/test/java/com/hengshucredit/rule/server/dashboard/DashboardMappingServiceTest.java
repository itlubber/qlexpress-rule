package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.ConsoleUserPreferenceMapper;
import com.hengshucredit.rule.server.service.ConsoleUserPreferenceService;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static com.hengshucredit.rule.server.dashboard.DashboardMapping.JsonSource.INPUT;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.RefType.VARIABLE;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.AMOUNT;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.LONGITUDE;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.PERIOD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class DashboardMappingServiceTest {

    @Test
    public void projectFieldsFallBackIndependentlyToUserThenSystemDefaults() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put("7:DASHBOARD_MAPPING", "{"
                + "\"schemaVersion\":1,"
                + "\"defaults\":{\"AMOUNT\":{\"refType\":\"VARIABLE\",\"refId\":88,\"jsonSource\":\"INPUT\"}},"
                + "\"projects\":{\"9\":{\"PERIOD\":{\"refType\":\"VARIABLE\",\"refId\":99,\"jsonSource\":\"INPUT\"}}}} ");
        DashboardMappingRepository references = repository(
                variable(73L, 0L, "GLOBAL", "gps_longitude", "NUMBER"),
                variable(88L, 0L, "GLOBAL", "loan_amount", "NUMBER"),
                variable(99L, 9L, "PROJECT", "loan_period", "NUMBER"));

        DashboardMapping.ResolvedMappings mappings = service(
                preferences, references).resolve(7L, 9L);

        assertEquals(Long.valueOf(99L), mappings.get(PERIOD).refId());
        assertEquals(DashboardMapping.MappingSource.PROJECT_OVERRIDE,
                mappings.get(PERIOD).mappingSource());
        assertEquals(Long.valueOf(88L), mappings.get(AMOUNT).refId());
        assertEquals(DashboardMapping.MappingSource.USER_DEFAULT,
                mappings.get(AMOUNT).mappingSource());
        assertEquals(Long.valueOf(73L), mappings.get(LONGITUDE).refId());
        assertEquals(DashboardMapping.MappingSource.SYSTEM_DEFAULT,
                mappings.get(LONGITUDE).mappingSource());
    }

    @Test
    public void invalidExplicitReferenceReportsConfigurationErrorWithoutGuessingFallback() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put("7:DASHBOARD_MAPPING", "{"
                + "\"schemaVersion\":1,"
                + "\"defaults\":{\"AMOUNT\":{\"refType\":\"VARIABLE\",\"refId\":88,\"jsonSource\":\"INPUT\"}},"
                + "\"projects\":{\"9\":{\"AMOUNT\":{\"refType\":\"VARIABLE\",\"refId\":100,\"jsonSource\":\"INPUT\"}}}} ");
        DashboardMappingRepository references = repository(
                variable(88L, 0L, "GLOBAL", "loan_amount", "NUMBER"),
                variable(100L, 10L, "PROJECT", "wrong_project_amount", "NUMBER"));

        DashboardMapping.ResolvedMapping amount = service(
                preferences, references).resolve(7L, 9L).get(AMOUNT);

        assertFalse(amount.valid());
        assertEquals(Long.valueOf(100L), amount.refId());
        assertEquals("字段引用不属于当前项目", amount.errorMessage());
    }

    @Test
    public void personalDefaultsRejectProjectScopedReferences() {
        Map<String, String> preferences = new HashMap<>();
        DashboardMappingRepository references = repository(
                variable(99L, 9L, "PROJECT", "loan_period", "NUMBER"));
        DashboardMapping.StoredMapping mapping = new DashboardMapping.StoredMapping(
                VARIABLE, 99L, INPUT, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(preferences, references).saveScope(
                        7L, "alice", null, Map.of(PERIOD, mapping)));

        assertEquals("个人默认只能引用全局字段", error.getMessage());
    }

    private DashboardMappingService service(
            Map<String, String> preferences,
            DashboardMappingRepository references) {
        ConsoleUserPreferenceMapper mapper = (ConsoleUserPreferenceMapper)
                Proxy.newProxyInstance(
                        ConsoleUserPreferenceMapper.class.getClassLoader(),
                        new Class<?>[]{ConsoleUserPreferenceMapper.class},
                        (proxy, method, args) -> {
                            if ("findValue".equals(method.getName())) {
                                return preferences.get(key(args[0], args[1]));
                            }
                            if ("upsertValue".equals(method.getName())) {
                                preferences.put(key(args[0], args[1]),
                                        String.valueOf(args[2]));
                                return 1;
                            }
                            if ("deleteValue".equals(method.getName())) {
                                preferences.remove(key(args[0], args[1]));
                                return 1;
                            }
                            if ("toString".equals(method.getName())) {
                                return "DashboardPreferenceMapper";
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
        return new DashboardMappingService(
                new ConsoleUserPreferenceService(mapper), references);
    }

    private DashboardMappingRepository repository(RuleVariable... variables) {
        Map<Long, RuleVariable> values = new HashMap<>();
        for (RuleVariable variable : variables) values.put(variable.getId(), variable);
        return new DashboardMappingRepository() {
            @Override
            public RuleVariable findVariable(Long id) {
                return values.get(id);
            }
        };
    }

    private RuleVariable variable(Long id, Long projectId, String scope,
                                  String code, String type) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setProjectId(projectId);
        variable.setScope(scope);
        variable.setVarCode(code);
        variable.setVarLabel(code);
        variable.setVarType(type);
        variable.setStatus(1);
        return variable;
    }

    private String key(Object userId, Object preferenceKey) {
        return userId + ":" + preferenceKey;
    }
}
