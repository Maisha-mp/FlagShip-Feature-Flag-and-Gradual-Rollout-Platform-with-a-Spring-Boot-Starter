package com.flagship.starter;

import java.time.Duration;

/** How long to wait before the next reconnect attempt: starts small, doubles, never exceeds a maximum. */
public final class Backoff {

    private final Duration initial;
    private final Duration max;

    public Backoff(Duration initial, Duration max) {
        if (initial.isZero() || initial.isNegative()) {
            throw new IllegalArgumentException("initial delay must be greater than zero");
        }
        if (max.compareTo(initial) < 0) {
            throw new IllegalArgumentException("max delay must not be smaller than the initial delay");
        }
        this.initial = initial;
        this.max = max;
    }

    /** Delay after this many failed attempts in a row (0 or 1 gives the initial delay, then it doubles). */
    public Duration delay(int consecutiveFailures) {
        long millis = initial.toMillis();
        long limit = max.toMillis();
        for (int i = 1; i < consecutiveFailures && millis < limit; i++) {
            millis *= 2;
        }
        return Duration.ofMillis(Math.min(millis, limit));
    }
}