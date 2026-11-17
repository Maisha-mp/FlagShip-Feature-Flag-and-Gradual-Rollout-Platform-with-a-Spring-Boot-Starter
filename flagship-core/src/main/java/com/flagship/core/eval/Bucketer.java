package com.flagship.core.eval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Bucketer {

    public static final int BUCKETS = 10_000;

    private Bucketer() { }

    /** Deterministic bucket 0..9999 for this flag and user. */
    public static int bucket(String flagKey, String userKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((flagKey + ":" + userKey).getBytes(StandardCharsets.UTF_8));
            long value = ((h[0] & 0xFFL) << 24) | ((h[1] & 0xFFL) << 16)
                       | ((h[2] & 0xFFL) << 8)  |  (h[3] & 0xFFL);
            return (int) (value % BUCKETS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** True if the user falls inside the first `percent` percent (0..100). */
    public static boolean inRollout(String flagKey, String userKey, double percent) {
        return bucket(flagKey, userKey) < percent * 100.0;
    }
}
