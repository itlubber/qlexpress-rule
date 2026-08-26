package com.hengshucredit.rule.server.dashboard;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record DashboardAccessContext(boolean unrestricted,
                                     Long userId,
                                     String username,
                                     Set<String> permissions) {

    public DashboardAccessContext {
        permissions = permissions == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(
                new LinkedHashSet<>(permissions));
    }

    public boolean can(String permission) {
        return unrestricted || permissions.contains(permission);
    }
}
