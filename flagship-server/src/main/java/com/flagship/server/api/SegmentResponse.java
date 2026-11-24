package com.flagship.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record SegmentResponse(String key, String name, JsonNode condition, Instant updatedAt) { }