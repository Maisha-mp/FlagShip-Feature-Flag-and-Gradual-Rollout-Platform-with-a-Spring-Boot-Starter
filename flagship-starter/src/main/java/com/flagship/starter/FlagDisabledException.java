package com.flagship.starter;

/** Thrown when a method guarded by @FlagGuard is called while its flag is off and no fallback is set. */
public class FlagDisabledException extends RuntimeException {

    private final String flagKey;

    public FlagDisabledException(String flagKey) {
        super("Feature flag '" + flagKey + "' is off");
        this.flagKey = flagKey;
    }

    public String flagKey() {
        return flagKey;
    }
}