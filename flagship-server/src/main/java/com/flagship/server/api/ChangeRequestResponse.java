package com.flagship.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagship.core.model.Environment;
import com.flagship.server.change.DiffEntry;
import com.flagship.server.entity.ChangeRequestStatus;
import java.time.Instant;
import java.util.List;

/** changes = what the request would change compared with the live configuration (only while it is open). */
public record ChangeRequestResponse(
        long id,
        String flagKey,
        Environment environment,
        ChangeRequestStatus status,
        String author,
        String reason,
        Instant createdAt,
        Instant applyAt,
        String decidedBy,
        Instant decidedAt,
        String decisionNote,
        Instant appliedAt,
        Integer appliedVersion,
        Integer restoredFrom,
        String failureMessage,
        JsonNode config,
        List<DiffEntry> changes) { }