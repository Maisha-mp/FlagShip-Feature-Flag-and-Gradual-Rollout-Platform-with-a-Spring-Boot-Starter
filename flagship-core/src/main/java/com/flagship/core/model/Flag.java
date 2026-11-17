package com.flagship.core.model;

import java.util.Map;

public record Flag(
        String key,
        FlagType type,
        Map<String, Variation> variations,               // name -> variation
        Map<Environment, EnvironmentConfig> environments) {

    public Flag {
        variations = Map.copyOf(variations);
        environments = Map.copyOf(environments);
    }
}