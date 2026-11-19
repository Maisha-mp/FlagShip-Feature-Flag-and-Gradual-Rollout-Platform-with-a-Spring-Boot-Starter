package com.flagship.core.eval;

import com.flagship.core.model.EnvironmentConfig;
import java.util.Optional;

/** One link in the chain: returns a Decision, or empty to pass to the next step. */
public interface EvaluationStep {
    Optional<Decision> evaluate(EnvironmentConfig cfg, String flagKey, EvaluationContext ctx);
}