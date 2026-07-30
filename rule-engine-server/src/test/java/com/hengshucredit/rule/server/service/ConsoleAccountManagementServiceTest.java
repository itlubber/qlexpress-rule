package com.hengshucredit.rule.server.service;

import cn.hutool.crypto.digest.BCrypt;
import com.hengshucredit.rule.model.dto.ConsoleUserSaveRequest;
import com.hengshucredit.rule.model.entity.ConsoleRole;
import com.hengshucredit.rule.model.entity.ConsoleUser;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ConsoleAccountManagementServiceTest {

    @Test(expected = ConsoleAccountManagementService.LastAdministratorException.class)
    public void cannotDisableLastAccountAdministrator() {
        TestService service = new TestService();
        service.targetIsAdministrator = true;
        service.otherAdministratorCount = 0;

        service.changeUserStatus(1L, 0, "admin");
    }

    @Test
    public void canDisableAdministratorWhenAnotherOneRemains() {
        TestService service = new TestService();
        service.targetIsAdministrator = true;
        service.otherAdministratorCount = 1;

        service.changeUserStatus(1L, 0, "admin");

        Assert.assertEquals(Integer.valueOf(0), service.savedStatus);
    }

    @Test
    public void passwordResetPersistsOnlyBcryptHash() {
        TestService service = new TestService();

        service.resetPassword(7L, "new-secret", "admin");

        Assert.assertNotNull(service.savedPasswordHash);
        Assert.assertTrue(service.savedPasswordHash.startsWith("$2"));
        Assert.assertTrue(BCrypt.checkpw(
                "new-secret", service.savedPasswordHash));
        Assert.assertNotEquals("new-secret", service.savedPasswordHash);
    }

    @Test
    public void disabledRolesDoNotContributeInheritedPermissions() {
        TestService service = new TestService();
        ConsoleRole enabled = new ConsoleRole();
        enabled.setId(1L);
        enabled.setStatus(1);
        ConsoleRole disabled = new ConsoleRole();
        disabled.setId(2L);
        disabled.setStatus(0);

        Assert.assertEquals(List.of(1L),
                service.enabledRoleIds(
                        List.of(enabled, disabled)));
    }

    @Test
    public void existingUsernameCannotChangeBecauseItOwnsAuditRecords() {
        TestService service = new TestService();
        ConsoleUserSaveRequest request = new ConsoleUserSaveRequest();
        request.setId(1L);
        request.setUsername("renamed");
        request.setDisplayName("New display name");

        try {
            service.saveUser(request, "admin");
            Assert.fail("existing usernames must remain immutable");
        } catch (IllegalArgumentException error) {
            Assert.assertTrue(error.getMessage().contains("用户名"));
        }
    }

    private static class TestService extends ConsoleAccountManagementService {
        private boolean targetIsAdministrator;
        private long otherAdministratorCount;
        private Integer savedStatus;
        private String savedPasswordHash;

        @Override
        protected ConsoleUser requireUser(Long userId) {
            ConsoleUser user = new ConsoleUser();
            user.setId(userId);
            user.setUsername("target");
            user.setStatus(1);
            return user;
        }

        @Override
        protected boolean isEnabledAdministrator(Long userId) {
            return targetIsAdministrator;
        }

        @Override
        protected long countOtherEnabledAdministrators(Long userId) {
            return otherAdministratorCount;
        }

        @Override
        protected void persistUserStatus(
                ConsoleUser user, Integer status, String operator) {
            savedStatus = status;
        }

        @Override
        protected void persistPasswordHash(
                ConsoleUser user, String passwordHash, String operator) {
            savedPasswordHash = passwordHash;
        }

        @Override
        protected void appendAudit(Long userId, String username,
                                   String action, String details) {
        }
    }
}
