package com.flagship.core.rule;

public sealed interface RuleNode permits Condition, AllOf, AnyOf {
    <R> R accept(RuleVisitor<R> visitor);
}