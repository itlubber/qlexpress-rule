package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.script.QLScriptAnalysis;
import com.hengshucredit.rule.core.script.QLScriptAnalyzer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * QL脚本直通编译器：不做模型转换，仅将脚本原文存入编译结果。
 * 适用于技术人员直接编写 QLExpress 脚本的场景。
 */
public class ScriptPassthroughCompiler implements RuleCompiler {

    private final QLScriptAnalyzer scriptAnalyzer = new QLScriptAnalyzer();

    @Override
    public CompileResult compile(String modelJson) {
        return compile(modelJson, null);
    }

    @Override
    public CompileResult compile(String modelJson, VarContext varContext) {
        if (modelJson == null || modelJson.trim().isEmpty() || "{}".equals(modelJson.trim())) {
            return CompileResult.fail("脚本内容为空，请先编写脚本再编译");
        }

        JSONObject model;
        try {
            model = JSON.parseObject(modelJson);
        } catch (RuntimeException e) {
            model = null;
        }
        String script = model == null ? modelJson : model.getString("script");
        if (script == null || script.trim().isEmpty()) {
            return CompileResult.fail("脚本内容为空，请先编写脚本再编译");
        }
        try {
            return CompileResult.ok(wrapResultIfNeeded(
                    inlineConstants(script,
                            model == null ? null : model.getJSONArray("scriptVarRefs"),
                            varContext)), "QLEXPRESS");
        } catch (IllegalArgumentException e) {
            return CompileResult.fail(e.getMessage());
        }
    }

    private String inlineConstants(String script, JSONArray refs, VarContext varContext) {
        if (script == null || refs == null || refs.isEmpty() || varContext == null) return script;
        Map<String, String> expressions = new HashMap<>();
        for (int i = 0; i < refs.size(); i++) {
            JSONObject ref = refs.getJSONObject(i);
            if (ref != null && "CONSTANT".equalsIgnoreCase(ref.getString("refType"))) {
                String code = ref.getString("refCode");
                if (code != null && !code.isEmpty()) expressions.put(code, varContext.resolveConstant(ref.getLong("varId")));
            }
        }
        if (expressions.isEmpty()) return script;

        StringBuilder out = new StringBuilder(script.length());
        boolean singleQuoted = false, doubleQuoted = false, lineComment = false, blockComment = false, escaped = false;
        for (int i = 0; i < script.length();) {
            char ch = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            if (lineComment) {
                out.append(ch); i++;
                if (ch == '\n' || ch == '\r') lineComment = false;
                continue;
            }
            if (blockComment) {
                out.append(ch); i++;
                if (ch == '*' && next == '/') { out.append(next); i++; blockComment = false; }
                continue;
            }
            if (!singleQuoted && !doubleQuoted && ch == '/' && next == '/') {
                out.append(ch).append(next); i += 2; lineComment = true; continue;
            }
            if (!singleQuoted && !doubleQuoted && ch == '/' && next == '*') {
                out.append(ch).append(next); i += 2; blockComment = true; continue;
            }
            if (singleQuoted || doubleQuoted) {
                out.append(ch); i++;
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (singleQuoted && ch == '\'') singleQuoted = false;
                else if (doubleQuoted && ch == '"') doubleQuoted = false;
                continue;
            }
            if (ch == '\'') { out.append(ch); i++; singleQuoted = true; continue; }
            if (ch == '"') { out.append(ch); i++; doubleQuoted = true; continue; }
            if (Character.isLetter(ch) || ch == '_') {
                int end = i + 1;
                while (end < script.length()) {
                    char tokenChar = script.charAt(end);
                    if (!Character.isLetterOrDigit(tokenChar) && tokenChar != '_') break;
                    end++;
                }
                String token = script.substring(i, end);
                out.append(expressions.containsKey(token) ? expressions.get(token) : token);
                i = end;
                continue;
            }
            out.append(ch); i++;
        }
        return out.toString();
    }

    private String wrapResultIfNeeded(String script) {
        QLScriptAnalysis analysis = scriptAnalyzer.analyze(script);
        if (analysis.hasErrors()) {
            throw new IllegalArgumentException(firstError(analysis));
        }
        if (analysis.hasExplicitResult()) {
            return script;
        }
        LinkedHashSet<String> outputs = analysis.getPublicOutputs().stream()
                .map(QLScriptAnalysis.OutputField::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (outputs.isEmpty()) {
            return script;
        }
        StringBuilder wrapped = new StringBuilder(script.trim()).append('\n');
        RuleScriptResultCollector.appendResultMapReturn(wrapped, outputs);
        return wrapped.toString();
    }

    private String firstError(QLScriptAnalysis analysis) {
        return analysis.getDiagnostics().stream()
                .filter(item -> "ERROR".equals(item.getSeverity()))
                .map(item -> item.getCode() + ": " + item.getMessage())
                .findFirst()
                .orElse("QL_PARSE_ERROR: QL 脚本解析失败");
    }
}
