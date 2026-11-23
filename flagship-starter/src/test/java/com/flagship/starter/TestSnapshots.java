package com.flagship.starter;

/** Builds small snapshot JSON texts for tests. */
final class TestSnapshots {

    private TestSnapshots() { }

    static String flag(String key, String defaultVariation) {
        return "{\"key\":\"" + key + "\",\"type\":\"BOOLEAN\","
                + "\"variations\":{\"on\":true,\"off\":false},"
                + "\"config\":{\"killSwitchOn\":false,\"offVariation\":\"off\","
                + "\"defaultVariation\":\"" + defaultVariation + "\","
                + "\"userTargets\":{},\"rules\":[],\"rollout\":null}}";
    }

    static String snapshot(String... flags) {
        return "{\"project\":\"checkout-app\",\"environment\":\"PRODUCTION\","
                + "\"generatedAt\":\"2026-11-22T10:00:00Z\",\"flags\":["
                + String.join(",", flags) + "]}";
    }
}