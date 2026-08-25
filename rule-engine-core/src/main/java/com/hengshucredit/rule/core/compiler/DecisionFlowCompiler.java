package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;

/**
 * 决策流编译器 - 支持 DAG 结构（含聚合节点）
 *
 * 校验 nodes/edges 图结构的合法性，将图编译为单一 QLExpress 脚本。
 * 支持分支汇合（join 节点），适用于分叉后有公共后续逻辑的场景。
 *
 * <p>VarContext 通过参数传递，不使用 ThreadLocal。
 */
public class DecisionFlowCompiler implements RuleCompiler {

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

            if (nodes == null || nodes.isEmpty()) {
                return CompileResult.fail("决策流模型缺少 nodes");
            }
            if (edges == null) {
                return CompileResult.fail("决策流模型缺少 edges");
            }

            DecisionGraphValidator.ValidationResult graph =
                    DecisionGraphValidator.validate(nodes, edges, false);

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
            return CompileResult.fail("决策流编译失败: " + e.getMessage());
        }
    }

}
