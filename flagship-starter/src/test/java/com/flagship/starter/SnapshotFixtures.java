package com.flagship.starter;

/** Builds a big, realistic snapshot JSON for the QA1 tests. */
final class SnapshotFixtures {

    private SnapshotFixtures() { }

    static String flagKey(int i) {
        return "flag-" + i;
    }

    static String snapshotJson(int flagCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"project\":\"checkout-app\",\"environment\":\"PRODUCTION\",")
                .append("\"generatedAt\":\"2026-11-21T08:00:00Z\",\"flags\":[");
        for (int i = 0; i < flagCount; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(flagJson(i));
        }
        return sb.append("]}").toString();
    }

    /**
     * One flag: 5 user targets, 10 rules (nested AND/OR, semver, regex) and a 10% rollout.
     * For the QA1 test users (country US, version 1.5.0, plan free, gmail address) no rule matches,
     * and every rule is checked all the way to its last condition.
     */
    private static String flagJson(int i) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"key\":\"").append(flagKey(i)).append("\",\"type\":\"BOOLEAN\",")
                .append("\"variations\":{\"on\":true,\"off\":false},")
                .append("\"config\":{\"killSwitchOn\":false,\"offVariation\":\"off\",")
                .append("\"defaultVariation\":\"off\",\"userTargets\":{");
        for (int u = 0; u < 5; u++) {
            if (u > 0) {
                sb.append(',');
            }
            sb.append("\"vip-").append(i).append('-').append(u).append("\":\"on\"");
        }
        sb.append("},\"rules\":[");
        for (int r = 0; r < 10; r++) {
            if (r > 0) {
                sb.append(',');
            }
            sb.append(r % 2 == 0 ? allOfRule(r) : anyOfRule(r));
        }
        sb.append("],\"rollout\":{\"variation\":\"on\",\"percent\":10.0}}}");
        return sb.toString();
    }

    /** AND of country IN, version >= 1.0.0 and a regex that a gmail address never matches. */
    private static String allOfRule(int r) {
        return "{\"id\":\"r" + r + "\",\"variation\":\"on\",\"condition\":{\"type\":\"allOf\",\"children\":["
                + "{\"type\":\"condition\",\"attribute\":\"country\",\"operator\":\"IN\",\"values\":[\"US\",\"JP\",\"DE\"]},"
                + "{\"type\":\"condition\",\"attribute\":\"appVersion\",\"operator\":\"SEMVER_GTE\",\"values\":[\"1.0.0\"]},"
                + "{\"type\":\"condition\",\"attribute\":\"email\",\"operator\":\"REGEX\",\"values\":[\"^x" + r
                + ".*@corp\\\\.com$\"]}]}}";
    }

    /** OR of plan = pro and a regex on the email: a free user with a gmail address matches neither. */
    private static String anyOfRule(int r) {
        return "{\"id\":\"r" + r + "\",\"variation\":\"on\",\"condition\":{\"type\":\"anyOf\",\"children\":["
                + "{\"type\":\"condition\",\"attribute\":\"plan\",\"operator\":\"EQUALS\",\"values\":[\"pro\"]},"
                + "{\"type\":\"condition\",\"attribute\":\"email\",\"operator\":\"REGEX\",\"values\":[\".*@example\\\\.com\"]}]}}";
    }
}