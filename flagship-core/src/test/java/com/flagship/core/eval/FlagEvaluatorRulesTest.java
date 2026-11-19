package com.flagship.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import com.flagship.core.model.*;
import com.flagship.core.rule.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlagEvaluatorRulesTest {

    private final FlagEvaluator evaluator = new FlagEvaluator();

    private static final Map<String, Variation> VARIATIONS = Map.of(
            "on", new Variation("on", true),
            "off", new Variation("off", false));

    private final Rule bdOn = new Rule("bd-on",
            new Condition("country", Operator.EQUALS, List.of("BD")), "on");
    private final Rule bdOff = new Rule("bd-off",
            new Condition("country", Operator.EQUALS, List.of("BD")), "off");

    private Flag flag(List<Rule> rules, Rollout rollout) {
        EnvironmentConfig prod = new EnvironmentConfig(
                false, "off", "off", Map.of("vip", "on"), rules, rollout);
        return new Flag("new-checkout", FlagType.BOOLEAN, VARIATIONS,
                Map.of(Environment.PRODUCTION, prod));
    }

    private EvaluationResult eval(Flag flag, EvaluationContext ctx) {
        return evaluator.evaluate(flag, Environment.PRODUCTION, ctx);
    }

    private EvaluationContext user(String key, String country) {
        return EvaluationContext.builder(key).attribute("country", country).build();
    }

    @Test
    void matchingRuleServesItsVariation() {
        Flag f = flag(List.of(bdOn), null);
        EvaluationResult bd = eval(f, user("u1", "BD"));
        assertEquals(true, bd.value());
        assertEquals(Reason.RULE_MATCH, bd.reason());

        EvaluationResult us = eval(f, user("u2", "US"));
        assertEquals(false, us.value());
        assertEquals(Reason.DEFAULT, us.reason());
    }

    @Test
    void userTargetBeatsRules() {
        Flag f = flag(List.of(bdOff), null);
        EvaluationResult r = eval(f, user("vip", "BD"));
        assertEquals(true, r.value());
        assertEquals(Reason.USER_TARGET, r.reason());
    }

    @Test
    void rulesBeatRollout() {
        Flag f = flag(List.of(bdOff), new Rollout("on", 100));
        EvaluationResult bd = eval(f, user("u1", "BD"));
        assertEquals(false, bd.value());
        assertEquals(Reason.RULE_MATCH, bd.reason());

        EvaluationResult us = eval(f, user("u2", "US"));
        assertEquals(true, us.value());
        assertEquals(Reason.ROLLOUT, us.reason());
    }

    @Test
    void zeroPercentServesNobodyAndHundredPercentServesEveryone() {
        Flag none = flag(List.of(), new Rollout("on", 0));
        Flag all = flag(List.of(), new Rollout("on", 100));
        for (int i = 0; i < 1_000; i++) {
            EvaluationContext ctx = EvaluationContext.builder("user-" + i).build();
            assertEquals(Reason.DEFAULT, eval(none, ctx).reason());
            assertEquals(Reason.ROLLOUT, eval(all, ctx).reason());
        }
    }

    @Test
    void raisingRolloutFrom10To20KeepsEveryoneInForOneMillionUsers() {
        Flag at10 = flag(List.of(), new Rollout("on", 10));
        Flag at20 = flag(List.of(), new Rollout("on", 20));
        int inAt10 = 0;
        for (int i = 0; i < 1_000_000; i++) {
            EvaluationContext ctx = EvaluationContext.builder("user-" + i).build();
            if (eval(at10, ctx).reason() == Reason.ROLLOUT) {
                inAt10++;
                assertEquals(Reason.ROLLOUT, eval(at20, ctx).reason(), "user-" + i + " dropped out");
            }
        }
        assertTrue(inAt10 > 98_000 && inAt10 < 102_000, "got " + inAt10);
    }
}