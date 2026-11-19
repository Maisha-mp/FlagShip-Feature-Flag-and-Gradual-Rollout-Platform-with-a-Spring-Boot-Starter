package com.flagship.core.eval;

import com.flagship.core.model.EnvironmentConfig;
import java.util.Optional;

final class UserTargetStep implements EvaluationStep {
    @Override
    public Optional<Decision> evaluate(EnvironmentConfig cfg, String flagKey, EvaluationContext ctx) {
        String variation = cfg.userTargets().get(ctx.userKey());
        if (variation != null) {
            return Optional.of(new Decision(variation, Reason.USER_TARGET));
        }
        return Optional.empty();
    }
}