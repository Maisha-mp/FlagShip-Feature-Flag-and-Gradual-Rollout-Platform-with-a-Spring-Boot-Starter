package com.flagship.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.Rollout;
import com.flagship.core.model.Rule;
import com.flagship.core.model.Variation;
import com.flagship.core.rule.AllOf;
import com.flagship.core.rule.AnyOf;
import com.flagship.core.rule.Condition;
import com.flagship.core.rule.Operator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlagCodecTest {

    private final FlagCodec codec = new FlagCodec(new ObjectMapper());

    @Test
    void rulesSurviveRoundTrip() {
        Rule rule = new Rule("r1",
                new AllOf(List.of(
                        new Condition("country", Operator.IN, List.of("BD", "IN")),
                        new AnyOf(List.of(
                                new Condition("plan", Operator.EQUALS, List.of("pro")),
                                new Condition("appVersion", Operator.SEMVER_GTE, List.of("2.0.0")))))),
                "on");

        assertEquals(List.of(rule), codec.rulesFromJson(codec.rulesToJson(List.of(rule))));
    }

    @Test
    void rolloutSurvivesRoundTrip() {
        Rollout rollout = new Rollout("on", 10.0);
        assertEquals(rollout, codec.rolloutFromJson(codec.rolloutToJson(rollout)));
    }

    @Test
    void variationsSurviveRoundTrip() {
        Map<String, Variation> variations = Map.of(
                "on", new Variation("on", true),
                "off", new Variation("off", false));
        assertEquals(variations, codec.variationsFromJson(codec.variationsToJson(variations)));
    }

    @Test
    void unknownRuleTypeIsRejected() {
        String bad = "[{\"id\":\"r1\",\"variation\":\"on\",\"condition\":{\"type\":\"nope\"}}]";
        assertThrows(IllegalArgumentException.class, () -> codec.rulesFromJson(bad));
    }
}