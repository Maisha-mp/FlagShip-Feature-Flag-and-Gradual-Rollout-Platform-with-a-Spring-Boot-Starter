package com.flagship.starter;

import static com.flagship.starter.TestSnapshots.flag;
import static com.flagship.starter.TestSnapshots.snapshot;
import static org.awaitility.Awaitility.await;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * QA3: a flag change on the server must reach connected applications quickly.
 * The limit below is an assumption (the spec text was never pasted): check it against the spec.
 */
class Qa3PropagationTest {

    /** Assumed limit: the slowest of the measured changes must arrive within this time. */
    private static final long LIMIT_MILLIS = 1000;
    private static final int WARM_UP = 3;
    private static final int MEASURED = 30;

    /** A pretend FlagShip server: a snapshot endpoint and a push stream the test can fire. */
    private static final class FakeServer {

        private final HttpServer http;
        private volatile String snapshotJson = snapshot(flag("new-checkout", "off"));
        private final List<OutputStream> streams = new CopyOnWriteArrayList<>();

        FakeServer() throws IOException {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "qa3-test-server");
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
                    Thread.sleep(120_000);   // keep the stream open until the test ends
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

    @TempDir
    Path dir;

    private FakeServer server;

    @BeforeEach
    void setUp() throws IOException {
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

    /** Waits (checking every millisecond) until the app shows the expected value; returns the wait in ms. */
    private static long millisUntilSeen(FlagClient client, String expected, long startNanos) {
        await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(1))
                .atMost(Duration.ofSeconds(10))
                .until(() -> expected.equals(variation(client)));
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @Test
    void pushedChangesReachTheAppWithinTheLimit() {
        // Polling is set to one hour, so only the push stream can explain a quick change.
        runner("flagship.poll-interval=1h").run(context -> {
            FlagClient client = context.getBean(FlagClient.class);
            SseChangeClient push = context.getBean(SseChangeClient.class);
            await().atMost(Duration.ofSeconds(5)).until(push::isConnected);
            Thread.sleep(300);   // let the catch-up refresh after "hello" finish

            List<Long> times = new ArrayList<>();
            for (int i = 0; i < WARM_UP + MEASURED; i++) {
                String expected = i % 2 == 0 ? "on" : "off";
                server.changeFlag(expected);
                long start = System.nanoTime();
                server.pushChange();
                long millis = millisUntilSeen(client, expected, start);
                if (i >= WARM_UP) {
                    times.add(millis);
                }
            }

            Collections.sort(times);
            long min = times.get(0);
            long median = times.get(times.size() / 2);
            long p95 = times.get((int) Math.ceil(0.95 * times.size()) - 1);
            long max = times.get(times.size() - 1);
            System.out.println("QA3 push propagation, " + MEASURED + " changes: min " + min
                    + " ms, median " + median + " ms, p95 " + p95 + " ms, max " + max
                    + " ms (limit " + LIMIT_MILLIS + " ms)");
            assertTrue(max < LIMIT_MILLIS, "slowest change took " + max + " ms");
        });
    }

    @Test
    void aMissedPushIsFixedByThePollingSafetyNet() {
        runner("flagship.poll-interval=500ms").run(context -> {
            FlagClient client = context.getBean(FlagClient.class);
            SseChangeClient push = context.getBean(SseChangeClient.class);
            await().atMost(Duration.ofSeconds(5)).until(push::isConnected);
            Thread.sleep(300);

            server.changeFlag("on");   // the server changes but sends NO push event
            long start = System.nanoTime();
            long millis = millisUntilSeen(client, "on", start);

            System.out.println("QA3 missed push, found by polling (500 ms interval): " + millis + " ms");
            assertTrue(millis < 3000, "polling took " + millis + " ms");
        });
    }
}