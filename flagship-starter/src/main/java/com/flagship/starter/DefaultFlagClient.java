package com.flagship.starter;

import com.flagship.codec.Snapshot;
import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.flagship.core.eval.FlagEvaluator;
import com.flagship.core.model.Flag;
import java.util.Optional;

/**
 * Evaluates flags from the in-memory snapshot using the SAME FlagEvaluator
 * class the server uses (QA4). No network access happens here.
 */
public final class DefaultFlagClient implements FlagClient {

    private static final System.Logger LOG = System.getLogger(DefaultFlagClient.class.getName());

    private final SnapshotStore store;
    private final FlagEvaluator evaluator = new FlagEvaluator();

    public DefaultFlagClient(SnapshotStore store) {
        this.store = store;
    }

    @Override
    public Optional<EvaluationResult> evaluate(String flagKey, EvaluationContext context) {
        Snapshot snapshot = store.current();   // read once, so a refresh mid-call cannot mix two snapshots
        if (snapshot == null) {
            return Optional.empty();
        }
        Flag flag = snapshot.flag(flagKey);
        if (flag == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(evaluator.evaluate(flag, snapshot.environment(), context));
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "FlagShip: flag ''{0}'' could not be evaluated, using the caller''s default: {1}",
                    flagKey, e.getMessage());
            return Optional.empty();
        }
    }
}