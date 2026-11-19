package com.flagship.core.rule;

import java.util.List;

/** AND group: matches only if every child matches. */
public record AllOf(List<RuleNode> children) implements RuleNode {

    public AllOf {
        children = List.copyOf(children);
    }

    @Override
    public <R> R accept(RuleVisitor<R> visitor) {
        return visitor.visitAllOf(this);
    }
}