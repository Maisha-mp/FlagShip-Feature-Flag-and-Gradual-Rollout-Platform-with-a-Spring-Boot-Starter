package com.flagship.core.rule;

import static com.flagship.core.rule.OperatorSupport.*;

import java.util.List;

public enum Operator {

    EQUALS {
        @Override public boolean test(Object actual, List<Object> values) {
            return !values.isEmpty() && same(actual, values.get(0));
        }
    },
    IN {
        @Override public boolean test(Object actual, List<Object> values) {
            for (Object v : values) {
                if (same(actual, v)) return true;
            }
            return false;
        }
    },
    CONTAINS {
        @Override public boolean test(Object actual, List<Object> values) {
            String text = String.valueOf(actual);
            for (Object v : values) {
                if (text.contains(String.valueOf(v))) return true;
            }
            return false;
        }
    },
    SEMVER_EQ {
        @Override public boolean test(Object actual, List<Object> values) {
            return !values.isEmpty() && semver(actual, values.get(0), c -> c == 0);
        }
    },
    SEMVER_GT {
        @Override public boolean test(Object actual, List<Object> values) {
            return !values.isEmpty() && semver(actual, values.get(0), c -> c > 0);
        }
    },
    SEMVER_GTE {
        @Override public boolean test(Object actual, List<Object> values) {
            return !values.isEmpty() && semver(actual, values.get(0), c -> c >= 0);
        }
    },
    SEMVER_LT {
        @Override public boolean test(Object actual, List<Object> values) {
            return !values.isEmpty() && semver(actual, values.get(0), c -> c < 0);
        }
    },
    SEMVER_LTE {
        @Override public boolean test(Object actual, List<Object> values) {
            return !values.isEmpty() && semver(actual, values.get(0), c -> c <= 0);
        }
    },
    REGEX {
        @Override public boolean test(Object actual, List<Object> values) {
            return !values.isEmpty() && regex(actual, values.get(0));
        }
    },
    DATE_BEFORE {
        @Override public boolean test(Object actual, List<Object> values) {
            return !values.isEmpty() && date(actual, values.get(0), true);
        }
    },
    DATE_AFTER {
        @Override public boolean test(Object actual, List<Object> values) {
            return !values.isEmpty() && date(actual, values.get(0), false);
        }
    };

    /** Does the user's attribute value satisfy this operator against the rule's values? */
    public abstract boolean test(Object actual, List<Object> values);
}
