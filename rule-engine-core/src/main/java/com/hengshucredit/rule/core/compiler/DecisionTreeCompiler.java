package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;

/**
 * 决策树编译器 - 严格树形结构
 *
 * 校验图必须是树形结构（禁止聚合节点、禁止多入边汇合），
 * 然后将 nodes/edges 编译为单一 QLExpress 脚本。
 *
 * <p>VarContext 通过参数传递，不使用 ThreadLocal。
 */
public class DecisionTreeCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        return compile(modelJson, null);
    }

    @Override
    public CompileResult compile(String modelJson, VarContext varContext) {
        return doCompile(modelJson, varContext);
    }

    private CompileResult doCompile(String modelJson, VarContext varContext) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            JSONArray nodes = model.getJSONArray("nodes");
            JSONArray edges = model.getJSONArray("edges");

            if (nodes == null || edges == null) {
                return CompileResult.fail("决策树模型缺少 nodes 或 edges");
            }

            DecisionGraphValidator.ValidationResult graph =
                    DecisionGraphValidator.validate(nodes, edges, true);

            LinkedHashSet<String> outputVars = new LinkedHashSet<>();
            ActionDataOutputVarCollector.collectFromGraphTaskNodes(nodes, outputVars, varContext);
            String script = GraphScriptGenerator.generate(
                    graph.getNodeMap(), graph.getOutgoing(), graph.getStartId(),
                    varContext, outputVars);
            StringBuilder sb = new StringBuilder(script);
            if (!outputVars.isEmpty()) {
                RuleScriptResultCollector.prependOutputNullInits(sb, outputVars);
                RuleScriptResultCollector.appendResultMapReturn(sb, outputVars);
            }

            return CompileResult.ok(sb.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("决策树编译失败: " + e.getMessage());
        }
    }
}
