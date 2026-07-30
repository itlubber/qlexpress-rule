package com.hengshucredit.rule.server.consolelogin;

import cn.hutool.crypto.digest.BCrypt;
import com.hengshucredit.rule.model.entity.ConsoleUser;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class DatabaseConsoleAccountServiceTest {

    @Test
    public void builtinLoginBootstrapsDatabaseOnlyWhenNoAccountsExist() {
        InMemoryService service = new InMemoryService();
        service.userCount = 0;
        service.builtinAccepted = true;

        DatabaseConsoleAccountService.ConsoleLoginResult result =
                service.authenticate("admin", "secret");

        Assert.assertTrue(result.authenticated());
        Assert.assertTrue(result.bootstrapped());
        Assert.assertEquals("admin", result.username());
        Assert.assertNotNull(service.savedUser);
        Assert.assertTrue(service.savedUser.getPasswordHash().startsWith("$2"));
        Assert.assertTrue(BCrypt.checkpw(
                "secret", service.savedUser.getPasswordHash()));
    }

    @Test
    public void databaseAccountsDisableBuiltinFallback() {
        InMemoryService service = new InMemoryService();
        service.userCount = 1;
        service.builtinAccepted = true;

        DatabaseConsoleAccountService.ConsoleLoginResult result =
                service.authenticate("legacy-admin", "secret");

        Assert.assertFalse(result.authenticated());
        Assert.assertEquals(0, service.builtinCalls);
    }

    @Test
    public void disabledDatabaseAccountCannotLogin() {
        InMemoryService service = new InMemoryService();
        service.userCount = 1;
        service.databaseUser = user(9L, "reviewer", "secret", 0);

        DatabaseConsoleAccountService.ConsoleLoginResult result =
                service.authenticate("reviewer", "secret");

        Assert.assertFalse(result.authenticated());
    }

    @Test
    public void enabledDatabaseAccountUsesBcryptPassword() {
        InMemoryService service = new InMemoryService();
        service.userCount = 1;
        service.databaseUser = user(9L, "reviewer", "secret", 1);

        DatabaseConsoleAccountService.ConsoleLoginResult result =
                service.authenticate("reviewer", "secret");

        Assert.assertTrue(result.authenticated());
        Assert.assertFalse(result.bootstrapped());
        Assert.assertEquals(9L, result.userId().longValue());
        Assert.assertEquals(Set.of("approval:view"), result.permissions());
        Assert.assertEquals(1, service.catalogSyncCalls);
    }

    private static ConsoleUser user(Long id, String username,
                                    String password, int status) {
        ConsoleUser user = new ConsoleUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setStatus(status);
        user.setPermissionVersion(1L);
        return user;
    }

    private static class InMemoryService extends DatabaseConsoleAccountService {
        private long userCount;
        private boolean builtinAccepted;
        private int builtinCalls;
        private ConsoleUser databaseUser;
        private ConsoleUser savedUser;
        private int catalogSyncCalls;

        @Override
        protected long countUsers() {
            return userCount;
        }

        @Override
        protected ConsoleUser findUser(String username) {
            return databaseUser != null
                    && databaseUser.getUsername().equals(username)
                    ? databaseUser : null;
        }

        @Override
        protected boolean authenticateBootstrapSource(
                String username, String rawPassword) {
            builtinCalls++;
            return builtinAccepted;
        }

        @Override
        protected void persistBootstrapUser(ConsoleUser user) {
            user.setId(1L);
            savedUser = user;
            databaseUser = user;
            userCount = 1;
        }

        @Override
        protected List<String> resolveRoleCodes(Long userId) {
            return userId != null && userId == 9L
                    ? List.of("REVIEWER")
                    : List.of("SUPER_ADMIN");
        }

        @Override
        protected Set<String> resolvePermissions(Long userId) {
            return userId != null && userId == 9L
                    ? Set.of("approval:view")
                    : Set.of("account:manage", "role:manage");
        }

        @Override
        protected void updateLastLogin(ConsoleUser user) {
        }

        @Override
        protected void synchronizePermissionCatalogForExistingUsers(
                String operator) {
            catalogSyncCalls++;
        }
    }
}
