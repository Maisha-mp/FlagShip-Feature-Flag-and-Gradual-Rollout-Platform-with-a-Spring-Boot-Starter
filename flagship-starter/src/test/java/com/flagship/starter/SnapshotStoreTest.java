package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.Snapshot;
import com.flagship.codec.SnapshotCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreTest {

    private static final String SNAPSHOT = """
            {"project":"checkout-app","environment":"PRODUCTION","generatedAt":"2026-11-19T07:49:51.554061200Z",
             "flags":[{"key":"new-checkout","type":"BOOLEAN","variations":{"on":true,"off":false},
               "config":{"killSwitchOn":false,"offVariation":"off","defaultVariation":"off",
                 "userTargets":{"user-1":"on"},
                 "rules":[{"id":"r1","variation":"on","condition":{"type":"condition","attribute":"country","operator":"IN","values":["BD","IN"]}}],
                 "rollout":{"variation":"on","percent":10.0}}}]}
            """;

    @TempDir
    Path dir;

    private SnapshotStore store(SnapshotFetcher fetcher) {
        return new SnapshotStore(fetcher,
                new SnapshotFile(dir.resolve("snapshot.json")),
                new SnapshotCodec(new ObjectMapper()));
    }

    @Test
    void refreshLoadsAndSavesFile() throws IOException {
        SnapshotStore store = store(() -> SNAPSHOT);

        assertTrue(store.refresh());
        assertNotNull(store.flag("new-checkout"));
        assertEquals(SNAPSHOT, Files.readString(dir.resolve("snapshot.json")));
    }

    @Test
    void serverDownKeepsLastGoodSnapshot() {
        AtomicBoolean up = new AtomicBoolean(true);
        SnapshotStore store = store(() -> {
            if (!up.get()) {
                throw new IOException("server down");
            }
            return SNAPSHOT;
        });

        assertTrue(store.refresh());
        Snapshot first = store.current();

        up.set(false);
        assertFalse(store.refresh());
        assertSame(first, store.current());
    }

    @Test
    void garbageResponseIsIgnored() {
        SnapshotStore store = store(() -> "oops");

        assertFalse(store.refresh());
        assertNull(store.current());
    }

    @Test
    void garbageDoesNotReplaceGoodSnapshotOrFile() throws IOException {
        AtomicReference<String> body = new AtomicReference<>(SNAPSHOT);
        SnapshotStore store = store(body::get);
        assertTrue(store.refresh());

        body.set("oops");
        assertFalse(store.refresh());

        assertNotNull(store.flag("new-checkout"));
        assertEquals(SNAPSHOT, Files.readString(dir.resolve("snapshot.json")));
    }

    @Test
    void startsFromFileWhenServerIsDown() {
        SnapshotStore online = store(() -> SNAPSHOT);
        assertTrue(online.refresh());

        SnapshotStore offline = store(() -> {
            throw new IOException("server down");
        });
        offline.initialize();

        assertNotNull(offline.flag("new-checkout"));
        assertEquals("checkout-app", offline.current().project());
    }

    @Test
    void noFileAndNoServerGivesNoSnapshotAndNoCrash() {
        SnapshotStore store = store(() -> {
            throw new IOException("server down");
        });

        store.initialize();

        assertFalse(store.isLoaded());
        assertNull(store.flag("new-checkout"));
    }

    @Test
    void corruptedFileIsIgnored() throws IOException {
        Files.writeString(dir.resolve("snapshot.json"), "garbage");
        SnapshotStore store = store(() -> {
            throw new IOException("server down");
        });

        store.initialize();

        assertNull(store.current());
    }
}