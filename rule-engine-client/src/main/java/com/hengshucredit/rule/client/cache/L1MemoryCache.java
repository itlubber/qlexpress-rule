package com.hengshucredit.rule.client.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class L1MemoryCache {

    private static final Logger log = LoggerFactory.getLogger(L1MemoryCache.class);
    private final Object cacheLock = new Object();
    private volatile ConcurrentHashMap<String, CachedRule> cache;
    private final int maxSize;

    public L1MemoryCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("L1 cache maxSize must be greater than zero");
        }
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>(maxSize);
    }

    public CachedRule get(String ruleCode) {
        return cache.get(ruleCode);
    }

    public void put(CachedRule rule) {
        synchronized (cacheLock) {
            if (cache.size() >= maxSize && !cache.containsKey(rule.getRuleCode())) {
                String toEvict = cache.keySet().iterator().next();
                cache.remove(toEvict);
                log.debug("L1 cache evicted: {}", toEvict);
            }
            cache.put(rule.getRuleCode(), rule);
        }
    }

    public void remove(String ruleCode) {
        synchronized (cacheLock) {
            cache.remove(ruleCode);
        }
    }

    public void clear() {
        synchronized (cacheLock) {
            cache.clear();
        }
    }

    /**
     * 使用服务端成功返回的完整快照原子替换本地缓存。
     * 构建快照过程中不会触碰当前缓存，避免同步失败或构建异常时丢失已缓存规则。
     */
    public void replaceSnapshot(List<CachedRule> rules) {
        if (rules == null) {
            throw new IllegalArgumentException("Rule snapshot must not be null");
        }
        synchronized (cacheLock) {
            ConcurrentHashMap<String, CachedRule> snapshot = new ConcurrentHashMap<>(maxSize);
            for (CachedRule rule : rules) {
                if (rule == null || rule.getRuleCode() == null) {
                    continue;
                }
                if (snapshot.size() >= maxSize && !snapshot.containsKey(rule.getRuleCode())) {
                    String toEvict = snapshot.keySet().iterator().next();
                    snapshot.remove(toEvict);
                    log.debug("L1 snapshot evicted: {}", toEvict);
                }
                snapshot.put(rule.getRuleCode(), rule);
            }
            cache = snapshot;
        }
    }

    public int size() {
        return cache.size();
    }

    public Map<String, Integer> getVersions() {
        ConcurrentHashMap<String, CachedRule> snapshot = cache;
        Map<String, Integer> versions = new LinkedHashMap<>();
        snapshot.forEach((k, v) -> versions.put(k, v.getVersion()));
        return versions;
    }
}
