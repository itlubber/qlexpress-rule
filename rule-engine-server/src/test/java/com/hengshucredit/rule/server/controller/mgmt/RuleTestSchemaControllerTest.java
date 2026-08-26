package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.security.RequirePermission;
import org.junit.Assert;
import org.junit.Test;

public class RuleTestSchemaControllerTest {

    @Test
    public void buildRequiresRuleViewPermission() throws Exception {
        RequirePermission permission = RuleTestSchemaController.class
                .getDeclaredMethod("build",
                        com.hengshucredit.rule.model.dto.RuleTestSchemaRequest.class)
                .getAnnotation(RequirePermission.class);

        Assert.assertNotNull(permission);
        Assert.assertEquals("rule:view", permission.value());
    }
}
