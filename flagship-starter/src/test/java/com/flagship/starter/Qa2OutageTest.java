package com.flagship.starter;

import static com.flagship.starter.TestSnapshots.flag;
import static com.flagship.starter.TestSnapshots.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * QA2: the application must keep working when the FlagShip server is unavailable.
 * The fake server can be stopped and started again on the same port.
 */
class Qa2OutageTest {

    /** A pretend FlagShip server that can be switched off and on. */
    private static final class SwitchableServer {

        private HttpServer http;
        private int port;

        void start(int wantedPort, String body) throws IOException {
            IOException last = null;
            // After a stop the port can take a moment to become free again, so try for a few seconds.
            for (int attempt = 0; attempt < 50; attempt++) {
                try {
                    http = HttpServer.create(new InetSocketAddress("127.0.0.1", wantedPort), 0);
                    last = null;
                    break;
                } catch (IOException e) {
                    last = e;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (last != null || http == null) {
                throw last != null ? last : new IOException("could not start the test server");
            }
            http.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "qa2-test-server");
                t.setDaemon(true);
                return t;
            }));
            http.createContext("/", exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            http.start();
            port = http.getAddress().getPort();
        }

        int port() {
            return port;
        }

        String url() {
            return "http://127.0.0.1:" + port;
        }

        void stop() {
            if (http != null) {
                http.stop(0);
                http = null;
            }
        }
    }

    @TempDir
    Path dir;

    private final SwitchableServer server = new SwitchableServer();

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private ApplicationContextRunner runner(String serverUrl) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlagShipAutoConfiguration.class))
                .withPropertyValues(
                        "flagship.project=checkout-app",
                        "flagship.server-url=" + serverUrl,
                        "flagship.push-enabled=false",
                        "flagship.poll-interval=300ms",
                        "flagship.request-timeout=2s",
                        "flagship.snapshot-file=" + dir.resolve("snapshot.json").toString().replace('\\', '/'));
    }

    private static String variation(FlagClient client) {
        return client.evaluate("new-checkout", EvaluationContext.builder("u1").build())
                .map(EvaluationResult::variationName)
                .orElse("none");
    }

    @Test
    void appKeepsAnsweringWhileTheServerIsDown() throws IOException {
        server.start(0, snapshot(flag("new-checkout", "on")));

        runner(server.url()).run(context -> {
            FlagClient client = context.getBean(FlagClient.class);
            SnapshotStore store = context.getBean(SnapshotStore.class);
            assertEquals("on", variation(client));

            server.stop();
            long failuresBefore = store.refreshFailureCount();
            // Polling keeps trying and failing: that is the outage, seen from the app.
            await().atMost(Duration.ofSeconds(10)).until(
                    () -> store.refreshFailureCount() >= failuresBefore + 2);

            for (int i = 0; i < 1000; i++) {
                assertEquals("on", variation(client));
            }
            assertTrue(store.isLoaded());
            System.out.println("QA2 outage: app answered 1,000 evaluations from memory after "
                    + (store.refreshFailureCount() - failuresBefore) + " failed refreshes");
        });
    }

    @Test
    void appStartsFromTheLocalCopyWhenTheServerIsDown() throws IOException {
        // A copy saved by an earlier run of the application.
        Files.writeString(dir.resolve("snapshot.json"), snapshot(flag("new-checkout", "on")));
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        runner("http://127.0.0.1:" + closedPort).run(context -> {
            FlagClient client = context.getBean(FlagClient.class);
            SnapshotStore store = context.getBean(SnapshotStore.class);

            assertTrue(store.isLoaded());
            assertEquals("on", variation(client));
            assertThat(store.lastRefreshSuccess()).isNull();   // nothing came from the server
            System.out.println("QA2 outage: app started with the server down and answered from the local copy");
        });
    }

    @Test
    void appRecoversByItselfWhenTheServerReturns() throws IOException {
        server.start(0, snapshot(flag("new-checkout", "off")));
        int port = server.port();

        runner(server.url()).run(context -> {
            FlagClient client = context.getBean(FlagClient.class);
            SnapshotStore store = context.getBean(SnapshotStore.class);
            assertEquals("off", variation(client));

            server.stop();
            await().atMost(Duration.ofSeconds(10)).until(() -> store.refreshFailureCount() >= 1);
            assertEquals("off", variation(client));   // still answering during the outage

            // The server comes back on the same address, now with a changed flag.
            long successesBefore = store.refreshSuccessCount();
            server.start(port, snapshot(flag("new-checkout", "on")));
            long start = System.nanoTime();

            await().atMost(Duration.ofSeconds(10)).until(() -> "on".equals(variation(client)));
            long millis = (System.nanoTime() - start) / 1_000_000;

            assertThat(store.refreshSuccessCount()).isGreaterThan(successesBefore);
            System.out.println("QA2 outage: app saw the restarted server's new value after " + millis + " ms");
        });
    }
}