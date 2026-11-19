package com.flagship.core.model;

public record Rollout(String variation, double percent) {

    public Rollout {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be between 0 and 100: " + percent);
        }
    }
}
