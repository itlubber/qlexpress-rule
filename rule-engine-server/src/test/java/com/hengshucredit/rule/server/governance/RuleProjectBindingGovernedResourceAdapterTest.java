package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionRef;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionRefMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class RuleProjectBindingGovernedResourceAdapterTest {

    @Test
    public void legacyBindingIsLoadedByBindingId() {
        FakeRepository repository = validRepository();
        repository.binding = binding(15L, 30L, 9L);

        ResourceSnapshot snapshot = adapter(repository)
                .loadEffective(15L);

        Map<String, Object> value =
                CanonicalJson.readMap(snapshot.snapshotJson());
        Assert.assertEquals(5, value.size());
        Assert.assertEquals(30L,
                ((Number) value.get("definitionId")).longValue());
        Assert.assertEquals(15L,
                ((Number) value.get("id")).longValue());
        Assert.assertEquals(9L,
                ((Number) value.get("projectId")).longValue());
        Assert.assertEquals("共享评分规则", value.get("ruleName"));
        Assert.assertEquals("风控项目", value.get("projectName"));
        Assert.assertEquals("ACTIVE", snapshot.effectiveStatus());
        Assert.assertEquals(Long.valueOf(15L), repository.loadedBindingId);
    }

    @Test
    public void normalizationKeepsOnlyIdReferencesAndCollectsExplicitDependencies() {
        FakeRepository repository = validRepository();
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"id\":99,\"definitionId\":30,\"projectId\":9,"
                                + "\"ruleCode\":\"must-not-link-by-code\","
                                + "\"projectName\":\"must-not-link-by-name\"}"));

        Map<String, Object> value =
                CanonicalJson.readMap(snapshot.snapshotJson());
        Assert.assertEquals(5, value.size());
        Assert.assertEquals(30L,
                ((Number) value.get("definitionId")).longValue());
        Assert.assertEquals(99L,
                ((Number) value.get("id")).longValue());
        Assert.assertEquals(9L,
                ((Number) value.get("projectId")).longValue());
        Assert.assertEquals("共享评分规则", value.get("ruleName"));
        Assert.assertEquals("风控项目", value.get("projectName"));
        Assert.assertEquals(List.of(
                new ResourceDependencyRef("RULE", 30L,
                        "RULE", "$.definitionId", "REFERENCES", true),
                new ResourceDependencyRef("PROJECT", 9L,
                        "PROJECT", "$.projectId", "BELONGS_TO", true)),
                adapter(repository).collectDependencies(snapshot));
    }

    @Test
    public void validationRejectsMissingIdsAndNeverFallsBackToNames() {
        FakeRepository repository = validRepository();
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"ruleCode\":\"GLOBAL_RULE\","
                                + "\"projectName\":\"risk-project\"}"));

        List<GovernanceIssue> issues = adapter(repository).validate(snapshot);

        Assert.assertTrue(hasError(issues,
                "RULE_PROJECT_BINDING_DEFINITION_REQUIRED"));
        Assert.assertTrue(hasError(issues,
                "RULE_PROJECT_BINDING_PROJECT_REQUIRED"));
        Assert.assertNull(repository.loadedDefinitionId);
        Assert.assertNull(repository.loadedProjectId);
    }

    @Test
    public void validationRejectsNonGlobalRuleMissingProjectAndDuplicateBinding() {
        FakeRepository repository = validRepository();
        repository.definition.setScope("PROJECT");
        repository.project = null;
        repository.bindingCount = 1L;
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"definitionId\":30,\"projectId\":9}"));

        List<GovernanceIssue> issues = adapter(repository).validate(snapshot);

        Assert.assertTrue(hasError(issues,
                "RULE_PROJECT_BINDING_RULE_NOT_GLOBAL"));
        Assert.assertTrue(hasError(issues,
                "RULE_PROJECT_BINDING_PROJECT_NOT_FOUND"));
        Assert.assertTrue(hasError(issues,
                "RULE_PROJECT_BINDING_DUPLICATE"));
    }

    @Test
    public void validationAcceptsExistingGlobalRuleAndProjectById() {
        FakeRepository repository = validRepository();
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"definitionId\":30,\"projectId\":9}"));

        List<GovernanceIssue> issues = adapter(repository).validate(snapshot);

        Assert.assertTrue(issues.toString(), issues.isEmpty());
        Assert.assertEquals(Long.valueOf(30L),
                repository.loadedDefinitionId);
        Assert.assertEquals(Long.valueOf(9L), repository.loadedProjectId);
    }

    @Test
    public void validationRejectsADeletedOrChangedExistingBinding() {
        FakeRepository repository = validRepository();
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"id\":15,\"definitionId\":30,"
                                + "\"projectId\":9}"));

        List<GovernanceIssue> missing = adapter(repository)
                .validate(snapshot);
        repository.binding = binding(15L, 31L, 9L);
        List<GovernanceIssue> changed = adapter(repository)
                .validate(snapshot);

        Assert.assertTrue(hasError(missing,
                "RULE_PROJECT_BINDING_NOT_FOUND"));
        Assert.assertTrue(hasError(changed,
                "RULE_PROJECT_BINDING_CHANGED"));
    }

    @Test
    public void createAndDeleteApplyOnlyTheBindingProjection() {
        FakeRepository repository = validRepository();
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"id\":999,\"definitionId\":30,"
                                + "\"projectId\":9}"));

        AppliedResource created = adapter(repository).apply(
                new ApprovalApplyContext(1L, null, 1,
                        "CREATE", snapshot, "applicant", null));
        AppliedResource deleted = adapter(repository).apply(
                new ApprovalApplyContext(2L, 77L, 2,
                        "DELETE", snapshot, "reviewer", null));

        Assert.assertEquals(Long.valueOf(61L), created.resourceId());
        Assert.assertEquals("ACTIVE", created.effectiveStatus());
        Assert.assertEquals(Long.valueOf(61L), repository.inserted.getId());
        Assert.assertEquals(Long.valueOf(30L),
                repository.inserted.getDefinitionId());
        Assert.assertEquals(Long.valueOf(9L),
                repository.inserted.getProjectId());
        Assert.assertNotNull(repository.inserted.getCreateTime());
        Assert.assertEquals(Long.valueOf(77L), deleted.resourceId());
        Assert.assertEquals("DELETED", deleted.effectiveStatus());
        Assert.assertEquals(Long.valueOf(77L), repository.deletedBindingId);
        Assert.assertEquals(0, repository.deletedRules);
        Assert.assertEquals(0, repository.deletedProjects);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateActionIsRejectedForAssociationResources() {
        FakeRepository repository = validRepository();
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson(
                        "{\"definitionId\":30,\"projectId\":9}"));

        adapter(repository).apply(new ApprovalApplyContext(
                1L, 15L, 2, "UPDATE", snapshot, "user", null));
    }

    private RuleProjectBindingGovernedResourceAdapter adapter(
            FakeRepository repository) {
        return new RuleProjectBindingGovernedResourceAdapter(
                repository.refMapper(), repository.definitionMapper(),
                repository.projectMapper());
    }

    private FakeRepository validRepository() {
        FakeRepository repository = new FakeRepository();
        repository.definition = new RuleDefinition();
        repository.definition.setId(30L);
        repository.definition.setScope("GLOBAL");
        repository.definition.setRuleName("共享评分规则");
        repository.project = new RuleProject();
        repository.project.setId(9L);
        repository.project.setProjectName("风控项目");
        return repository;
    }

    private boolean hasError(List<GovernanceIssue> issues,
                             String code) {
        return issues.stream().anyMatch(issue -> issue.isError()
                && code.equals(issue.code()));
    }

    private RuleDefinitionRef binding(Long id, Long definitionId,
                                      Long projectId) {
        RuleDefinitionRef ref = new RuleDefinitionRef();
        ref.setId(id);
        ref.setDefinitionId(definitionId);
        ref.setProjectId(projectId);
        ref.setCreateTime(LocalDateTime.of(2026, 8, 3, 0, 0));
        return ref;
    }

    private static class FakeRepository {
        private RuleDefinitionRef binding;
        private RuleDefinitionRef inserted;
        private RuleDefinition definition;
        private RuleProject project;
        private Long bindingCount = 0L;
        private Long loadedBindingId;
        private Long loadedDefinitionId;
        private Long loadedProjectId;
        private Long deletedBindingId;
        private int deletedRules;
        private int deletedProjects;

        private RuleDefinitionRefMapper refMapper() {
            return proxy(RuleDefinitionRefMapper.class,
                    (method, args) -> switch (method) {
                        case "selectById" -> {
                            loadedBindingId = ((Number) args[0]).longValue();
                            yield binding;
                        }
                        case "selectCount" -> bindingCount;
                        case "insert" -> {
                            inserted = (RuleDefinitionRef) args[0];
                            inserted.setId(61L);
                            yield 1;
                        }
                        case "deleteById" -> {
                            deletedBindingId = ((Number) args[0]).longValue();
                            yield 1;
                        }
                        default -> null;
                    });
        }

        private RuleDefinitionMapper definitionMapper() {
            return proxy(RuleDefinitionMapper.class,
                    (method, args) -> {
                        if ("selectById".equals(method)) {
                            loadedDefinitionId =
                                    ((Number) args[0]).longValue();
                            return definition;
                        }
                        if ("deleteById".equals(method)) {
                            deletedRules++;
                            return 1;
                        }
                        return null;
                    });
        }

        private RuleProjectMapper projectMapper() {
            return proxy(RuleProjectMapper.class,
                    (method, args) -> {
                        if ("selectById".equals(method)) {
                            loadedProjectId =
                                    ((Number) args[0]).longValue();
                            return project;
                        }
                        if ("deleteById".equals(method)) {
                            deletedProjects++;
                            return 1;
                        }
                        return null;
                    });
        }

        private <T> T proxy(Class<T> type, MapperCall call) {
            return type.cast(Proxy.newProxyInstance(
                    type.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, args) -> {
                        Object value = call.invoke(method.getName(), args);
                        if (value != null || !method.getReturnType().isPrimitive()) {
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
