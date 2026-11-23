package com.flagship.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagship.core.model.Environment;
import java.time.Instant;

public record VersionResponse(
        int version,
        Environment environment,
        String author,
        String reason,
        Instant createdAt,
        Integer restoredFrom,
        JsonNode config) { }