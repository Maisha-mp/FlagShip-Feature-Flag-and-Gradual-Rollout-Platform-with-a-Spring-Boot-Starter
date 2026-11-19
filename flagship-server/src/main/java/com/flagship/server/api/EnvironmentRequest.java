package com.flagship.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** One environment's settings, as sent by the client. */
public record EnvironmentRequest(
        boolean killSwitchOn,
        @NotBlank String offVariation,
        @NotBlank String defaultVariation,
        Map<String, String> userTargets,   // optional
        JsonNode rules,                    // optional JSON array
        JsonNode rollout) { }              // optional JSON object