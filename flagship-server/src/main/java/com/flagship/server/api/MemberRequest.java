package com.flagship.server.api;

import com.flagship.server.entity.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record MemberRequest(@NotNull ProjectRole role) { }