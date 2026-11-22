package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.SnapshotCodec;
import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Qa1SpeedTest {

    private static final int FLAGS = 1000;
    private static final int USERS = 10;
    private static final int WARMUP = 100_000;
    private static final int SAMPLES = 100_000;
    private static final long LIMIT_NANOS = 50_000;   // 50 microseconds

    @TempDir
    Path dir;

    private SnapshotStore loadedStore() {
        String json = SnapshotFixtures.snapshotJson(FLAGS);
        SnapshotStore store = new SnapshotStore(() -> json,
                new SnapshotFile(dir.resolve("snapshot.json")),
                new SnapshotCodec(new ObjectMapper()));
        assertTrue(store.refresh());
        return store;
    }

    /** Users who match no target and no rule, so every call runs the whole chain down to the rollout. */
    private static EvaluationContext[] users() {
        EvaluationContext[] users = new EvaluationContext[USERS];
        for (int i = 0; i < USERS; i++) {
            users[i] = EvaluationContext.builder("user-" + i)
                    .attribute("country", "US")
                    .attribute("appVersion", "1.5.0")
                    .attribute("plan", "free")
                    .attribute("email", "someone" + i + "@gmail.com")
                    .build();
        }
        return users;
    }

    /** The time of each single call in nanoseconds, sorted. */
    private static long[] measure(FlagClient client) {
        EvaluationContext[] users = users();
        Random random = new Random(42);

        for (int i = 0; i < WARMUP; i++) {   // lets Java optimise the code first
            client.evaluate(SnapshotFixtures.flagKey(random.nextInt(FLAGS)),
                    users[random.nextInt(USERS)]);
        }

        long[] times = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            String key = SnapshotFixtures.flagKey(random.nextInt(FLAGS));
            EvaluationContext context = users[random.nextInt(USERS)];

            long start = System.nanoTime();
            Optional<EvaluationResult> result = client.evaluate(key, context);
            times[i] = System.nanoTime() - start;

            assertTrue(result.isPresent());
        }
        Arrays.sort(times);
        return times;
    }

    private static long p99(long[] sorted) {
        return sorted[(int) (SAMPLES * 0.99)];
    }

    private static void report(String name, long[] sorted) {
        System.out.println("QA1 speed, " + name + ": p50 = " + sorted[SAMPLES / 2] / 1000.0
                + " us, p99 = " + p99(sorted) / 1000.0
                + " us, max = " + sorted[SAMPLES - 1] / 1000.0
                + " us (limit " + LIMIT_NANOS / 1000 + " us for p99)");
    }

    @Test
    void uncachedEvaluationMeetsTheLimit() {
        long[] times = measure(new DefaultFlagClient(loadedStore()));

        report("without cache", times);
        assertTrue(p99(times) < LIMIT_NANOS, "p99 was " + p99(times) + " ns");
    }

    @Test
    void cachedEvaluationMeetsTheLimit() {
        SnapshotStore store = loadedStore();
        CachingFlagClient client = new CachingFlagClient(
                new DefaultFlagClient(store), store::current, 20_000);

        // Fill the cache with every flag x user combination, then measure cache hits.
        EvaluationContext[] users = users();
        for (int f = 0; f < FLAGS; f++) {
            for (EvaluationContext user : users) {
                client.evaluate(SnapshotFixtures.flagKey(f), user);
            }
        }
        assertEquals(FLAGS * USERS, client.cachedEntries());

        long[] times = measure(client);

        report("with cache", times);
        assertTrue(p99(times) < LIMIT_NANOS, "p99 was " + p99(times) + " ns");
    }
}