package com.hengshucredit.rule.server.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConsolePermissionCodes {

    private static final List<PermissionDefinition> CATALOG = buildCatalog();

    private ConsolePermissionCodes() {
    }

    public static List<PermissionDefinition> catalog() {
        return CATALOG;
    }

    public static Set<String> allCodes() {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (PermissionDefinition definition : CATALOG) {
            codes.add(definition.code());
        }
        return Collections.unmodifiableSet(codes);
    }

    private static List<PermissionDefinition> buildCatalog() {
        List<PermissionDefinition> definitions = new ArrayList<>();
        addGovernedResource(definitions, "field", "字段管理", "/variable", 100);
        addGovernedResource(definitions, "model", "模型管理", "/model", 200);
        addGovernedResource(definitions, "datasource", "外数管理", "/datasource", 300);
        addGovernedResource(definitions, "database", "数据库管理", "/database", 400);
        addGovernedResource(definitions, "function", "函数管理", "/function", 500);
        addGovernedResource(definitions, "rule", "规则管理", "/rule", 600);
        addGovernedResource(definitions, "experiment", "分流管理", "/experiment", 700);
        addGovernedResource(definitions, "project", "项目管理", "/project", 800);
        definitions.add(new PermissionDefinition(
                "approval:view", "查看审批", "审批管理", "MENU", "/approval", 900));
        definitions.add(new PermissionDefinition(
                "approval:submit", "提交审批", "审批管理", "ACTION", null, 901));
        definitions.add(new PermissionDefinition(
                "approval:approve", "审批处理", "审批管理", "ACTION", null, 902));
        definitions.add(new PermissionDefinition(
                "account:view", "查看账户", "账户管理", "MENU", "/account", 1000));
        definitions.add(new PermissionDefinition(
                "account:manage", "管理账户", "账户管理", "ACTION", null, 1001));
        definitions.add(new PermissionDefinition(
                "role:view", "查看角色", "账户管理", "ACTION", null, 1002));
        definitions.add(new PermissionDefinition(
                "role:manage", "管理角色", "账户管理", "ACTION", null, 1003));
        return Collections.unmodifiableList(definitions);
    }

    private static void addGovernedResource(List<PermissionDefinition> definitions,
                                            String code,
                                            String name,
                                            String menuPath,
                                            int order) {
        definitions.add(new PermissionDefinition(
                code + ":view", "查看" + name, name, "MENU", menuPath, order));
        definitions.add(new PermissionDefinition(
                code + ":edit", "编辑" + name, name, "ACTION", null, order + 1));
        definitions.add(new PermissionDefinition(
                code + ":submit", "提交" + name + "审批", name, "ACTION", null, order + 2));
    }

    public record PermissionDefinition(String code,
                                       String name,
                                       String group,
                                       String type,
                                       String menuPath,
                                       int sortOrder) {
    }
}
