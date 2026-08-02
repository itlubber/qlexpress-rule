package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleListRecordChangeRequest;
import com.hengshucredit.rule.server.security.RequirePermission;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Arrays;

public class RuleListControllerTest {

    @Test
    public void recordWritesExposeOnlyApprovalBatchEndpoints()
            throws Exception {
        Method single = RuleListController.class.getMethod(
                "stageRecordChange", Long.class,
                RuleListRecordChangeRequest.class);
        Method imported = RuleListController.class.getMethod(
                "importRecords", Long.class, MultipartFile.class);

        Assert.assertArrayEquals(
                new String[]{"/{listId:\\d+}/change-batch"},
                single.getAnnotation(PostMapping.class).value());
        Assert.assertEquals("approval:submit",
                single.getAnnotation(RequirePermission.class).value());
        Assert.assertEquals("approval:submit",
                imported.getAnnotation(RequirePermission.class).value());
        for (String removed : new String[]{
                "createRecord", "updateRecord", "deleteRecord"}) {
            Assert.assertFalse(Arrays.stream(
                            RuleListController.class.getDeclaredMethods())
                    .anyMatch(method -> removed.equals(method.getName())));
        }
    }
}
