package com.hengshucredit.rule.server.service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class VariableResolutionInvocationCache {

    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> apiResponses =
            new ConcurrentHashMap<>();

    public Map<String, Object> resolve(String key, Supplier<Map<String, Object>> supplier) {
        if (key == null || supplier == null) {
            throw new IllegalArgumentException("API response cache key and supplier must not be null");
        }
        CompletableFuture<Map<String, Object>> created = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> existing = apiResponses.putIfAbsent(key, created);
        CompletableFuture<Map<String, Object>> shared = existing == null ? created : existing;
        if (existing == null) {
            try {
                created.complete(copyMap(supplier.get()));
            } catch (Throwable e) {
                created.completeExceptionally(e);
            }
        }
        try {
            return copyMap(shared.join());
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    private Object copyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] copy = new Object[length];
            for (int i = 0; i < length; i++) {
                copy[i] = copyValue(Array.get(value, i));
            }
            return copy;
        }
        return value;
    }
}
