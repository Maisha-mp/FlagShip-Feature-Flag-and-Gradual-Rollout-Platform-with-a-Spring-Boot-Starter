package com.flagship.starter;

import com.flagship.codec.Snapshot;
import com.flagship.codec.SnapshotCodec;
import com.flagship.core.model.Flag;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the current snapshot in memory. Reads never touch the network.
 * A failed or broken refresh never throws and never replaces a good snapshot.
 */
public final class SnapshotStore {

    private static final System.Logger LOG = System.getLogger(SnapshotStore.class.getName());

    private final SnapshotFetcher fetcher;
    private final SnapshotFile file;
    private final SnapshotCodec codec;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public SnapshotStore(SnapshotFetcher fetcher, SnapshotFile file, SnapshotCodec codec) {
        this.fetcher = fetcher;
        this.file = file;
        this.codec = codec;
    }

    /** Downloads, checks, stores in memory and saves to the file. True if it worked. */
    public boolean refresh() {
        try {
            String json = fetcher.fetch();
            Snapshot snapshot = codec.fromJson(json);   // check BEFORE replacing anything
            current.set(snapshot);
            try {
                file.save(json);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "FlagShip: could not save the local snapshot copy: {0}", e.getMessage());
            }
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "FlagShip: could not refresh the snapshot, keeping the previous one: {0}",
                    e.getMessage());
            return false;
        }
    }

    /** Loads the local copy. True if a usable snapshot was found. */
    public boolean loadFromFile() {
        try {
            Optional<String> json = file.load();
            if (json.isEmpty()) {
                return false;
            }
            current.set(codec.fromJson(json.get()));
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "FlagShip: could not use the local snapshot copy: {0}", e.getMessage());
            return false;
        }
    }

    /** Server first; if that fails, the local copy; if that fails too, no snapshot yet. */
    public void initialize() {
        if (!refresh()) {
            loadFromFile();
        }
    }

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
}