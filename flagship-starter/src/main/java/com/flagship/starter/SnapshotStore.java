package com.flagship.starter;

import com.flagship.codec.Snapshot;
import com.flagship.codec.SnapshotCodec;
import com.flagship.core.model.Flag;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the current snapshot in memory. Reads never touch the network.
 * A failed or broken refresh never throws and never replaces a good snapshot.
 * This is the "subject" of the Observer pattern: listeners are told what changed.
 */
public final class SnapshotStore {

    private static final System.Logger LOG = System.getLogger(SnapshotStore.class.getName());

    private final SnapshotFetcher fetcher;
    private final SnapshotFile file;
    private final SnapshotCodec codec;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();
    private final List<FlagChangeListener> listeners = new CopyOnWriteArrayList<>();

    /** Makes refreshes run one at a time, so listeners are told about changes in order. */
    private final Object lock = new Object();

    public SnapshotStore(SnapshotFetcher fetcher, SnapshotFile file, SnapshotCodec codec) {
        this.fetcher = fetcher;
        this.file = file;
        this.codec = codec;
    }

    // ---------- observers ----------

    public void addListener(FlagChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(FlagChangeListener listener) {
        listeners.remove(listener);
    }

    // ---------- loading ----------

    /** Downloads, checks, stores in memory and saves to the file. True if it worked. */
    public boolean refresh() {
        synchronized (lock) {
            try {
                String json = fetcher.fetch();
                Snapshot snapshot = codec.fromJson(json);   // check BEFORE replacing anything
                Snapshot previous = current.getAndSet(snapshot);
                try {
                    file.save(json);
                } catch (IOException e) {
                    LOG.log(System.Logger.Level.WARNING,
                            "FlagShip: could not save the local snapshot copy: {0}", e.getMessage());
                }
                notifyListeners(previous, snapshot);
                return true;
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "FlagShip: could not refresh the snapshot, keeping the previous one: {0}",
                        e.getMessage());
                return false;
            }
        }
    }

    /** Loads the local copy. True if a usable snapshot was found. */
    public boolean loadFromFile() {
        synchronized (lock) {
            try {
                Optional<String> json = file.load();
                if (json.isEmpty()) {
                    return false;
                }
                Snapshot snapshot = codec.fromJson(json.get());
                Snapshot previous = current.getAndSet(snapshot);
                notifyListeners(previous, snapshot);
                return true;
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "FlagShip: could not use the local snapshot copy: {0}", e.getMessage());
                return false;
            }
        }
    }

    /** Server first; if that fails, the local copy; if that fails too, no snapshot yet. */
    public void initialize() {
        if (!refresh()) {
            loadFromFile();
        }
    }

    // ---------- reading ----------

    public Snapshot current() {
        return current.get();
    }

    public boolean isLoaded() {
        return current.get() != null;
    }

    /** The flag with this key, or null if there is no snapshot or no such flag. */
    public Flag flag(String key) {
        Snapshot snapshot = current.get();
        return snapshot == null ? null : snapshot.flag(key);
    }

    // ---------- notifying ----------

    private void notifyListeners(Snapshot previous, Snapshot snapshot) {
        if (listeners.isEmpty()) {
            return;
        }
        List<FlagChange> changes = SnapshotDiff.between(previous, snapshot);
        if (changes.isEmpty()) {
            return;   // same flags as before: stay silent
        }
        FlagChangeEvent event = new FlagChangeEvent(snapshot, changes);
        for (FlagChangeListener listener : listeners) {
            try {
                listener.onFlagsChanged(event);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "FlagShip: a change listener failed and was skipped: {0}", e.getMessage());
            }
        }
    }
}