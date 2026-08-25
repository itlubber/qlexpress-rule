package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** 决策流与决策树共用的结构正确性门禁。 */
public final class DecisionGraphValidator {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "start", "end", "task", "decision", "join");

    private DecisionGraphValidator() {
    }

    public static ValidationResult validate(JSONArray nodes, JSONArray edges, boolean treeMode) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException(treeMode ? "决策树模型缺少 nodes" : "决策流模型缺少 nodes");
        }
        if (edges == null) {
            throw new IllegalArgumentException(treeMode ? "决策树模型缺少 edges" : "决策流模型缺少 edges");
        }

        Map<String, JSONObject> nodeMap = new LinkedHashMap<>();
        Map<String, List<JSONObject>> outgoing = new LinkedHashMap<>();
        Map<String, List<JSONObject>> incoming = new LinkedHashMap<>();
        String startId = null;
        int startCount = 0;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String id = text(node, "id");
            String type = text(node, "type");
            if (id == null) {
                throw new IllegalArgumentException("节点 #" + i + " 缺少 id");
            }
            if (type == null || !SUPPORTED_TYPES.contains(type)) {
                throw new IllegalArgumentException("节点 [" + id + "] 类型无效: " + type);
            }
            if (nodeMap.putIfAbsent(id, node) != null) {
                throw new IllegalArgumentException("节点 id 重复: " + id);
            }
            outgoing.put(id, new ArrayList<>());
            incoming.put(id, new ArrayList<>());
            if ("start".equals(type)) {
                startCount++;
                startId = id;
            }
            if (treeMode && "join".equals(type)) {
                throw new IllegalArgumentException("决策树不允许使用聚合节点 [" + name(node) + "]，请使用决策流");
            }
        }
        if (startCount == 0) {
            throw new IllegalArgumentException("缺少开始节点（start）");
        }
        if (startCount > 1) {
            throw new IllegalArgumentException("开始节点（start）只能有一个，当前有 " + startCount + " 个");
        }

        for (int i = 0; i < edges.size(); i++) {
            JSONObject edge = edges.getJSONObject(i);
            String source = text(edge, "source");
            String target = text(edge, "target");
            if (!nodeMap.containsKey(source)) {
                throw new IllegalArgumentException("连线 source 指向不存在的节点: " + source);
            }
            if (!nodeMap.containsKey(target)) {
                throw new IllegalArgumentException("连线 target 指向不存在的节点: " + target);
            }
            outgoing.get(source).add(edge);
            incoming.get(target).add(edge);
        }

        if (!incoming.get(startId).isEmpty()) {
            throw new IllegalArgumentException("开始节点不能有入边");
        }

        if (hasCycle(nodeMap.keySet(), outgoing)) {
            throw new IllegalArgumentException((treeMode ? "决策树" : "决策流") + "不允许存在循环路径");
        }

        Set<String> reachable = reachableFrom(startId, outgoing);
        if (reachable.size() != nodeMap.size()) {
            Set<String> unreachable = new LinkedHashSet<>(nodeMap.keySet());
            unreachable.removeAll(reachable);
            throw new IllegalArgumentException("存在从开始节点不可达的节点: " + String.join(", ", unreachable));
        }
        boolean reachableEnd = reachable.stream()
                .map(nodeMap::get)
                .anyMatch(node -> "end".equals(node.getString("type")));
        if (!reachableEnd) {
            throw new IllegalArgumentException("缺少从开始节点可达的结束节点");
        }

        for (Map.Entry<String, JSONObject> entry : nodeMap.entrySet()) {
            String id = entry.getKey();
            JSONObject node = entry.getValue();
            String type = node.getString("type");
            List<JSONObject> out = outgoing.getOrDefault(id, Collections.emptyList());
            List<JSONObject> in = incoming.getOrDefault(id, Collections.emptyList());
            if ("start".equals(type) || "task".equals(type) || "join".equals(type)) {
                if (out.size() != 1) {
                    throw new IllegalArgumentException("节点 [" + name(node) + "] 必须且只能有一条出边");
                }
            } else if ("end".equals(type) && !out.isEmpty()) {
                throw new IllegalArgumentException("结束节点 [" + name(node) + "] 不能有出边");
            } else if ("decision".equals(type)) {
                if (out.size() < 2) {
                    throw new IllegalArgumentException("判断节点 [" + name(node) + "] 至少需要两条出边");
                }
                long defaults = out.stream()
                        .filter(edge -> !GraphScriptGenerator.isConditionalEdge(edge))
                        .count();
                if (defaults > 1) {
                    throw new IllegalArgumentException("判断节点 [" + name(node) + "] 最多只能有一个默认分支");
                }
            }
            if ("join".equals(type) && in.size() < 2) {
                throw new IllegalArgumentException("聚合节点 [" + name(node) + "] 至少两条入边");
            }
            if (treeMode && !"start".equals(type) && in.size() > 1) {
                throw new IllegalArgumentException("决策树中节点 [" + name(node) + "] 有多条入边，不允许分支汇合");
            }
        }

        return new ValidationResult(nodeMap, outgoing, incoming, startId);
    }

    private static String text(JSONObject value, String key) {
        if (value == null) return null;
        String text = value.getString(key);
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private static String name(JSONObject node) {
        String name = text(node, "name");
        return name == null ? node.getString("id") : name;
    }

    private static Set<String> reachableFrom(
            String startId, Map<String, List<JSONObject>> outgoing) {
        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!reachable.add(current)) continue;
            for (JSONObject edge : outgoing.getOrDefault(current, Collections.emptyList())) {
                queue.add(edge.getString("target"));
            }
        }
        return reachable;
    }

    private static boolean hasCycle(
            Set<String> nodeIds, Map<String, List<JSONObject>> outgoing) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String nodeId : nodeIds) {
            if (hasCycleFrom(nodeId, outgoing, visiting, visited)) return true;
        }
        return false;
    }

    private static boolean hasCycleFrom(
            String nodeId, Map<String, List<JSONObject>> outgoing,
            Set<String> visiting, Set<String> visited) {
        if (visited.contains(nodeId)) return false;
        if (!visiting.add(nodeId)) return true;
        for (JSONObject edge : outgoing.getOrDefault(nodeId, Collections.emptyList())) {
            if (hasCycleFrom(edge.getString("target"), outgoing, visiting, visited)) return true;
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    public static final class ValidationResult {
        private final Map<String, JSONObject> nodeMap;
        private final Map<String, List<JSONObject>> outgoing;
        private final Map<String, List<JSONObject>> incoming;
        private final String startId;

        private ValidationResult(
                Map<String, JSONObject> nodeMap,
                Map<String, List<JSONObject>> outgoing,
                Map<String, List<JSONObject>> incoming,
                String startId) {
            this.nodeMap = nodeMap;
            this.outgoing = outgoing;
            this.incoming = incoming;
            this.startId = startId;
        }

        public Map<String, JSONObject> getNodeMap() {
            return nodeMap;
        }

        public Map<String, List<JSONObject>> getOutgoing() {
            return outgoing;
        }

        public Map<String, List<JSONObject>> getIncoming() {
            return incoming;
        }

        public String getStartId() {
            return startId;
        }
    }
}
