package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.security.RequirePermission;
import org.junit.Assert;
import org.junit.Test;

public class GovernanceApprovalControllerTest {

    @Test
    public void reviewEndpointsRequireApprovePermission() throws Exception {
        RequirePermission approve = GovernanceApprovalController.class
                .getMethod("approve", Long.class,
                        com.hengshucredit.rule.model.dto
                                .GovernanceReviewRequest.class)
                .getAnnotation(RequirePermission.class);
        RequirePermission reject = GovernanceApprovalController.class
                .getMethod("reject", Long.class,
                        com.hengshucredit.rule.model.dto
                                .GovernanceReviewRequest.class)
                .getAnnotation(RequirePermission.class);

        Assert.assertEquals("approval:approve", approve.value());
        Assert.assertEquals("approval:approve", reject.value());
    }

    @Test
    public void listAndDetailRequireViewPermission() throws Exception {
        RequirePermission list = GovernanceApprovalController.class
                .getMethod("requests",
                        com.hengshucredit.rule.model.dto
                                .GovernanceApprovalQuery.class)
                .getAnnotation(RequirePermission.class);
        RequirePermission detail = GovernanceApprovalController.class
                .getMethod("detail", Long.class)
                .getAnnotation(RequirePermission.class);

        Assert.assertEquals("approval:view", list.value());
        Assert.assertEquals("approval:view", detail.value());
    }
}
