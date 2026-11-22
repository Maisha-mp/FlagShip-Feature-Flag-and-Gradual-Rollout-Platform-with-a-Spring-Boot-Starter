package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class Qa1StartupTest {

    /** The 2 s startup limit (default) plus a margin for Spring's own setup on slow machines. */
    private static final long BOUND_MILLIS = 3000;

    private record Result(long millis, boolean loaded, int flagCount) { }

    @TempDir
    Path dir;

    private HttpServer server;

    @BeforeAll
    static void warmUpSpring() {
        // Loads Spring's classes once, so the measurements below do not include that.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlagShipAutoConfiguration.class))
                .withPropertyValues("flagship.enabled=false")
                .run(context -> { });
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** A tiny test server that answers after delayMillis. Returns its base URL. */
    private String startServer(long delayMillis, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "qa1-test-server");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", exchange -> {
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // the client gave up or the test already ended
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Starts a real Spring context with the starter and measures how long that takes. */
    private Result startWith(String serverUrl) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlagShipAutoConfiguration.class))
                .withPropertyValues(
                        "flagship.project=checkout-app",
                        "flagship.server-url=" + serverUrl,
                        "flagship.request-timeout=10s",   // so only the STARTUP limit can cut the wait
                        "flagship.snapshot-file=" + dir.resolve("snapshot.json").toString().replace('\\', '/'));

        AtomicReference<Result> out = new AtomicReference<>();
        long start = System.nanoTime();
        runner.run(context -> {
            long millis = (System.nanoTime() - start) / 1_000_000;
            SnapshotStore store = context.getBean(SnapshotStore.class);
            out.set(new Result(millis, store.isLoaded(),
                    store.current() == null ? 0 : store.current().flags().size()));
        });
        return out.get();
    }

    @Test
    void serverDownStartsWithinTheLimit() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        Result r = startWith("http://127.0.0.1:" + freePort);

        System.out.println("QA1 startup, server down, no local copy: " + r.millis() + " ms");
        assertFalse(r.loaded());
        assertTrue(r.millis() < BOUND_MILLIS, "took " + r.millis() + " ms");
    }

    @Test
    void slowServerDoesNotHoldUpStartup() throws IOException {
        String url = startServer(4000, SnapshotFixtures.snapshotJson(1));

        Result r = startWith(url);

        System.out.println("QA1 startup, server needs 4 s, no local copy: " + r.millis() + " ms");
        assertFalse(r.loaded());
        assertTrue(r.millis() < BOUND_MILLIS, "took " + r.millis() + " ms");
    }

    @Test
    void slowServerWithLocalCopyStartsWithFlagsAlreadyLoaded() throws IOException {
        Files.writeString(dir.resolve("snapshot.json"), SnapshotFixtures.snapshotJson(1000));
        String url = startServer(4000, SnapshotFixtures.snapshotJson(1));

        Result r = startWith(url);

        System.out.println("QA1 startup, server needs 4 s, local copy of 1,000 flags: " + r.millis() + " ms");
        assertTrue(r.loaded());
        assertEquals(1000, r.flagCount());
        assertTrue(r.millis() < BOUND_MILLIS, "took " + r.millis() + " ms");
    }

    @Test
    void healthyServerLoadsAThousandFlagsWithinTheLimit() throws IOException {
        String url = startServer(0, SnapshotFixtures.snapshotJson(1000));

        Result r = startWith(url);

        System.out.println("QA1 startup, healthy server, 1,000 flags downloaded and parsed: " + r.millis() + " ms");
        assertTrue(r.loaded());
        assertEquals(1000, r.flagCount());
        assertTrue(r.millis() < BOUND_MILLIS, "took " + r.millis() + " ms");
    }
}