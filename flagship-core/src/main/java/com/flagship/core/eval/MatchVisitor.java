package com.flagship.core.eval;

import com.flagship.core.rule.AllOf;
import com.flagship.core.rule.AnyOf;
import com.flagship.core.rule.Condition;
import com.flagship.core.rule.RuleNode;
import com.flagship.core.rule.RuleVisitor;

/** Walks a rule tree and answers: does this user match? */
public final class MatchVisitor implements RuleVisitor<Boolean> {

    private final EvaluationContext ctx;

    public MatchVisitor(EvaluationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Boolean visitCondition(Condition c) {
        Object actual = ctx.attribute(c.attribute());
        return actual != null && c.operator().test(actual, c.values());
    }

    @Override
    public Boolean visitAllOf(AllOf node) {
        for (RuleNode child : node.children()) {
            if (!child.accept(this)) return false;
        }
        return true;
    }

    @Override
    public Boolean visitAnyOf(AnyOf node) {
        for (RuleNode child : node.children()) {
            if (child.accept(this)) return true;
        }
        return false;
    }
}