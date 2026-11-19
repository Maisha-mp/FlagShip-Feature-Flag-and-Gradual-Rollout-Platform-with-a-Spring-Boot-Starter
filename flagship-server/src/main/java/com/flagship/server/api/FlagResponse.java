package com.flagship.server.api;

import com.flagship.core.model.Environment;
import com.flagship.core.model.FlagType;
import java.time.Instant;
import java.util.Map;

public record FlagResponse(
        String key,
        String name,
        FlagType type,
        Map<String, Object> variations,
        Map<Environment, EnvironmentResponse> environments,
        Instant createdAt) { }