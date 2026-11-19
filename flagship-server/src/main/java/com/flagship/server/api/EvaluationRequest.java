package com.flagship.server.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** "Who is the user?" Attributes are optional, e.g. {"country":"BD"}. */
public record EvaluationRequest(
        @NotBlank String userKey,
        Map<String, Object> attributes) { }