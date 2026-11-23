package com.flagship.starter;

import static com.flagship.starter.TestSnapshots.flag;
import static com.flagship.starter.TestSnapshots.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class PushWiringTest {

    /** A pretend FlagShip server: a snapshot endpoint and a push stream the test can fire. */
    private static final class FakeServer {

        private final HttpServer http;
        private volatile String snapshotJson = snapshot(flag("new-checkout", "off"));
        private final List<OutputStream> streams = new CopyOnWriteArrayList<>();

        FakeServer() throws IOException {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "fake-flagship-server");
                t.setDaemon(true);
                return t;
            }));
            http.createContext("/api/projects/checkout-app/snapshot", exchange -> {
                byte[] bytes = snapshotJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            http.createContext("/api/projects/checkout-app/stream", exchange -> {
                try {
                    exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    OutputStream out = exchange.getResponseBody();
                    write(out, "event:hello\ndata:{\"version\":0}\n\n");
                    streams.add(out);
                    Thread.sleep(8000);   // keep the stream open; the test ends it
                    exchange.close();
                } catch (IOException | InterruptedException e) {
                    // the app left, or the test is over
                }
            });
            http.start();
        }

        String url() {
            return "http://127.0.0.1:" + http.getAddress().getPort();
        }

        /** Changes what the snapshot endpoint returns (does not tell anyone). */
        void changeFlag(String defaultVariation) {
            snapshotJson = snapshot(flag("new-checkout", defaultVariation));
        }

        /** Tells every connected app "something changed". */
        void pushChange() {
            for (OutputStream out : streams) {
                try {
                    write(out, "event:change\ndata:{\"flag\":\"new-checkout\"}\n\n");
                } catch (IOException e) {
                    streams.remove(out);
                }
            }
        }

        void stop() {
            http.stop(0);
        }

        private static void write(OutputStream out, String text) throws IOException {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    /** A listener written the way an application would: just a bean. */
    private static final List<FlagChangeEvent> HEARD = new CopyOnWriteArrayList<>();

    @Configuration(proxyBeanMethods = false)
    static class RecordingConfig {

        @Bean
        FlagChangeListener recordingListener() {
            return HEARD::add;
        }
    }

    @TempDir
    Path dir;

    private FakeServer server;

    @BeforeEach
    void setUp() throws IOException {
        HEARD.clear();
        server = new FakeServer();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private ApplicationContextRunner runner(String... extraProperties) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlagShipAutoConfiguration.class))
                .withPropertyValues(
                        "flagship.project=checkout-app",
                        "flagship.server-url=" + server.url(),
                        "flagship.snapshot-file=" + dir.resolve("snapshot.json").toString().replace('\\', '/'))
                .withPropertyValues(extraProperties);
    }

    private static String variation(FlagClient client) {
        return client.evaluate("new-checkout", EvaluationContext.builder("u1").build())
                .map(EvaluationResult::variationName)
                .orElse("none");
    }

    private static boolean waitUntil(BooleanSupplier condition, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }

    @Test
    void aPushedChangeIsVisibleAlmostAtOnce() {
        // Polling is set to one hour, so only the push stream can explain a quick change.
        runner("flagship.poll-interval=1h").run(context -> {
            FlagClient client = context.getBean(FlagClient.class);
            SseChangeClient push = context.getBean(SseChangeClient.class);
            assertEquals("off", variation(client));
            assertTrue(waitUntil(push::isConnected, 5000));
            Thread.sleep(300);   // let the catch-up refresh after "hello" finish

            server.changeFlag("on");
            long start = System.nanoTime();
            server.pushChange();

            assertTrue(waitUntil(() -> "on".equals(variation(client)), 3000), "the change never arrived");
            System.out.println("QA3 push propagation (server change to app sees it): "
                    + (System.nanoTime() - start) / 1_000_000 + " ms");
        });
    }

    @Test
    void pollingStillWorksWhenPushIsSwitchedOff() {
        runner("flagship.push-enabled=false", "flagship.poll-interval=300ms").run(context -> {
            FlagClient client = context.getBean(FlagClient.class);
            assertEquals(0, context.getBeanNamesForType(SseChangeClient.class).length);
            assertEquals("off", variation(client));

            server.changeFlag("on");   // nobody is told; only polling can find out

            assertTrue(waitUntil(() -> "on".equals(variation(client)), 5000), "polling never saw the change");
        });
    }

    @Test
    void applicationListenersAreToldAboutPushedChanges() {
        runner("flagship.poll-interval=1h").withUserConfiguration(RecordingConfig.class).run(context -> {
            SseChangeClient push = context.getBean(SseChangeClient.class);
            assertTrue(waitUntil(push::isConnected, 5000));
            Thread.sleep(300);

            server.changeFlag("on");
            server.pushChange();

            assertTrue(waitUntil(() -> !HEARD.isEmpty(), 3000), "the listener heard nothing");
            FlagChangeEvent event = HEARD.get(0);
            assertTrue(event.affects("new-checkout"));
            assertEquals(FlagChange.Type.UPDATED, event.changes().get(0).type());
        });
    }

    @Test
    void theApplicationGetsAPushClientUnlessItIsSwitchedOff() {
        runner().run(context ->
                assertEquals(1, context.getBeanNamesForType(SseChangeClient.class).length));
        runner("flagship.push-enabled=false").run(context ->
                assertEquals(0, context.getBeanNamesForType(SseChangeClient.class).length));
    }

    @Test
    void pushSettingsHaveSensibleDefaults() {
        runner().run(context -> {
            FlagShipProperties p = context.getBean(FlagShipProperties.class);
            assertTrue(p.isPushEnabled());
            assertEquals(Duration.ofSeconds(45), p.getPushIdleTimeout());
            assertEquals(Duration.ofSeconds(1), p.getPushRetryInitial());
            assertEquals(Duration.ofSeconds(30), p.getPushRetryMax());
        });
    }

    @Test
    void impossibleRetrySettingsFailWithAClearMessage() {
        runner("flagship.push-retry-initial=10s", "flagship.push-retry-max=5s").run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(rootMessage(context.getStartupFailure()).contains("max delay"));
        });
    }
}