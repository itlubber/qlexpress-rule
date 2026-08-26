package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DashboardAccessServiceTest {

    @Test
    public void loginDisabledIsUnrestrictedWithoutSyntheticUser() {
        RuleEngineConsoleLoginProperties properties =
                new RuleEngineConsoleLoginProperties();
        DashboardAccessContext access = new DashboardAccessService(
                properties, new FixedPermissions()).resolve(
                new MockHttpServletRequest());

        assertTrue(access.unrestricted());
        assertNull(access.userId());
        assertTrue(access.can("anything:view"));
    }

    @Test
    public void loginEnabledUsesCurrentSessionIdentityAndPermissions() {
        RuleEngineConsoleLoginProperties properties =
                new RuleEngineConsoleLoginProperties();
        properties.setEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(
                properties.getSessionUserIdAttribute(), "9");
        request.getSession().setAttribute(
                properties.getSessionUsernameAttribute(), "reviewer");

        DashboardAccessContext access = new DashboardAccessService(
                properties, new FixedPermissions()).resolve(request);

        assertFalse(access.unrestricted());
        assertEquals(Long.valueOf(9L), access.userId());
        assertEquals("reviewer", access.username());
        assertTrue(access.can("rule:view"));
        assertFalse(access.can("database:view"));
    }

    private static class FixedPermissions extends ConsolePermissionService {
        @Override
        public Set<String> effectivePermissions(Long userId) {
            return Set.of("rule:view");
        }
    }
}
