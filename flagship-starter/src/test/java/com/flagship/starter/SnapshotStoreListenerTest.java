package com.flagship.starter;

import static com.flagship.starter.TestSnapshots.flag;
import static com.flagship.starter.TestSnapshots.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.SnapshotCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreListenerTest {

    @TempDir
    Path dir;

    private SnapshotStore store(SnapshotFetcher fetcher) {
        return new SnapshotStore(fetcher,
                new SnapshotFile(dir.resolve("snapshot.json")),
                new SnapshotCodec(new ObjectMapper()));
    }

    @Test
    void listenerHearsAboutFlagsAddedByTheFirstRefresh() {
        SnapshotStore store = store(() -> snapshot(flag("a", "off"), flag("b", "off")));
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        store.addListener(events::add);

        assertTrue(store.refresh());

        assertEquals(1, events.size());
        assertEquals(2, events.get(0).changes().size());
        assertTrue(events.get(0).affects("a"));
        assertFalse(events.get(0).affects("zzz"));
    }

    @Test
    void listenerHearsAboutChangesButNotAboutAnUnchangedRefresh() {
        AtomicReference<String> body = new AtomicReference<>(snapshot(flag("a", "off")));
        SnapshotStore store = store(body::get);
        store.refresh();

        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        store.addListener(events::add);

        store.refresh();   // same flags as before
        assertEquals(0, events.size());

        body.set(snapshot(flag("a", "on")));
        store.refresh();
        assertEquals(1, events.size());
        assertEquals(FlagChange.Type.UPDATED, events.get(0).changes().get(0).type());
    }

    @Test
    void aRemovedListenerHearsNothingMore() {
        AtomicReference<String> body = new AtomicReference<>(snapshot(flag("a", "off")));
        SnapshotStore store = store(body::get);
        AtomicInteger calls = new AtomicInteger();
        FlagChangeListener listener = event -> calls.incrementAndGet();
        store.addListener(listener);

        store.refresh();
        assertEquals(1, calls.get());

        store.removeListener(listener);
        body.set(snapshot(flag("a", "on")));
        store.refresh();

        assertEquals(1, calls.get());
    }

    @Test
    void aListenerThatThrowsDoesNotBreakOthersOrTheRefresh() {
        SnapshotStore store = store(() -> snapshot(flag("a", "off")));
        AtomicInteger heard = new AtomicInteger();
        store.addListener(event -> {
            throw new IllegalStateException("bad listener");
        });
        store.addListener(event -> heard.incrementAndGet());

        assertTrue(store.refresh());

        assertEquals(1, heard.get());
        assertNotNull(store.flag("a"));
    }

    @Test
    void aFailedRefreshDoesNotNotifyAnyone() {
        SnapshotStore store = store(() -> {
            throw new IOException("server down");
        });
        AtomicInteger calls = new AtomicInteger();
        store.addListener(event -> calls.incrementAndGet());

        assertFalse(store.refresh());

        assertEquals(0, calls.get());
    }

    @Test
    void loadingFromTheLocalCopyNotifiesToo() throws IOException {
        Files.writeString(dir.resolve("snapshot.json"), snapshot(flag("a", "off")));
        SnapshotStore store = store(() -> {
            throw new IOException("server down");
        });
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        store.addListener(events::add);

        store.initialize();

        assertEquals(1, events.size());
        assertEquals(FlagChange.Type.ADDED, events.get(0).changes().get(0).type());
    }
}