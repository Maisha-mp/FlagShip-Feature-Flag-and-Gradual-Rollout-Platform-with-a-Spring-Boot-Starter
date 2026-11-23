package com.flagship.starter;

import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Decorator: wraps another FlagClient and remembers recent answers.
 * The cache is dropped as soon as the snapshot changes (so it is never stale)
 * and is emptied when it is full (so it never grows without limit).
 */
public final class CachingFlagClient implements FlagClient {

    private record Key(String flagKey, EvaluationContext context) { }

    /** The answers remembered for ONE version of the snapshot. */
    private static final class State {
        final Object version;
        final ConcurrentHashMap<Key, Optional<EvaluationResult>> entries = new ConcurrentHashMap<>();

        State(Object version) {
            this.version = version;
        }
    }

    private final FlagClient delegate;
    private final Supplier<?> versionSource;
    private final int maxEntries;
    private final FlagShipStats stats;
    private volatile State state;

    /**
     * @param versionSource returns an object that changes whenever the flags change
     *                      (the starter passes the current Snapshot object)
     */
    public CachingFlagClient(FlagClient delegate, Supplier<?> versionSource, int maxEntries) {
        this(delegate, versionSource, maxEntries, new FlagShipStats());
    }

    public CachingFlagClient(FlagClient delegate, Supplier<?> versionSource, int maxEntries,
                             FlagShipStats stats) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be at least 1");
        }
        this.delegate = delegate;
        this.versionSource = versionSource;
        this.maxEntries = maxEntries;
        this.stats = stats;
        this.state = new State(versionSource.get());
    }

    @Override
    public Optional<EvaluationResult> evaluate(String flagKey, EvaluationContext context) {
        Object version = versionSource.get();
        State current = state;
        if (current.version != version) {   // same object? if not, the flags changed
            current = new State(version);
            state = current;
        }

        Key key = new Key(flagKey, context);
        Optional<EvaluationResult> cached = current.entries.get(key);
        if (cached != null) {
            stats.recordCacheHit();
            stats.recordEvaluation(cached);
            return cached;
        }

        stats.recordCacheMiss();
        Optional<EvaluationResult> result = delegate.evaluate(flagKey, context);
        if (current.entries.size() >= maxEntries) {
            current.entries.clear();
        }
        current.entries.put(key, result);
        stats.recordEvaluation(result);
        return result;
    }

    /** How many answers are remembered right now (used by tests and the Actuator endpoint). */
    public int cachedEntries() {
        return state.entries.size();
    }
}