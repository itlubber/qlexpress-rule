package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

public class RuleListLibraryGovernedResourceAdapterTest {

    @Test
    public void normalizationPreservesBusinessValuesAndRemovesDisplayFields() {
        FakeRepository repository = validRepository();

        ResourceSnapshot normalized = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"listCode\":\"Mobile_Black\","
                        + "\"listName\":\"手机号黑名单\","
                        + "\"listType\":\"BLACK\",\"status\":1,"
                        + "\"projectName\":\"不可作为关联\","
                        + "\"recordCount\":99,\"createTime\":\"x\"}"));

        Map<String, Object> value = CanonicalJson.readMap(
                normalized.snapshotJson());
        Assert.assertEquals("Mobile_Black", value.get("listCode"));
        Assert.assertEquals("手机号黑名单", value.get("listName"));
        Assert.assertFalse(value.containsKey("projectName"));
        Assert.assertFalse(value.containsKey("recordCount"));
        Assert.assertFalse(value.containsKey("createTime"));
        Assert.assertEquals(List.of(new ResourceDependencyRef(
                        "PROJECT", 7L, "PROJECT", "$.projectId",
                        "BELONGS_TO", true)),
                adapter(repository).collectDependencies(normalized));
    }

    @Test
    public void globalLibraryHasNoProjectDependencyAndUsesProjectZero() {
        FakeRepository repository = validRepository();

        ResourceSnapshot normalized = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"projectId\":7,"
                        + "\"scope\":\"GLOBAL\","
                        + "\"listCode\":\"global_list\","
                        + "\"listName\":\"全局名单\"}"));

        Map<String, Object> value = CanonicalJson.readMap(
                normalized.snapshotJson());
        Assert.assertEquals(0L,
                ((Number) value.get("projectId")).longValue());
        Assert.assertTrue(adapter(repository)
                .collectDependencies(normalized).isEmpty());
    }

    @Test
    public void validationRejectsMissingProjectInvalidTypeAndDuplicateCode() {
        FakeRepository repository = validRepository();
        repository.project = null;
        repository.duplicateCount = 1L;
        ResourceSnapshot normalized = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"listCode\":\"dup\","
                        + "\"listName\":\"重复名单\","
                        + "\"listType\":\"UNKNOWN\",\"status\":2}"));

        List<GovernanceIssue> issues = adapter(repository)
                .validate(normalized);

        Assert.assertTrue(hasError(issues, "LIST_PROJECT_NOT_FOUND"));
        Assert.assertTrue(hasError(issues, "LIST_TYPE_INVALID"));
        Assert.assertTrue(hasError(issues, "LIST_STATUS_INVALID"));
        Assert.assertTrue(hasError(issues, "LIST_CODE_DUPLICATE"));
    }

    @Test
    public void validationRejectsChangedExistingIdentity() {
        FakeRepository repository = validRepository();
        repository.library = library(15L, 7L, "PROJECT", "stable_code");
        ResourceSnapshot normalized = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"id\":15,\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"listCode\":\"changed_code\","
                        + "\"listName\":\"名单\",\"listType\":\"BLACK\"}"));

        List<GovernanceIssue> issues = adapter(repository)
                .validate(normalized);

        Assert.assertTrue(hasError(issues, "LIST_IDENTITY_CHANGED"));
    }

    @Test
    public void applyCreatesUpdatesAndSoftDeletesOnlyListProjection() {
        FakeRepository repository = validRepository();
        ResourceSnapshot snapshot = adapter(repository).normalizeDraft(
                ResourceSnapshot.ofJson("{\"projectId\":7,"
                        + "\"scope\":\"PROJECT\","
                        + "\"listCode\":\"Mobile_Black\","
                        + "\"listName\":\"手机号黑名单\","
                        + "\"listType\":\"BLACK\",\"status\":1}"));

        AppliedResource created = adapter(repository).apply(
                new ApprovalApplyContext(1L, null, 1,
                        "CREATE", snapshot, "owner", null));
        AppliedResource disabled = adapter(repository).apply(
                new ApprovalApplyContext(2L, 20L, 2,
                        "DISABLE", snapshot, "owner", null));
        AppliedResource deleted = adapter(repository).apply(
                new ApprovalApplyContext(3L, 20L, 3,
                        "DELETE", snapshot, "owner", null));

        Assert.assertEquals(Long.valueOf(31L), created.resourceId());
        Assert.assertEquals("ACTIVE", created.effectiveStatus());
        Assert.assertEquals("Mobile_Black", repository.inserted.getListCode());
        Assert.assertEquals(Integer.valueOf(0), repository.updates.get(0).getStatus());
        Assert.assertEquals(Integer.valueOf(-1), repository.updates.get(1).getStatus());
        Assert.assertEquals("DELETED", deleted.effectiveStatus());
        Assert.assertEquals("DISABLED", disabled.effectiveStatus());
    }

    private RuleListLibraryGovernedResourceAdapter adapter(
            FakeRepository repository) {
        return new RuleListLibraryGovernedResourceAdapter(
                repository.listMapper(), repository.projectMapper());
    }

    private FakeRepository validRepository() {
        FakeRepository repository = new FakeRepository();
        repository.project = new RuleProject();
        repository.project.setId(7L);
        repository.project.setProjectName("风控项目");
        return repository;
    }

    private RuleListLibrary library(Long id, Long projectId,
                                    String scope, String code) {
        RuleListLibrary library = new RuleListLibrary();
        library.setId(id);
        library.setProjectId(projectId);
        library.setScope(scope);
        library.setListCode(code);
        library.setListName("名单");
        library.setListType("BLACK");
        library.setStatus(1);
        return library;
    }

    private boolean hasError(List<GovernanceIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> issue.isError()
                && code.equals(issue.code()));
    }

    private static class FakeRepository {
        private RuleListLibrary library;
        private RuleListLibrary inserted;
        private final java.util.ArrayList<RuleListLibrary> updates =
                new java.util.ArrayList<>();
        private RuleProject project;
        private Long duplicateCount = 0L;

        private RuleListLibraryMapper listMapper() {
            return proxy(RuleListLibraryMapper.class, (method, args) ->
                    switch (method) {
                        case "selectById" -> library;
                        case "selectCount" -> duplicateCount;
                        case "insert" -> {
                            inserted = (RuleListLibrary) args[0];
                            inserted.setId(31L);
                            yield 1;
                        }
                        case "updateById" -> {
                            RuleListLibrary value = (RuleListLibrary) args[0];
                            RuleListLibrary copy = new RuleListLibrary();
                            copy.setId(value.getId());
                            copy.setStatus(value.getStatus());
                            updates.add(copy);
                            yield 1;
                        }
                        default -> null;
                    });
        }

        private RuleProjectMapper projectMapper() {
            return proxy(RuleProjectMapper.class, (method, args) ->
                    "selectById".equals(method) ? project : null);
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
