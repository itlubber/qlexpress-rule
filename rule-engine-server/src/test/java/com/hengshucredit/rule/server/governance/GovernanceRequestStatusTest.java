package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.enums.GovernanceRequestStatus;
import org.junit.Assert;
import org.junit.Test;

public class GovernanceRequestStatusTest {

    @Test
    public void rejectedIsTerminalAndCannotReturnToEditing() {
        Assert.assertFalse(GovernanceRequestStatus.REJECTED.canTransitionTo(
                GovernanceRequestStatus.EDITING));
    }

    @Test
    public void editingCanSubmitAndPendingCanReachEveryTerminalReviewState() {
        Assert.assertTrue(GovernanceRequestStatus.EDITING.canTransitionTo(
                GovernanceRequestStatus.PENDING));
        Assert.assertTrue(GovernanceRequestStatus.PENDING.canTransitionTo(
                GovernanceRequestStatus.APPROVED));
        Assert.assertTrue(GovernanceRequestStatus.PENDING.canTransitionTo(
                GovernanceRequestStatus.REJECTED));
        Assert.assertTrue(GovernanceRequestStatus.PENDING.canTransitionTo(
                GovernanceRequestStatus.CONFLICT));
    }

    @Test
    public void onlyEditingAndPendingAreActive() {
        Assert.assertTrue(GovernanceRequestStatus.EDITING.isActive());
        Assert.assertTrue(GovernanceRequestStatus.PENDING.isActive());
        Assert.assertFalse(GovernanceRequestStatus.APPROVED.isActive());
        Assert.assertFalse(GovernanceRequestStatus.REJECTED.isActive());
    }
}
