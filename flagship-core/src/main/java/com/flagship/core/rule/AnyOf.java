package com.flagship.core.rule;

import java.util.List;

/** OR group: matches if at least one child matches. */
public record AnyOf(List<RuleNode> children) implements RuleNode {

    public AnyOf {
        children = List.copyOf(children);
    }

    @Override
    public <R> R accept(RuleVisitor<R> visitor) {
        return visitor.visitAnyOf(this);
    }
}
