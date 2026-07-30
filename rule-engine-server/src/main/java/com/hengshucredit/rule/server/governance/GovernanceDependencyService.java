package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernanceDependencySnapshot;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.GovernanceDependencySnapshotMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class GovernanceDependencyService {

    private static final Set<String> EFFECTIVE_STATUSES = Set.of(
            "ACTIVE", "ENABLED", "PUBLISHED", "APPROVED", "1");

    @Autowired(required = false)
    private GovernedResourceMapper resourceMapper;
    @Autowired(required = false)
    private GovernedResourceVersionMapper versionMapper;
    @Autowired(required = false)
    private GovernanceDependencySnapshotMapper dependencySnapshotMapper;
    @Autowired(required = false)
    private GovernanceResourceBootstrapService bootstrapService;

    public GovernancePreflightReport preflight(
            String sourceResourceType,
            Long sourceResourceId,
            List<ResourceDependencyRef> dependencies,
            List<GovernanceIssue> validationIssues) {
        List<GovernanceIssue> errors = new ArrayList<>();
        List<GovernanceIssue> warnings = new ArrayList<>();
        splitIssues(validationIssues, errors, warnings);

        List<GovernancePreflightReport.ResolvedDependency> resolved =
                new ArrayList<>();
        for (ResourceDependencyRef dependency : safe(dependencies)) {
            resolved.add(resolve(sourceResourceType, sourceResourceId,
                    dependency, errors, warnings));
        }
        resolved.sort(Comparator
                .comparing(GovernancePreflightReport.ResolvedDependency
                        ::targetResourceType)
                .thenComparing(
                        GovernancePreflightReport.ResolvedDependency
                                ::targetResourceId)
                .thenComparing(value -> value.referencePath() == null
                        ? "" : value.referencePath()));
        return report(errors, warnings, resolved);
    }

    public GovernancePreflightReport revalidate(
            List<GovernancePreflightReport.ResolvedDependency> submitted) {
        List<GovernanceIssue> errors = new ArrayList<>();
        List<GovernanceIssue> warnings = new ArrayList<>();
        List<GovernancePreflightReport.ResolvedDependency> resolved =
                new ArrayList<>();
        for (GovernancePreflightReport.ResolvedDependency previous
                : safeResolved(submitted)) {
            GovernedResource target = findTarget(
                    previous.targetResourceType(),
                    previous.targetResourceId());
            if (target == null) {
                errors.add(issue("DEPENDENCY_MISSING",
                        "依赖资源不存在", previous));
                resolved.add(withIssue(previous, "MISSING",
                        "DEPENDENCY_MISSING", "依赖资源不存在"));
                continue;
            }
            if (!isEffective(target)) {
                errors.add(issue("DEPENDENCY_INACTIVE",
                        "依赖资源当前未生效", previous));
                resolved.add(current(previous, target, "INACTIVE",
                        null, "DEPENDENCY_INACTIVE", "依赖资源当前未生效"));
                continue;
            }
            if (!target.getEffectiveVersionId()
                    .equals(previous.targetVersionId())) {
                errors.add(issue("DEPENDENCY_VERSION_CHANGED",
                        "依赖资源在提交后已变更版本，请重新提交审批", previous));
                GovernedResourceVersion current =
                        findVersion(target.getEffectiveVersionId());
                resolved.add(current(previous, target,
                        "VERSION_CHANGED", current,
                        "DEPENDENCY_VERSION_CHANGED",
                        "依赖资源在提交后已变更版本，请重新提交审批"));
                continue;
            }
            GovernedResourceVersion version =
                    findVersion(target.getEffectiveVersionId());
            if (version == null) {
                errors.add(issue("DEPENDENCY_VERSION_MISSING",
                        "依赖资源的生效版本不存在", previous));
                resolved.add(current(previous, target,
                        "VERSION_MISSING", null,
                        "DEPENDENCY_VERSION_MISSING",
                        "依赖资源的生效版本不存在"));
                continue;
            }
            if (!safeEquals(previous.targetDigest(),
                    version.getSnapshotDigest())) {
                errors.add(issue("DEPENDENCY_DIGEST_CHANGED",
                        "依赖资源版本摘要已变化，请重新提交审批", previous));
                resolved.add(current(previous, target,
                        "DIGEST_CHANGED", version,
                        "DEPENDENCY_DIGEST_CHANGED",
                        "依赖资源版本摘要已变化，请重新提交审批"));
                continue;
            }
            resolved.add(current(previous, target, "RESOLVED",
                    version, null, null));
        }
        resolved.sort(Comparator
                .comparing(GovernancePreflightReport.ResolvedDependency
                        ::targetResourceType)
                .thenComparing(
                        GovernancePreflightReport.ResolvedDependency
                                ::targetResourceId));
        return report(errors, warnings, resolved);
    }

    public void persist(GovernanceApprovalRequest request,
                        GovernancePreflightReport report,
                        Long versionId) {
        if (dependencySnapshotMapper == null) {
            return;
        }
        dependencySnapshotMapper.delete(new LambdaUpdateWrapper<
                GovernanceDependencySnapshot>()
                .eq(GovernanceDependencySnapshot::getRequestId,
                        request.getId()));
        for (GovernancePreflightReport.ResolvedDependency dependency
                : report.dependencies()) {
            GovernanceDependencySnapshot row =
                    new GovernanceDependencySnapshot();
            row.setRequestId(request.getId());
            row.setVersionId(versionId);
            row.setSourceResourceType(request.getResourceType());
            row.setSourceResourceId(request.getResourceId());
            row.setTargetResourceType(
                    dependency.targetResourceType());
            row.setTargetResourceId(dependency.targetResourceId());
            row.setTargetVersionId(dependency.targetVersionId());
            row.setTargetVersionNo(dependency.targetVersionNo());
            row.setReferencePath(dependency.referencePath());
            row.setRelationType(dependency.relationType());
            row.setRequired(dependency.required() ? 1 : 0);
            row.setResolutionStatus(dependency.resolutionStatus());
            row.setTargetDigest(dependency.targetDigest());
            row.setIssueCode(dependency.issueCode());
            row.setIssueMessage(dependency.issueMessage());
            dependencySnapshotMapper.insert(row);
        }
    }

    public List<GovernancePreflightReport.ResolvedDependency> load(
            Long requestId) {
        if (dependencySnapshotMapper == null) {
            return List.of();
        }
        return dependencySnapshotMapper.selectList(new LambdaQueryWrapper<
                        GovernanceDependencySnapshot>()
                        .eq(GovernanceDependencySnapshot::getRequestId,
                                requestId)
                        .orderByAsc(GovernanceDependencySnapshot::getId))
                .stream()
                .map(row -> new GovernancePreflightReport
                        .ResolvedDependency(
                        row.getTargetResourceType(),
                        row.getTargetResourceId(),
                        row.getTargetVersionId(),
                        row.getTargetVersionNo(),
                        row.getReferencePath(),
                        row.getRelationType(),
                        Integer.valueOf(1).equals(row.getRequired()),
                        row.getResolutionStatus(),
                        row.getTargetDigest(),
                        row.getIssueCode(),
                        row.getIssueMessage()))
                .toList();
    }

    public void bindVersion(Long requestId, Long versionId) {
        if (dependencySnapshotMapper == null) {
            return;
        }
        GovernanceDependencySnapshot update =
                new GovernanceDependencySnapshot();
        update.setVersionId(versionId);
        dependencySnapshotMapper.update(update, new LambdaUpdateWrapper<
                GovernanceDependencySnapshot>()
                .eq(GovernanceDependencySnapshot::getRequestId,
                        requestId));
    }

    protected GovernedResource findTarget(String resourceType,
                                          Long resourceId) {
        if (resourceMapper == null) {
            return null;
        }
        GovernedResource resource = resourceMapper.selectOne(
                new LambdaQueryWrapper<
                GovernedResource>()
                .eq(GovernedResource::getResourceType,
                        normalizeType(resourceType))
                .eq(GovernedResource::getResourceId, resourceId)
                .last("LIMIT 1"));
        if (resource == null && bootstrapService != null
                && resourceId != null && resourceId > 0) {
            try {
                return bootstrapService.ensure(
                        resourceType, resourceId);
            } catch (IllegalArgumentException notFound) {
                return null;
            }
        }
        return resource;
    }

    protected GovernedResourceVersion findVersion(Long versionId) {
        return versionMapper == null || versionId == null
                ? null : versionMapper.selectById(versionId);
    }

    private GovernancePreflightReport.ResolvedDependency resolve(
            String sourceResourceType,
            Long sourceResourceId,
            ResourceDependencyRef dependency,
            List<GovernanceIssue> errors,
            List<GovernanceIssue> warnings) {
        String targetType = normalizeType(
                dependency.targetResourceType());
        if (dependency.refType() != null
                && !dependency.refType().isBlank()
                && !targetType.equals(
                normalizeType(dependency.refType()))) {
            GovernanceIssue issue = GovernanceIssue.error(
                    "DEPENDENCY_TYPE_MISMATCH",
                    "依赖引用类型与目标资源类型不一致",
                    targetType, dependency.targetResourceId(),
                    dependency.referencePath());
            errors.add(issue);
            return unresolved(dependency, "TYPE_MISMATCH", issue);
        }
        if (targetType.equals(normalizeType(sourceResourceType))
                && dependency.targetResourceId().equals(sourceResourceId)) {
            GovernanceIssue issue = GovernanceIssue.error(
                    "DEPENDENCY_SELF_REFERENCE",
                    "资源不能依赖自身", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath());
            errors.add(issue);
            return unresolved(dependency, "SELF_REFERENCE", issue);
        }
        GovernedResource target = findTarget(
                targetType, dependency.targetResourceId());
        if (target == null) {
            GovernanceIssue issue = dependency.required()
                    ? GovernanceIssue.error("DEPENDENCY_MISSING",
                    "依赖资源不存在", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath())
                    : warning("DEPENDENCY_OPTIONAL_MISSING",
                    "可选依赖资源不存在", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath());
            add(issue, errors, warnings);
            return unresolved(dependency, "MISSING", issue);
        }
        if (!isEffective(target)) {
            GovernanceIssue issue = dependency.required()
                    ? GovernanceIssue.error("DEPENDENCY_INACTIVE",
                    "依赖资源当前未生效", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath())
                    : warning("DEPENDENCY_OPTIONAL_INACTIVE",
                    "可选依赖资源当前未生效", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath());
            add(issue, errors, warnings);
            return current(dependency, target, "INACTIVE",
                    null, issue.code(), issue.message());
        }
        GovernedResourceVersion version =
                findVersion(target.getEffectiveVersionId());
        if (version == null) {
            GovernanceIssue issue = GovernanceIssue.error(
                    "DEPENDENCY_VERSION_MISSING",
                    "依赖资源的生效版本不存在", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath());
            errors.add(issue);
            return current(dependency, target, "VERSION_MISSING",
                    null, issue.code(), issue.message());
        }
        return current(dependency, target, "RESOLVED",
                version, null, null);
    }

    private boolean isEffective(GovernedResource resource) {
        return resource.getEffectiveVersionId() != null
                && resource.getEffectiveStatus() != null
                && EFFECTIVE_STATUSES.contains(
                resource.getEffectiveStatus().toUpperCase(Locale.ROOT));
    }

    private GovernancePreflightReport report(
            List<GovernanceIssue> errors,
            List<GovernanceIssue> warnings,
            List<GovernancePreflightReport.ResolvedDependency> resolved) {
        StringBuilder digestInput = new StringBuilder();
        for (GovernancePreflightReport.ResolvedDependency dependency
                : resolved) {
            digestInput.append(dependency.targetResourceType()).append(':')
                    .append(dependency.targetResourceId()).append(':')
                    .append(dependency.targetVersionId()).append(':')
                    .append(dependency.targetDigest()).append(':')
                    .append(dependency.referencePath()).append('|');
        }
        return new GovernancePreflightReport(errors.isEmpty(),
                errors, warnings, resolved,
                Sha256Digests.text(digestInput.toString()));
    }

    private GovernancePreflightReport.ResolvedDependency unresolved(
            ResourceDependencyRef dependency,
            String status,
            GovernanceIssue issue) {
        return new GovernancePreflightReport.ResolvedDependency(
                normalizeType(dependency.targetResourceType()),
                dependency.targetResourceId(), null, null,
                dependency.referencePath(), dependency.relationType(),
                dependency.required(), status, null,
                issue.code(), issue.message());
    }

    private GovernancePreflightReport.ResolvedDependency current(
            ResourceDependencyRef dependency,
            GovernedResource target,
            String status,
            GovernedResourceVersion version,
            String issueCode,
            String issueMessage) {
        return new GovernancePreflightReport.ResolvedDependency(
                normalizeType(dependency.targetResourceType()),
                dependency.targetResourceId(),
                target.getEffectiveVersionId(),
                target.getEffectiveVersionNo(),
                dependency.referencePath(), dependency.relationType(),
                dependency.required(), status,
                version == null ? null : version.getSnapshotDigest(),
                issueCode, issueMessage);
    }

    private GovernancePreflightReport.ResolvedDependency current(
            GovernancePreflightReport.ResolvedDependency previous,
            GovernedResource target,
            String status,
            GovernedResourceVersion version,
            String issueCode,
            String issueMessage) {
        return new GovernancePreflightReport.ResolvedDependency(
                previous.targetResourceType(),
                previous.targetResourceId(),
                target.getEffectiveVersionId(),
                target.getEffectiveVersionNo(),
                previous.referencePath(), previous.relationType(),
                previous.required(), status,
                version == null ? null : version.getSnapshotDigest(),
                issueCode, issueMessage);
    }

    private GovernancePreflightReport.ResolvedDependency withIssue(
            GovernancePreflightReport.ResolvedDependency previous,
            String status,
            String issueCode,
            String issueMessage) {
        return new GovernancePreflightReport.ResolvedDependency(
                previous.targetResourceType(),
                previous.targetResourceId(), null, null,
                previous.referencePath(), previous.relationType(),
                previous.required(), status, null,
                issueCode, issueMessage);
    }

    private GovernanceIssue issue(
            String code,
            String message,
            GovernancePreflightReport.ResolvedDependency dependency) {
        return GovernanceIssue.error(code, message,
                dependency.targetResourceType(),
                dependency.targetResourceId(),
                dependency.referencePath());
    }

    private GovernanceIssue warning(String code,
                                    String message,
                                    String resourceType,
                                    Long resourceId,
                                    String referencePath) {
        return new GovernanceIssue("WARNING", code, message,
                resourceType, resourceId, referencePath, null);
    }

    private void add(GovernanceIssue issue,
                     List<GovernanceIssue> errors,
                     List<GovernanceIssue> warnings) {
        (issue.isError() ? errors : warnings).add(issue);
    }

    private void splitIssues(List<GovernanceIssue> issues,
                             List<GovernanceIssue> errors,
                             List<GovernanceIssue> warnings) {
        for (GovernanceIssue issue : safeIssues(issues)) {
            add(issue, errors, warnings);
        }
    }

    private String normalizeType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("资源类型不能为空");
        }
        return resourceType.trim().toUpperCase(Locale.ROOT);
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private List<ResourceDependencyRef> safe(
            List<ResourceDependencyRef> dependencies) {
        return dependencies == null ? List.of() : dependencies;
    }

    private List<GovernanceIssue> safeIssues(
            List<GovernanceIssue> issues) {
        return issues == null ? List.of() : issues;
    }

    private List<GovernancePreflightReport.ResolvedDependency> safeResolved(
            List<GovernancePreflightReport.ResolvedDependency> dependencies) {
        return dependencies == null ? List.of() : dependencies;
    }
}
