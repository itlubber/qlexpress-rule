package com.hengshucredit.rule.server.artifact;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PublishedRuleFieldSnapshotResolverTest {

    @Test
    public void resolvesFieldsOnlyFromValidatedArtifactComponents() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = artifact(resolver.published, true);

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().toString(), fields.getDiagnostics().isEmpty());
        Assert.assertEquals("frozen_input", fields.getInputFields().get(0).getFieldName());
        Assert.assertEquals("frozen_output", fields.getOutputFields().get(0).getFieldName());
        Assert.assertTrue(fields.getLocalOutputNames().contains("frozen_output"));
        Assert.assertEquals("NUMBER", fields.getInputPropertySchemas().get("frozen_input"));
        Assert.assertEquals("SCRIPT", fields.getSnapshotModelType());
    }

    @Test
    public void missingArtifactIsBlockingAndNeverFallsBack() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING".equals(issue.getCode())
                        && Long.valueOf(301L).equals(issue.getRevisionId())));
        Assert.assertTrue(fields.getInputFields().isEmpty());
    }

    @Test
    public void corruptPackageIsBlockingAndNeverFallsBack() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = artifact(resolver.published, true);
        resolver.artifact.setPackageContent(new byte[]{1, 2, 3});

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_INVALID".equals(issue.getCode())));
        Assert.assertTrue(fields.getInputFields().isEmpty());
    }

    @Test
    public void missingRequiredFieldComponentIsBlocking() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = artifact(resolver.published, false);

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING".equals(issue.getCode())));
    }

    @Test
    public void manifestMetadataMismatchIsBlocking() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        RulePublished mismatchedManifest = published();
        mismatchedManifest.setRevisionId(302L);
        resolver.artifact = artifact(mismatchedManifest, true);
        resolver.artifact.setDefinitionId(resolver.published.getDefinitionId());
        resolver.artifact.setRevisionId(resolver.published.getRevisionId());
        resolver.published.setArtifactDigest(resolver.artifact.getArtifactDigest());

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_INVALID".equals(issue.getCode())));
    }

    @Test
    public void legacyLocalOutputUsesFrozenPackageModelTypeNotMutablePublishedType() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = artifact(resolver.published, true, false);
        resolver.published.setModelType("FLOW");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().toString(), fields.getDiagnostics().isEmpty());
        Assert.assertTrue(fields.getLocalOutputNames().contains("frozen_output"));
    }

    @Test
    public void flowArtifactCannotDeclareLocalOutputs() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.published.setModelType("FLOW");
        resolver.artifact = artifact(resolver.published, true, true);

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void legacyFlowArtifactDoesNotInferNullReferenceOutputAsLocal() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.published.setModelType("FLOW");
        resolver.artifact = artifact(resolver.published, true, false);

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().toString(), fields.getDiagnostics().isEmpty());
        Assert.assertTrue(fields.getLocalOutputNames().isEmpty());
        Assert.assertEquals("FLOW", fields.getSnapshotModelType());
    }

    @Test
    public void localOutputNameMustMatchNullReferenceSnapshotOutput() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = rawArtifact(
                resolver.published, 101L, 301L, "SCRIPT",
                "[]",
                "[{\"fieldName\":\"other\",\"scriptName\":\"other\",\"fieldType\":\"NUMBER\"}]",
                "{\"localOutputNames\":[\"forged\"],"
                        + "\"inputPropertySchemas\":{},\"outputPropertySchemas\":{}}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void referencedSnapshotOutputCannotBeDeclaredLocal() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = rawArtifact(
                resolver.published, 101L, 301L, "SCRIPT",
                "[]",
                "[{\"fieldName\":\"forged\",\"scriptName\":\"forged\","
                        + "\"fieldType\":\"NUMBER\",\"varId\":7,\"refType\":\"VARIABLE\"}]",
                "{\"localOutputNames\":[\"forged\"],"
                        + "\"inputPropertySchemas\":{},\"outputPropertySchemas\":{}}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void malformedFieldJsonIsInvalidInsteadOfEscaping() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[{", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void duplicateJsonKeyIsRejected() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[]",
                "[{\"fieldName\":\"first\",\"fieldName\":\"second\"}]",
                "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void trailingJsonTokenIsRejected() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[] trailing", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullInputFieldArrayRootIsInvalid() {
        FixtureResolver resolver = resolverWithRawComponents(
                "null", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullOutputFieldArrayRootIsInvalid() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[]", "null", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullInputFieldArrayElementIsInvalid() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[null]", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullOutputFieldArrayElementIsInvalid() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[]", "[null]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullFieldResolutionRootIsInvalidInsteadOfEscaping() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[]", "[]", "null");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void fractionalManifestIdIsRejectedWithoutTruncation() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = rawArtifact(
                resolver.published, 101.5d, 301L, "SCRIPT",
                "[]", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    private static RulePublished published() {
        RulePublished published = new RulePublished();
        published.setDefinitionId(101L);
        published.setRevisionId(301L);
        published.setArtifactId(401L);
        published.setModelType("SCRIPT");
        published.setStatus(1);
        return published;
    }

    private static DecisionArtifact artifact(RulePublished published, boolean includeOutput) {
        return artifact(published, includeOutput, true);
    }

    private static DecisionArtifact artifact(
            RulePublished published, boolean includeOutput, boolean includeResolution) {
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setFieldName("frozen_input");
        input.setScriptName("frozen_input");
        input.setFieldType("NUMBER");
        input.setVarId(7L);
        input.setRefType("VARIABLE");
        input.setStatus(1);
        RuleDefinitionOutputField output = new RuleDefinitionOutputField();
        output.setFieldName("frozen_output");
        output.setScriptName("frozen_output");
        output.setFieldType("NUMBER");
        output.setStatus(1);

        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.putMetadata("definitionId", published.getDefinitionId());
        artifactPackage.putMetadata("revisionId", published.getRevisionId());
        artifactPackage.putMetadata("modelType", published.getModelType());
        artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.INPUT_FIELDS_PATH,
                "application/json", CanonicalJson.writeBytes(
                        JSON.parse(JSON.toJSONString(Collections.singletonList(input)))));
        if (includeOutput) {
            artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.OUTPUT_FIELDS_PATH,
                    "application/json", CanonicalJson.writeBytes(
                            JSON.parse(JSON.toJSONString(Collections.singletonList(output)))));
        }
        Map<String, Object> fieldResolution = new LinkedHashMap<>();
        fieldResolution.put("localOutputNames", Collections.singletonList("frozen_output"));
        fieldResolution.put("inputPropertySchemas",
                Collections.singletonMap("frozen_input", "NUMBER"));
        fieldResolution.put("outputPropertySchemas", Collections.emptyMap());
        if (includeResolution) {
            artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.FIELD_RESOLUTION_PATH,
                    "application/json", CanonicalJson.writeBytes(fieldResolution));
        }

        DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();
        byte[] packageContent = codec.encode(artifactPackage);
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(packageContent);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(published.getArtifactId());
        artifact.setDefinitionId(published.getDefinitionId());
        artifact.setRevisionId(published.getRevisionId());
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setPackageContent(packageContent);
        published.setArtifactDigest(decoded.getArtifactDigest());
        return artifact;
    }

    private static FixtureResolver resolverWithRawComponents(
            String inputs, String outputs, String resolution) {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = rawArtifact(
                resolver.published, 101L, 301L, "SCRIPT",
                inputs, outputs, resolution);
        return resolver;
    }

    private static DecisionArtifact rawArtifact(
            RulePublished published,
            Object manifestDefinitionId,
            Object manifestRevisionId,
            String modelType,
            String inputs,
            String outputs,
            String resolution) {
        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.putMetadata("definitionId", manifestDefinitionId);
        artifactPackage.putMetadata("revisionId", manifestRevisionId);
        artifactPackage.putMetadata("modelType", modelType);
        artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.INPUT_FIELDS_PATH,
                "application/json", inputs.getBytes(StandardCharsets.UTF_8));
        artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.OUTPUT_FIELDS_PATH,
                "application/json", outputs.getBytes(StandardCharsets.UTF_8));
        if (resolution != null) {
            artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.FIELD_RESOLUTION_PATH,
                    "application/json", resolution.getBytes(StandardCharsets.UTF_8));
        }
        DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();
        byte[] packageContent = codec.encode(artifactPackage);
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(packageContent);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(published.getArtifactId());
        artifact.setDefinitionId(published.getDefinitionId());
        artifact.setRevisionId(published.getRevisionId());
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setPackageContent(packageContent);
        published.setArtifactDigest(decoded.getArtifactDigest());
        return artifact;
    }

    private static void assertInvalid(RuleFieldAnalyzer.ResolvedFields fields) {
        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_INVALID".equals(issue.getCode())));
        Assert.assertTrue(fields.getInputFields().isEmpty());
        Assert.assertTrue(fields.getOutputFields().isEmpty());
        Assert.assertTrue(fields.getLocalOutputNames().isEmpty());
    }

    private static final class FixtureResolver extends PublishedRuleFieldSnapshotResolver {
        private RulePublished published;
        private DecisionArtifact artifact;

        @Override
        protected RulePublished loadPublishedRule(Long definitionId) {
            return published;
        }

        @Override
        protected DecisionArtifact loadArtifact(Long artifactId) {
            return artifact;
        }
    }
}
