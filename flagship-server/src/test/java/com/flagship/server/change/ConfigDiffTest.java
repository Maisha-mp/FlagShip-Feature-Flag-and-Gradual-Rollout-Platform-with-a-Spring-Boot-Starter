package com.flagship.server.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigDiffTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .build();

    private JsonNode cfg(boolean kill, String defaultVariation, String targets, String rules, String rollout)
            throws Exception {
        return mapper.readTree("{'killSwitchOn':" + kill + ",'offVariation':'off','defaultVariation':'"
                + defaultVariation + "','userTargets':" + targets + ",'rules':" + rules
                + ",'rollout':" + rollout + "}");
    }

    private static List<String> summary(List<DiffEntry> entries) {
        return entries.stream().map(e -> e.kind() + " " + e.field()).toList();
    }

    @Test
    void identicalConfigsHaveNoDifferences() throws Exception {
        JsonNode a = cfg(false, "off", "{'u1':'on'}", "[]", "null");
        JsonNode b = cfg(false, "off", "{'u1':'on'}", "[]", "null");
        assertTrue(ConfigDiff.between(a, b).isEmpty());
    }

    @Test
    void simpleFieldChangesAreListed() throws Exception {
        List<DiffEntry> diff = ConfigDiff.between(
                cfg(false, "off", "{}", "[]", "null"),
                cfg(true, "on", "{}", "[]", "null"));

        assertEquals(List.of("CHANGED killSwitchOn", "CHANGED defaultVariation"), summary(diff));
        assertEquals("false", diff.get(0).before());
        assertEquals("true", diff.get(0).after());
    }

    @Test
    void userTargetsAndRulesAreComparedOneByOne() throws Exception {
        JsonNode before = cfg(false, "off", "{'u1':'on','u2':'off'}",
                "[{'id':'r1','variation':'on'},{'id':'r2','variation':'off'}]", "null");
        JsonNode after = cfg(false, "off", "{'u2':'on','u3':'on'}",
                "[{'id':'r1','variation':'off'},{'id':'r3','variation':'on'}]", "null");

        assertEquals(List.of(
                "REMOVED userTargets.u1", "CHANGED userTargets.u2", "ADDED userTargets.u3",
                "CHANGED rules.r1", "REMOVED rules.r2", "ADDED rules.r3"),
                summary(ConfigDiff.between(before, after)));
    }

    @Test
    void rolloutAddedRampedAndRemoved() throws Exception {
        JsonNode none = cfg(false, "off", "{}", "[]", "null");
        JsonNode ten = cfg(false, "off", "{}", "[]", "{'variation':'on','percent':10}");
        JsonNode twenty = cfg(false, "off", "{}", "[]", "{'variation':'on','percent':20}");
        JsonNode tenPointZero = cfg(false, "off", "{}", "[]", "{'variation':'on','percent':10.0}");

        assertEquals(List.of("ADDED rollout"), summary(ConfigDiff.between(none, ten)));
        assertEquals(List.of("CHANGED rollout.percent"), summary(ConfigDiff.between(ten, twenty)));
        assertTrue(ConfigDiff.between(ten, tenPointZero).isEmpty());   // 10 and 10.0 are the same
        assertEquals(List.of("REMOVED rollout"), summary(ConfigDiff.between(twenty, none)));
    }

    @Test
    void sameRulesInAnotherOrderIsAChange() throws Exception {
        JsonNode before = cfg(false, "off", "{}",
                "[{'id':'r1','variation':'on'},{'id':'r2','variation':'off'}]", "null");
        JsonNode after = cfg(false, "off", "{}",
                "[{'id':'r2','variation':'off'},{'id':'r1','variation':'on'}]", "null");

        List<DiffEntry> diff = ConfigDiff.between(before, after);

        assertEquals(List.of("CHANGED rules.order"), summary(diff));
        assertEquals("r1,r2", diff.get(0).before());
        assertEquals("r2,r1", diff.get(0).after());
    }
}