package com.hengshucredit.rule.server.governance;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class GovernedResourceAdapterRegistry {

    private final Map<String, GovernedResourceAdapter> adapters;

    public GovernedResourceAdapterRegistry(List<GovernedResourceAdapter> adapters) {
        Map<String, GovernedResourceAdapter> indexed = new LinkedHashMap<>();
        if (adapters != null) {
            for (GovernedResourceAdapter adapter : adapters) {
                if (adapter == null || adapter.resourceType() == null
                        || adapter.resourceType().isBlank()) {
                    throw new IllegalStateException("资源治理适配器类型不能为空");
                }
                String type = normalize(adapter.resourceType());
                if (indexed.putIfAbsent(type, adapter) != null) {
                    throw new IllegalStateException("资源治理适配器重复: " + type);
                }
            }
        }
        this.adapters = Collections.unmodifiableMap(indexed);
    }

    public GovernedResourceAdapter require(String resourceType) {
        String type = normalize(resourceType);
        GovernedResourceAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的治理资源类型: " + type);
        }
        return adapter;
    }

    public Set<String> resourceTypes() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(adapters.keySet()));
    }

    private static String normalize(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("资源类型不能为空");
        }
        return resourceType.trim().toUpperCase(Locale.ROOT);
    }
}
