package com.flagship.server.api;

import com.flagship.server.entity.ProjectRole;

public record MemberResponse(String username, ProjectRole role) { }