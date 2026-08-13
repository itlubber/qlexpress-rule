package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.publish.RuleFunctionPushService;

import java.util.List;
import java.util.Set;

public class FunctionGovernedResourceAdapter
        implements GovernedResourceAdapter {

    private final SimpleEntityGovernedResourceAdapter<RuleFunction> delegate;
    private final RuleFunctionPushService functionPushService;

    public FunctionGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleFunction>
                    store,
            GovernanceSecretCodec secretCodec,
            RuleFunctionPushService functionPushService) {
        this.delegate = new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.FUNCTION,
                RuleFunction.class,
                store,
                RuleFunction::getId,
                RuleFunction::setId,
                RuleFunction::getStatus,
                RuleFunction::setStatus,
                Set.of("funcCode", "funcName", "implType"),
                Set.of(), secretCodec);
        this.functionPushService = functionPushService;
    }

    @Override
    public String resourceType() {
        return delegate.resourceType();
    }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        return delegate.loadEffective(resourceId);
    }

    @Override
    public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
        RuleFunction function = function(draft);
        functionPushService.prepare(function, "FUNC_UPDATE");
        return delegate.normalizeDraft(snapshot(draft, function));
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        return delegate.validate(draft);
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        return delegate.collectDependencies(draft);
    }

    @Override
    public ResourceDiff diff(ResourceSnapshot left,
                             ResourceSnapshot right) {
        return delegate.diff(left, right);
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        RuleFunction function = function(context.snapshot());
        String action = "DELETE".equalsIgnoreCase(context.action())
                ? "FUNC_DELETE" : "FUNC_UPDATE";
        RulePushMessage message = functionPushService.prepare(function,
                action);
        ResourceSnapshot normalized = delegate.normalizeDraft(
                snapshot(context.snapshot(), function));
        AppliedResource applied = delegate.apply(new ApprovalApplyContext(
                context.requestId(), context.resourceId(),
                context.nextVersionNo(), context.action(), normalized,
                context.actor(), context.sourceVersionId()));
        functionPushService.push(message);
        return applied;
    }

    private RuleFunction function(ResourceSnapshot snapshot) {
        RuleFunction function = JSON.parseObject(snapshot.snapshotJson(),
                RuleFunction.class);
        if (function == null) {
            throw new IllegalArgumentException("函数审批快照无效");
        }
        return function;
    }

    private ResourceSnapshot snapshot(ResourceSnapshot original,
                                      RuleFunction function) {
        return new ResourceSnapshot(JSON.toJSONString(function),
                original.effectiveStatus(),
                original.secretPayloadCiphertext(),
                original.secretDigest());
    }
}
