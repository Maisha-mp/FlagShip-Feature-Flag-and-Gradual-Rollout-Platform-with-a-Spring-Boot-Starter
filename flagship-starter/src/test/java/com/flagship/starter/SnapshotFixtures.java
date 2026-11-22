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

    /** One flag: 5 user targets, 2 rules (nested AND/OR, semver, regex) and a 10% rollout. */
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
        sb.append("},\"rules\":[")
                .append("{\"id\":\"r1\",\"variation\":\"on\",\"condition\":{\"type\":\"allOf\",\"children\":[")
                .append("{\"type\":\"condition\",\"attribute\":\"country\",\"operator\":\"IN\",\"values\":[\"BD\",\"IN\",\"PK\"]},")
                .append("{\"type\":\"condition\",\"attribute\":\"appVersion\",\"operator\":\"SEMVER_GTE\",\"values\":[\"2.0.0\"]}]}},")
                .append("{\"id\":\"r2\",\"variation\":\"on\",\"condition\":{\"type\":\"anyOf\",\"children\":[")
                .append("{\"type\":\"condition\",\"attribute\":\"plan\",\"operator\":\"EQUALS\",\"values\":[\"pro\"]},")
                .append("{\"type\":\"condition\",\"attribute\":\"email\",\"operator\":\"REGEX\",\"values\":[\".*@example\\\\.com\"]}]}}")
                .append("],\"rollout\":{\"variation\":\"on\",\"percent\":10.0}}}");
        return sb.toString();
    }
}