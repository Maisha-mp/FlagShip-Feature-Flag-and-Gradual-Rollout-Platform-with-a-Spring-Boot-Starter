package com.flagship.core.eval;

import com.flagship.core.model.*;

public final class FlagEvaluator {

    public EvaluationResult evaluate(Flag flag, Environment env, EvaluationContext ctx) {
        EnvironmentConfig cfg = flag.environments().get(env);
        if (cfg == null) {
            throw new IllegalArgumentException(
                "Flag " + flag.key() + " has no config for " + env);
        }

        // 1. Kill switch
        if (cfg.killSwitchOn()) {
            return result(flag, cfg.offVariation(), Reason.KILL_SWITCH);
        }
        // 2. Individual user target
        String targeted = cfg.userTargets().get(ctx.userKey());
        if (targeted != null) {
            return result(flag, targeted, Reason.USER_TARGET);
        }
        // 3. Default
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