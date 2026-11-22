package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.SnapshotCodec;
import com.flagship.core.eval.EvaluationContext;
import java.lang.ref.Reference;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Qa1HeapTest {

    private static final long LIMIT_BYTES = 20L * 1024 * 1024;   // 20 MB

    @TempDir
    Path dir;

    /** Memory in use after asking Java to clean up several times. */
    private static long usedHeap() {
        for (int i = 0; i < 5; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static String mb(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Test
    void thousandFlagsAndAFullCacheStayUnderTwentyMegabytes() {
        String json = SnapshotFixtures.snapshotJson(1000);
        SnapshotStore store = new SnapshotStore(() -> json,
                new SnapshotFile(dir.resolve("snapshot.json")),
                new SnapshotCodec(new ObjectMapper()));

        long before = usedHeap();

        assertTrue(store.refresh());
        long afterSnapshot = usedHeap();

        CachingFlagClient client = new CachingFlagClient(
                new DefaultFlagClient(store), store::current, 10_000);
        for (int i = 0; i < 10_000; i++) {
            client.evaluate(SnapshotFixtures.flagKey(i % 1000),
                    EvaluationContext.builder("user-" + i)
                            .attribute("country", "BD")
                            .attribute("plan", "pro")
                            .build());
        }
        assertEquals(10_000, client.cachedEntries());
        long afterCache = usedHeap();

        Reference.reachabilityFence(store);    // keep both alive until here
        Reference.reachabilityFence(client);

        long snapshotBytes = afterSnapshot - before;
        long totalBytes = afterCache - before;
        System.out.println("QA1 heap: 1,000 flags in memory = " + mb(snapshotBytes));
        System.out.println("QA1 heap: plus a full cache of 10,000 answers = " + mb(totalBytes)
                + " (limit " + mb(LIMIT_BYTES) + ")");

        assertTrue(totalBytes < LIMIT_BYTES, "used " + mb(totalBytes));
    }
}