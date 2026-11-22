package com.flagship.core.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class EvaluationContextTest {

    @Test
    void sameUserAndAttributesAreEqualWhateverTheOrder() {
        EvaluationContext a = EvaluationContext.builder("u1")
                .attribute("country", "BD").attribute("plan", "pro").build();
        EvaluationContext b = EvaluationContext.builder("u1")
                .attribute("plan", "pro").attribute("country", "BD").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void aDifferentAttributeValueMakesThemDifferent() {
        EvaluationContext bd = EvaluationContext.builder("u1").attribute("country", "BD").build();
        EvaluationContext us = EvaluationContext.builder("u1").attribute("country", "US").build();

        assertNotEquals(bd, us);
    }

    @Test
    void aDifferentUserMakesThemDifferent() {
        EvaluationContext u1 = EvaluationContext.builder("u1").attribute("country", "BD").build();
        EvaluationContext u2 = EvaluationContext.builder("u2").attribute("country", "BD").build();

        assertNotEquals(u1, u2);
    }
}