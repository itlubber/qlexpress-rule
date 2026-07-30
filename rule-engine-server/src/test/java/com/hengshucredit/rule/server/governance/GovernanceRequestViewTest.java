package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class GovernanceRequestViewTest {

    @Test
    public void apiViewsNeverExposeCredentialCiphertextOrDigest() {
        GovernanceApprovalRequest request =
                new GovernanceApprovalRequest();
        request.setId(1L);
        request.setSecretPayloadCiphertext("ciphertext");
        request.setSecretDigest("secret-digest");
        GovernanceRequestView requestView =
                GovernanceRequestView.from(request);

        Assert.assertTrue(requestView.hasProtectedCredential());
        Assert.assertFalse(hasComponent(
                GovernanceRequestView.class,
                "secretPayloadCiphertext"));
        Assert.assertFalse(hasComponent(
                GovernanceRequestView.class, "secretDigest"));

        GovernedResourceVersion version =
                new GovernedResourceVersion();
        version.setSecretPayloadCiphertext("ciphertext");
        version.setSecretDigest("secret-digest");
        GovernanceVersionView versionView =
                GovernanceVersionView.from(version);
        Assert.assertTrue(versionView.hasProtectedCredential());
        Assert.assertFalse(hasComponent(
                GovernanceVersionView.class,
                "secretPayloadCiphertext"));
        Assert.assertFalse(hasComponent(
                GovernanceVersionView.class, "secretDigest"));
    }

    private boolean hasComponent(Class<?> type, String name) {
        return Arrays.stream(type.getRecordComponents())
                .anyMatch(component -> name.equals(
                        component.getName()));
    }
}
