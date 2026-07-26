package com.hengshucredit.rule.server.controller.mgmt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleRevisionRepairRequest;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.common.GlobalExceptionHandler;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleDraftService;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import com.hengshucredit.rule.server.service.RuleRevisionRepairService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RuleDefinitionControllerTest {
    private static final String PREVIEW_DIGEST =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    public void normalizeModelJsonUnquotesJsonStringRequestBody() {
        RuleDefinitionController controller = new RuleDefinitionController();

        String normalized = ReflectionTestUtils.invokeMethod(
                controller, "normalizeModelJson", "\"{\\\"nodes\\\":[],\\\"edges\\\":[]}\"");

        assertEquals("{\"nodes\":[],\"edges\":[]}", normalized);
    }

    @Test
    public void lifecycleEndpointsValidateDefinitionScopeAndReturnBusinessCodes() {
        RuleDefinitionController controller = new RuleDefinitionController();
        ReflectionTestUtils.setField(controller, "lifecycleService", new RuleLifecycleService() {
            @Override
            public RuleRevision getRevision(Long definitionId, Long revisionId) {
                if (!Long.valueOf(10L).equals(definitionId)) {
                    throw new IllegalArgumentException("规则修订不存在");
                }
                RuleRevision revision = new RuleRevision();
                revision.setId(revisionId);
                revision.setDefinitionId(definitionId);
                revision.setState("DRAFT");
                return revision;
            }

            @Override
            public RuleRevision submit(Long revisionId, RuleLifecycleActionRequest request) {
                RuleRevision revision = new RuleRevision();
                revision.setId(revisionId);
                revision.setDefinitionId(10L);
                revision.setState("REVIEW");
                return revision;
            }

            @Override
            public RuleRevision approve(Long revisionId, RuleLifecycleActionRequest request) {
                throw new IllegalStateException("发布前验证未通过: unresolved model");
            }
        });

        R<RuleRevision> submitted = controller.submitRevision(10L, 3L, null);
        R<RuleRevision> wrongDefinition = controller.submitRevision(11L, 3L, null);
        R<RuleRevision> invalidApproval = controller.approveRevision(10L, 3L, null);

        assertEquals(200, submitted.getCode());
        assertEquals("REVIEW", submitted.getData().getState());
        assertEquals(400, wrongDefinition.getCode());
        assertEquals(422, invalidApproval.getCode());
    }

    @Test
    public void preflightReturnsReportWith422WhenHardErrorsExist() {
        RuleDefinitionController controller = new RuleDefinitionController();
        ReflectionTestUtils.setField(controller, "lifecycleService", new RuleLifecycleService() {
            @Override
            public RulePreflightReport preflightReport(Long definitionId, Long revisionId) {
                RulePreflightReport report = new RulePreflightReport();
                report.setRevisionId(revisionId);
                report.setValid(false);
                return report;
            }
        });

        R<RulePreflightReport> response = controller.preflight(10L, 3L);

        assertEquals(422, response.getCode());
        assertEquals(Long.valueOf(3L), response.getData().getRevisionId());
    }

    @Test
    public void saveReturnsSavedRevisionAndCompileDiagnostics() throws Exception {
        RuleDefinitionController controller = new RuleDefinitionController();
        ReflectionTestUtils.setField(controller, "ruleDraftService",
                new RuleDraftService() {
                    @Override
                    public RuleDraftSaveResponse save(RuleDraftSaveRequest request) {
                        RuleRevision revision = new RuleRevision();
                        revision.setId(request.getRevisionId());
                        revision.setDefinitionId(request.getDefinitionId());
                        revision.setState("DRAFT");
                        revision.setLockVersion(request.getLockVersion() + 1);
                        RuleDraftSaveResponse response =
                                new RuleDraftSaveResponse();
                        response.setRevision(revision);
                        response.setCompileSuccess(false);
                        response.setCompileMessage("QL_PARSE_ERROR");
                        response.setIssues(Collections.singletonList(
                                RuleValidationIssue.error("QL_PARSE_ERROR",
                                        "$.script", "invalid QL")));
                        return response;
                    }
                });
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        MvcResult result = mockMvc.perform(post("/api/rule/definition/save")
                        .contentType("application/json")
                        .content("{\"definitionId\":30,\"revisionId\":6,"
                                + "\"lockVersion\":0,"
                                + "\"modelJson\":\"{\\\"script\\\":"
                                + "\\\"_result = {\\\"}\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject body = JSON.parseObject(
                result.getResponse().getContentAsString());
        JSONObject data = body.getJSONObject("data");

        assertEquals(200, body.getIntValue("code"));
        assertEquals(6L, data.getJSONObject("revision").getLongValue("id"));
        assertEquals(1, data.getJSONObject("revision")
                .getIntValue("lockVersion"));
        assertEquals(false, data.getBooleanValue("compileSuccess"));
        assertEquals("QL_PARSE_ERROR", data.getJSONArray("issues")
                .getJSONObject(0).getString("code"));
    }

    @Test
    public void governanceFailureKeepsHttpBusinessAndErrorCodesAligned()
            throws Exception {
        RuleDefinitionController controller = new RuleDefinitionController();
        ReflectionTestUtils.setField(controller, "lifecycleService",
                new RuleLifecycleService() {
                    @Override
                    public RuleRevision createDraft(Long definitionId,
                                                    Long baseRevisionId) {
                        throw new RuleGovernanceException(
                                409, "DRAFT_BASE_MISMATCH",
                                "已有草稿基线与请求不一致",
                                Collections.singletonList(
                                        RuleValidationIssue.error(
                                                "DRAFT_BASE_MISMATCH", "$",
                                                "base revision mismatch")));
                    }
                });
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        MvcResult result = mockMvc.perform(post(
                        "/api/rule/definition/30/revisions/draft")
                        .contentType("application/json")
                        .content("{\"baseRevisionId\":6}"))
                .andExpect(status().isConflict())
                .andReturn();
        JSONObject body = JSON.parseObject(
                result.getResponse().getContentAsString());
        JSONObject data = body.getJSONObject("data");

        assertEquals(409, body.getIntValue("code"));
        assertEquals("DRAFT_BASE_MISMATCH",
                data.getString("errorCode"));
        assertEquals("DRAFT_BASE_MISMATCH", data.getJSONArray("issues")
                .getJSONObject(0).getString("code"));
    }

    @Test
    public void nullSaveBodyReturnsStructuredBadRequest() throws Exception {
        RuleDefinitionController controller = new RuleDefinitionController();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        MvcResult result = mockMvc.perform(post("/api/rule/definition/save")
                        .contentType("application/json")
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andReturn();
        JSONObject body = JSON.parseObject(
                result.getResponse().getContentAsString());
        JSONObject data = body.getJSONObject("data");

        assertEquals(400, body.getIntValue("code"));
        assertEquals("DRAFT_SAVE_REQUEST_REQUIRED",
                data.getString("errorCode"));
        assertEquals("DRAFT_SAVE_REQUEST_REQUIRED",
                data.getJSONArray("issues").getJSONObject(0)
                        .getString("code"));
    }

    @Test
    public void refreshFieldsUsesCompleteAtomicSaveRequest() throws Exception {
        RuleDefinitionController controller = new RuleDefinitionController();
        final RuleDraftSaveRequest[] captured = {null};
        ReflectionTestUtils.setField(controller, "definitionService",
                new RuleDefinitionService() {
                    @Override
                    public RuleDraftSaveResponse refreshFields(
                            RuleDraftSaveRequest request) {
                        captured[0] = request;
                        RuleDraftSaveResponse response =
                                new RuleDraftSaveResponse();
                        response.setCompileSuccess(true);
                        return response;
                    }
                });
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(post(
                        "/api/rule/definition/refreshFields/30")
                        .contentType("application/json")
                        .content("{\"definitionId\":30,\"revisionId\":6,"
                                + "\"lockVersion\":2,"
                                + "\"modelJson\":\"{\\\"script\\\":"
                                + "\\\"score = 1\\\"}\"}"))
                .andExpect(status().isOk());

        assertEquals(Long.valueOf(30L), captured[0].getDefinitionId());
        assertEquals(Long.valueOf(6L), captured[0].getRevisionId());
        assertEquals(Integer.valueOf(2), captured[0].getLockVersion());
    }

    @Test
    public void reviewBlocksDraftCreationWithStructuredConflict()
            throws Exception {
        RuleDefinitionController controller = new RuleDefinitionController();
        ReflectionTestUtils.setField(controller, "lifecycleService",
                new RuleLifecycleService() {
                    @Override
                    public RuleRevision createDraft(Long definitionId,
                                                    Long baseRevisionId) {
                        throw new RuleGovernanceException(
                                409, "DRAFT_CREATION_BLOCKED",
                                "规则存在 REVIEW 修订",
                                Collections.singletonList(
                                        RuleValidationIssue.error(
                                                "DRAFT_CREATION_BLOCKED", "$",
                                                "review blocks draft")));
                    }
                });
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        MvcResult result = mockMvc.perform(post(
                        "/api/rule/definition/30/revisions/draft")
                        .contentType("application/json")
                        .content("{\"baseRevisionId\":6}"))
                .andExpect(status().isConflict())
                .andReturn();
        JSONObject body = JSON.parseObject(
                result.getResponse().getContentAsString());
        JSONObject data = body.getJSONObject("data");

        assertEquals(409, body.getIntValue("code"));
        assertEquals("DRAFT_CREATION_BLOCKED",
                data.getString("errorCode"));
        assertEquals("DRAFT_CREATION_BLOCKED",
                data.getJSONArray("issues").getJSONObject(0)
                        .getString("code"));
    }

    @Test
    public void repairPreviewEndpointReturnsStableIdPlan()
            throws Exception {
        RuleDefinitionController controller =
                new RuleDefinitionController();
        ReflectionTestUtils.setField(controller,
                "revisionRepairService",
                new RuleRevisionRepairService() {
                    @Override
                    public RepairPreview preview(Long definitionId) {
                        return new RepairPreview(
                                6L,
                                Collections.singletonList(
                                        "VARIABLE:302"),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                true, true,
                                PREVIEW_DIGEST);
                    }
                });
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler()).build();

        MvcResult result = mockMvc.perform(get(
                        "/api/rule/definition/30/revisions/"
                                + "repair-preview"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject body = JSON.parseObject(
                result.getResponse().getContentAsString());

        assertEquals(200, body.getIntValue("code"));
        assertEquals(6L, body.getJSONObject("data")
                .getLongValue("sourceRevisionId"));
        assertEquals("VARIABLE:302", body.getJSONObject("data")
                .getJSONArray("recoverableReferenceKeys")
                .getString(0));
    }

    @Test
    public void changedRepairPreviewReturnsHttpConflict()
            throws Exception {
        RuleDefinitionController controller =
                new RuleDefinitionController();
        ReflectionTestUtils.setField(controller,
                "revisionRepairService",
                new RuleRevisionRepairService() {
                    @Override
                    public RuleRevision repair(
                            Long definitionId,
                            RuleRevisionRepairRequest request) {
                        throw new RepairPreviewChangedException(
                                "修复预览已变化，请重新预览");
                    }
                });
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler()).build();

        MvcResult result = mockMvc.perform(post(
                        "/api/rule/definition/30/revisions/repair")
                        .contentType("application/json")
                        .content("{\"sourceRevisionId\":6,"
                                + "\"previewDigest\":\""
                                + PREVIEW_DIGEST + "\"}"))
                .andExpect(status().isConflict())
                .andReturn();
        JSONObject body = JSON.parseObject(
                result.getResponse().getContentAsString());

        assertEquals(409, body.getIntValue("code"));
        assertEquals("REPAIR_PREVIEW_CHANGED",
                body.getJSONObject("data")
                        .getString("errorCode"));
    }

    @Test
    public void unresolvedRepairInputsReturnHttpUnprocessableEntity()
            throws Exception {
        RuleDefinitionController controller =
                new RuleDefinitionController();
        ReflectionTestUtils.setField(controller,
                "revisionRepairService",
                new RuleRevisionRepairService() {
                    @Override
                    public RuleRevision repair(
                            Long definitionId,
                            RuleRevisionRepairRequest request) {
                        throw new RuleGovernanceException(
                                422,
                                "SCRIPT_INPUT_REF_MISSING",
                                "历史稳定 ID 无法覆盖全部脚本输入",
                                Collections.singletonList(
                                        RuleValidationIssue.error(
                                                "SCRIPT_INPUT_REF_MISSING",
                                                "$.script.input",
                                                "输入缺少稳定 ID")));
                    }
                });
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler()).build();

        MvcResult result = mockMvc.perform(post(
                        "/api/rule/definition/30/revisions/repair")
                        .contentType("application/json")
                        .content("{\"sourceRevisionId\":6,"
                                + "\"previewDigest\":\""
                                + PREVIEW_DIGEST + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        JSONObject body = JSON.parseObject(
                result.getResponse().getContentAsString());

        assertEquals(422, body.getIntValue("code"));
        assertEquals("SCRIPT_INPUT_REF_MISSING",
                body.getJSONObject("data")
                        .getString("errorCode"));
        assertEquals("SCRIPT_INPUT_REF_MISSING",
                body.getJSONObject("data")
                        .getJSONArray("issues").getJSONObject(0)
                        .getString("code"));
    }
}
