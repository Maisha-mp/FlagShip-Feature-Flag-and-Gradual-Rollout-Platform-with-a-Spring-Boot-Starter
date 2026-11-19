package com.flagship.core.rule;

public interface RuleVisitor<R> {
    R visitCondition(Condition condition);
    R visitAllOf(AllOf allOf);
    R visitAnyOf(AnyOf anyOf);
}