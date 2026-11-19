package com.flagship.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                 message = "must be lowercase letters, digits and single dashes, e.g. checkout-app")
        String key,

        @NotBlank
        @Size(max = 200)
        String name) { }