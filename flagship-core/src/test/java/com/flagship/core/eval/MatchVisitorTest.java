package com.flagship.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import com.flagship.core.rule.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchVisitorTest {

    private final EvaluationContext ctx = EvaluationContext.builder("u1")
            .attribute("country", "BD")
            .attribute("appVersion", "2.1.0")
            .build();

    private final RuleNode bd = new Condition("country", Operator.EQUALS, List.of("BD"));
    private final RuleNode us = new Condition("country", Operator.EQUALS, List.of("US"));
    private final RuleNode newApp = new Condition("appVersion", Operator.SEMVER_GTE, List.of("2.0.0"));

    private boolean matches(RuleNode node) {
        return node.accept(new MatchVisitor(ctx));
    }

    @Test
    void allOfNeedsEveryChild() {
        assertTrue(matches(new AllOf(List.of(bd, newApp))));
        assertFalse(matches(new AllOf(List.of(us, newApp))));
    }

    @Test
    void anyOfNeedsOneChild() {
        assertTrue(matches(new AnyOf(List.of(us, bd))));
        assertFalse(matches(new AnyOf(List.of(us))));
    }

    @Test
    void groupsCanBeNested() {
        assertTrue(matches(new AllOf(List.of(newApp, new AnyOf(List.of(us, bd))))));
    }

    @Test
    void aMissingAttributeNeverMatches() {
        RuleNode plan = new Condition("plan", Operator.EQUALS, List.of("pro"));
        assertFalse(matches(plan));
    }
}
