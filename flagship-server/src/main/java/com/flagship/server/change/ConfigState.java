package com.flagship.server.change;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flagship.server.entity.FlagEnvironmentConfig;

/** The complete state of ONE environment's configuration. This is what a version stores. */
public record ConfigState(boolean killSwitchOn, String offVariation, String defaultVariation,
                          String userTargetsJson, String rulesJson, String rolloutJson) {

    public static ConfigState from(FlagEnvironmentConfig e) {
        return new ConfigState(e.isKillSwitchOn(), e.getOffVariation(), e.getDefaultVariation(),
                e.getUserTargetsJson(), e.getRulesJson(), e.getRolloutJson());
    }

    public void writeTo(FlagEnvironmentConfig e) {
        e.setKillSwitchOn(killSwitchOn);
        e.setOffVariation(offVariation);
        e.setDefaultVariation(defaultVariation);
        e.setUserTargetsJson(userTargetsJson);
        e.setRulesJson(rulesJson);
        e.setRolloutJson(rolloutJson);
    }

    /** A readable JSON tree (same shape as the request that sets a config). */
    public ObjectNode toTree(ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("killSwitchOn", killSwitchOn);
        node.put("offVariation", offVariation);
        node.put("defaultVariation", defaultVariation);
        node.set("userTargets", parse(mapper, userTargetsJson));
        node.set("rules", parse(mapper, rulesJson));
        node.set("rollout", rolloutJson == null ? mapper.nullNode() : parse(mapper, rolloutJson));
        return node;
    }

    public static ConfigState fromTree(JsonNode node) {
        JsonNode targets = node.get("userTargets");
        JsonNode rules = node.get("rules");
        JsonNode rollout = node.get("rollout");
        return new ConfigState(
                node.path("killSwitchOn").asBoolean(),
                node.path("offVariation").asText(),
                node.path("defaultVariation").asText(),
                targets == null || targets.isNull() ? "{}" : targets.toString(),
                rules == null || rules.isNull() ? "[]" : rules.toString(),
                rollout == null || rollout.isNull() ? null : rollout.toString());
    }

    private static JsonNode parse(ObjectMapper mapper, String text) {
        try {
            return mapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is invalid", e);
        }
    }
}