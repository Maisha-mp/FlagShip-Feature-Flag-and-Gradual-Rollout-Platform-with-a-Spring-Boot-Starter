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