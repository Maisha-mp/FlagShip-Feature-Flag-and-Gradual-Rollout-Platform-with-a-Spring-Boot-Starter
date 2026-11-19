package com.flagship.core.model;

import java.util.List;
import java.util.Map;

/** A flag's settings in ONE environment. */
public record EnvironmentConfig(
        boolean killSwitchOn,
        String offVariation,                // served when the kill switch is on
        String defaultVariation,            // served when nothing else matches
        Map<String, String> userTargets,    // userKey -> variation name
        List<Rule> rules,                   // checked in order, first match wins
        Rollout rollout) {                  // may be null = no rollout

    public EnvironmentConfig {
        userTargets = Map.copyOf(userTargets);
        rules = List.copyOf(rules);
    }

    /** Convenience for configs with no rules and no rollout. */
    public EnvironmentConfig(boolean killSwitchOn, String offVariation,
                             String defaultVariation, Map<String, String> userTargets) {
        this(killSwitchOn, offVariation, defaultVariation, userTargets, List.of(), null);
    }
}