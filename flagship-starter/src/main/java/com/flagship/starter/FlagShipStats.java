package com.flagship.starter;

import com.flagship.core.eval.EvaluationResult;
import com.flagship.core.eval.Reason;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

/**
 * Plain counters for what the starter does (no Micrometer here, so the starter also works without it).
 * The evaluation counters are kept per Reason plus UNKNOWN (flag missing or no snapshot yet):
 * a small fixed set, so a metrics system never receives thousands of different tags.
 */
public final class FlagShipStats {

    /** Used when there is no result: unknown flag, no snapshot, or a flag that could not be evaluated. */
    public static final String UNKNOWN = "UNKNOWN";

    private final Map<String, LongAdder> evaluations = new LinkedHashMap<>();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();

    public FlagShipStats() {
        for (Reason reason : Reason.values()) {
            evaluations.put(reason.name(), new LongAdder());
        }
        evaluations.put(UNKNOWN, new LongAdder());   // the map is never changed after this point
    }

    public void recordEvaluation(Optional<EvaluationResult> result) {
        String kind = result.map(r -> r.reason().name()).orElse(UNKNOWN);
        evaluations.get(kind).increment();
    }

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }

    /** The names used as the "reason" tag. */
    public Set<String> evaluationKinds() {
        return Collections.unmodifiableSet(evaluations.keySet());
    }

    public long evaluations(String kind) {
        LongAdder adder = evaluations.get(kind);
        return adder == null ? 0 : adder.sum();
    }

    public long cacheHits() {
        return cacheHits.sum();
    }

    public long cacheMisses() {
        return cacheMisses.sum();
    }
}