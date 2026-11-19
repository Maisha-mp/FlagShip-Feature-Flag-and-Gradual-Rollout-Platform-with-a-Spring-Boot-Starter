package com.flagship.core.eval;

import com.flagship.core.model.EnvironmentConfig;
import java.util.Optional;

final class KillSwitchStep implements EvaluationStep {
    @Override
    public Optional<Decision> evaluate(EnvironmentConfig cfg, String flagKey, EvaluationContext ctx) {
        if (cfg.killSwitchOn()) {
            return Optional.of(new Decision(cfg.offVariation(), Reason.KILL_SWITCH));
        }
        return Optional.empty();
    }
}