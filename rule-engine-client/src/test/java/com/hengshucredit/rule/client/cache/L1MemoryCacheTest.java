package com.hengshucredit.rule.client.cache;

import org.junit.Test;

import java.util.Collections;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class L1MemoryCacheTest {

    @Test
    public void replaceSnapshotRemovesRulesMissingFromSuccessfulFullSync() {
        L1MemoryCache cache = new L1MemoryCache(10);
        cache.put(rule("obsolete"));
        cache.put(rule("current"));

        cache.replaceSnapshot(Collections.singletonList(rule("current")));

        assertNull(cache.get("obsolete"));
        assertEquals("current", cache.get("current").getRuleCode());
        assertEquals(1, cache.size());
    }

    @Test
    public void incrementalPutWaitsForSnapshotCommitInsteadOfWritingTheReplacedMap() throws Exception {
        L1MemoryCache cache = new L1MemoryCache(10);
        CountDownLatch snapshotEntered = new CountDownLatch(1);
        CountDownLatch allowSnapshotCommit = new CountDownLatch(1);
        Thread snapshot = new Thread(() -> cache.replaceSnapshot(new BlockingRuleList(
                rule("synced"), snapshotEntered, allowSnapshotCommit)));
        snapshot.start();
        assertEquals(true, snapshotEntered.await(2, TimeUnit.SECONDS));

        Thread incremental = new Thread(() -> cache.put(rule("pushed")));
        incremental.start();
        allowSnapshotCommit.countDown();
        snapshot.join(2000);
        incremental.join(2000);

        assertEquals("synced", cache.get("synced").getRuleCode());
        assertEquals("pushed", cache.get("pushed").getRuleCode());
    }

    private CachedRule rule(String code) {
        CachedRule rule = new CachedRule();
        rule.setRuleCode(code);
        return rule;
    }

    private static class BlockingRuleList extends AbstractList<CachedRule> {
        private final CachedRule rule;
        private final CountDownLatch entered;
        private final CountDownLatch release;

        private BlockingRuleList(CachedRule rule, CountDownLatch entered, CountDownLatch release) {
            this.rule = rule;
            this.entered = entered;
            this.release = release;
        }

        @Override
        public CachedRule get(int index) {
            if (index != 0) throw new IndexOutOfBoundsException();
            entered.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Snapshot release timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            return rule;
        }

        @Override
        public int size() {
            return 1;
        }
    }
}
