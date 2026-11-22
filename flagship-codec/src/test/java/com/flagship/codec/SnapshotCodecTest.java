package com.flagship.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.flagship.core.eval.FlagEvaluator;
import com.flagship.core.eval.Reason;
import com.flagship.core.model.Environment;
import com.flagship.core.model.Flag;
import com.flagship.core.model.FlagType;
import org.junit.jupiter.api.Test;

class SnapshotCodecTest {

    // The same shape the server returned for GET .../snapshot?env=PRODUCTION
    private static final String SNAPSHOT = """
            {"project":"checkout-app","environment":"PRODUCTION","generatedAt":"2026-11-19T07:49:51.554061200Z",
             "flags":[{"key":"new-checkout","type":"BOOLEAN","variations":{"on":true,"off":false},
               "config":{"killSwitchOn":false,"offVariation":"off","defaultVariation":"off",
                 "userTargets":{"user-1":"on"},
                 "rules":[{"id":"r1","variation":"on","condition":{"type":"condition","attribute":"country","operator":"IN","values":["BD","IN"]}}],
                 "rollout":{"variation":"on","percent":10.0}}}]}
            """;

    private final SnapshotCodec codec = new SnapshotCodec(new ObjectMapper());
    private final FlagEvaluator evaluator = new FlagEvaluator();

    @Test
    void parsesServerSnapshot() {
        Snapshot snapshot = codec.fromJson(SNAPSHOT);

        assertEquals("checkout-app", snapshot.project());
        assertEquals(Environment.PRODUCTION, snapshot.environment());
        assertEquals(1, snapshot.flags().size());

        Flag flag = snapshot.flag("new-checkout");
        assertNotNull(flag);
        assertEquals(FlagType.BOOLEAN, flag.type());
        assertEquals(2, flag.variations().size());
    }

    @Test
    void parsedFlagEvaluatesLikeTheServer() {
        Flag flag = codec.fromJson(SNAPSHOT).flag("new-checkout");

        EvaluationResult bd = evaluator.evaluate(flag, Environment.PRODUCTION,
                EvaluationContext.builder("u1").attribute("country", "BD").build());
        assertEquals("on", bd.variationName());
        assertEquals(true, bd.value());
        assertEquals(Reason.RULE_MATCH, bd.reason());

        EvaluationResult targeted = evaluator.evaluate(flag, Environment.PRODUCTION,
                EvaluationContext.builder("user-1").attribute("country", "US").build());
        assertEquals("on", targeted.variationName());
        assertEquals(Reason.USER_TARGET, targeted.reason());
    }

    @Test
    void killSwitchStillWins() {
        String withKillSwitch = SNAPSHOT.replace("\"killSwitchOn\":false", "\"killSwitchOn\":true");
        Flag flag = codec.fromJson(withKillSwitch).flag("new-checkout");

        EvaluationResult r = evaluator.evaluate(flag, Environment.PRODUCTION,
                EvaluationContext.builder("u1").attribute("country", "BD").build());
        assertEquals("off", r.variationName());
        assertEquals(Reason.KILL_SWITCH, r.reason());
    }

    @Test
    void brokenJsonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> codec.fromJson("not json"));
        assertThrows(IllegalArgumentException.class, () -> codec.fromJson(""));
        assertThrows(IllegalArgumentException.class, () -> codec.fromJson(
                "{\"project\":\"p\",\"environment\":\"PRODUCTION\",\"generatedAt\":\"2026-11-19T07:49:51Z\"}"));
    }

    @Test
    void flagWithoutConfigIsRejected() {
        String noConfig = "{\"project\":\"p\",\"environment\":\"PRODUCTION\","
                + "\"generatedAt\":\"2026-11-19T07:49:51Z\","
                + "\"flags\":[{\"key\":\"f\",\"type\":\"BOOLEAN\",\"variations\":{\"on\":true},\"config\":null}]}";
        assertThrows(IllegalArgumentException.class, () -> codec.fromJson(noConfig));
    }
}