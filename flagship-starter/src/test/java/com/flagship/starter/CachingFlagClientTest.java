package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.SnapshotCodec;
import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.flagship.core.eval.Reason;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachingFlagClientTest {

    private static final String SNAPSHOT = """
            {"project":"checkout-app","environment":"PRODUCTION","generatedAt":"2026-11-19T07:49:51.554061200Z",
             "flags":[{"key":"new-checkout","type":"BOOLEAN","variations":{"on":true,"off":false},
               "config":{"killSwitchOn":false,"offVariation":"off","defaultVariation":"off",
                 "userTargets":{"user-1":"on"},
                 "rules":[{"id":"r1","variation":"on","condition":{"type":"condition","attribute":"country","operator":"IN","values":["BD","IN"]}}],
                 "rollout":{"variation":"on","percent":10.0}}}]}
            """;

    /** A fake client that counts how often it is really asked. */
    private static final class CountingClient implements FlagClient {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Optional<EvaluationResult> evaluate(String flagKey, EvaluationContext context) {
            calls.incrementAndGet();
            return Optional.of(new EvaluationResult("on", true, Reason.DEFAULT));
        }
    }

    @TempDir
    Path dir;

    private static EvaluationContext user(String key, String country) {
        return EvaluationContext.builder(key).attribute("country", country).build();
    }

    @Test
    void sameFlagAndSameUserIsComputedOnlyOnce() {
        CountingClient delegate = new CountingClient();
        CachingFlagClient client = new CachingFlagClient(delegate, () -> "v1", 100);

        client.evaluate("f", user("u1", "BD"));
        client.evaluate("f", user("u1", "BD"));                      // a separately built, equal context
        assertTrue(client.boolValue("f", user("u1", "BD"), false));  // typed getters use the cache too

        assertEquals(1, delegate.calls.get());
    }

    @Test
    void differentAttributesUsersAndFlagsAreSeparateEntries() {
        CountingClient delegate = new CountingClient();
        CachingFlagClient client = new CachingFlagClient(delegate, () -> "v1", 100);

        client.evaluate("f", user("u1", "BD"));
        client.evaluate("f", user("u1", "US"));   // same user, other country
        client.evaluate("f", user("u2", "BD"));   // other user
        client.evaluate("g", user("u1", "BD"));   // other flag

        assertEquals(4, delegate.calls.get());
    }

    @Test
    void cacheIsDroppedWhenTheSnapshotChanges() {
        CountingClient delegate = new CountingClient();
        AtomicReference<Object> version = new AtomicReference<>(new Object());
        CachingFlagClient client = new CachingFlagClient(delegate, version::get, 100);

        client.evaluate("f", user("u1", "BD"));
        client.evaluate("f", user("u1", "BD"));
        assertEquals(1, delegate.calls.get());

        version.set(new Object());   // a new snapshot arrived

        client.evaluate("f", user("u1", "BD"));
        client.evaluate("f", user("u1", "BD"));
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void cacheNeverGrowsPastItsLimit() {
        CachingFlagClient client = new CachingFlagClient(new CountingClient(), () -> "v1", 3);

        for (int i = 0; i < 20; i++) {
            assertTrue(client.evaluate("f", user("user-" + i, "BD")).isPresent());
            assertTrue(client.cachedEntries() <= 3);
        }
    }

    @Test
    void nonsenseLimitIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CachingFlagClient(new CountingClient(), () -> "v1", 0));
    }

    @Test
    void cachedAnswersAreIdenticalToUncachedAnswers() {
        SnapshotStore store = new SnapshotStore(() -> SNAPSHOT,
                new SnapshotFile(dir.resolve("snapshot.json")),
                new SnapshotCodec(new ObjectMapper()));
        store.refresh();

        FlagClient uncached = new DefaultFlagClient(store);
        FlagClient cached = new CachingFlagClient(new DefaultFlagClient(store), store::current, 1000);

        for (int i = 0; i < 300; i++) {
            EvaluationContext ctx = user("user-" + i, i % 2 == 0 ? "BD" : "US");
            Optional<EvaluationResult> expected = uncached.evaluate("new-checkout", ctx);

            assertEquals(expected, cached.evaluate("new-checkout", ctx));   // computed
            assertEquals(expected, cached.evaluate("new-checkout", ctx));   // from the cache
        }
    }
}