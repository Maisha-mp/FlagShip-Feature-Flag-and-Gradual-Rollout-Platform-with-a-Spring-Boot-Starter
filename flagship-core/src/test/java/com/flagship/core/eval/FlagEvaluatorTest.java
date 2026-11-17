package com.flagship.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import com.flagship.core.model.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlagEvaluatorTest {

    private final FlagEvaluator evaluator = new FlagEvaluator();

    private Flag flag(boolean killSwitchOn, String defaultVar) {
        Map<String, Variation> variations = Map.of(
                "on", new Variation("on", true),
                "off", new Variation("off", false));
        EnvironmentConfig prod = new EnvironmentConfig(
                killSwitchOn, "off", defaultVar, Map.of("alice", "on"));
        return new Flag("new-checkout", FlagType.BOOLEAN, variations,
                Map.of(Environment.PRODUCTION, prod));
    }

    private EvaluationContext user(String key) {
        return EvaluationContext.builder(key).build();
    }

    @Test
    void unknownUserGetsDefault() {
        EvaluationResult r = evaluator.evaluate(flag(false, "off"), Environment.PRODUCTION, user("bob"));
        assertEquals(false, r.value());
        assertEquals(Reason.DEFAULT, r.reason());
    }

    @Test
    void targetedUserGetsTargetVariation() {
        EvaluationResult r = evaluator.evaluate(flag(false, "off"), Environment.PRODUCTION, user("alice"));
        assertEquals(true, r.value());
        assertEquals(Reason.USER_TARGET, r.reason());
    }

    @Test
    void killSwitchBeatsUserTarget() {
        EvaluationResult r = evaluator.evaluate(flag(true, "on"), Environment.PRODUCTION, user("alice"));
        assertEquals(false, r.value());
        assertEquals(Reason.KILL_SWITCH, r.reason());
    }

    @Test
    void unknownVariationIsRejected() {
        Flag broken = flag(false, "does-not-exist");
        assertThrows(IllegalStateException.class,
                () -> evaluator.evaluate(broken, Environment.PRODUCTION, user("bob")));
    }
    }