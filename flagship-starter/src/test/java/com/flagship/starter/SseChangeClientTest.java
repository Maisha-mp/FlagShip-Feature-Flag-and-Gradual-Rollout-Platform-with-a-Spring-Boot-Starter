package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flagship.core.model.Environment;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SseChangeClientTest {

    private enum Mode {
        /** hello, then a ping and a change, then stay open. */
        NORMAL,
        /** hello, then hang up at once. */
        HANG_UP_AFTER_HELLO,
        /** answer 500 twice, then behave like NORMAL. */
        ERROR_FIRST_TWO,
        /** hello, then stay open but say nothing. */
        SILENT
    }

    private HttpServer server;
    private SseChangeClient client;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger signals = new AtomicInteger();

    @AfterEach
    void cleanUp() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    /** A tiny fake FlagShip server that only knows the stream endpoint. */
    private void startServer(Mode mode) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-push-server");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/api/projects/checkout-app/stream", exchange -> {
            int n = requests.incrementAndGet();
            try {
                if (mode == Mode.ERROR_FIRST_TWO && n <= 2) {
                    exchange.sendResponseHeaders(500, -1);
                    exchange.close();
                    return;
                }
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);   // 0 = keep writing (chunked)
                OutputStream out = exchange.getResponseBody();
                write(out, "event:hello\ndata:{\"version\":0}\n\n");
                if (mode == Mode.HANG_UP_AFTER_HELLO) {
                    exchange.close();
                    return;
                }
                if (mode == Mode.NORMAL || mode == Mode.ERROR_FIRST_TWO) {
                    Thread.sleep(200);
                    write(out, ":ping\n\n");
                    write(out, "event:change\ndata:{\"flag\":\"new-checkout\"}\n\n");
                }
                Thread.sleep(8000);   // keep the stream open; the test ends it
                exchange.close();
            } catch (IOException | InterruptedException e) {
                // the client left, or the test is over
            }
        });
        server.start();
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private SseChangeClient newClient(String serverUrl, Duration idleTimeout) {
        return new SseChangeClient(serverUrl, "checkout-app", Environment.PRODUCTION,
                Duration.ofSeconds(2), idleTimeout,
                new Backoff(Duration.ofMillis(50), Duration.ofMillis(400)),
                signals::incrementAndGet);
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

    @Test
    void helloAndChangeEachTriggerARefreshButPingsDoNot() throws Exception {
        startServer(Mode.NORMAL);
        client = newClient(url(), Duration.ofSeconds(30));

        client.start();

        assertTrue(waitUntil(() -> signals.get() >= 2, 5000), "signals: " + signals.get());
        assertTrue(client.isConnected());
        Thread.sleep(300);
        assertEquals(2, signals.get());   // the hello and the change, nothing for the ping
    }

    @Test
    void reconnectsAfterTheServerHangsUp() throws Exception {
        startServer(Mode.HANG_UP_AFTER_HELLO);
        client = newClient(url(), Duration.ofSeconds(30));

        client.start();

        // every new connection says hello again, and each hello triggers a refresh
        assertTrue(waitUntil(() -> signals.get() >= 3, 5000), "signals: " + signals.get());
    }

    @Test
    void recoversAfterTheServerAnswersWithErrors() throws Exception {
        startServer(Mode.ERROR_FIRST_TWO);
        client = newClient(url(), Duration.ofSeconds(30));

        client.start();

        assertTrue(waitUntil(() -> signals.get() >= 1, 5000), "signals: " + signals.get());
        assertTrue(requests.get() >= 3, "requests: " + requests.get());
    }

    @Test
    void reconnectsWhenTheStreamGoesQuiet() throws Exception {
        startServer(Mode.SILENT);
        client = newClient(url(), Duration.ofMillis(400));

        client.start();

        // the server never closes the stream, so only the idle timeout can cause request number 2
        assertTrue(waitUntil(() -> requests.get() >= 2, 6000), "requests: " + requests.get());
    }

    @Test
    void closeStopsReconnecting() throws Exception {
        startServer(Mode.HANG_UP_AFTER_HELLO);
        client = newClient(url(), Duration.ofSeconds(30));
        client.start();
        assertTrue(waitUntil(() -> requests.get() >= 2, 5000));

        client.close();
        Thread.sleep(300);   // let anything already under way finish
        int after = requests.get();
        Thread.sleep(700);

        assertEquals(after, requests.get());
        assertFalse(client.isConnected());
    }

    @Test
    void anUnreachableServerIsHandledQuietly() throws Exception {
        client = newClient("http://127.0.0.1:1", Duration.ofSeconds(30));

        client.start();
        Thread.sleep(800);

        assertEquals(0, signals.get());
        assertFalse(client.isConnected());
    }

    @Test
    void nonsenseSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SseChangeClient("http://localhost:8080", " ", Environment.PRODUCTION,
                        Duration.ofSeconds(1), Duration.ofSeconds(30),
                        new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(30)), () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new SseChangeClient("http://localhost:8080", "checkout-app", Environment.PRODUCTION,
                        Duration.ofSeconds(1), Duration.ZERO,
                        new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(30)), () -> { }));
    }
}