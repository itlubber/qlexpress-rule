package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

public class GovernanceSecretCodecTest {

    @Test
    public void secretValuesNeverRemainInPublicSnapshotOrDiff() {
        GovernanceSecretCodec codec = codec();
        ResourceSnapshot normalized = codec.normalize(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"db\",\"password\":\"top-secret\","
                                + "\"sshPrivateKey\":\"private-key\"}"),
                Set.of("password", "sshPrivateKey"));

        Assert.assertFalse(
                normalized.snapshotJson().contains("top-secret"));
        Assert.assertFalse(
                normalized.snapshotJson().contains("private-key"));
        Assert.assertNotNull(normalized.secretPayloadCiphertext());
        Assert.assertNotNull(normalized.secretDigest());

        Map<String, Object> restored = codec.restore(normalized);
        Assert.assertEquals("top-secret", restored.get("password"));
        Assert.assertEquals("private-key",
                restored.get("sshPrivateKey"));

        ResourceDiff diff = JsonResourceDiff.compare(
                ResourceSnapshot.ofJson("{\"name\":\"db\"}"),
                normalized);
        Assert.assertTrue(diff.fields().stream()
                .filter(ResourceDiff.FieldDiff::sensitive)
                .allMatch(field -> !"top-secret".equals(
                        field.rightValue())));
    }

    @Test
    public void existingCipherIsRetainedWhenDraftHasNoPlaintextSecret() {
        GovernanceSecretCodec codec = codec();
        ResourceSnapshot initial = codec.normalize(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"db\",\"password\":\"top-secret\"}"),
                Set.of("password"));
        ResourceSnapshot next = codec.normalize(new ResourceSnapshot(
                        "{\"name\":\"db2\",\"_secretConfigured\":"
                                + "{\"$.password\":true}}",
                        "ACTIVE",
                        initial.secretPayloadCiphertext(),
                        initial.secretDigest()),
                Set.of("password"));

        Assert.assertEquals(initial.secretPayloadCiphertext(),
                next.secretPayloadCiphertext());
        Assert.assertEquals("top-secret",
                codec.restore(next).get("password"));
    }

    @Test
    public void blankMaskedSecretDoesNotOverwriteExistingCipher() {
        GovernanceSecretCodec codec = codec();
        ResourceSnapshot initial = codec.normalize(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"db\",\"password\":\"top-secret\"}"),
                Set.of("password"));
        ResourceSnapshot next = codec.normalize(new ResourceSnapshot(
                        "{\"name\":\"db2\",\"password\":\"\"}",
                        "ACTIVE",
                        initial.secretPayloadCiphertext(),
                        initial.secretDigest()),
                Set.of("password"));

        Assert.assertEquals(initial.secretPayloadCiphertext(),
                next.secretPayloadCiphertext());
        Assert.assertEquals(initial.secretDigest(),
                next.secretDigest());
        Assert.assertEquals("top-secret",
                codec.restore(next).get("password"));
    }

    private GovernanceSecretCodec codec() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setActiveKeyId("test");
        properties.setMasterKeys(Map.of(
                "test",
                "test-governance-master-key-32-characters-long"));
        return new GovernanceSecretCodec(
                new CredentialCipher(properties));
    }
}
