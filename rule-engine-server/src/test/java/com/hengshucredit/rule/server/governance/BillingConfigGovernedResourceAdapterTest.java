package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleBillingConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionRefMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BillingConfigGovernedResourceAdapterTest {

    @Test
    public void normalizationPreservesCodeAndCollectsTypedDependencies() {
        FakeRepository repository = validRepository();
        ResourceSnapshot normalized = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"projectId\":7,"
                        + "\"scope\":\"project\","
                        + "\"billingCode\":\"Engine_Count_V1\","
                        + "\"billingName\":\"规则调用计费\","
                        + "\"billingTarget\":\"engine\","
                        + "\"targetRefId\":9,"
                        + "\"chargeType\":\"count\","
                        + "\"unitPrice\":\"0.125000\","
                        + "\"currency\":\"CNY\","
                        + "\"projectName\":\"展示字段\",\"status\":1}"));

        Map<String, Object> value = CanonicalJson.readMap(
                normalized.snapshotJson());
        Assert.assertEquals("Engine_Count_V1", value.get("billingCode"));
        Assert.assertEquals("ENGINE", value.get("billingTarget"));
        Assert.assertEquals("COUNT", value.get("chargeType"));
        Assert.assertFalse(value.containsKey("projectName"));
        Assert.assertEquals(List.of(
                        new ResourceDependencyRef("PROJECT", 7L,
                                "PROJECT", "$.projectId",
                                "BELONGS_TO", true),
                        new ResourceDependencyRef("RULE", 9L,
                                "RULE", "$.targetRefId",
                                "BILLS", true)),
                adapter(repository).collectDependencies(normalized));
    }

    @Test
    public void validationRejectsInvalidBusinessConfiguration() {
        FakeRepository repository = validRepository();
        repository.project = null;
        repository.duplicateCount = 1L;
        ResourceSnapshot normalized = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"billingCode\":\"dup\","
                        + "\"billingName\":\"计费\","
                        + "\"billingTarget\":\"UNKNOWN\","
                        + "\"chargeType\":\"OTHER\","
                        + "\"unitPrice\":-1,\"currency\":\"\","
                        + "\"effectiveTime\":\"2026-08-04T00:00:00\","
                        + "\"expireTime\":\"2026-08-03T00:00:00\","
                        + "\"status\":2}"));

        List<GovernanceIssue> issues = adapter(repository)
                .validate(normalized, "CREATE");

        Assert.assertTrue(hasError(issues, "BILLING_PROJECT_NOT_FOUND"));
        Assert.assertTrue(hasError(issues, "BILLING_TARGET_INVALID"));
        Assert.assertTrue(hasError(issues, "BILLING_CHARGE_TYPE_INVALID"));
        Assert.assertTrue(hasError(issues, "BILLING_UNIT_PRICE_INVALID"));
        Assert.assertTrue(hasError(issues, "BILLING_CURRENCY_REQUIRED"));
        Assert.assertTrue(hasError(issues, "BILLING_TIME_RANGE_INVALID"));
        Assert.assertTrue(hasError(issues, "BILLING_STATUS_INVALID"));
        Assert.assertTrue(hasError(issues, "BILLING_CODE_DUPLICATE"));
    }

    @Test
    public void validationRejectsMissingOrCrossProjectTarget() {
        FakeRepository repository = validRepository();
        RuleDbDatasource datasource = new RuleDbDatasource();
        datasource.setId(31L);
        datasource.setScope("PROJECT");
        datasource.setProjectId(8L);
        repository.dbDatasource = datasource;
        ResourceSnapshot crossProject = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(validJson("DB", 31L)));
        Assert.assertTrue(hasError(adapter(repository)
                        .validate(crossProject, "CREATE"),
                "BILLING_TARGET_SCOPE_MISMATCH"));

        repository.dbDatasource = null;
        ResourceSnapshot missing = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(validJson("DB", 31L)));
        Assert.assertTrue(hasError(adapter(repository)
                        .validate(missing, "CREATE"),
                "BILLING_TARGET_NOT_FOUND"));
    }

    @Test
    public void projectBillingRequiresGlobalRuleToBeBoundToProject() {
        FakeRepository repository = validRepository();
        RuleDefinition globalRule = new RuleDefinition();
        globalRule.setId(9L);
        globalRule.setScope("GLOBAL");
        globalRule.setProjectId(0L);
        repository.definition = globalRule;
        repository.definitionRefCount = 0L;
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(validJson("ENGINE", 9L)));

        Assert.assertTrue(hasError(adapter(repository)
                        .validate(snapshot, "CREATE"),
                "BILLING_TARGET_SCOPE_MISMATCH"));

        repository.definitionRefCount = 1L;
        Assert.assertFalse(hasError(adapter(repository)
                        .validate(snapshot, "CREATE"),
                "BILLING_TARGET_SCOPE_MISMATCH"));
    }

    @Test
    public void validationProtectsIdentityAndRequiresExplicitStatusAction() {
        FakeRepository repository = validRepository();
        repository.current = config(15L, 7L, "PROJECT", "Stable_Code", 1);
        ResourceSnapshot changed = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"id\":15,\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"billingCode\":\"Changed_Code\","
                        + "\"billingName\":\"计费\","
                        + "\"billingTarget\":\"ENGINE\","
                        + "\"chargeType\":\"COUNT\","
                        + "\"unitPrice\":0,\"currency\":\"CNY\","
                        + "\"status\":0}"));

        List<GovernanceIssue> issues = adapter(repository)
                .validate(changed, "UPDATE");

        Assert.assertTrue(hasError(issues, "BILLING_IDENTITY_CHANGED"));
        Assert.assertTrue(hasError(issues,
                "BILLING_STATUS_ACTION_REQUIRED"));
    }

    @Test
    public void applyCreatesUpdatesDisablesAndSoftDeletesProjection() {
        FakeRepository repository = validRepository();
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(validJson("ENGINE", null)));

        AppliedResource created = adapter(repository).apply(
                new ApprovalApplyContext(1L, null, 1,
                        "CREATE", snapshot, "owner", null));
        adapter(repository).apply(new ApprovalApplyContext(
                2L, 20L, 2, "UPDATE", snapshot, "owner", null));
        adapter(repository).apply(new ApprovalApplyContext(
                3L, 20L, 3, "DISABLE", snapshot, "owner", null));
        AppliedResource deleted = adapter(repository).apply(
                new ApprovalApplyContext(4L, 20L, 4,
                        "DELETE", snapshot, "owner", null));

        Assert.assertEquals(Long.valueOf(51L), created.resourceId());
        Assert.assertEquals("Billing_Code",
                repository.inserted.getBillingCode());
        Assert.assertEquals(Integer.valueOf(1),
                repository.updates.get(0).getStatus());
        Assert.assertEquals(Integer.valueOf(0),
                repository.updates.get(1).getStatus());
        Assert.assertEquals(Integer.valueOf(-1),
                repository.updates.get(2).getStatus());
        Assert.assertEquals("DELETED", deleted.effectiveStatus());
    }

    private String validJson(String target, Long targetRefId) {
        return "{\"projectId\":7,\"scope\":\"PROJECT\","
                + "\"billingCode\":\"Billing_Code\","
                + "\"billingName\":\"计费\","
                + "\"billingTarget\":\"" + target + "\","
                + (targetRefId == null ? ""
                : "\"targetRefId\":" + targetRefId + ",")
                + "\"chargeType\":\"COUNT\",\"unitPrice\":0,"
                + "\"currency\":\"CNY\",\"status\":1}";
    }

    private BillingConfigGovernedResourceAdapter adapter(
            FakeRepository repository) {
        return new BillingConfigGovernedResourceAdapter(
                repository.billingMapper(), repository.projectMapper(),
                repository.definitionMapper(), repository.definitionRefMapper(),
                repository.apiMapper(), repository.externalDatasourceMapper(),
                repository.dbMapper());
    }

    private FakeRepository validRepository() {
        FakeRepository repository = new FakeRepository();
        repository.project = new RuleProject();
        repository.project.setId(7L);
        return repository;
    }

    private RuleBillingConfig config(Long id, Long projectId,
                                     String scope, String code,
                                     Integer status) {
        RuleBillingConfig config = new RuleBillingConfig();
        config.setId(id);
        config.setProjectId(projectId);
        config.setScope(scope);
        config.setBillingCode(code);
        config.setBillingName("计费");
        config.setBillingTarget("ENGINE");
        config.setChargeType("COUNT");
        config.setUnitPrice(java.math.BigDecimal.ZERO);
        config.setCurrency("CNY");
        config.setStatus(status);
        return config;
    }

    private boolean hasError(List<GovernanceIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> issue.isError()
                && code.equals(issue.code()));
    }

    private static class FakeRepository {
        private RuleBillingConfig current;
        private RuleBillingConfig inserted;
        private RuleProject project;
        private RuleDefinition definition;
        private Long definitionRefCount = 0L;
        private RuleExternalApiConfig api;
        private RuleExternalDatasource externalDatasource;
        private RuleDbDatasource dbDatasource;
        private Long duplicateCount = 0L;
        private final List<RuleBillingConfig> updates = new ArrayList<>();

        private RuleBillingConfigMapper billingMapper() {
            return proxy(RuleBillingConfigMapper.class, (method, args) ->
                    switch (method) {
                        case "selectById" -> current;
                        case "selectCount" -> duplicateCount;
                        case "insert" -> {
                            inserted = copy((RuleBillingConfig) args[0]);
                            ((RuleBillingConfig) args[0]).setId(51L);
                            yield 1;
                        }
                        case "updateById" -> {
                            updates.add(copy((RuleBillingConfig) args[0]));
                            yield 1;
                        }
                        default -> null;
                    });
        }

        private RuleProjectMapper projectMapper() {
            return proxy(RuleProjectMapper.class, (method, args) ->
                    "selectById".equals(method) ? project : null);
        }

        private RuleDefinitionMapper definitionMapper() {
            return proxy(RuleDefinitionMapper.class, (method, args) ->
                    "selectById".equals(method) ? definition : null);
        }

        private RuleDefinitionRefMapper definitionRefMapper() {
            return proxy(RuleDefinitionRefMapper.class, (method, args) ->
                    "selectCount".equals(method) ? definitionRefCount : null);
        }

        private RuleExternalApiConfigMapper apiMapper() {
            return proxy(RuleExternalApiConfigMapper.class, (method, args) ->
                    "selectById".equals(method) ? api : null);
        }

        private RuleExternalDatasourceMapper externalDatasourceMapper() {
            return proxy(RuleExternalDatasourceMapper.class,
                    (method, args) -> "selectById".equals(method)
                            ? externalDatasource : null);
        }

        private RuleDbDatasourceMapper dbMapper() {
            return proxy(RuleDbDatasourceMapper.class, (method, args) ->
                    "selectById".equals(method) ? dbDatasource : null);
        }

        private RuleBillingConfig copy(RuleBillingConfig source) {
            RuleBillingConfig copy = new RuleBillingConfig();
            copy.setId(source.getId());
            copy.setProjectId(source.getProjectId());
            copy.setScope(source.getScope());
            copy.setBillingCode(source.getBillingCode());
            copy.setBillingName(source.getBillingName());
            copy.setBillingTarget(source.getBillingTarget());
            copy.setTargetRefId(source.getTargetRefId());
            copy.setChargeType(source.getChargeType());
            copy.setUnitPrice(source.getUnitPrice());
            copy.setCurrency(source.getCurrency());
            copy.setEffectiveTime(source.getEffectiveTime());
            copy.setExpireTime(source.getExpireTime());
            copy.setDescription(source.getDescription());
            copy.setStatus(source.getStatus());
            return copy;
        }

        private <T> T proxy(Class<T> type, MapperCall call) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                    new Class<?>[]{type}, (proxy, method, args) -> {
                        Object value = call.invoke(method.getName(), args);
                        if (value != null
                                || !method.getReturnType().isPrimitive()) {
                            return value;
                        }
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == long.class) return 0L;
                        return 0;
                    }));
        }
    }

    @FunctionalInterface
    private interface MapperCall {
        Object invoke(String method, Object[] args);
    }
}
