package com.flagship.starter;

import com.flagship.core.model.Flag;

/** What happened to one flag. before is null for ADDED, after is null for REMOVED. */
public record FlagChange(String flagKey, Type type, Flag before, Flag after) {

    public enum Type { ADDED, UPDATED, REMOVED }
}