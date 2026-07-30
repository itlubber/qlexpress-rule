package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import org.junit.Assert;
import org.junit.Test;

public class GovernanceApprovalRequestMappingTest {

    @Test
    public void terminalRequestCanReleaseActiveResourceKey() throws Exception {
        TableField mapping = GovernanceApprovalRequest.class
                .getDeclaredField("activeResourceKey")
                .getAnnotation(TableField.class);

        Assert.assertNotNull(mapping);
        Assert.assertEquals(FieldStrategy.ALWAYS, mapping.updateStrategy());
    }
}
