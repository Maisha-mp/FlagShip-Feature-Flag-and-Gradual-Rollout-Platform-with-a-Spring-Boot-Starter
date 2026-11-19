package com.flagship.core.eval;

import com.flagship.core.model.EnvironmentConfig;
import com.flagship.core.model.Rollout;
import java.util.Optional;

final class RolloutStep implements EvaluationStep {
    @Override
    public Optional<Decision> evaluate(EnvironmentConfig cfg, String flagKey, EvaluationContext ctx) {
        Rollout rollout = cfg.rollout();
        if (rollout != null && Bucketer.inRollout(flagKey, ctx.userKey(), rollout.percent())) {
            return Optional.of(new Decision(rollout.variation(), Reason.ROLLOUT));
        }
        return Optional.empty();
    }
}