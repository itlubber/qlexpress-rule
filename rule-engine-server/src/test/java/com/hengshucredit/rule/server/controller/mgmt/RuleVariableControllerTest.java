package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.VariableSourcePreviewRequest;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.governance.GovernanceIssue;
import com.hengshucredit.rule.server.governance.VariableSourceCatalog;
import com.hengshucredit.rule.server.governance.VariableSourceReferenceValidator;
import com.hengshucredit.rule.server.service.VariableSourceResolver;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

public class RuleVariableControllerTest {

    @Test
    public void sourceCatalogAndDraftPreviewUseServerValidation() {
        TrackingValidator validator = new TrackingValidator();
        TrackingResolver resolver = new TrackingResolver();
        RuleVariableController controller = new RuleVariableController();
        ReflectionTestUtils.setField(controller,
                "sourceReferenceValidator", validator);
        ReflectionTestUtils.setField(controller,
                "variableSourceResolver", resolver);
        RuleVariable variable = new RuleVariable();
        variable.setVarCode("riskScore");
        variable.setVarSource("API");
        VariableSourcePreviewRequest request =
                new VariableSourcePreviewRequest();
        request.setVariable(variable);
        request.setParams(Map.of("requestId", "R001"));

        controller.sourceOptions("PROJECT", 9L);
        Map<String, Object> result = controller.previewVariable(request)
                .getData();

        Assert.assertEquals("PROJECT", validator.scope);
        Assert.assertEquals(Long.valueOf(9L), validator.projectId);
        Assert.assertSame(variable, validator.validated);
        Assert.assertSame(variable, resolver.previewed);
        Assert.assertEquals("R001", result.get("requestId"));
    }

    private static class TrackingValidator
            extends VariableSourceReferenceValidator {
        private String scope;
        private Long projectId;
        private RuleVariable validated;

        @Override
        public VariableSourceCatalog catalog(
                String value, Long project) {
            scope = value;
            projectId = project;
            return new VariableSourceCatalog(
                    List.of(), List.of(), List.of());
        }

        @Override
        public List<GovernanceIssue> validate(RuleVariable variable) {
            validated = variable;
            return List.of();
        }

        @Override
        public void validateOrThrow(RuleVariable variable) {
            validated = variable;
        }
    }

    private static class TrackingResolver
            extends VariableSourceResolver {
        private RuleVariable previewed;

        @Override
        public Map<String, Object> previewVariable(
                RuleVariable variable, Map<String, Object> params) {
            previewed = variable;
            return Map.of("requestId", params.get("requestId"));
        }
    }
}
