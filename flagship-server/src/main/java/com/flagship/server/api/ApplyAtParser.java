package com.flagship.server.api;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Reads the optional applyAt query parameter (a UTC time like 2026-11-25T10:00:00Z). */
final class ApplyAtParser {

    private ApplyAtParser() { }

    static Instant parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("applyAt must be a UTC time like 2026-11-25T10:00:00Z");
        }
    }
}