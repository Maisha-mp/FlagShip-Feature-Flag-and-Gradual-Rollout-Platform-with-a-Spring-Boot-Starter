package com.flagship.server.api;

import com.flagship.core.model.Environment;
import com.flagship.core.model.FlagType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Every flag of one project, with only ONE environment's settings. */
public record SnapshotResponse(
        String project,
        Environment environment,
        Instant generatedAt,
        List<Item> flags) {

    public record Item(
            String key,
            FlagType type,
            Map<String, Object> variations,
            EnvironmentResponse config) { }
}