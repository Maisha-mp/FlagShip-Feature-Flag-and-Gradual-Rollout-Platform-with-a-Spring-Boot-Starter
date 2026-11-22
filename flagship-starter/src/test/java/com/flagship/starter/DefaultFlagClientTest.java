package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.SnapshotCodec;
import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.Reason;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultFlagClientTest {

    private static final String SNAPSHOT = """
            {"project":"checkout-app","environment":"PRODUCTION","generatedAt":"2026-11-19T07:49:51.554061200Z",
             "flags":[
              {"key":"new-checkout","type":"BOOLEAN","variations":{"on":true,"off":false},
               "config":{"killSwitchOn":false,"offVariation":"off","defaultVariation":"off",
                 "userTargets":{"user-1":"on"},
                 "rules":[{"id":"r1","variation":"on","condition":{"type":"condition","attribute":"country","operator":"IN","values":["BD","IN"]}}],
                 "rollout":null}},
              {"key":"button-color","type":"STRING","variations":{"blue":"blue","green":"green"},
               "config":{"killSwitchOn":false,"offVariation":"blue","defaultVariation":"green",
                 "userTargets":{},"rules":[],"rollout":null}},
              {"key":"page-size","type":"NUMBER","variations":{"small":10,"large":50},
               "config":{"killSwitchOn":false,"offVariation":"small","defaultVariation":"large",
                 "userTargets":{},"rules":[],"rollout":null}},
              {"key":"banner","type":"JSON","variations":{"a":{"text":"hi"},"b":{"text":"bye"}},
               "config":{"killSwitchOn":false,"offVariation":"b","defaultVariation":"a",
                 "userTargets":{},"rules":[],"rollout":null}},
              {"key":"broken","type":"BOOLEAN","variations":{"on":true,"off":false},
               "config":{"killSwitchOn":false,"offVariation":"off","defaultVariation":"missing",
                 "userTargets":{},"rules":[],"rollout":null}}
             ]}
            """;

    @TempDir
    Path dir;

    private FlagClient clientFor(String json) {
        SnapshotStore store = new SnapshotStore(() -> json,
                new SnapshotFile(dir.resolve("snapshot.json")),
                new SnapshotCodec(new ObjectMapper()));
        store.refresh();
        return new DefaultFlagClient(store);
    }

    private static EvaluationContext user(String key, String country) {
        return EvaluationContext.builder(key).attribute("country", country).build();
    }

    @Test
    void ruleMatchTurnsTheFlagOn() {
        FlagClient client = clientFor(SNAPSHOT);

        assertTrue(client.boolValue("new-checkout", user("u1", "BD"), false));
    }

    @Test
    void otherCountriesGetTheDefaultVariation() {
        FlagClient client = clientFor(SNAPSHOT);

        // The caller's fallback is true, so false proves the flag was really evaluated.
        assertFalse(client.boolValue("new-checkout", user("u2", "US"), true));
    }

    @Test
    void userTargetWins() {
        FlagClient client = clientFor(SNAPSHOT);

        assertTrue(client.boolValue("new-checkout", user("user-1", "US"), false));
    }

    @Test
    void evaluateExposesTheReason() {
        FlagClient client = clientFor(SNAPSHOT);

        var result = client.evaluate("new-checkout", user("u1", "BD"));

        assertTrue(result.isPresent());
        assertEquals(Reason.RULE_MATCH, result.get().reason());
        assertEquals("on", result.get().variationName());
    }

    @Test
    void stringNumberAndJsonFlagsWork() {
        FlagClient client = clientFor(SNAPSHOT);
        EvaluationContext ctx = user("u1", "US");

        assertEquals("green", client.stringValue("button-color", ctx, "none"));
        assertEquals(50.0, client.numberValue("page-size", ctx, 0), 0.0001);
        assertEquals(Map.of("text", "hi"), client.jsonValue("banner", ctx, null));
    }

    @Test
    void wrongTypeFallsBackToTheCallersDefault() {
        FlagClient client = clientFor(SNAPSHOT);
        EvaluationContext ctx = user("u1", "BD");

        assertEquals("fallback", client.stringValue("new-checkout", ctx, "fallback"));
        assertEquals(-1.0, client.numberValue("button-color", ctx, -1), 0.0001);
        assertTrue(client.boolValue("page-size", ctx, true));
    }

    @Test
    void unknownFlagReturnsTheDefault() {
        FlagClient client = clientFor(SNAPSHOT);

        assertTrue(client.boolValue("does-not-exist", user("u1", "BD"), true));
        assertTrue(client.evaluate("does-not-exist", user("u1", "BD")).isEmpty());
    }

    @Test
    void noSnapshotYetReturnsTheDefault() {
        SnapshotStore store = new SnapshotStore(() -> {
            throw new IOException("server down");
        }, new SnapshotFile(dir.resolve("snapshot.json")), new SnapshotCodec(new ObjectMapper()));
        FlagClient client = new DefaultFlagClient(store);

        assertTrue(client.boolValue("new-checkout", user("u1", "BD"), true));
        assertEquals("x", client.stringValue("button-color", user("u1", "BD"), "x"));
    }

    @Test
    void killSwitchWins() {
        String allKilled = SNAPSHOT.replace("\"killSwitchOn\":false", "\"killSwitchOn\":true");
        FlagClient client = clientFor(allKilled);

        assertFalse(client.boolValue("new-checkout", user("u1", "BD"), true));
        assertEquals("blue", client.stringValue("button-color", user("u1", "BD"), "none"));
    }

    @Test
    void brokenFlagFallsBackToTheDefaultInsteadOfThrowing() {
        FlagClient client = clientFor(SNAPSHOT);

        // "broken" points at a variation that does not exist; the evaluator throws inside.
        assertTrue(client.boolValue("broken", user("u1", "BD"), true));
        assertTrue(client.evaluate("broken", user("u1", "BD")).isEmpty());
    }
}