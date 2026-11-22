package com.flagship.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.Environment;
import com.flagship.core.model.EnvironmentConfig;
import com.flagship.core.model.Flag;
import com.flagship.core.model.FlagType;
import com.flagship.core.model.Variation;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the server's snapshot JSON into core objects. Plain Java, shared by the
 * starter and anything else that talks to the snapshot endpoint.
 * Bad input throws IllegalArgumentException.
 */
public final class SnapshotCodec {

    private final ObjectMapper mapper;
    private final FlagCodec codec;

    public SnapshotCodec(ObjectMapper mapper) {
        this.mapper = mapper;
        this.codec = new FlagCodec(mapper);
    }

    public Snapshot fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("snapshot is empty");
        }
        JsonNode root = readTree(json);
        if (!root.isObject()) {
            throw new IllegalArgumentException("snapshot must be a JSON object");
        }

        String project = text(root, "project");
        Environment env = parseEnvironment(text(root, "environment"));
        Instant generatedAt = parseInstant(text(root, "generatedAt"));

        JsonNode items = root.get("flags");
        if (items == null || !items.isArray()) {
            throw new IllegalArgumentException("snapshot needs a 'flags' array");
        }

        Map<String, Flag> flags = new LinkedHashMap<>();
        for (JsonNode item : items) {
            Flag flag = flagFrom(item, env);
            flags.put(flag.key(), flag);
        }
        return new Snapshot(project, env, generatedAt, flags);
    }

    private Flag flagFrom(JsonNode item, Environment env) {
        String key = text(item, "key");
        FlagType type = parseType(text(item, "type"));
        Map<String, Variation> variations =
                codec.variationsFromJson(require(item, "variations", key).toString());

        JsonNode config = require(item, "config", key);
        EnvironmentConfig envConfig = new EnvironmentConfig(
                bool(config, "killSwitchOn"),
                text(config, "offVariation"),
                text(config, "defaultVariation"),
                codec.userTargetsFromJson(optional(config, "userTargets", "{}")),
                codec.rulesFromJson(optional(config, "rules", "[]")),
                codec.rolloutFromJson(optional(config, "rollout", null)));

        return new Flag(key, type, variations, Map.of(env, envConfig));
    }

    // ---------- small helpers ----------

    private JsonNode require(JsonNode n, String field, String flagKey) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            throw new IllegalArgumentException("Flag '" + flagKey + "' has no '" + field + "'");
        }
        return v;
    }

    /** The field as JSON text, or the fallback if it is missing or null. */
    private String optional(JsonNode n, String field, String fallback) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? fallback : v.toString();
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || !v.isTextual()) {
            throw new IllegalArgumentException("Missing text field: " + field);
        }
        return v.asText();
    }

    private boolean bool(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || !v.isBoolean()) {
            throw new IllegalArgumentException("Missing true/false field: " + field);
        }
        return v.asBoolean();
    }

    private Environment parseEnvironment(String name) {
        try {
            return Environment.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown environment: " + name);
        }
    }

    private FlagType parseType(String name) {
        try {
            return FlagType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown flag type: " + name);
        }
    }

    private Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Bad generatedAt time: " + text);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getOriginalMessage(), e);
        }
    }
}