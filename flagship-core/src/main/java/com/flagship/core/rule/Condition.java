package com.flagship.core.rule;

import java.util.List;

/** Leaf: "attribute <operator> values", e.g. country IN [BD, IN]. */
public record Condition(String attribute, Operator operator, List<Object> values) implements RuleNode {

    public Condition {
        values = List.copyOf(values);
    }

    @Override
    public <R> R accept(RuleVisitor<R> visitor) {
        return visitor.visitCondition(this);
    }
}