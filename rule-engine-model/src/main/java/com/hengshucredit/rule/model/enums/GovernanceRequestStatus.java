package com.hengshucredit.rule.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum GovernanceRequestStatus {
    EDITING,
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    CONFLICT;

    public boolean isActive() {
        return this == EDITING || this == PENDING;
    }

    public boolean isTerminal() {
        return !isActive();
    }

    public boolean canTransitionTo(GovernanceRequestStatus target) {
        if (target == null) return false;
        Set<GovernanceRequestStatus> targets = switch (this) {
            case EDITING -> EnumSet.of(PENDING, CANCELLED);
            case PENDING -> EnumSet.of(
                    APPROVED, REJECTED, CANCELLED, CONFLICT);
            default -> EnumSet.noneOf(GovernanceRequestStatus.class);
        };
        return targets.contains(target);
    }
}
