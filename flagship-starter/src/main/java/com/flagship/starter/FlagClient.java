package com.flagship.starter;

import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What application code uses to read flags. Never throws because of a flag problem:
 * when something is missing or has the wrong type, the caller's default is returned.
 */
public interface FlagClient {

    /** Evaluates one flag. Empty if the flag is unknown or no snapshot has been loaded yet. */
    Optional<EvaluationResult> evaluate(String flagKey, EvaluationContext context);

    default boolean boolValue(String flagKey, EvaluationContext context, boolean defaultValue) {
        return evaluate(flagKey, context)
                .map(EvaluationResult::value)
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(defaultValue);
    }

    default String stringValue(String flagKey, EvaluationContext context, String defaultValue) {
        return evaluate(flagKey, context)
                .map(EvaluationResult::value)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(defaultValue);
    }

    default double numberValue(String flagKey, EvaluationContext context, double defaultValue) {
        return evaluate(flagKey, context)
                .map(EvaluationResult::value)
                .filter(Number.class::isInstance)
                .map(v -> ((Number) v).doubleValue())
                .orElse(defaultValue);
    }

    /** For JSON flags: the value is a Map or a List. */
    default Object jsonValue(String flagKey, EvaluationContext context, Object defaultValue) {
        return evaluate(flagKey, context)
                .map(EvaluationResult::value)
                .filter(v -> v instanceof Map || v instanceof List)
                .orElse(defaultValue);
    }
}