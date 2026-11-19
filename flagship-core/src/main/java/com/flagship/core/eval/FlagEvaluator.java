package com.flagship.core.eval;

import com.flagship.core.model.*;
import java.util.List;
import java.util.Optional;

public final class FlagEvaluator {

    // The order here IS the evaluation order from the spec (FR2).
    private final List<EvaluationStep> chain = List.of(
            new KillSwitchStep(),
            new UserTargetStep(),
            new RuleStep(),
            new RolloutStep());

    public EvaluationResult evaluate(Flag flag, Environment env, EvaluationContext ctx) {
        EnvironmentConfig cfg = flag.environments().get(env);
        if (cfg == null) {
            throw new IllegalArgumentException(
                "Flag " + flag.key() + " has no config for " + env);
        }

        for (EvaluationStep step : chain) {
            Optional<Decision> decision = step.evaluate(cfg, flag.key(), ctx);
            if (decision.isPresent()) {
                return result(flag, decision.get().variationName(), decision.get().reason());
            }
        }
        return result(flag, cfg.defaultVariation(), Reason.DEFAULT);
    }

    private EvaluationResult result(Flag flag, String variationName, Reason reason) {
        Variation v = flag.variations().get(variationName);
        if (v == null) {
            throw new IllegalStateException(
                "Unknown variation '" + variationName + "' in flag " + flag.key());
        }
        return new EvaluationResult(v.name(), v.value(), reason);
    }
}