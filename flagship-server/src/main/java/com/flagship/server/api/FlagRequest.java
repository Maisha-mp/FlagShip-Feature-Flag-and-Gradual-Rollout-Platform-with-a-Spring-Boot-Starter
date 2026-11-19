package com.flagship.server.api;

import com.flagship.core.model.Environment;
import com.flagship.core.model.FlagType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record FlagRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                 message = "must be lowercase letters, digits and single dashes, e.g. new-checkout")
        String key,

        @NotBlank @Size(max = 200) String name,

        @NotNull FlagType type,

        @NotEmpty Map<String, Object> variations,   // name -> value

        @NotEmpty Map<Environment, @Valid EnvironmentRequest> environments) { }