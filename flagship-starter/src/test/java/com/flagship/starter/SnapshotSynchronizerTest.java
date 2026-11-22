package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.SnapshotCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotSynchronizerTest {

    private static final String SNAPSHOT = """
            {"project":"checkout-app","environment":"PRODUCTION","generatedAt":"2026-11-19T07:49:51.554061200Z",
             "flags":[{"key":"new-checkout","type":"BOOLEAN","variations":{"on":true,"off":false},
               "config":{"killSwitchOn":false,"offVariation":"off","defaultVariation":"off",
                 "userTargets":{},"rules":[],"rollout":null}}]}
            """;

    @TempDir
    Path dir;

    private SnapshotStore store(SnapshotFetcher fetcher) {
        return new SnapshotStore(fetcher,
                new SnapshotFile(dir.resolve("snapshot.json")),
                new SnapshotCodec(new ObjectMapper()));
    }

    private static SnapshotFetcher slowServer() {
        return () -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
            return SNAPSHOT;
        };
    }

    @Test
    void startupDoesNotWaitLongerThanTheLimitWhenServerIsSlow() {
        SnapshotSynchronizer sync = new SnapshotSynchronizer(
                store(slowServer()), Duration.ofMillis(300), Duration.ofSeconds(30));
        try {
            long start = System.nanoTime();
            sync.start();
            long millis = (System.nanoTime() - start) / 1_000_000;

            assertTrue(millis < 1500, "start() took " + millis + " ms");
        } finally {
            sync.close();
        }
    }

    @Test
    void localCopyIsUsableRightAfterStartEvenIfServerIsSlow() throws IOException {
        Files.writeString(dir.resolve("snapshot.json"), SNAPSHOT);
        SnapshotStore store = store(slowServer());
        SnapshotSynchronizer sync = new SnapshotSynchronizer(
                store, Duration.ofMillis(300), Duration.ofSeconds(30));
        try {
            sync.start();

            assertTrue(store.isLoaded());
        } finally {
            sync.close();
        }
    }

    @Test
    void pollsRepeatedly() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        SnapshotStore store = store(() -> {
            calls.incrementAndGet();
            return SNAPSHOT;
        });
        SnapshotSynchronizer sync = new SnapshotSynchronizer(
                store, Duration.ofSeconds(1), Duration.ofMillis(100));
        try {
            sync.start();

            long deadline = System.currentTimeMillis() + 3000;
            while (calls.get() < 3 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(calls.get() >= 3, "only " + calls.get() + " downloads happened");
        } finally {
            sync.close();
        }
    }
}