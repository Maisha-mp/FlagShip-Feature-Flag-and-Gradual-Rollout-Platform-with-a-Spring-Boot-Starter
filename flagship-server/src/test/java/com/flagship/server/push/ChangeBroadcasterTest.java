package com.flagship.server.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.Environment;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangeBroadcasterTest {

    /** A pretend receiver that remembers what it was sent. */
    private static final class FakeSink implements ChangeSink {
        final List<String> events = new CopyOnWriteArrayList<>();   // "name|json"
        final AtomicInteger pings = new AtomicInteger();
        volatile boolean alive = true;

        @Override
        public boolean send(String eventName, String json) {
            if (!alive) {
                return false;
            }
            events.add(eventName + "|" + json);
            return true;
        }

        @Override
        public boolean ping() {
            if (!alive) {
                return false;
            }
            pings.incrementAndGet();
            return true;
        }

        String name(int index) {
            String e = events.get(index);
            return e.substring(0, e.indexOf('|'));
        }

        String json(int index) {
            String e = events.get(index);
            return e.substring(e.indexOf('|') + 1);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private ChangeBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new ChangeBroadcaster(mapper, Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() {
        broadcaster.close();
    }

    private static FlagsChangedEvent change(String project, Environment env) {
        return new FlagsChangedEvent(project, "new-checkout", env);
    }

    @Test
    void helloIsSentWhenAnAppConnects() throws Exception {
        FakeSink sink = new FakeSink();

        broadcaster.register("checkout-app", Environment.PRODUCTION, sink);

        assertEquals(1, sink.events.size());
        assertEquals("hello", sink.name(0));
        assertEquals("checkout-app", mapper.readTree(sink.json(0)).get("project").asText());
        assertEquals("PRODUCTION", mapper.readTree(sink.json(0)).get("environment").asText());
    }

    @Test
    void changeReachesOnlyAppsOfTheSameProject() {
        FakeSink mine = new FakeSink();
        FakeSink other = new FakeSink();
        broadcaster.register("checkout-app", Environment.PRODUCTION, mine);
        broadcaster.register("other-project", Environment.PRODUCTION, other);

        broadcaster.onChange(change("checkout-app", Environment.PRODUCTION));

        assertEquals(2, mine.events.size());
        assertEquals("change", mine.name(1));
        assertEquals(1, other.events.size());   // only its hello
    }

    @Test
    void changeInAnotherEnvironmentIsNotSent() {
        FakeSink sink = new FakeSink();
        broadcaster.register("checkout-app", Environment.PRODUCTION, sink);

        broadcaster.onChange(change("checkout-app", Environment.DEVELOPMENT));

        assertEquals(1, sink.events.size());   // only the hello
    }

    @Test
    void changeWithoutEnvironmentReachesEveryEnvironment() {
        FakeSink dev = new FakeSink();
        FakeSink prod = new FakeSink();
        broadcaster.register("checkout-app", Environment.DEVELOPMENT, dev);
        broadcaster.register("checkout-app", Environment.PRODUCTION, prod);

        broadcaster.onChange(change("checkout-app", null));

        assertEquals("change", dev.name(1));
        assertEquals("change", prod.name(1));
    }

    @Test
    void goneAppsAreRemovedWhenASendFails() {
        FakeSink sink = new FakeSink();
        broadcaster.register("checkout-app", Environment.PRODUCTION, sink);
        assertEquals(1, broadcaster.subscriberCount("checkout-app"));

        sink.alive = false;
        broadcaster.onChange(change("checkout-app", Environment.PRODUCTION));

        assertEquals(0, broadcaster.subscriberCount("checkout-app"));
    }

    @Test
    void unregisteredAppsGetNothingMore() {
        FakeSink sink = new FakeSink();
        Runnable unregister = broadcaster.register("checkout-app", Environment.PRODUCTION, sink);

        unregister.run();
        broadcaster.onChange(change("checkout-app", Environment.PRODUCTION));

        assertEquals(1, sink.events.size());   // only the hello
        assertEquals(0, broadcaster.subscriberCount("checkout-app"));
    }

    @Test
    void changeNumbersCountUp() throws Exception {
        FakeSink sink = new FakeSink();
        broadcaster.register("checkout-app", Environment.PRODUCTION, sink);

        broadcaster.onChange(change("checkout-app", Environment.PRODUCTION));
        broadcaster.onChange(change("checkout-app", Environment.PRODUCTION));

        assertEquals(1, mapper.readTree(sink.json(1)).get("version").asLong());
        assertEquals(2, mapper.readTree(sink.json(2)).get("version").asLong());
        assertEquals("new-checkout", mapper.readTree(sink.json(1)).get("flag").asText());
    }

    @Test
    void heartbeatPingsLiveAppsAndDropsDeadOnes() throws InterruptedException {
        ChangeBroadcaster fast = new ChangeBroadcaster(mapper, Duration.ofMillis(50));
        try {
            FakeSink live = new FakeSink();
            FakeSink dead = new FakeSink();
            fast.register("checkout-app", Environment.PRODUCTION, live);
            fast.register("checkout-app", Environment.PRODUCTION, dead);
            dead.alive = false;

            long deadline = System.currentTimeMillis() + 3000;
            while ((live.pings.get() < 2 || fast.subscriberCount("checkout-app") != 1)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }

            assertTrue(live.pings.get() >= 2, "only " + live.pings.get() + " pings");
            assertEquals(1, fast.subscriberCount("checkout-app"));
        } finally {
            fast.close();
        }
    }
}