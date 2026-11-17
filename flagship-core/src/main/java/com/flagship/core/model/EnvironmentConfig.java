package com.flagship.core.model;

import java.util.Map;

/** A flag's settings in ONE environment. */
public record EnvironmentConfig(
        boolean killSwitchOn,
        String offVariation,                // served when the kill switch is on
        String defaultVariation,            // served when nothing else matches
        Map<String, String> userTargets) {  // userKey -> variation name

    public EnvironmentConfig {
        userTargets = Map.copyOf(userTargets);
    }
}
