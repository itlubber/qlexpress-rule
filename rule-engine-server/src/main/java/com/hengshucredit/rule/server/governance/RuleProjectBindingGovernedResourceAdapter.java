package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionRef;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionRefMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class RuleProjectBindingGovernedResourceAdapter
        implements GovernedResourceAdapter {

    private final RuleDefinitionRefMapper refMapper;
    private final RuleDefinitionMapper definitionMapper;
    private final RuleProjectMapper projectMapper;

    public RuleProjectBindingGovernedResourceAdapter(
            RuleDefinitionRefMapper refMapper,
            RuleDefinitionMapper definitionMapper,
            RuleProjectMapper projectMapper) {
        this.refMapper = refMapper;
        this.definitionMapper = definitionMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public String resourceType() {
        return GovernanceResourceTypes.RULE_PROJECT_BINDING;
    }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        RuleDefinitionRef ref = refMapper.selectById(resourceId);
        if (ref == null) {
            throw new IllegalArgumentException(
                    "项目规则关联不存在: " + resourceId);
        }
        return snapshot(ref, "ACTIVE");
    }

    @Override
    public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
        Map<String, Object> source = CanonicalJson.readMap(
                draft == null ? null : draft.snapshotJson());
        Map<String, Object> normalized = new LinkedHashMap<>();
        putId(normalized, "id", source.get("id"));
        putId(normalized, "definitionId", source.get("definitionId"));
        putId(normalized, "projectId", source.get("projectId"));
        putDisplaySummaries(normalized);
        return new ResourceSnapshot(
                CanonicalJson.write(normalized),
                draft.effectiveStatus() == null
                        ? "ACTIVE" : draft.effectiveStatus(),
                null, null);
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        Map<String, Object> value =
                CanonicalJson.readMap(draft.snapshotJson());
        Long bindingId = longValue(value.get("id"));
        Long definitionId = longValue(value.get("definitionId"));
        Long projectId = longValue(value.get("projectId"));
        List<GovernanceIssue> issues = new ArrayList<>();
        if (positive(bindingId)) {
            RuleDefinitionRef current = refMapper.selectById(bindingId);
            if (current == null) {
                issues.add(error(
                        "RULE_PROJECT_BINDING_NOT_FOUND",
                        "项目规则关联已不存在", bindingId, "$"));
            } else if (!Objects.equals(
                    definitionId, current.getDefinitionId())
                    || !Objects.equals(
                    projectId, current.getProjectId())) {
                issues.add(error(
                        "RULE_PROJECT_BINDING_CHANGED",
                        "项目规则关联已发生变化，请刷新后重试",
                        bindingId, "$"));
            }
        }
        if (!positive(definitionId)) {
            issues.add(error(
                    "RULE_PROJECT_BINDING_DEFINITION_REQUIRED",
                    "全局规则 ID 不能为空", bindingId,
                    "$.definitionId"));
        } else {
            RuleDefinition definition =
                    definitionMapper.selectById(definitionId);
            if (definition == null) {
                issues.add(error(
                        "RULE_PROJECT_BINDING_RULE_NOT_FOUND",
                        "全局规则不存在", bindingId,
                        "$.definitionId"));
            } else if (!"GLOBAL".equalsIgnoreCase(
                    definition.getScope())) {
                issues.add(error(
                        "RULE_PROJECT_BINDING_RULE_NOT_GLOBAL",
                        "只能将全局规则加入项目", bindingId,
                        "$.definitionId"));
            }
        }
        if (!positive(projectId)) {
            issues.add(error(
                    "RULE_PROJECT_BINDING_PROJECT_REQUIRED",
                    "项目 ID 不能为空", bindingId,
                    "$.projectId"));
        } else if (projectMapper.selectById(projectId) == null) {
            issues.add(error(
                    "RULE_PROJECT_BINDING_PROJECT_NOT_FOUND",
                    "项目不存在", bindingId, "$.projectId"));
        }
        if (bindingId == null && positive(definitionId)
                && positive(projectId)) {
            Long count = refMapper.selectCount(
                    new LambdaQueryWrapper<RuleDefinitionRef>()
                            .eq(RuleDefinitionRef::getDefinitionId,
                                    definitionId)
                            .eq(RuleDefinitionRef::getProjectId,
                                    projectId));
            if (count != null && count > 0) {
                issues.add(error(
                        "RULE_PROJECT_BINDING_DUPLICATE",
                        "该全局规则已加入当前项目", null, "$"));
            }
        }
        return issues;
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        Map<String, Object> value =
                CanonicalJson.readMap(draft.snapshotJson());
        List<ResourceDependencyRef> dependencies = new ArrayList<>();
        Long definitionId = longValue(value.get("definitionId"));
        if (positive(definitionId)) {
            dependencies.add(new ResourceDependencyRef(
                    GovernanceResourceTypes.RULE, definitionId,
                    GovernanceResourceTypes.RULE,
                    "$.definitionId", "REFERENCES", true));
        }
        Long projectId = longValue(value.get("projectId"));
        if (positive(projectId)) {
            dependencies.add(new ResourceDependencyRef(
                    GovernanceResourceTypes.PROJECT, projectId,
                    GovernanceResourceTypes.PROJECT,
                    "$.projectId", "BELONGS_TO", true));
        }
        return dependencies;
    }

    @Override
    public ResourceDiff diff(ResourceSnapshot left,
                             ResourceSnapshot right) {
        return JsonResourceDiff.compare(left, right);
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        String action = context.action() == null
                ? "" : context.action().toUpperCase(Locale.ROOT);
        if ("CREATE".equals(action)) {
            Map<String, Object> value = CanonicalJson.readMap(
                    context.snapshot().snapshotJson());
            RuleDefinitionRef ref = new RuleDefinitionRef();
            ref.setDefinitionId(longValue(value.get("definitionId")));
            ref.setProjectId(longValue(value.get("projectId")));
            ref.setCreateTime(LocalDateTime.now());
            if (refMapper.insert(ref) != 1 || ref.getId() == null) {
                throw new IllegalStateException("项目规则关联创建失败");
            }
            return new AppliedResource(ref.getId(),
                    context.nextVersionNo(), "ACTIVE", null);
        }
        if ("DELETE".equals(action)) {
            if (!positive(context.resourceId())) {
                throw new IllegalArgumentException(
                        "移出项目必须指定关联 ID");
            }
            if (refMapper.deleteById(context.resourceId()) != 1) {
                throw new IllegalStateException(
                        "项目规则关联不存在或已被移除");
            }
            return new AppliedResource(context.resourceId(),
                    context.nextVersionNo(), "DELETED", null);
        }
        throw new IllegalArgumentException(
                "项目规则关联不支持动作: " + context.action());
    }

    private ResourceSnapshot snapshot(RuleDefinitionRef ref,
                                      String effectiveStatus) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", ref.getId());
        value.put("definitionId", ref.getDefinitionId());
        value.put("projectId", ref.getProjectId());
        putDisplaySummaries(value);
        return new ResourceSnapshot(CanonicalJson.write(value),
                effectiveStatus, null, null);
    }

    private GovernanceIssue error(String code, String message,
                                  Long resourceId,
                                  String referencePath) {
        return GovernanceIssue.error(code, message,
                GovernanceResourceTypes.RULE_PROJECT_BINDING,
                resourceId, referencePath);
    }

    private void putId(Map<String, Object> value,
                       String key, Object rawValue) {
        Long id = longValue(rawValue);
        if (id != null) {
            value.put(key, id);
        }
    }

    private void putDisplaySummaries(Map<String, Object> value) {
        Long definitionId = longValue(value.get("definitionId"));
        if (positive(definitionId)) {
            RuleDefinition definition =
                    definitionMapper.selectById(definitionId);
            if (definition != null && definition.getRuleName() != null
                    && !definition.getRuleName().isBlank()) {
                value.put("ruleName", definition.getRuleName());
            }
        }
        Long projectId = longValue(value.get("projectId"));
        if (positive(projectId)) {
            RuleProject project = projectMapper.selectById(projectId);
            if (project != null && project.getProjectName() != null
                    && !project.getProjectName().isBlank()) {
                value.put("projectName", project.getProjectName());
            }
        }
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
