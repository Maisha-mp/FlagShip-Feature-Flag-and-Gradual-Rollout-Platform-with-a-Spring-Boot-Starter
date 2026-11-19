package com.flagship.core.model;

import com.flagship.core.rule.RuleNode;

/** If the condition tree matches the user, serve the named variation. */
public record Rule(String id, RuleNode condition, String variation) { }