package com.flagship.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flagship.core.model.Rollout;
import com.flagship.core.model.Rule;
import com.flagship.core.model.Variation;
import com.flagship.core.rule.AllOf;
import com.flagship.core.rule.AnyOf;
import com.flagship.core.rule.Condition;
import com.flagship.core.rule.Operator;
import com.flagship.core.rule.RuleNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns JSON text into core objects and back. Plain Java (no Spring), shared by
 * the server and the starter so both read a rule exactly the same way (QA4).
 * Bad input throws IllegalArgumentException.
 */
public final class FlagCodec {

    private final ObjectMapper mapper;

    public FlagCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // ---------- variations ----------

    public Map<String, Variation> variationsFromJson(String json) {
        Map<String, Object> raw = read(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        Map<String, Variation> result = new LinkedHashMap<>();
        raw.forEach((name, value) -> result.put(name, new Variation(name, value)));
        return result;
    }

    public String variationsToJson(Map<String, Variation> variations) {
        Map<String, Object> raw = new LinkedHashMap<>();
        variations.forEach((name, v) -> raw.put(name, v.value()));
        return write(raw);
    }

    // ---------- user targets ----------

    public Map<String, String> userTargetsFromJson(String json) {
        return read(json, new TypeReference<Map<String, String>>() { });
    }

    public String userTargetsToJson(Map<String, String> targets) {
        return write(targets);
    }

    // ---------- rollout ----------

    public Rollout rolloutFromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode n = readTree(json);
        JsonNode percent = n.get("percent");
        if (percent == null || !percent.isNumber()) {
            throw new IllegalArgumentException("rollout needs a numeric 'percent'");
        }
        return new Rollout(text(n, "variation"), percent.asDouble());
    }

    public String rolloutToJson(Rollout rollout) {
        if (rollout == null) {
            return null;
        }
        ObjectNode n = mapper.createObjectNode();
        n.put("variation", rollout.variation());
        n.put("percent", rollout.percent());
        return write(n);
    }

    // ---------- rules ----------

    public List<Rule> rulesFromJson(String json) {
        JsonNode root = readTree(json);
        if (!root.isArray()) {
            throw new IllegalArgumentException("rules must be a JSON array");
        }
        List<Rule> rules = new ArrayList<>();
        for (JsonNode n : root) {
            rules.add(new Rule(text(n, "id"), nodeFromJson(n.get("condition")), text(n, "variation")));
        }
        return rules;
    }

    public String rulesToJson(List<Rule> rules) {
        ArrayNode array = mapper.createArrayNode();
        for (Rule r : rules) {
            ObjectNode n = array.addObject();
            n.put("id", r.id());
            n.put("variation", r.variation());
            n.set("condition", nodeToJson(r.condition()));
        }
        return write(array);
    }

    private RuleNode nodeFromJson(JsonNode n) {
        if (n == null || !n.isObject()) {
            throw new IllegalArgumentException("rule condition must be a JSON object");
        }
        String type = text(n, "type");
        return switch (type) {
            case "condition" -> new Condition(
                    text(n, "attribute"), parseOperator(text(n, "operator")), values(n));
            case "allOf" -> new AllOf(children(n));
            case "anyOf" -> new AnyOf(children(n));
            default -> throw new IllegalArgumentException("Unknown rule type: " + type);
        };
    }

    private JsonNode nodeToJson(RuleNode node) {
        ObjectNode out = mapper.createObjectNode();
        switch (node) {
            case Condition c -> {
                out.put("type", "condition");
                out.put("attribute", c.attribute());
                out.put("operator", c.operator().name());
                JsonNode valuesNode = mapper.valueToTree(c.values());
                out.set("values", valuesNode);
            }
            case AllOf all -> {
                out.put("type", "allOf");
                ArrayNode kids = out.putArray("children");
                for (RuleNode child : all.children()) {
                    kids.add(nodeToJson(child));
                }
            }
            case AnyOf any -> {
                out.put("type", "anyOf");
                ArrayNode kids = out.putArray("children");
                for (RuleNode child : any.children()) {
                    kids.add(nodeToJson(child));
                }
            }
        }
        return out;
    }

    private List<RuleNode> children(JsonNode n) {
        JsonNode kids = n.get("children");
        if (kids == null || !kids.isArray()) {
            throw new IllegalArgumentException("allOf/anyOf needs a 'children' array");
        }
        List<RuleNode> result = new ArrayList<>();
        for (JsonNode k : kids) {
            result.add(nodeFromJson(k));
        }
        return result;
    }

    private List<Object> values(JsonNode n) {
        JsonNode v = n.get("values");
        if (v == null || !v.isArray()) {
            throw new IllegalArgumentException("condition needs a 'values' array");
        }
        return mapper.convertValue(v, new TypeReference<List<Object>>() { });
    }

    private Operator parseOperator(String name) {
        try {
            return Operator.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown operator: " + name);
        }
    }

    // ---------- small helpers ----------

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || !v.isTextual()) {
            throw new IllegalArgumentException("Missing text field: " + field);
        }
        return v.asText();
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getOriginalMessage(), e);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getOriginalMessage(), e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot write JSON: " + e.getOriginalMessage(), e);
        }
    }
}