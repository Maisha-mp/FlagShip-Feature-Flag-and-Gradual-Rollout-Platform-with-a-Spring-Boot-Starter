package com.flagship.server.api;

import com.flagship.server.entity.Project;
import java.time.Instant;

public record ProjectResponse(Long id, String key, String name, Instant createdAt) {

    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), p.getKey(), p.getName(), p.getCreatedAt());
    }
}