package com.hengshucredit.rule.client.cache;

import org.junit.Test;

import java.util.Collections;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class L1MemoryCacheTest {

    @Test
    public void rejectsNonPositiveMaximumSize() {
        assertThrows(IllegalArgumentException.class, () -> new L1MemoryCache(0));
        assertThrows(IllegalArgumentException.class, () -> new L1MemoryCache(-1));
    }

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

    @Test
    public void readsCurrentSnapshotWithoutWaitingForSnapshotWriter() throws Exception {
        L1MemoryCache cache = new L1MemoryCache(10);
        cache.put(rule("current", 7));
        CountDownLatch snapshotEntered = new CountDownLatch(1);
        CountDownLatch allowSnapshotCommit = new CountDownLatch(1);
        Thread snapshot = new Thread(() -> cache.replaceSnapshot(new BlockingRuleList(
                rule("replacement", 8), snapshotEntered, allowSnapshotCommit)));
        snapshot.start();
        assertTrue(snapshotEntered.await(2, TimeUnit.SECONDS));

        CountDownLatch readsCompleted = new CountDownLatch(3);
        AtomicReference<CachedRule> found = new AtomicReference<>();
        AtomicInteger size = new AtomicInteger(-1);
        AtomicReference<Map<String, Integer>> versions = new AtomicReference<>();
        Thread getReader = new Thread(() -> {
            found.set(cache.get("current"));
            readsCompleted.countDown();
        });
        Thread sizeReader = new Thread(() -> {
            size.set(cache.size());
            readsCompleted.countDown();
        });
        Thread versionsReader = new Thread(() -> {
            versions.set(cache.getVersions());
            readsCompleted.countDown();
        });
        getReader.start();
        sizeReader.start();
        versionsReader.start();

        boolean completedBeforeCommit;
        try {
            completedBeforeCommit = readsCompleted.await(1, TimeUnit.SECONDS);
        } finally {
            allowSnapshotCommit.countDown();
            snapshot.join(2000);
            getReader.join(2000);
            sizeReader.join(2000);
            versionsReader.join(2000);
        }

        assertTrue("reads should not wait for the snapshot writer", completedBeforeCommit);
        assertFalse(snapshot.isAlive());
        assertFalse(getReader.isAlive());
        assertFalse(sizeReader.isAlive());
        assertFalse(versionsReader.isAlive());
        assertEquals("current", found.get().getRuleCode());
        assertEquals(1, size.get());
        assertEquals(Collections.singletonMap("current", 7), versions.get());
        assertNull(cache.get("current"));
        assertEquals("replacement", cache.get("replacement").getRuleCode());
    }

    private CachedRule rule(String code) {
        return rule(code, 0);
    }

    private CachedRule rule(String code, int version) {
        CachedRule rule = new CachedRule();
        rule.setRuleCode(code);
        rule.setVersion(version);
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
