package com.flagship.starter;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Keeps the store fresh: loads the local copy, tries the server for at most
 * startupTimeout, then polls in the background. Never blocks startup for longer
 * than startupTimeout and never throws because the server is down.
 */
public final class SnapshotSynchronizer {

    private static final System.Logger LOG = System.getLogger(SnapshotSynchronizer.class.getName());

    private final SnapshotStore store;
    private final Duration startupTimeout;
    private final Duration pollInterval;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "flagship-snapshot-poller");
        t.setDaemon(true);   // never keeps the application alive on its own
        return t;
    });

    public SnapshotSynchronizer(SnapshotStore store, Duration startupTimeout, Duration pollInterval) {
        if (pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("flagship.poll-interval must be greater than zero");
        }
        this.store = store;
        this.startupTimeout = startupTimeout;
        this.pollInterval = pollInterval;
    }

    public void start() {
        store.loadFromFile();   // flags are usable immediately if a local copy exists

        Callable<Boolean> firstRefresh = store::refresh;
        Future<Boolean> first = executor.submit(firstRefresh);
        try {
            first.get(startupTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "FlagShip: the server did not answer within {0} ms; starting with {1} and still trying in the background",
                    startupTimeout.toMillis(),
                    store.isLoaded() ? "the local copy" : "no flags (defaults only)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOG.log(System.Logger.Level.WARNING, "FlagShip: first refresh failed: {0}", e.getMessage());
        }

        executor.scheduleWithFixedDelay(
                store::refresh, pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void close() {
        executor.shutdownNow();
    }
}