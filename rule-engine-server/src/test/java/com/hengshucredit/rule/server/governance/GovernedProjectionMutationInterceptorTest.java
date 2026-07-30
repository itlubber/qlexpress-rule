package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.controller.RuleModelController;
import com.hengshucredit.rule.server.controller.mgmt.DbDatasourceController;
import com.hengshucredit.rule.server.controller.mgmt.ExternalDatasourceController;
import com.hengshucredit.rule.server.controller.mgmt.RuleDataObjectController;
import com.hengshucredit.rule.server.controller.mgmt.RuleDefinitionController;
import com.hengshucredit.rule.server.controller.mgmt.RuleExperimentController;
import com.hengshucredit.rule.server.controller.mgmt.RuleFunctionController;
import com.hengshucredit.rule.server.controller.mgmt.RuleProjectController;
import com.hengshucredit.rule.server.controller.mgmt.RuleVariableController;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Arrays;

public class GovernedProjectionMutationInterceptorTest {

    @Test
    public void directProjectionMutationIsRejectedWithStableConflict() throws Exception {
        GovernedProjectionMutationInterceptor interceptor =
                new GovernedProjectionMutationInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("PUT", "/api/rule/model/7"),
                response,
                handler("directMutation"));

        Assert.assertFalse(allowed);
        Assert.assertEquals(409, response.getStatus());
        Assert.assertTrue(response.getContentAsString()
                .contains("GOVERNANCE_APPROVAL_REQUIRED"));
    }

    @Test
    public void ordinaryAndGovernanceEndpointsRemainAvailable() throws Exception {
        GovernedProjectionMutationInterceptor interceptor =
                new GovernedProjectionMutationInterceptor();

        Assert.assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/rule/model/7"),
                new MockHttpServletResponse(),
                handler("read")));
        Assert.assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/rule/governance/drafts"),
                new MockHttpServletResponse(),
                handler("read")));
    }

    @Test
    public void everyLegacyEffectiveProjectionWriterIsGuarded() {
        assertGuarded(RuleProjectController.class,
                "create", "update", "delete");
        assertGuarded(RuleVariableController.class,
                "create", "update", "toGlobal", "delete", "saveOptions");
        assertGuarded(RuleDataObjectController.class,
                "createOrUpdate", "update", "toGlobal", "updateType",
                "updateScriptName", "delete", "createField",
                "updateField", "deleteField", "saveFieldOptions");
        assertGuarded(DbDatasourceController.class,
                "create", "update", "delete");
        assertGuarded(ExternalDatasourceController.class,
                "create", "update", "delete", "createApiConfig",
                "updateApiConfig", "deleteApiConfig");
        assertGuarded(RuleFunctionController.class,
                "create", "update", "delete");
        assertGuarded(RuleExperimentController.class,
                "save", "delete");
        assertGuarded(RuleDefinitionController.class, "delete");
        assertGuarded(RuleModelController.class,
                "update", "delete", "publish", "unpublish",
                "saveTestParams", "updateInputField", "updateOutputField");
    }

    @Test
    public void lifecycleImportEndpointsAreNotBlockedAsLegacyWriters() {
        assertNotGuarded(RuleVariableController.class,
                "importConstantsJava", "importConstantsJson");
        assertNotGuarded(RuleDataObjectController.class,
                "importJava", "importJavaFile", "importJson", "importDdl");
    }

    private static void assertGuarded(
            Class<?> controller, String... methodNames) {
        for (String methodName : methodNames) {
            Method method = Arrays.stream(controller.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            controller.getSimpleName() + "." + methodName
                                    + " not found"));
            Assert.assertNotNull(
                    controller.getSimpleName() + "." + methodName
                            + " must require governance approval",
                    method.getAnnotation(
                            GovernedProjectionMutation.class));
        }
    }

    private static void assertNotGuarded(
            Class<?> controller, String... methodNames) {
        for (String methodName : methodNames) {
            Method method = Arrays.stream(controller.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            Assert.assertNull(method.getAnnotation(
                    GovernedProjectionMutation.class));
        }
    }

    private static HandlerMethod handler(String methodName) throws Exception {
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new TestController(), method);
    }

    private static class TestController {
        @GovernedProjectionMutation
        public void directMutation() {
        }

        public void read() {
        }
    }
}
