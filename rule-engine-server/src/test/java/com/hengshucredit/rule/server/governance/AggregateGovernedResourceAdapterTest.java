package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVariableOption;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldOptionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentGroupMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableOptionMapper;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import com.hengshucredit.rule.server.service.RuleDraftService;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AggregateGovernedResourceAdapterTest {

    @Test
    public void variableVersionContainsAndAppliesOptions() {
        RuleVariable variable = variable();
        RuleVariableOption option = new RuleVariableOption();
        option.setId(9L);
        option.setVariableId(7L);
        option.setOptionValue("A");
        option.setOptionLabel("甲");
        List<Object> inserted = new ArrayList<>();
        RuleVariableOptionMapper optionMapper =
                mapper(RuleVariableOptionMapper.class,
                        Map.of("selectList", List.of(option)),
                        inserted);
        VariableGovernedResourceAdapter adapter =
                new VariableGovernedResourceAdapter(
                        store(variable, RuleVariable::setId),
                        optionMapper, codec());

        ResourceSnapshot snapshot = adapter.loadEffective(7L);
        Assert.assertEquals(1, ((List<?>) CanonicalJson
                .readMap(snapshot.snapshotJson())
                .get("options")).size());
        Assert.assertFalse(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "VARIABLE".equals(
                        ref.targetResourceType())
                        && Long.valueOf(7L).equals(
                        ref.targetResourceId())));
        adapter.apply(new ApprovalApplyContext(
                1L, 7L, 2, "UPDATE",
                snapshot, "user", null));

        RuleVariableOption saved =
                (RuleVariableOption) inserted.get(0);
        Assert.assertEquals(Long.valueOf(7L),
                saved.getVariableId());
        Assert.assertNull(saved.getId());
    }

    @Test
    public void variableSourceFailureDoesNotBlockDisableOrDelete() {
        RuleVariable variable = variable();
        RuleVariableOptionMapper optionMapper =
                mapper(RuleVariableOptionMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        VariableSourceReferenceValidator sourceValidator =
                new VariableSourceReferenceValidator() {
                    @Override
                    public List<GovernanceIssue> validate(
                            RuleVariable ignored) {
                        return List.of(GovernanceIssue.error(
                                "VARIABLE_SOURCE_NOT_FOUND",
                                "来源已删除",
                                GovernanceResourceTypes.VARIABLE,
                                7L, "$.sourceConfig.apiConfigId"));
                    }
                };
        VariableGovernedResourceAdapter adapter =
                new VariableGovernedResourceAdapter(
                        store(variable, RuleVariable::setId),
                        optionMapper, codec(), sourceValidator);
        ResourceSnapshot snapshot = adapter.loadEffective(7L);

        Assert.assertFalse(adapter.validate(snapshot, "UPDATE").isEmpty());
        Assert.assertTrue(adapter.validate(snapshot, "DISABLE").isEmpty());
        Assert.assertTrue(adapter.validate(snapshot, "DELETE").isEmpty());
    }

    @Test
    public void dataObjectVersionContainsFieldsAndNestedOptions() {
        RuleDataObject object = new RuleDataObject();
        object.setId(4L);
        object.setObjectCode("request");
        object.setObjectLabel("请求");
        object.setObjectType("OBJECT");
        object.setStatus(1);
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(8L);
        field.setObjectId(4L);
        field.setVarCode("age");
        field.setVarLabel("年龄");
        field.setVarType("INTEGER");
        RuleDataObjectFieldMapper fieldMapper =
                mapper(RuleDataObjectFieldMapper.class,
                        Map.of("selectList", List.of(field)),
                        new ArrayList<>());
        RuleDataObjectFieldOptionMapper optionMapper =
                mapper(RuleDataObjectFieldOptionMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        DataObjectGovernedResourceAdapter adapter =
                new DataObjectGovernedResourceAdapter(
                        store(object, RuleDataObject::setId),
                        fieldMapper, optionMapper, codec());

        ResourceSnapshot version = adapter.loadEffective(4L);
        Map<String, Object> snapshot = CanonicalJson.readMap(
                version.snapshotJson());
        List<?> fields = (List<?>) snapshot.get("fields");

        Assert.assertEquals(1, fields.size());
        Assert.assertTrue(((Map<?, ?>) fields.get(0))
                .containsKey("options"));
        Assert.assertFalse(adapter.collectDependencies(version).stream()
                .anyMatch(ref -> "DATA_OBJECT".equals(
                        ref.targetResourceType())
                        && Long.valueOf(4L).equals(
                        ref.targetResourceId())));
    }

    @Test
    public void modelBinaryIsProtectedWhileInputContractIsVersioned() {
        RuleModel model = new RuleModel();
        model.setId(5L);
        model.setModelCode("credit");
        model.setModelName("授信模型");
        model.setModelType("XGBOOST");
        model.setModelFormat("ONNX");
        model.setModelContent("binary-content");
        model.setStatus(1);
        RuleModelInputField input = new RuleModelInputField();
        input.setId(10L);
        input.setModelId(5L);
        input.setFieldName("age");
        input.setFieldLabel("年龄");
        input.setFieldType("INTEGER");
        RuleModelInputFieldMapper inputMapper =
                mapper(RuleModelInputFieldMapper.class,
                        Map.of("selectList", List.of(input)),
                        new ArrayList<>());
        RuleModelOutputFieldMapper outputMapper =
                mapper(RuleModelOutputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        ModelGovernedResourceAdapter adapter =
                new ModelGovernedResourceAdapter(
                        store(model, RuleModel::setId),
                        inputMapper, outputMapper, codec());

        ResourceSnapshot snapshot = adapter.loadEffective(5L);
        Map<String, Object> publicValue =
                CanonicalJson.readMap(snapshot.snapshotJson());

        Assert.assertFalse(snapshot.snapshotJson()
                .contains("binary-content"));
        Assert.assertNotNull(snapshot.secretPayloadCiphertext());
        Assert.assertEquals(1,
                ((List<?>) publicValue.get("inputFields")).size());
        Assert.assertFalse(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "MODEL".equals(
                        ref.targetResourceType())
                        && Long.valueOf(5L).equals(
                        ref.targetResourceId())));
    }

    @Test
    public void experimentGroupRuleIdsParticipateInPreflight() {
        RuleExperiment experiment = new RuleExperiment();
        experiment.setId(6L);
        experiment.setExperimentCode("routing");
        experiment.setExperimentName("路由实验");
        experiment.setRoutingMode("HASH");
        experiment.setStatus(1);
        RuleExperimentGroup group = new RuleExperimentGroup();
        group.setId(12L);
        group.setExperimentId(6L);
        group.setGroupType("CHAMPION");
        group.setRuleId(77L);
        group.setStatus(1);
        RuleExperimentGroupMapper groupMapper =
                mapper(RuleExperimentGroupMapper.class,
                        Map.of("selectList", List.of(group)),
                        new ArrayList<>());
        ExperimentGovernedResourceAdapter adapter =
                new ExperimentGovernedResourceAdapter(
                        store(experiment, RuleExperiment::setId),
                        groupMapper, codec());

        ResourceSnapshot snapshot = adapter.loadEffective(6L);

        Assert.assertTrue(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "RULE".equals(
                        ref.targetResourceType())
                        && Long.valueOf(77L).equals(
                        ref.targetResourceId())));
        Assert.assertFalse(adapter.collectDependencies(snapshot).stream()
                .anyMatch(ref -> "EXPERIMENT".equals(
                        ref.targetResourceType())
                        && Long.valueOf(6L).equals(
                        ref.targetResourceId())));
        Assert.assertTrue(adapter.validate(snapshot).isEmpty());
    }

    @Test
    public void ruleVersionContainsDesignerContentAndFieldContract() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(9L);
        definition.setRuleCode("R001");
        definition.setRuleName("准入规则");
        definition.setModelType("DECISION_TABLE");
        definition.setStatus(1);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(9L);
        content.setModelJson("{\"rows\":[{\"id\":1}]}");
        RuleDefinitionInputField input =
                new RuleDefinitionInputField();
        input.setDefinitionId(9L);
        input.setVarId(3L);
        input.setRefType("VARIABLE");
        RuleDefinitionContentMapper contentMapper =
                mapper(RuleDefinitionContentMapper.class,
                        Map.of("selectOne", content),
                        new ArrayList<>());
        RuleDefinitionInputFieldMapper inputMapper =
                mapper(RuleDefinitionInputFieldMapper.class,
                        Map.of("selectList", List.of(input)),
                        new ArrayList<>());
        RuleDefinitionOutputFieldMapper outputMapper =
                mapper(RuleDefinitionOutputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleGovernedResourceAdapter adapter =
                new RuleGovernedResourceAdapter(
                        store(definition, RuleDefinition::setId),
                        codec(), new RuleLifecycleService(),
                        null,
                        contentMapper, inputMapper, outputMapper);

        ResourceSnapshot version = adapter.loadEffective(9L);
        Map<String, Object> snapshot = CanonicalJson.readMap(
                version.snapshotJson());

        Assert.assertTrue(CanonicalJson.write(snapshot.get("content"))
                .contains("rows"));
        Assert.assertEquals(1,
                ((List<?>) snapshot.get("inputFieldsJson")).size());
        Assert.assertFalse(adapter.collectDependencies(version).stream()
                .anyMatch(ref -> "RULE".equals(
                        ref.targetResourceType())
                        && Long.valueOf(9L).equals(
                        ref.targetResourceId())));
        Assert.assertTrue(adapter.collectDependencies(version).stream()
                .anyMatch(ref -> "VARIABLE".equals(
                        ref.targetResourceType())
                        && Long.valueOf(3L).equals(
                        ref.targetResourceId())));
    }

    @Test
    public void ruleRejectionKeepsOfflineEffectiveStatus() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(9L);
        definition.setRuleCode("R001");
        definition.setRuleName("准入规则");
        definition.setModelType("SCRIPT");
        definition.setStatus(2);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(9L);
        content.setModelJson("{\"script\":\"effective = 1\"}");
        RuleDefinitionContentMapper contentMapper =
                mapper(RuleDefinitionContentMapper.class,
                        Map.of("selectOne", content),
                        new ArrayList<>());
        RuleDefinitionInputFieldMapper inputMapper =
                mapper(RuleDefinitionInputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleDefinitionOutputFieldMapper outputMapper =
                mapper(RuleDefinitionOutputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleRevision review = new RuleRevision();
        review.setId(8L);
        review.setDefinitionId(9L);
        review.setState("REVIEW");
        boolean[] rejected = {false};
        RuleLifecycleService lifecycle =
                new RuleLifecycleService() {
                    @Override
                    public List<RuleRevision> listRevisions(
                            Long definitionId) {
                        return List.of(review);
                    }

                    @Override
                    public RuleRevision returnToDraft(
                            Long revisionId,
                            RuleLifecycleActionRequest request) {
                        rejected[0] = true;
                        review.setState("REJECTED");
                        return review;
                    }
                };
        RuleGovernedResourceAdapter adapter =
                new RuleGovernedResourceAdapter(
                        store(definition, RuleDefinition::setId),
                        codec(), lifecycle, null,
                        contentMapper, inputMapper, outputMapper);
        ResourceSnapshot effective = adapter.loadEffective(9L);

        adapter.onApprovalTerminated(
                9L, effective, "alice", "需要修改", "REJECTED");

        Assert.assertTrue(rejected[0]);
        Assert.assertEquals(2, ((Number) CanonicalJson.readMap(
                adapter.loadEffective(9L).snapshotJson())
                .get("status")).intValue());
    }

    @Test
    public void ruleRestoreBuildsNewRevisionFromHistoricalSnapshot() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(9L);
        definition.setRuleCode("R001");
        definition.setRuleName("准入规则");
        definition.setModelType("SCRIPT");
        definition.setStatus(1);
        RuleDefinitionContent current = new RuleDefinitionContent();
        current.setDefinitionId(9L);
        current.setModelJson("{\"script\":\"current = 1\"}");
        RuleDefinitionContentMapper contentMapper =
                mapper(RuleDefinitionContentMapper.class,
                        Map.of("selectOne", current),
                        new ArrayList<>());
        RuleDefinitionInputFieldMapper inputMapper =
                mapper(RuleDefinitionInputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleDefinitionOutputFieldMapper outputMapper =
                mapper(RuleDefinitionOutputFieldMapper.class,
                        Map.of("selectList", List.of()),
                        new ArrayList<>());
        RuleRevision offline = new RuleRevision();
        offline.setId(6L);
        offline.setDefinitionId(9L);
        offline.setRevisionNo(4);
        offline.setState("OFFLINE");
        RuleRevision draft = new RuleRevision();
        draft.setId(7L);
        draft.setDefinitionId(9L);
        draft.setRevisionNo(5);
        draft.setState("DRAFT");
        draft.setLockVersion(0);
        Long[] selectedBase = {null};
        RuleLifecycleService lifecycle =
                new RuleLifecycleService() {
                    @Override
                    public List<RuleRevision> listRevisions(
                            Long definitionId) {
                        return List.of(offline);
                    }

                    @Override
                    public RuleRevision createDraft(
                            Long definitionId,
                            Long baseRevisionId) {
                        selectedBase[0] = baseRevisionId;
                        return draft;
                    }

                    @Override
                    public RuleRevision submit(
                            Long revisionId,
                            RuleLifecycleActionRequest request) {
                        draft.setState("REVIEW");
                        return draft;
                    }

                    @Override
                    public RuleRevision approve(
                            Long revisionId,
                            RuleLifecycleActionRequest request) {
                        draft.setState("APPROVED");
                        draft.setArtifactId(99L);
                        return draft;
                    }

                    @Override
                    public RuleRevision publish(
                            Long revisionId,
                            RuleLifecycleActionRequest request) {
                        draft.setState("PUBLISHED");
                        return draft;
                    }
                };
        RuleDraftSaveRequest[] saved = {null};
        RuleDraftService draftService = new RuleDraftService() {
            @Override
            public RuleDraftSaveResponse save(
                    RuleDraftSaveRequest request) {
                saved[0] = request;
                draft.setModelJson(request.getModelJson());
                RuleDraftSaveResponse response =
                        new RuleDraftSaveResponse();
                response.setRevision(draft);
                return response;
            }
        };
        RuleGovernedResourceAdapter adapter =
                new RuleGovernedResourceAdapter(
                        store(definition, RuleDefinition::setId),
                        codec(), lifecycle, draftService,
                        contentMapper, inputMapper, outputMapper);
        ResourceSnapshot historical =
                ResourceSnapshot.ofJson(
                        "{\"id\":9,\"ruleCode\":\"R001\","
                                + "\"ruleName\":\"准入规则\","
                                + "\"modelType\":\"SCRIPT\","
                                + "\"content\":{\"modelJson\":"
                                + "\"{\\\"script\\\":"
                                + "\\\"historical = 1\\\"}\"},"
                                + "\"inputFieldsJson\":[],"
                                + "\"outputFieldsJson\":[]}");

        AppliedResource applied = adapter.apply(
                new ApprovalApplyContext(
                        41L, 9L, 3, "RESTORE",
                        historical, "alice", 1L));

        Assert.assertEquals(Long.valueOf(6L), selectedBase[0]);
        Assert.assertEquals("{\"script\":\"historical = 1\"}",
                saved[0].getModelJson());
        Assert.assertEquals("PUBLISHED", draft.getState());
        Assert.assertEquals(Long.valueOf(99L),
                applied.artifactId());
    }

    private RuleVariable variable() {
        RuleVariable variable = new RuleVariable();
        variable.setId(7L);
        variable.setVarCode("grade");
        variable.setVarLabel("等级");
        variable.setVarType("ENUM");
        variable.setStatus(1);
        return variable;
    }

    private <T> SimpleEntityGovernedResourceAdapter.EntityStore<T> store(
            T initial, IdSetter<T> idSetter) {
        return new SimpleEntityGovernedResourceAdapter.EntityStore<>() {
            private T value = initial;

            @Override
            public T load(Long id) {
                return value;
            }

            @Override
            public void insert(T entity) {
                idSetter.set(entity, 100L);
                value = entity;
            }

            @Override
            public void update(T entity) {
                value = entity;
            }
        };
    }

    private GovernanceSecretCodec codec() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setActiveKeyId("test");
        properties.setMasterKeys(Map.of(
                "test",
                "test-governance-master-key-32-characters-long"));
        return new GovernanceSecretCodec(
                new CredentialCipher(properties));
    }

    @SuppressWarnings("unchecked")
    private <T> T mapper(Class<T> type,
                         Map<String, Object> returns,
                         List<Object> inserted) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())
                            && args != null && args.length > 0) {
                        inserted.add(args[0]);
                        return 1;
                    }
                    if (returns.containsKey(method.getName())) {
                        return returns.get(method.getName());
                    }
                    Class<?> resultType = method.getReturnType();
                    if (resultType == int.class) {
                        return 1;
                    }
                    if (resultType == long.class) {
                        return 0L;
                    }
                    if (resultType == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private interface IdSetter<T> {
        void set(T value, Long id);
    }
}
