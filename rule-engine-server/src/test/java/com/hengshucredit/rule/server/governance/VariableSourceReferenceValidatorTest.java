package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VariableSourceReferenceValidatorTest {

    private VariableSourceReferenceValidator validator;

    @Before
    public void setUp() {
        RuleExternalDatasource globalApiSource = apiSource(
                1L, 0L, "GLOBAL", 1);
        RuleExternalDatasource projectApiSource = apiSource(
                2L, 7L, "PROJECT", 1);
        RuleExternalDatasource foreignApiSource = apiSource(
                3L, 8L, "PROJECT", 1);
        RuleExternalApiConfig globalApi = api(10L, 1L, 1);
        RuleExternalApiConfig projectApi = api(11L, 2L, 1);
        RuleExternalApiConfig foreignApi = api(12L, 3L, 1);
        RuleDbDatasource globalDb = db(20L, 0L, "GLOBAL", 1);
        RuleDbDatasource projectDb = db(21L, 7L, "PROJECT", 1);
        RuleDbDatasource disabledDb = db(22L, 7L, "PROJECT", 0);
        RuleListLibrary globalList = list(30L, 0L, "GLOBAL", 1);
        RuleListLibrary projectList = list(31L, 7L, "PROJECT", 1);
        RuleListLibrary foreignList = list(32L, 8L, "PROJECT", 1);

        validator = new VariableSourceReferenceValidator();
        ReflectionTestUtils.setField(validator, "apiConfigMapper",
                mapper(RuleExternalApiConfigMapper.class,
                        List.of(globalApi, projectApi, foreignApi)));
        ReflectionTestUtils.setField(validator, "externalDatasourceMapper",
                mapper(RuleExternalDatasourceMapper.class,
                        List.of(globalApiSource, projectApiSource,
                                foreignApiSource)));
        ReflectionTestUtils.setField(validator, "dbDatasourceMapper",
                mapper(RuleDbDatasourceMapper.class,
                        List.of(globalDb, projectDb, disabledDb)));
        ReflectionTestUtils.setField(validator, "listLibraryMapper",
                mapper(RuleListLibraryMapper.class,
                        List.of(globalList, projectList, foreignList)));
    }

    @Test
    public void projectCatalogIncludesGlobalAndCurrentProjectOnly() {
        VariableSourceCatalog catalog = validator.catalog(
                "PROJECT", 7L);

        Assert.assertEquals(List.of(10L, 11L), catalog.apiOptions()
                .stream().map(VariableSourceOption::id).toList());
        Assert.assertEquals(List.of(20L, 21L), catalog.databaseOptions()
                .stream().map(VariableSourceOption::id).toList());
        Assert.assertEquals(List.of(30L, 31L), catalog.listOptions()
                .stream().map(VariableSourceOption::id).toList());
    }

    @Test
    public void globalCatalogExcludesProjectResources() {
        VariableSourceCatalog catalog = validator.catalog(
                "GLOBAL", 0L);

        Assert.assertEquals(List.of(10L), catalog.apiOptions()
                .stream().map(VariableSourceOption::id).toList());
        Assert.assertEquals(List.of(20L), catalog.databaseOptions()
                .stream().map(VariableSourceOption::id).toList());
        Assert.assertEquals(List.of(30L), catalog.listOptions()
                .stream().map(VariableSourceOption::id).toList());
    }

    @Test
    public void validationRejectsCrossProjectAndDisabledSources() {
        RuleVariable listVariable = variable("LIST",
                "{\"listIds\":[31,32]}");
        RuleVariable dbVariable = variable("DB",
                "{\"datasourceId\":22,\"sql\":\"select 1\"}");

        List<GovernanceIssue> listIssues = validator.validate(listVariable);
        List<GovernanceIssue> dbIssues = validator.validate(dbVariable);

        Assert.assertTrue(listIssues.stream().anyMatch(issue ->
                "VARIABLE_SOURCE_SCOPE_MISMATCH".equals(issue.code())));
        Assert.assertTrue(dbIssues.stream().anyMatch(issue ->
                "VARIABLE_SOURCE_DISABLED".equals(issue.code())));
    }

    @Test
    public void validationRejectsMalformedSourceConfiguration() {
        RuleVariable variable = variable("API", "not-json");

        List<GovernanceIssue> issues = validator.validate(variable);

        Assert.assertTrue(issues.stream().anyMatch(issue ->
                "VARIABLE_SOURCE_CONFIG_INVALID".equals(issue.code())));
    }

    @Test
    public void validationReportsMalformedListReferenceWithoutThrowing() {
        RuleVariable variable = variable("LIST",
                "{\"listIds\":[\"not-a-number\"]}");

        List<GovernanceIssue> issues = validator.validate(variable);

        Assert.assertTrue(issues.stream().anyMatch(issue ->
                "VARIABLE_SOURCE_REFERENCE_INVALID".equals(issue.code())
                        && "$.sourceConfig.listIds[0]".equals(
                        issue.referencePath())));
    }

    private RuleVariable variable(String source, String sourceConfig) {
        RuleVariable variable = new RuleVariable();
        variable.setId(91L);
        variable.setScope("PROJECT");
        variable.setProjectId(7L);
        variable.setVarSource(source);
        variable.setSourceConfig(sourceConfig);
        return variable;
    }

    private RuleExternalDatasource apiSource(Long id, Long projectId,
                                             String scope, int status) {
        RuleExternalDatasource value = new RuleExternalDatasource();
        value.setId(id);
        value.setProjectId(projectId);
        value.setScope(scope);
        value.setDatasourceCode("api-source-" + id);
        value.setDatasourceName("外数源" + id);
        value.setStatus(status);
        return value;
    }

    private RuleExternalApiConfig api(Long id, Long datasourceId,
                                      int status) {
        RuleExternalApiConfig value = new RuleExternalApiConfig();
        value.setId(id);
        value.setDatasourceId(datasourceId);
        value.setApiCode("api-" + id);
        value.setApiName("接口" + id);
        value.setStatus(status);
        return value;
    }

    private RuleDbDatasource db(Long id, Long projectId,
                                String scope, int status) {
        RuleDbDatasource value = new RuleDbDatasource();
        value.setId(id);
        value.setProjectId(projectId);
        value.setScope(scope);
        value.setDatasourceCode("db-" + id);
        value.setDatasourceName("数据库" + id);
        value.setStatus(status);
        return value;
    }

    private RuleListLibrary list(Long id, Long projectId,
                                 String scope, int status) {
        RuleListLibrary value = new RuleListLibrary();
        value.setId(id);
        value.setProjectId(projectId);
        value.setScope(scope);
        value.setListCode("list-" + id);
        value.setListName("名单" + id);
        value.setStatus(status);
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T mapper(Class<T> mapperType, List<?> values) {
        Map<Object, Object> byId = new LinkedHashMap<>();
        for (Object value : values) {
            Object id = ReflectionTestUtils.getField(value, "id");
            byId.put(id, value);
        }
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(), new Class<?>[]{mapperType},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return byId.get(args[0]);
                    }
                    if ("selectList".equals(method.getName())) {
                        return values;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }
                    return null;
                });
    }
}
