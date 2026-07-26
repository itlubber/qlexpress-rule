package com.hengshucredit.rule.core.script;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QLScriptAnalyzerTest {

    private final QLScriptAnalyzer analyzer = new QLScriptAnalyzer();

    private static List<String> outputNames(QLScriptAnalysis result) {
        return result.getPublicOutputs().stream()
                .map(QLScriptAnalysis.OutputField::getName)
                .collect(Collectors.toList());
    }

    @Test
    public void icekreditProfileScriptSeparatesInputsLocalsAndPublicOutputs() {
        String script = "credit_score_v1 = icekredit_vn_credit_profile_features.credit_score_v1;\n"
                + "credit_apply_count_1m = icekredit_vn_credit_profile_features.credit_apply_count_1m;\n"
                + "_result = {\"credit_score_v1\": credit_score_v1,"
                + "\"credit_apply_count_1m\": credit_apply_count_1m}\n_result";

        QLScriptAnalysis result = analyzer.analyze(script);

        assertEquals(Arrays.asList(
                "icekredit_vn_credit_profile_features.credit_score_v1",
                "icekredit_vn_credit_profile_features.credit_apply_count_1m"),
                result.getDirectInputs());
        assertEquals(Arrays.asList("credit_score_v1", "credit_apply_count_1m", "_result"),
                result.getLocalSymbols());
        assertEquals(
                "icekredit_vn_credit_profile_features.credit_score_v1",
                result.getLocalAssignments().get("credit_score_v1"));
        assertEquals(
                "icekredit_vn_credit_profile_features.credit_apply_count_1m",
                result.getLocalAssignments().get("credit_apply_count_1m"));
        assertEquals(Arrays.asList("credit_score_v1", "credit_apply_count_1m"),
                result.getPublicOutputs().stream()
                        .map(QLScriptAnalysis.OutputField::getName).collect(Collectors.toList()));
        assertTrue(result.hasExplicitResult());
    }

    @Test
    public void sameLineAssignmentsAreAllLocalSymbols() {
        QLScriptAnalysis result = analyzer.analyze("a = x; b = a + y;");
        assertEquals(Arrays.asList("x", "y"), result.getDirectInputs());
        assertEquals(Arrays.asList("a", "b"), result.getLocalSymbols());
    }

    @Test
    public void functionNamesAndMapKeysAreNotInputs() {
        QLScriptAnalysis result = analyzer.analyze(
                "score = max(request.score, 0); _result = {\"score\": score}; _result");
        assertEquals(Collections.singletonList("request.score"), result.getDirectInputs());
    }

    @Test
    public void objectAndListReadsKeepFullPaths() {
        QLScriptAnalysis result = analyzer.analyze(
                "x = request.items[0].score; _result = {\"x\": x}; _result");
        assertEquals(Collections.singletonList("request.items[0].score"), result.getDirectInputs());
    }

    @Test
    public void noExplicitResultUsesTopLevelAssignmentsAsEffectiveOutputs() {
        QLScriptAnalysis result = analyzer.analyze("a = x; b = y;");
        assertEquals(Arrays.asList("a", "b"), outputNames(result));
        assertFalse(result.hasExplicitResult());
    }

    @Test
    public void dynamicResultProducesOpenOutputWarning() {
        QLScriptAnalysis result = analyzer.analyze("_result = buildResult(x); _result");
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(item -> "OBJECT_SHAPE_INCOMPLETE".equals(item.getCode())));
    }

    @Test
    public void syntaxFailureUsesQlParseErrorDiagnostic() {
        QLScriptAnalysis result = analyzer.analyze("_result = {");
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(item -> "QL_PARSE_ERROR".equals(item.getCode())
                        && "ERROR".equals(item.getSeverity())));
    }

    @Test
    public void resultReferenceDoesNotResolveAssignmentAfterResult() {
        QLScriptAnalysis result = analyzer.analyze(
                "_result = r; r = {\"late\": x}; _result");

        assertEquals(Collections.emptyList(), outputNames(result));
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(item -> "OBJECT_SHAPE_INCOMPLETE".equals(item.getCode())));
    }

    @Test
    public void resultReferenceKeepsMapVisibleAtAssignmentTime() {
        QLScriptAnalysis result = analyzer.analyze(
                "r = {\"early\": x}; _result = r; r = buildResult(y); _result");

        assertEquals(Collections.singletonList("early"), outputNames(result));
    }

    @Test
    public void explicitResultValueUsesAssignmentVisibleBeforeResult() {
        QLScriptAnalysis result = analyzer.analyze(
                "x = input.number; _result = {\"x\": x}; x = \"late\"; _result");

        assertEquals("input.number",
                result.getPublicOutputs().get(0).getSourceExpression());
    }

    @Test
    public void explicitResultValueDoesNotResolveAssignmentAfterResult() {
        QLScriptAnalysis result = analyzer.analyze(
                "_result = {\"x\": x}; x = input.number; _result");

        assertEquals("x", result.getPublicOutputs().get(0).getSourceExpression());
    }

    @Test
    public void aliasedResultMapResolvesMultiLevelAssignmentsAtMapCreationTime() {
        QLScriptAnalysis result = analyzer.analyze(
                "a = input.number; b = a; r = {\"x\": b};"
                        + " a = \"late\"; b = a; _result = r; _result");

        assertEquals("input.number",
                result.getPublicOutputs().get(0).getSourceExpression());
    }

    @Test
    public void compoundAssignmentReadsLeftAndRightAndKeepsEffectiveExpression() {
        QLScriptAnalysis result = analyzer.analyze("a += x;");

        assertEquals(Arrays.asList("a", "x"), result.getDirectInputs());
        assertEquals(Collections.singletonList("a"), result.getLocalSymbols());
        assertEquals("a+x", result.getLocalAssignments().get("a"));
    }
}
