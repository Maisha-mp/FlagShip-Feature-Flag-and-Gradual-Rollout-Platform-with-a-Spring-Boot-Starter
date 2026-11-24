package com.flagship.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** condition: a rule condition, e.g. {"type":"condition","attribute":"country","operator":"IN","values":["BD"]}. */
public record SegmentRequest(
        @NotBlank @Size(max = 200) String name,
        JsonNode condition) { }