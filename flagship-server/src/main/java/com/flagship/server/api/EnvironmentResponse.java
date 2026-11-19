package com.flagship.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record EnvironmentResponse(
        boolean killSwitchOn,
        String offVariation,
        String defaultVariation,
        Map<String, String> userTargets,
        JsonNode rules,
        JsonNode rollout) { }