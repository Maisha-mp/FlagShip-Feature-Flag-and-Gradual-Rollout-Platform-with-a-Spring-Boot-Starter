package com.flagship.core.eval;

import com.flagship.core.model.EnvironmentConfig;
import com.flagship.core.model.Rule;
import java.util.Optional;

final class RuleStep implements EvaluationStep {
    @Override
    public Optional<Decision> evaluate(EnvironmentConfig cfg, String flagKey, EvaluationContext ctx) {
        MatchVisitor matcher = new MatchVisitor(ctx);
        for (Rule rule : cfg.rules()) {
            if (rule.condition().accept(matcher)) {
                return Optional.of(new Decision(rule.variation(), Reason.RULE_MATCH));
            }
        }
        return Optional.empty();
    }
}