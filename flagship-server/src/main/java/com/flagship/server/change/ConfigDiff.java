package com.flagship.server.change;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Lists what is different between two stored configurations. */
public final class ConfigDiff {

    private ConfigDiff() { }

    public static List<DiffEntry> between(JsonNode before, JsonNode after) {
        List<DiffEntry> out = new ArrayList<>();
        scalar(out, "killSwitchOn", "killSwitchOn", before, after);
        scalar(out, "offVariation", "offVariation", before, after);
        scalar(out, "defaultVariation", "defaultVariation", before, after);
        userTargets(out, before.path("userTargets"), after.path("userTargets"));
        rules(out, before.path("rules"), after.path("rules"));
        rollout(out, before.path("rollout"), after.path("rollout"));
        return out;
    }

    private static void scalar(List<DiffEntry> out, String label, String field,
                               JsonNode before, JsonNode after) {
        String b = text(before.get(field));
        String a = text(after.get(field));
        if (!Objects.equals(b, a)) {
            out.add(new DiffEntry(label, DiffEntry.CHANGED, b, a));
        }
    }

    private static void userTargets(List<DiffEntry> out, JsonNode before, JsonNode after) {
        Set<String> keys = new TreeSet<>();
        before.fieldNames().forEachRemaining(keys::add);
        after.fieldNames().forEachRemaining(keys::add);
        for (String key : keys) {
            compare(out, "userTargets." + key, text(before.get(key)), text(after.get(key)));
        }
    }

    private static void rules(List<DiffEntry> out, JsonNode before, JsonNode after) {
        Map<String, JsonNode> b = byId(before);
        Map<String, JsonNode> a = byId(after);
        Set<String> ids = new LinkedHashSet<>(b.keySet());
        ids.addAll(a.keySet());
        for (String id : ids) {
            compare(out, "rules." + id, text(b.get(id)), text(a.get(id)));
        }
        // Rules are checked in order, so the same rules in another order is a real change.
        boolean sameRules = b.keySet().equals(a.keySet());
        if (sameRules && !List.copyOf(b.keySet()).equals(List.copyOf(a.keySet()))) {
            out.add(new DiffEntry("rules.order", DiffEntry.CHANGED,
                    String.join(",", b.keySet()), String.join(",", a.keySet())));
        }
    }

    private static void rollout(List<DiffEntry> out, JsonNode before, JsonNode after) {
        boolean hadBefore = present(before);
        boolean hasAfter = present(after);
        if (!hadBefore && !hasAfter) {
            return;
        }
        if (!hadBefore) {
            out.add(new DiffEntry("rollout", DiffEntry.ADDED, null, after.toString()));
            return;
        }
        if (!hasAfter) {
            out.add(new DiffEntry("rollout", DiffEntry.REMOVED, before.toString(), null));
            return;
        }
        scalar(out, "rollout.variation", "variation", before, after);
        // Compared as numbers, so 10 and 10.0 are the same percentage.
        if (Double.compare(before.path("percent").asDouble(), after.path("percent").asDouble()) != 0) {
            out.add(new DiffEntry("rollout.percent", DiffEntry.CHANGED,
                    text(before.get("percent")), text(after.get("percent"))));
        }
    }

    private static void compare(List<DiffEntry> out, String field, String before, String after) {
        if (before == null && after != null) {
            out.add(new DiffEntry(field, DiffEntry.ADDED, null, after));
        } else if (before != null && after == null) {
            out.add(new DiffEntry(field, DiffEntry.REMOVED, before, null));
        } else if (before != null && !before.equals(after)) {
            out.add(new DiffEntry(field, DiffEntry.CHANGED, before, after));
        }
    }

    private static Map<String, JsonNode> byId(JsonNode array) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode rule : array) {
            map.put(rule.path("id").asText(), rule);
        }
        return map;
    }

    private static boolean present(JsonNode node) {
        return node != null && !node.isNull() && !node.isMissingNode();
    }

    private static String text(JsonNode node) {
        if (!present(node)) {
            return null;
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }
}