package com.flagship.core.eval;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class EvaluationContext {

    private final String userKey;
    private final Map<String, Object> attributes;

    private EvaluationContext(Builder b) {
        this.userKey = Objects.requireNonNull(b.userKey, "userKey");
        this.attributes = Map.copyOf(b.attributes);
    }

    public String userKey() { return userKey; }

    public Object attribute(String name) { return attributes.get(name); }

    public static Builder builder(String userKey) { return new Builder(userKey); }

    /** Two contexts are equal when they describe the same user with the same attributes. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EvaluationContext other)) {
            return false;
        }
        return userKey.equals(other.userKey) && attributes.equals(other.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey, attributes);
    }

    public static final class Builder {
        private final String userKey;
        private final Map<String, Object> attributes = new HashMap<>();

        private Builder(String userKey) { this.userKey = userKey; }

        public Builder attribute(String name, Object value) {
            attributes.put(name, value);
            return this;
        }

        public EvaluationContext build() { return new EvaluationContext(this); }
    }
}