package com.flagship.core.eval;

/** A step's answer: which variation, and why. */
public record Decision(String variationName, Reason reason) { }