package com.flagship.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.flagship.core.eval.FlagEvaluator;
import com.flagship.core.eval.Reason;
import com.flagship.core.model.*;
import com.flagship.core.rule.*;
import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.FlagEnvironmentConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlagConverterTest {

    private final FlagConverter converter = new FlagConverter(new ObjectMapper());

    @Test
    void rulesSurviveRoundTrip() {
        Rule rule = new Rule("r1",
                new AllOf(List.of(
                        new Condition("country", Operator.IN, List.of("BD", "IN")),
                        new AnyOf(List.of(
                                new Condition("plan", Operator.EQUALS, List.of("pro")),
                                new Condition("appVersion", Operator.SEMVER_GTE, List.of("2.0.0")))))),
                "on");

        String json = converter.rulesToJson(List.of(rule));
        List<Rule> back = converter.rulesFromJson(json);

        assertEquals(List.of(rule), back);
    }

    @Test
    void rolloutSurvivesRoundTrip() {
        Rollout rollout = new Rollout("on", 10.0);
        assertEquals(rollout, converter.rolloutFromJson(converter.rolloutToJson(rollout)));
    }

    @Test
    void convertedFlagEvaluatesLikeCore() {
        FlagEntity entity = new FlagEntity();
        entity.setKey("new-checkout");
        entity.setName("New checkout");
        entity.setType(FlagType.BOOLEAN);
        entity.setVariationsJson("{\"on\":true,\"off\":false}");

        Rule rule = new Rule("r1", new Condition("country", Operator.IN, List.of("BD")), "on");

        FlagEnvironmentConfig cfg = new FlagEnvironmentConfig();
        cfg.setEnvironment(Environment.PRODUCTION);
        cfg.setKillSwitchOn(false);
        cfg.setOffVariation("off");
        cfg.setDefaultVariation("off");
        cfg.setUserTargetsJson("{}");
        cfg.setRulesJson(converter.rulesToJson(List.of(rule)));
        cfg.setRolloutJson(null);

        Flag flag = converter.toCoreFlag(entity, List.of(cfg));
        FlagEvaluator evaluator = new FlagEvaluator();

        EvaluationResult bd = evaluator.evaluate(flag, Environment.PRODUCTION,
                EvaluationContext.builder("u1").attribute("country", "BD").build());
        assertEquals("on", bd.variationName());
        assertEquals(true, bd.value());
        assertEquals(Reason.RULE_MATCH, bd.reason());

        EvaluationResult us = evaluator.evaluate(flag, Environment.PRODUCTION,
                EvaluationContext.builder("u2").attribute("country", "US").build());
        assertEquals("off", us.variationName());
        assertEquals(Reason.DEFAULT, us.reason());
    }

    @Test
    void unknownRuleTypeIsRejected() {
        String bad = "[{\"id\":\"r1\",\"variation\":\"on\",\"condition\":{\"type\":\"nope\"}}]";
        assertThrows(IllegalArgumentException.class, () -> converter.rulesFromJson(bad));
    }
}